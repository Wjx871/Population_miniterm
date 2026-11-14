package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.system.HealthController;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Step 2 Group D (Scenarios 25-30 + /api/health + cleanupExpired + RevocationEvents):
 * Login precedence, Redis outage resilience on login, pre-cutoff login block,
 * login-vs-cutoff race protection, /api/health non-sensitive status across modes,
 * 300,000ms bounded cleanupExpired with concurrency & DB fault isolation, and 6 new events.
 */
@SpringBootTest(properties = {
        "security.jwt.revocation.mode=DURABLE_DB_REQUIRED",
        "security.jwt.revocation.allow-zero-cutoff-for-test=true",
        "security.jwt.revocation.cleanup-batch-size=2",
        "population.redis.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:step2-login-health-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class DurableLoginHealthCleanupTest {

    private static final String SECRET_DB_URL = "jdbc:mysql://secret-db-host:3306/population_miniterm password=leak";
    private static final AtomicBoolean RACE_ADVANCE_ON_SECOND_CHECKPOINT_READ = new AtomicBoolean(false);
    private static final AtomicInteger CHECKPOINT_READ_COUNT = new AtomicInteger(0);
    private static final AtomicBoolean FAIL_CLEANUP_DELETE = new AtomicBoolean(false);

    @TestConfiguration
    static class Step2LoginCleanupHookConfig {
        @Bean
        static BeanPostProcessor loginRaceAndCleanupFaultProxy() {
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
                                if ("selectCheckpoint".equals(method.getName())
                                        && RACE_ADVANCE_ON_SECOND_CHECKPOINT_READ.get()) {
                                    int callIndex = CHECKPOINT_READ_COUNT.incrementAndGet();
                                    if (callIndex == 2) {
                                        long futureCutoffMs = (Instant.now().getEpochSecond() + 10L) * 1000L;
                                        target.advanceCheckpointMonotonic(
                                                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY,
                                                futureCutoffMs,
                                                java.time.LocalDateTime.now(ZoneOffset.UTC),
                                                "CONCURRENT_CUTOVER_RACE");
                                    }
                                }
                                if ("deleteByRevocationIds".equals(method.getName())
                                        && FAIL_CLEANUP_DELETE.get()) {
                                    throw new DataAccessResourceFailureException(SECRET_DB_URL);
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
    @MockBean private StringRedisTemplate redis;

    private volatile boolean redisDown;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        RACE_ADVANCE_ON_SECOND_CHECKPOINT_READ.set(false);
        CHECKPOINT_READ_COUNT.set(0);
        FAIL_CLEANUP_DELETE.set(false);
        revocationProperties.setAllowZeroCutoffForTest(true);
        redisDown = false;

        jdbc.update("DELETE FROM jwt_revocation");
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("UPDATE sys_user SET last_login_time = NULL, last_login_ip = NULL");
        resetCheckpointReady(0L);

        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenAnswer(call -> {
            if (redisDown) {
                throw new RedisConnectionFailureException("simulated-redis-down");
            }
            return connection;
        });
        when(connection.ping()).thenAnswer(call -> {
            if (redisDown) {
                throw new RedisConnectionFailureException("simulated-redis-down");
            }
            return "PONG";
        });
    }

    @AfterEach
    void tearDown() {
        RACE_ADVANCE_ON_SECOND_CHECKPOINT_READ.set(false);
        FAIL_CLEANUP_DELETE.set(false);
        revocationProperties.setAllowZeroCutoffForTest(true);
        resetCheckpointReady(0L);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void scenario25And26_loginPrecedenceAndRedisOutageResilienceInDurableMode() throws Exception {
        // Scenario 25: Invalid credentials (401) and disabled user/role (403) take precedence
        // even when checkpoint is INVALID.
        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'INVALID', min_valid_iat_epoch_ms = 1000 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);

        mockMvc.perform(login("admin", "wrong")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("missing", "123456")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("disabled", "123456")).andExpect(status().isForbidden());
        mockMvc.perform(login("disabled-role", "123456")).andExpect(status().isForbidden());

        // Valid credentials fail-closed 503 when checkpoint is INVALID, with zero login side effects
        mockMvc.perform(login("admin", "123456"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")));
        assertNoSuccessfulLoginForAdmin();

        // Scenario 26: With READY checkpoint, login succeeds (200) even when Redis is completely DOWN
        resetCheckpointReady(0L);
        redisDown = true;
        when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("simulated-redis-down"));

        mockMvc.perform(login("admin", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
        assertThat(loginSuccessCount()).isEqualTo(1);
        verify(redis, never()).hasKey(anyString());
    }

    @Test
    void scenario27_loginPreflightBlocksBeforeCutoffIsReachedAndSucceedsOnceReached() {
        Instant beforeCut = Instant.parse("2030-05-01T12:00:00.400Z");
        Instant cutoffInstant = Instant.parse("2030-05-01T12:00:01.000Z");
        MutableClock clock = new MutableClock(beforeCut);

        DurableRevocationStore clockedStore = new DurableRevocationStore(txExecutor, clock);
        clockedStore.advanceCheckpoint(cutoffInstant.toEpochMilli(), "TEST_CUTOVER");

        RevocationProperties props = new RevocationProperties();
        props.setMode(RevocationProperties.Mode.DURABLE_DB_REQUIRED);
        TokenRevocationService svc = new TokenRevocationService(
                mock(OptionalRedisService.class), props, clock, events, new MockEnvironment(), clockedStore);

        // Before UTC reaches cutoff (12:00:00.400 < 12:00:01.000) -> 503 fail-closed
        assertThatThrownBy(svc::requireLoginStore)
                .isInstanceOf(RevocationStoreUnavailableException.class);

        // Once UTC reaches exact cutoff second (12:00:01.000) -> passes
        clock.set(cutoffInstant);
        svc.requireLoginStore();
        svc.verifyIssuedTokenNotSuperseded(Instant.ofEpochSecond(cutoffInstant.getEpochSecond()));
    }

    @Test
    void scenario28_loginIssuanceRaceWithRuntimeCutoffAdvanceFailsClosed503WithNoLoginSideEffects() throws Exception {
        RACE_ADVANCE_ON_SECOND_CHECKPOINT_READ.set(true);
        CHECKPOINT_READ_COUNT.set(0);

        mockMvc.perform(login("admin", "123456"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")));

        assertNoSuccessfulLoginForAdmin();
    }

    @Test
    void scenario29_healthEndpointReportsNonSensitiveRevocationStatusAcrossAllModes() throws Exception {
        // 29a. HTTP /api/health permitAll without Bearer under DURABLE_DB_REQUIRED (READY + allowZeroCutoff=true)
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.revocationMode").value("DURABLE_DB_REQUIRED"))
                .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_PRIMARY_UP"));

        // 29b. Redis down does NOT prevent /api/health 200 and does NOT alter DB_PRIMARY_UP
        redisDown = true;
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.redisStatus").value("DOWN"))
                .andExpect(jsonPath("$.data.revocationMode").value("DURABLE_DB_REQUIRED"))
                .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_PRIMARY_UP"));
        verify(redis, never()).hasKey(anyString());
        verify(redis, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any());

        // 29c. Disallowed READY+0 or UNINITIALIZED/INVALID/missing -> 200 with DB_CHECKPOINT_NOT_READY
        revocationProperties.setAllowZeroCutoffForTest(false);
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_CHECKPOINT_NOT_READY"));

        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'UNINITIALIZED', min_valid_iat_epoch_ms = NULL "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_CHECKPOINT_NOT_READY"));
        assertThat(jdbc.queryForObject(
                "SELECT checkpoint_status FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                String.class, DurableRevocationStore.GLOBAL_CHECKPOINT_KEY)).isEqualTo("UNINITIALIZED");

        // 29d. DB primary down -> 200 with database=DOWN, revocationStoreStatus=DB_PRIMARY_DOWN, no secret leak
        DataSource failingDs = mock(DataSource.class);
        when(failingDs.getConnection()).thenThrow(new SQLException(SECRET_DB_URL));
        OptionalRedisService redisDownSvc = mock(OptionalRedisService.class);
        when(redisDownSvc.enabled()).thenReturn(true);
        when(redisDownSvc.ping()).thenReturn(false);
        RevocationProperties durableProps = new RevocationProperties();
        durableProps.setMode(RevocationProperties.Mode.DURABLE_DB_REQUIRED);
        HealthController dbDownHealth = new HealthController(
                failingDs, redisDownSvc, durableProps, providerOf(store), new MockEnvironment());
        Map<String, Object> downData = dbDownHealth.health().data();
        assertThat(downData)
                .containsEntry("database", "DOWN")
                .containsEntry("revocationMode", "DURABLE_DB_REQUIRED")
                .containsEntry("revocationStoreStatus", "DB_PRIMARY_DOWN");
        assertThat(downData.toString()).doesNotContain("secret-db-host", "password=leak", "jdbc:mysql");

        // 29e. SINGLE_INSTANCE_LOCAL and SHARED_REDIS_REQUIRED status strings
        DataSource upDs = mock(DataSource.class);
        java.sql.Connection conn = mock(java.sql.Connection.class);
        java.sql.Statement stmt = mock(java.sql.Statement.class);
        java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
        when(upDs.getConnection()).thenReturn(conn);
        when(conn.createStatement()).thenReturn(stmt);
        when(stmt.executeQuery("SELECT 1")).thenReturn(rs);
        when(rs.next()).thenReturn(true, false);
        when(rs.getInt(1)).thenReturn(1);
        when(rs.getObject(1)).thenReturn(1);

        RevocationProperties localProps = new RevocationProperties();
        localProps.setMode(RevocationProperties.Mode.SINGLE_INSTANCE_LOCAL);
        HealthController localHealth = new HealthController(
                upDs, redisDownSvc, localProps, providerOf(null), new MockEnvironment());
        assertThat(localHealth.health().data())
                .containsEntry("revocationMode", "SINGLE_INSTANCE_LOCAL")
                .containsEntry("revocationStoreStatus", "LOCAL_MEMORY");

        RevocationProperties sharedProps = new RevocationProperties();
        sharedProps.setMode(RevocationProperties.Mode.SHARED_REDIS_REQUIRED);
        HealthController sharedDownHealth = new HealthController(
                upDs, redisDownSvc, sharedProps, providerOf(null), new MockEnvironment());
        assertThat(sharedDownHealth.health().data())
                .containsEntry("revocationMode", "SHARED_REDIS_REQUIRED")
                .containsEntry("revocationStoreStatus", "REDIS_DOWN");

        OptionalRedisService redisUpSvc = mock(OptionalRedisService.class);
        when(redisUpSvc.enabled()).thenReturn(true);
        when(redisUpSvc.ping()).thenReturn(true);
        HealthController sharedUpHealth = new HealthController(
                upDs, redisUpSvc, sharedProps, providerOf(null), new MockEnvironment());
        assertThat(sharedUpHealth.health().data())
                .containsEntry("revocationMode", "SHARED_REDIS_REQUIRED")
                .containsEntry("revocationStoreStatus", "REDIS_UP");
    }

    @Test
    void scenario30_cleanupExpiredEnforces300sGraceSkewBoundedBatchConcurrencyAndFaultIsolation() throws Exception {
        Instant now = Instant.now();
        // 3 rows past 300,000ms grace skew -> eligible for bounded deletion (batchSize=2)
        for (int i = 0; i < 3; i++) {
            store.saveRevocation("cleanup-expired-" + i, now.minusMillis(300_000L + (i + 1) * 1000L));
        }
        // 1 row expired 299s ago (inside 300s grace skew) and 1 live row -> MUST survive
        String graceJti = "cleanup-grace-" + UUID.randomUUID();
        String liveJti = "cleanup-live-" + UUID.randomUUID();
        store.saveRevocation(graceJti, now.minusMillis(299_000L));
        store.saveRevocation(liveJti, now.plusSeconds(600));

        long cleanupEventBefore = events.count(RevocationEvents.REVOCATION_CLEANUP_BATCH);
        revocations.cleanupExpired(); // deletes 2
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(3);
        revocations.cleanupExpired(); // deletes remaining 1
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(2);
        assertThat(events.count(RevocationEvents.REVOCATION_CLEANUP_BATCH)).isEqualTo(cleanupEventBefore + 2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?",
                Integer.class, DurableRevocationStore.sha256Hex(graceJti))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?",
                Integer.class, DurableRevocationStore.sha256Hex(liveJti))).isEqualTo(1);

        // Concurrent safety: cleanupExpired() + revoke() simultaneously without exceptions
        for (int i = 0; i < 6; i++) {
            store.saveRevocation("cleanup-conc-exp-" + i, now.minusMillis(350_000L));
        }
        ExecutorService pool = Executors.newFixedThreadPool(4);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int t = 0; t < 2; t++) {
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < 5; i++) {
                        revocations.cleanupExpired();
                    }
                    return null;
                }));
            }
            for (int t = 0; t < 2; t++) {
                final int idx = t;
                futures.add(pool.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    for (int i = 0; i < 5; i++) {
                        revocations.revoke("cleanup-conc-live-" + idx + "-" + i, now.plusSeconds(600), 3L);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> f : futures) {
                f.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class))
                .isGreaterThanOrEqualTo(12); // grace + live + 10 conc-live

        // DB failure during cleanupExpired() is isolated (does NOT throw) and keeps persisted revocations intact
        store.saveRevocation("cleanup-fail-trigger", now.minusMillis(400_000L));
        int countBeforeFailure = jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class);
        long dbUnavailBefore = events.count(RevocationEvents.REVOCATION_DB_UNAVAILABLE);
        FAIL_CLEANUP_DELETE.set(true);
        revocations.cleanupExpired(); // must NOT throw
        FAIL_CLEANUP_DELETE.set(false);
        assertThat(events.count(RevocationEvents.REVOCATION_DB_UNAVAILABLE)).isEqualTo(dbUnavailBefore + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class))
                .isEqualTo(countBeforeFailure);
    }

    @Test
    void revocationEventsSixNewEventsAreSanitizedCountedAndRateLimited() {
        List<LogEvent> captured = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("step2-events-check", null, null, false, null) {
            @Override
            public void append(LogEvent event) {
                captured.add(event.toImmutable());
            }
        };
        Logger logger = (Logger) LogManager.getLogger(RevocationEvents.class);
        Level prev = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        try {
            RevocationEvents localEvents = new RevocationEvents();
            long started = System.nanoTime();
            localEvents.record(RevocationEvents.REVOCATION_DB_CONFIRMED, "LOGOUT",
                    RevocationProperties.Mode.DURABLE_DB_REQUIRED, "NONE", started);
            localEvents.record(RevocationEvents.REVOCATION_REJECTED_DB, "AUTHENTICATE",
                    RevocationProperties.Mode.DURABLE_DB_REQUIRED, "NONE", started);
            localEvents.record(RevocationEvents.REVOCATION_REJECTED_CUTOFF, "AUTHENTICATE",
                    RevocationProperties.Mode.DURABLE_DB_REQUIRED, "NONE", started);
            // Rate-limited *UNAVAILABLE events (each recorded twice -> counted 2x, logged 1x)
            for (int i = 0; i < 2; i++) {
                localEvents.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "AUTHENTICATE",
                        RevocationProperties.Mode.DURABLE_DB_REQUIRED,
                        "eyJhbGciOiJIUzI1NiJ9.eyJqdGkiOiJzZWNyZXQifQ.sig_longer_than_64_characters_jwt", started);
                localEvents.record(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE, "LOGOUT",
                        RevocationProperties.Mode.DURABLE_DB_REQUIRED, "redis://pw=secret", started);
                localEvents.record(RevocationEvents.REVOCATION_CACHE_READ_UNAVAILABLE, "AUTHENTICATE",
                        RevocationProperties.Mode.DURABLE_DB_REQUIRED, "CONNECTION", started);
            }

            assertThat(localEvents.count(RevocationEvents.REVOCATION_DB_CONFIRMED)).isEqualTo(1);
            assertThat(localEvents.count(RevocationEvents.REVOCATION_REJECTED_DB)).isEqualTo(1);
            assertThat(localEvents.count(RevocationEvents.REVOCATION_REJECTED_CUTOFF)).isEqualTo(1);
            assertThat(localEvents.count(RevocationEvents.REVOCATION_DB_UNAVAILABLE)).isEqualTo(2);
            assertThat(localEvents.count(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE)).isEqualTo(2);
            assertThat(localEvents.count(RevocationEvents.REVOCATION_CACHE_READ_UNAVAILABLE)).isEqualTo(2);
            assertThat(captured).hasSize(6); // 3 info + 3 rate-limited warn
            assertThat(captured).allSatisfy(evt -> assertThat(evt.getMessage().getFormattedMessage())
                    .doesNotContain("eyJhbGciOiJIUzI1NiJ9", "redis://", "pw=secret"));
        } finally {
            logger.setLevel(prev);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    private void resetCheckpointReady(long cutoffMs) {
        jdbc.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbc.update("INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "VALUES (?, 'READY', ?, CURRENT_TIMESTAMP, 'TEST_RESET')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY, cutoffMs);
    }

    private void assertNoSuccessfulLoginForAdmin() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT last_login_time, last_login_ip FROM sys_user WHERE username = 'admin'");
        assertThat(row.values()).containsOnlyNulls();
        assertThat(loginSuccessCount()).isZero();
    }

    private int loginSuccessCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGIN_SUCCESS' AND operation_result = 'SUCCESS'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String username, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
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
