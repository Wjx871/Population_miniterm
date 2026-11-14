package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wjx871.population.auth.AuthService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Step 2 Group B (Scenarios 8-16): HTTP JWT authentication filter, strict claims validation,
 * RFC 7519 iat-vs-cutoff boundary, immediate runtime watermark propagation, DB/checkpoint
 * fail-closed 503, Redis cache hit vs invalid checkpoint priority, and SecurityContext clearing.
 */
@SpringBootTest(properties = {
        "security.jwt.revocation.mode=DURABLE_DB_REQUIRED",
        "security.jwt.revocation.allow-zero-cutoff-for-test=true",
        "population.redis.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:step2-auth-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class DurableAuthFilterAndWatermarkTest {

    private static final String SECRET_LEAK_CAUSE = "jdbc:mysql://secret-host:3306/prod password=super-secret-pw";
    private static final AtomicBoolean SIMULATE_DB_READ_FAILURE = new AtomicBoolean(false);
    private static final AtomicBoolean DB_LOOKUP_INVOKED = new AtomicBoolean(false);

    @TestConfiguration
    static class MapperFaultConfig {
        @Bean
        static BeanPostProcessor authLookupFaultProxy() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (!(bean instanceof JwtRevocationMapper target)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            JwtRevocationMapper.class.getClassLoader(),
                            new Class<?>[] {JwtRevocationMapper.class},
                            (proxy, method, args) -> {
                                if ("selectAuthoritativeState".equals(method.getName())) {
                                    DB_LOOKUP_INVOKED.set(true);
                                    if (SIMULATE_DB_READ_FAILURE.get()) {
                                        throw new DataAccessResourceFailureException(SECRET_LEAK_CAUSE);
                                    }
                                }
                                try {
                                    return method.invoke(target, args);
                                } catch (InvocationTargetException ex) {
                                    throw ex.getTargetException();
                                }
                            });
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DurableRevocationStore store;
    @Autowired private DurableRevocationTxExecutor txExecutor;
    @Autowired private TokenRevocationService revocations;
    @Autowired private RevocationProperties revocationProperties;
    @Autowired private RevocationEvents events;
    @Autowired private AuthService authService;
    @Autowired private JwtService jwtService;
    @Value("${security.jwt.secret}") private String jwtSecret;
    @MockBean private StringRedisTemplate redis;

    private Set<String> redisMarkers;
    private RedisStringCommands strings;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        SIMULATE_DB_READ_FAILURE.set(false);
        DB_LOOKUP_INVOKED.set(false);
        revocationProperties.setAllowZeroCutoffForTest(true);
        ensureCheckpointConstraint();
        resetCheckpointReady(0L);
        jdbc.update("DELETE FROM jwt_revocation");

        redisMarkers = ConcurrentHashMap.newKeySet();
        RedisConnection connection = mock(RedisConnection.class);
        strings = mock(RedisStringCommands.class);
        when(redis.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(redis.hasKey(anyString())).thenAnswer(call -> redisMarkers.contains(call.getArgument(0, String.class)));
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any()))
                .thenAnswer(call -> call.<RedisCallback<Boolean>>getArgument(0).doInRedis(connection));
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenAnswer(call -> {
                    redisMarkers.add(new String(call.getArgument(0, byte[].class), StandardCharsets.UTF_8));
                    return true;
                });
    }

    @AfterEach
    void tearDown() {
        SIMULATE_DB_READ_FAILURE.set(false);
        revocationProperties.setAllowZeroCutoffForTest(true);
        ensureCheckpointConstraint();
        resetCheckpointReady(0L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(CurrentUserContext.getUserOrNull()).isNull();
    }

    @Test
    void scenario8And9_unrevokedReturns200AndDbRevokedReturns401() throws Exception {
        String token = jwtService.createToken(authService.loadUser("admin"));
        String jti = jwtService.parseClaims(token).getId();

        // Scenario 8: unrevoked valid JWT -> 200
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));

        // Scenario 9: revoked in DB (with empty Redis) -> 401
        store.saveRevocation(jti, Instant.now().plusSeconds(300));
        redisMarkers.clear();
        long beforeDbReject = events.count(RevocationEvents.REVOCATION_REJECTED_DB);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"))
                .andExpect(jsonPath("$.data").doesNotExist());
        assertThat(events.count(RevocationEvents.REVOCATION_REJECTED_DB)).isEqualTo(beforeDbReject + 1);
    }

    @Test
    void scenario10_missingRequiredClaimsAreRejected401AndSingleArgIsRevokedFailsClosed() throws Exception {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();

        String missingIat = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("admin")
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
        String missingJti = Jwts.builder()
                .subject("admin")
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
        String missingSub = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(300)))
                .signWith(key)
                .compact();
        String missingExp = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("admin")
                .issuedAt(Date.from(now))
                .signWith(key)
                .compact();
        String expired = Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject("admin")
                .issuedAt(Date.from(now.minusSeconds(120)))
                .expiration(Date.from(now.minusSeconds(10)))
                .signWith(key)
                .compact();

        for (String bad : new String[] {missingIat, missingJti, missingSub, missingExp, "not-a-jwt"}) {
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + bad))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("身份令牌无效"));
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        }

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已过期"));

        // Direct 1-arg isRevoked(jti) in DURABLE_DB_REQUIRED MUST fail closed (null issuedAt -> cutoff reject)
        long beforeCutoffReject = events.count(RevocationEvents.REVOCATION_REJECTED_CUTOFF);
        assertThat(revocations.isRevoked("unrevoked-" + UUID.randomUUID())).isTrue();
        assertThat(events.count(RevocationEvents.REVOCATION_REJECTED_CUTOFF)).isEqualTo(beforeCutoffReject + 1);
    }

    @Test
    void scenario11And12_cutoffLessThanAndExactBoundaryWithRfc7519Precision() throws Exception {
        // 12a. Deterministic MutableClock test for RFC 7519 second truncation vs ms cutoff
        Instant tBeforeWall = Instant.parse("2030-05-01T12:00:00.200Z"); // S.200s -> iat = S
        Instant tCutWall = Instant.parse("2030-05-01T12:00:00.500Z");    // S.500s -> cutoff = (S+1)*1000
        Instant tAfterWall = Instant.parse("2030-05-01T12:00:01.050Z");  // (S+1).050s -> iat = S+1

        MutableClock mutableClock = new MutableClock(tBeforeWall);
        JwtService clockedJwt = new JwtService(jwtSecret, 10, mutableClock);
        DurableRevocationStore clockedStore = new DurableRevocationStore(txExecutor, mutableClock);
        TokenRevocationService clockedRevocations = new TokenRevocationService(
                mock(com.wjx871.population.cache.OptionalRedisService.class),
                revocationProperties,
                mutableClock,
                events,
                new org.springframework.mock.env.MockEnvironment(),
                clockedStore);

        AuthenticatedUser admin = authService.loadUser("admin");
        JwtService.IssuedJwt tBefore = clockedJwt.createIssuedToken(admin);
        assertThat(tBefore.issuedAt().toEpochMilli()).isEqualTo(Instant.parse("2030-05-01T12:00:00Z").toEpochMilli());

        long alignedCutoff = DurableRevocationStore.nextWholeUtcSecondCutoffMillis(tCutWall);
        assertThat(alignedCutoff).isEqualTo(Instant.parse("2030-05-01T12:00:01Z").toEpochMilli());
        clockedStore.advanceCheckpoint(alignedCutoff, "TEST_BOUNDARY");

        mutableClock.set(tAfterWall);
        JwtService.IssuedJwt tAfter = clockedJwt.createIssuedToken(admin);
        assertThat(tAfter.issuedAt().toEpochMilli()).isEqualTo(alignedCutoff);

        assertThat(clockedRevocations.isRevoked(tBefore.jti(), tBefore.issuedAt(), tBefore.expiresAt())).isTrue();
        assertThat(clockedRevocations.isRevoked(tAfter.jti(), tAfter.issuedAt(), tAfter.expiresAt())).isFalse();

        // 12b & 11. HTTP verification for iat < cutoff (401) vs iat == cutoff (200)
        long nowSec = Instant.now().getEpochSecond();
        long cutoffMs = nowSec * 1000L;
        resetCheckpointReady(cutoffMs);

        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        String httpBefore = Jwts.builder()
                .id("http-before-" + UUID.randomUUID())
                .subject("admin")
                .issuedAt(Date.from(Instant.ofEpochSecond(nowSec - 1)))
                .expiration(Date.from(Instant.ofEpochSecond(nowSec + 600)))
                .signWith(key)
                .compact();
        String httpExactBoundary = Jwts.builder()
                .id("http-exact-" + UUID.randomUUID())
                .subject("admin")
                .issuedAt(Date.from(Instant.ofEpochSecond(nowSec)))
                .expiration(Date.from(Instant.ofEpochSecond(nowSec + 600)))
                .signWith(key)
                .compact();

        long cutoffRejectBefore = events.count(RevocationEvents.REVOCATION_REJECTED_CUTOFF);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + httpBefore))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
        assertThat(events.count(RevocationEvents.REVOCATION_REJECTED_CUTOFF)).isEqualTo(cutoffRejectBefore + 1);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + httpExactBoundary))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void scenario13_runtimeCutoffAdvanceTakesEffectOnImmediateNextHttpRequestWithoutCache() throws Exception {
        JwtService.IssuedJwt issued = jwtService.createIssuedToken(authService.loadUser("admin"));
        resetCheckpointReady(issued.issuedAt().toEpochMilli());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + issued.token()))
                .andExpect(status().isOk());

        // Advance cutoff directly in DB past issuedAt (no app restart, no Redis flush, no broadcast)
        store.advanceCheckpoint(issued.issuedAt().toEpochMilli() + 1000L, "OPS_RUNTIME_ADVANCE");

        // Very next HTTP request MUST immediately reject with 401
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + issued.token()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
    }

    @Test
    void scenario14_dbQueryFailureReturns503WithoutCauseLeakAndRecovers() throws Exception {
        String token = jwtService.createToken(authService.loadUser("admin"));
        long dbUnavailBefore = events.count(RevocationEvents.REVOCATION_DB_UNAVAILABLE);

        SIMULATE_DB_READ_FAILURE.set(true);
        String body = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain("secret-host", "super-secret-pw", "jdbc:mysql", "DataAccessResourceFailureException");
        assertThat(events.count(RevocationEvents.REVOCATION_DB_UNAVAILABLE)).isGreaterThan(dbUnavailBefore);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        // Recovers immediately when DB comes back
        SIMULATE_DB_READ_FAILURE.set(false);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void scenario15_runtimeCheckpointCorruptionReturns503FailClosed() throws Exception {
        String token = jwtService.createToken(authService.loadUser("admin"));

        // 15a. Missing checkpoint row -> 503
        jdbc.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assert503(token);

        // 15b. UNINITIALIZED -> 503
        jdbc.update("INSERT INTO jwt_revocation_checkpoint(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, updated_by) "
                        + "VALUES (?, 'UNINITIALIZED', NULL, 'CORRUPT_TEST')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assert503(token);

        // 15c. INVALID -> 503
        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'INVALID', min_valid_iat_epoch_ms = 1000 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assert503(token);

        // 15d. READY + NULL and READY + negative (simulate corrupted row without constraint) -> 503
        jdbc.execute("ALTER TABLE jwt_revocation_checkpoint DROP CONSTRAINT chk_jwt_checkpoint_ready_cutoff");
        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = NULL "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assert503(token);

        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = -10 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assert503(token);

        // 15e. Runtime READY + 0 when allowZeroCutoffForTest=false -> 503
        resetCheckpointReady(0L);
        ensureCheckpointConstraint();
        revocationProperties.setAllowZeroCutoffForTest(false);
        assert503(token);
    }

    @Test
    void scenario16_redisPositiveHit401TakesPrecedenceOverInvalidCheckpoint503AndClearsContext() throws Exception {
        String revokedToken = jwtService.createToken(authService.loadUser("admin"));
        String validToken = jwtService.createToken(authService.loadUser("viewer"));
        String revokedJti = jwtService.parseClaims(revokedToken).getId();
        String shaKey = "population:security:jwt:blacklist:" + DurableRevocationStore.sha256Hex(revokedJti);
        redisMarkers.add(shaKey);

        // Corrupt checkpoint to INVALID
        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'INVALID', min_valid_iat_epoch_ms = 1000 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);

        // Revoked token hits Redis fast path -> 401 without querying DB
        DB_LOOKUP_INVOKED.set(false);
        long cacheRejectBefore = events.count(RevocationEvents.REVOCATION_REJECTED_CACHE);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + revokedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
        assertThat(DB_LOOKUP_INVOKED.get()).isFalse();
        assertThat(events.count(RevocationEvents.REVOCATION_REJECTED_CACHE)).isEqualTo(cacheRejectBefore + 1);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(CurrentUserContext.getUserOrNull()).isNull();

        // Unrevoked token misses Redis -> hits DB -> sees INVALID checkpoint -> 503
        DB_LOOKUP_INVOKED.set(false);
        assert503(validToken);
        assertThat(DB_LOOKUP_INVOKED.get()).isTrue();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(CurrentUserContext.getUserOrNull()).isNull();

        // Subsequent anonymous request still rejected with 401 (no leaked SecurityContext)
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        verify(strings, never()).set(any(), any(), any(), any());
    }

    private void assert503(String token) throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")));
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(CurrentUserContext.getUserOrNull()).isNull();
    }

    private void resetCheckpointReady(long cutoffMs) {
        jdbc.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbc.update("INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "VALUES (?, 'READY', ?, CURRENT_TIMESTAMP, 'TEST_RESET')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY, cutoffMs);
    }

    private void ensureCheckpointConstraint() {
        try {
            jdbc.execute("ALTER TABLE jwt_revocation_checkpoint "
                    + "ADD CONSTRAINT IF NOT EXISTS chk_jwt_checkpoint_ready_cutoff "
                    + "CHECK (checkpoint_status <> 'READY' OR (min_valid_iat_epoch_ms IS NOT NULL AND min_valid_iat_epoch_ms >= 0))");
        } catch (DataAccessException ignored) {
            // already present
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void set(Instant instant) {
            current.set(instant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant(), zone);
        }

        @Override
        public Instant instant() {
            return current.get();
        }
    }
}
