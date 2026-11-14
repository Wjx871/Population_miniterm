package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.auth.AuthMapper;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.cache.RedisProperties;
import io.jsonwebtoken.Claims;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Step 2 Group C (Scenarios 17-24): DB-first logout commit, Redis miss/outage/write-failure
 * isolation, DB commit failure fail-closed 503, idempotent repeat/concurrent revoke,
 * JTI isolation, simulated cross-instance consistency without local Map, and post-commit
 * audit failure isolation.
 *
 * <p><strong>Scope boundary:</strong> Scenario 24 here verifies cross-instance visibility
 * across two independent {@link TokenRevocationService} instances sharing the durable store
 * in one test JVM. True multi-JVM process + real isolated Redis 8.10.3 verification
 * ({@code issue64_red_gate.py --stage target-green}) is deferred to Step 3.
 */
@SpringBootTest(properties = {
        "security.jwt.revocation.mode=DURABLE_DB_REQUIRED",
        "security.jwt.revocation.allow-zero-cutoff-for-test=true",
        "population.redis.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:step2-logout-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class DurableLogoutAndRedisFaultTest {

    private static final String PRIVATE_CAUSE = "redis://private-redis:6379 password=secret-redis-pw";
    private static final AtomicBoolean FAIL_DB_INSERT = new AtomicBoolean(false);
    private static final AtomicBoolean FAIL_DB_AFTER_INSERT_BEFORE_COMMIT = new AtomicBoolean(false);

    @TestConfiguration
    static class MapperWriteFaultConfig {
        @Bean
        static BeanPostProcessor logoutMapperFaultProxy() {
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
                                if ("insertRevocation".equals(method.getName())) {
                                    if (FAIL_DB_INSERT.get()) {
                                        throw new DataAccessResourceFailureException("simulated db insert failure");
                                    }
                                    Object res;
                                    try {
                                        res = method.invoke(target, args);
                                    } catch (InvocationTargetException ex) {
                                        throw ex.getTargetException();
                                    }
                                    if (FAIL_DB_AFTER_INSERT_BEFORE_COMMIT.get()) {
                                        throw new DataAccessResourceFailureException("simulated failure before commit");
                                    }
                                    return res;
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
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService authService;
    @Autowired private JwtService jwtService;
    @Autowired private DurableRevocationStore durableStore;
    @Autowired private TokenRevocationService revocations;
    @Autowired private RevocationProperties revocationProperties;
    @Autowired private RedisProperties redisProperties;
    @Autowired private Clock clock;
    @Autowired private RevocationEvents events;
    @MockBean private StringRedisTemplate redis;

    private Set<String> redisMarkers;
    private volatile RuntimeException redisReadFailure;
    private volatile RuntimeException redisWriteFailure;
    private volatile boolean redisWriteReturnsFalse;
    private RedisStringCommands strings;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        FAIL_DB_INSERT.set(false);
        FAIL_DB_AFTER_INSERT_BEFORE_COMMIT.set(false);
        redisReadFailure = null;
        redisWriteFailure = null;
        redisWriteReturnsFalse = false;
        redisMarkers = ConcurrentHashMap.newKeySet();

        jdbc.update("DELETE FROM jwt_revocation");
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbc.update("INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "VALUES (?, 'READY', 0, CURRENT_TIMESTAMP, 'TEST_RESET')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);

        RedisConnection connection = mock(RedisConnection.class);
        strings = mock(RedisStringCommands.class);
        when(redis.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(redis.hasKey(anyString())).thenAnswer(call -> {
            if (redisReadFailure != null) {
                throw redisReadFailure;
            }
            return redisMarkers.contains(call.getArgument(0, String.class));
        });
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any()))
                .thenAnswer(call -> call.<RedisCallback<Boolean>>getArgument(0).doInRedis(connection));
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenAnswer(call -> {
                    if (redisWriteFailure != null) {
                        throw redisWriteFailure;
                    }
                    if (redisWriteReturnsFalse) {
                        return false;
                    }
                    redisMarkers.add(new String(call.getArgument(0, byte[].class), StandardCharsets.UTF_8));
                    return true;
                });
    }

    @AfterEach
    void tearDown() {
        FAIL_DB_INSERT.set(false);
        FAIL_DB_AFTER_INSERT_BEFORE_COMMIT.set(false);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void scenario17_logoutCommitsSha256ToDbLogsAuditAndWritesSha256KeyToRedisWithPxat() throws Exception {
        String token = loginToken("admin", "123456");
        Claims claims = jwtService.parseClaims(token);
        String jti = claims.getId();
        String expectedSha = DurableRevocationStore.sha256Hex(jti);
        String expectedRedisKey = "population:security:jwt:blacklist:" + expectedSha;
        long dbConfirmedBefore = events.count(RevocationEvents.REVOCATION_DB_CONFIRMED);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(events.count(RevocationEvents.REVOCATION_DB_CONFIRMED)).isEqualTo(dbConfirmedBefore + 1);

        // Verify DB row stores sha256(jti), user_id, LOGOUT reason, and never raw jti or token
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT jti_sha256, user_id, expires_at_epoch_ms, revocation_reason FROM jwt_revocation WHERE jti_sha256 = ?",
                expectedSha);
        assertThat(row.get("jti_sha256")).isEqualTo(expectedSha);
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(3L);
        assertThat(row.get("revocation_reason")).isEqualTo("LOGOUT");
        assertThat(((Number) row.get("expires_at_epoch_ms")).longValue())
                .isEqualTo(claims.getExpiration().toInstant().toEpochMilli());
        assertThat(expectedSha).doesNotContain(jti);

        // Verify operation_log has LOGOUT SUCCESS
        assertThat(logoutSuccessCount()).isEqualTo(1);

        // Verify Redis key uses SHA256(JTI), not raw JTI, with PXAT expiration
        assertThat(redisMarkers).contains(expectedRedisKey);
        assertThat(redisMarkers).doesNotContain("population:security:jwt:blacklist:" + jti);
        ArgumentCaptor<Expiration> expiryCaptor = ArgumentCaptor.forClass(Expiration.class);
        verify(strings).set(eq(expectedRedisKey.getBytes(StandardCharsets.UTF_8)), any(byte[].class),
                expiryCaptor.capture(), eq(SetOption.UPSERT));
        assertThat(expiryCaptor.getValue().isUnixTimestamp()).isTrue();
        assertThat(expiryCaptor.getValue().getExpirationTimeInMilliseconds())
                .isEqualTo(claims.getExpiration().toInstant().toEpochMilli());
    }

    @Test
    void scenario18_redisMissFallsBackToDbAndBestEffortBackfillsCache() throws Exception {
        String token = loginToken("population", "123456");
        Claims claims = jwtService.parseClaims(token);
        String expectedRedisKey = "population:security:jwt:blacklist:" + DurableRevocationStore.sha256Hex(claims.getId());

        // Revoke into DB only (simulate Redis MISS)
        durableStore.saveRevocation(claims.getId(), claims.getExpiration().toInstant(), 2L, "LOGOUT", "n1", "t1");
        assertThat(redisMarkers).isEmpty();

        long dbRejectBefore = events.count(RevocationEvents.REVOCATION_REJECTED_DB);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));

        assertThat(events.count(RevocationEvents.REVOCATION_REJECTED_DB)).isEqualTo(dbRejectBefore + 1);
        assertThat(redisMarkers).contains(expectedRedisKey);

        // Even if Redis backfill write fails on MISS, still returns 401 and records REVOCATION_CACHE_WRITE_UNAVAILABLE
        redisMarkers.clear();
        redisWriteFailure = new RedisConnectionFailureException(PRIVATE_CAUSE);
        long cacheWriteFailBefore = events.count(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
        assertThat(events.count(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE))
                .isEqualTo(cacheWriteFailBefore + 1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONNECTION", "TIMEOUT"})
    void scenario19_redisReadFailureFallsBackToAuthoritativeDbForBothRevokedAndValidTokens(String category)
            throws Exception {
        String revokedToken = loginToken("admin", "123456");
        String validToken = loginToken("viewer", "123456");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + revokedToken))
                .andExpect(status().isOk());

        redisReadFailure = "TIMEOUT".equals(category)
                ? new QueryTimeoutException(PRIVATE_CAUSE)
                : new RedisConnectionFailureException(PRIVATE_CAUSE);

        long cacheReadFailBefore = events.count(RevocationEvents.REVOCATION_CACHE_READ_UNAVAILABLE);
        // Revoked token -> 401 via MySQL/H2 authoritative store (NOT 503)
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + revokedToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));

        // Valid token -> 200 via MySQL/H2 authoritative store (NOT 503)
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("viewer"));

        assertThat(events.count(RevocationEvents.REVOCATION_CACHE_READ_UNAVAILABLE))
                .isEqualTo(cacheReadFailBefore + 2);
    }

    @Test
    void scenario20_redisWriteFailureAfterDbCommitStillReturns200AndRejectsSubsequentAuth() throws Exception {
        String token = loginToken("admin", "123456");
        String sha = DurableRevocationStore.sha256Hex(jwtService.parseClaims(token).getId());
        redisWriteFailure = new RedisConnectionFailureException(PRIVATE_CAUSE);

        long dbConfirmedBefore = events.count(RevocationEvents.REVOCATION_DB_CONFIRMED);
        long cacheWriteUnavailBefore = events.count(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE);

        // Logout MUST still return 200 because DB commit succeeded
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertThat(events.count(RevocationEvents.REVOCATION_DB_CONFIRMED)).isEqualTo(dbConfirmedBefore + 1);
        assertThat(events.count(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE))
                .isEqualTo(cacheWriteUnavailBefore + 1);
        assertThat(logoutSuccessCount()).isEqualTo(1);
        assertThat(redisMarkers).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha))
                .isEqualTo(1);

        // Subsequent auth hits DB and returns 401
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
    }

    @Test
    void scenario21_dbCommitFailureReturns503WithoutWritingRedisOrLogoutSuccessAudit() throws Exception {
        String token = loginToken("admin", "123456");
        String sha = DurableRevocationStore.sha256Hex(jwtService.parseClaims(token).getId());
        FAIL_DB_AFTER_INSERT_BEFORE_COMMIT.set(true);

        long dbUnavailBefore = events.count(RevocationEvents.REVOCATION_DB_UNAVAILABLE);
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")));

        assertThat(events.count(RevocationEvents.REVOCATION_DB_UNAVAILABLE)).isEqualTo(dbUnavailBefore + 1);
        verify(strings, never()).set(any(), any(), any(), any());
        assertThat(redisMarkers).isEmpty();
        assertThat(logoutSuccessCount()).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha))
                .isZero();

        // Since DB rolled back and Redis was not written, token remains unrevoked
        FAIL_DB_AFTER_INSERT_BEFORE_COMMIT.set(false);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void scenario22_repeatAndConcurrentRevokeAreIdempotentAndKeepMaxExpiry() throws Exception {
        String token = loginToken("admin", "123456");
        Claims claims = jwtService.parseClaims(token);
        String jti = claims.getId();
        String sha = DurableRevocationStore.sha256Hex(jti);

        // HTTP first logout 200, second HTTP logout with same token is rejected 401 by auth filter
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());

        // Service-level repeat revoke is idempotent and monotonically retains maximum expiry
        Instant expBase = claims.getExpiration().toInstant();
        Instant expExtended = expBase.plusSeconds(300);
        Instant expShorter = expBase.plusSeconds(30);
        revocations.revoke(jti, expExtended, 3L);
        revocations.revoke(jti, expShorter, 3L);

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "SELECT expires_at_epoch_ms FROM jwt_revocation WHERE jti_sha256 = ?", Long.class, sha))
                .isEqualTo(expExtended.toEpochMilli());

        // Concurrent service revoke across 8 threads
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < threads; i++) {
                final Instant candidate = expExtended.plusSeconds(i * 10L);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    revocations.revoke(jti, candidate, 3L);
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha))
                .isEqualTo(1);
    }

    @Test
    void scenario23_differentJtisForSameAndOtherUsersAreIsolated() throws Exception {
        String t1 = loginToken("admin", "123456");
        String t2 = loginToken("admin", "123456");
        String tOther = loginToken("viewer", "123456");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + t1))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t1))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + t2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + tOther))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("viewer"));
    }

    @Test
    void scenario24_simulatedCrossInstanceWithoutLocalMapOrRedisObservesRevocationImmediately() throws Exception {
        OptionalRedisService emptyRedisA = new OptionalRedisService(redis, json, redisProperties);
        OptionalRedisService emptyRedisB = new OptionalRedisService(redis, json, redisProperties);
        TokenRevocationService instanceA = new TokenRevocationService(
                emptyRedisA, revocationProperties, clock, events, new MockEnvironment(), durableStore);
        TokenRevocationService instanceB = new TokenRevocationService(
                emptyRedisB, revocationProperties, clock, events, new MockEnvironment(), durableStore);

        String token = loginToken("admin", "123456");
        Claims claims = jwtService.parseClaims(token);
        String jti = claims.getId();
        Instant iat = claims.getIssuedAt().toInstant();
        Instant exp = claims.getExpiration().toInstant();

        assertThat(instanceB.isRevoked(jti, iat, exp)).isFalse();

        // Instance A revokes; clear Redis markers to simulate empty Redis / lost Redis cache
        instanceA.revoke(jti, exp, 3L);
        redisMarkers.clear();

        // Independent Instance B has zero local entries and immediately observes DB revocation
        assertThat(instanceB.localEntryCount()).isZero();
        assertThat(instanceA.localEntryCount()).isZero();
        assertThat(instanceB.isRevoked(jti, iat, exp)).isTrue();
    }

    @Test
    void postCommitAuditFailureDoesNotRollBackCommittedRevocationOrReturn500() {
        OperationLogService failingAudit = mock(OperationLogService.class);
        doThrow(new CannotCreateTransactionException("simulated audit TX failure"))
                .when(failingAudit).record(any(), eq("LOGOUT"), eq("SUCCESS"), any(), any());

        AuthService isolatedAuth = new AuthService(
                mock(AuthMapper.class),
                mock(PasswordEncoder.class),
                jwtService,
                failingAudit,
                revocations);

        AuthenticatedUser admin = authService.loadUser("admin");
        String token = jwtService.createToken(admin);
        String jti = jwtService.parseClaims(token).getId();
        String sha = DurableRevocationStore.sha256Hex(jti);

        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/logout");
        req.addHeader("Authorization", "Bearer " + token);

        // Must NOT throw despite OperationLogService REQUIRES_NEW proxy failure
        isolatedAuth.logout(admin, req);

        // DB revocation remains committed and active
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha))
                .isEqualTo(1);
        assertThat(revocations.isRevoked(jti, jwtService.parseClaims(token).getIssuedAt().toInstant(),
                jwtService.parseClaims(token).getExpiration().toInstant())).isTrue();
    }

    private String loginToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private int logoutSuccessCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGOUT' AND operation_result = 'SUCCESS'",
                Integer.class);
        return count == null ? 0 : count;
    }
}
