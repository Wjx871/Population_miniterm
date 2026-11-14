package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.system.HealthController;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.mock.env.MockEnvironment;

/**
 * Step 2 Group A (Scenarios 1-7): Three-mode configuration compatibility,
 * DURABLE_DB_REQUIRED startup validation, production zero-cutoff protection,
 * no-auto-activation, and anti-disguise checks.
 */
@Tag("unit")
class DurableRevocationConfigurationStep2Test {

    private static final String PREFIX = "security.jwt.revocation.";

    private ApplicationContextRunner durableRunner(DurableRevocationStore store, boolean redisEnabled) {
        return new ApplicationContextRunner()
                .withUserConfiguration(Step2ConfigUnderTest.class)
                .withBean(OptionalRedisService.class, () -> redisClient(redisEnabled))
                .withBean(DurableRevocationStore.class, () -> store);
    }

    @Test
    void scenario1And2_localAndSharedRedisModesRemainCompatibleAndWarnInProd() {
        // Scenario 1: SINGLE_INSTANCE_LOCAL starts without Redis in non-prod and works locally
        new ApplicationContextRunner()
                .withUserConfiguration(Step2ConfigUnderTest.class)
                .withBean(OptionalRedisService.class, () -> redisClient(false))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RevocationProperties props = context.getBean(RevocationProperties.class);
                    assertThat(props.getMode()).isEqualTo(RevocationProperties.Mode.SINGLE_INSTANCE_LOCAL);
                    assertThat(props.getCleanupGraceSkew())
                            .isEqualTo(Duration.ofMillis(DurableRevocationStore.DEFAULT_CLEANUP_GRACE_SKEW_MS));
                    assertThat(props.isAllowZeroCutoffForTest()).isFalse();
                });

        // Scenario 2: SHARED_REDIS_REQUIRED fails if redis disabled, starts when enabled, warns in prod
        new ApplicationContextRunner()
                .withUserConfiguration(Step2ConfigUnderTest.class)
                .withBean(OptionalRedisService.class, () -> redisClient(false))
                .withPropertyValues(PREFIX + "mode=SHARED_REDIS_REQUIRED")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("population.redis.enabled=true"));

        List<String> logMessages = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("shared-prod-warn", null, null, false, null) {
            @Override
            public void append(LogEvent event) {
                logMessages.add(event.getMessage().getFormattedMessage());
            }
        };
        Logger logger = (Logger) LogManager.getLogger(TokenRevocationService.class);
        Level prev = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            new ApplicationContextRunner()
                    .withUserConfiguration(Step2ConfigUnderTest.class)
                    .withBean(OptionalRedisService.class, () -> redisClient(true))
                    .withPropertyValues(PREFIX + "mode=SHARED_REDIS_REQUIRED", PREFIX + "production=true")
                    .run(context -> assertThat(context).hasNotFailed());
            assertThat(logMessages).anySatisfy(msg -> assertThat(msg)
                    .contains("event=REVOCATION_LEGACY_REDIS_MODE_WARNING", "mode=SHARED_REDIS_REQUIRED"));
        } finally {
            logger.setLevel(prev);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void scenario3_durableModeStartsWithReadyNonZeroCheckpointWithOrWithoutRedis() {
        DurableRevocationStore store = mock(DurableRevocationStore.class);
        when(store.requireReadyCheckpoint()).thenReturn(readySnapshot(1_794_537_301_000L));

        for (boolean redisEnabled : new boolean[] {false, true}) {
            durableRunner(store, redisEnabled)
                    .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        RevocationProperties props = context.getBean(RevocationProperties.class);
                        assertThat(props.getMode()).isEqualTo(RevocationProperties.Mode.DURABLE_DB_REQUIRED);
                        assertThat(props.getCleanupGraceSkew()).isEqualTo(Duration.ofMillis(300_000L));
                    });
        }

        // Production with non-zero READY checkpoint also starts cleanly
        durableRunner(store, false)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void scenario4_durableModeRefusesStartupOnMissingUninitializedInvalidNullNegativeOrDbError() {
        // 4a. Missing DurableRevocationStore bean
        new ApplicationContextRunner()
                .withUserConfiguration(Step2ConfigUnderTest.class)
                .withBean(OptionalRedisService.class, () -> redisClient(false))
                .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));

        // 4b. requireReadyCheckpoint throws RevocationStoreUnavailableException (missing/UNINITIALIZED/INVALID/DB down)
        for (Throwable cause : List.of(
                new IllegalStateException("Missing checkpoint row: GLOBAL_JWT_CUTOFF"),
                new IllegalStateException("Untrusted checkpoint state: status=UNINITIALIZED, cutoff=null"),
                new IllegalStateException("Untrusted checkpoint state: status=INVALID, cutoff=1000"),
                new DataAccessResourceFailureException("Simulated MySQL connection failure"))) {
            DurableRevocationStore failingStore = mock(DurableRevocationStore.class);
            when(failingStore.requireReadyCheckpoint()).thenThrow(new RevocationStoreUnavailableException(cause));
            durableRunner(failingStore, true)
                    .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED")
                    .run(context -> {
                        assertThat(context).hasFailed();
                        Throwable failure = context.getStartupFailure();
                        assertThat(hasThrowableInChain(failure, IllegalStateException.class)).isTrue();
                        assertThat(failure).hasRootCauseInstanceOf(cause.getClass());
                    });
        }

        // 4c. Corrupted non-ready snapshot returned directly (READY+null / READY+negative / null snapshot)
        for (CheckpointSnapshot badSnapshot : new CheckpointSnapshot[] {
                null,
                new CheckpointSnapshot(DurableRevocationStore.GLOBAL_CHECKPOINT_KEY, "READY", null, null, "X", null),
                new CheckpointSnapshot(DurableRevocationStore.GLOBAL_CHECKPOINT_KEY, "READY", -1L, null, "X", null),
                new CheckpointSnapshot(DurableRevocationStore.GLOBAL_CHECKPOINT_KEY, "UNINITIALIZED", null, null, "X", null)
        }) {
            DurableRevocationStore badStore = mock(DurableRevocationStore.class);
            when(badStore.requireReadyCheckpoint()).thenReturn(badSnapshot);
            durableRunner(badStore, false)
                    .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED")
                    .run(context -> assertThat(context).hasFailed().getFailure()
                            .hasRootCauseInstanceOf(IllegalStateException.class));
        }

        // 4d. Negative cleanupGraceSkew rejected
        DurableRevocationStore okStore = mock(DurableRevocationStore.class);
        when(okStore.requireReadyCheckpoint()).thenReturn(readySnapshot(1_794_537_301_000L));
        durableRunner(okStore, false)
                .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED", PREFIX + "cleanup-grace-skew=-1s")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("Invalid JWT revocation mode or cleanup configuration"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void scenario5_zeroCutoffRefusedInProductionAndWithoutTestSwitch(String profile) {
        DurableRevocationStore zeroCutoffStore = mock(DurableRevocationStore.class);
        when(zeroCutoffStore.requireReadyCheckpoint()).thenReturn(readySnapshot(0L));

        // Non-prod WITHOUT allowZeroCutoffForTest=true MUST refuse READY+0
        durableRunner(zeroCutoffStore, false)
                .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("READY+0"));

        // Non-prod WITH explicit allowZeroCutoffForTest=true succeeds (controlled test fixture only)
        durableRunner(zeroCutoffStore, false)
                .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED",
                        PREFIX + "allow-zero-cutoff-for-test=true")
                .run(context -> assertThat(context).hasNotFailed());

        // Production profile WITH allowZeroCutoffForTest=true MUST still fail
        durableRunner(zeroCutoffStore, false)
                .withInitializer(ctx -> ctx.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED",
                        PREFIX + "allow-zero-cutoff-for-test=true")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));

        // Explicit production=true flag WITH allowZeroCutoffForTest=true MUST also fail
        durableRunner(zeroCutoffStore, false)
                .withPropertyValues(PREFIX + "mode=DURABLE_DB_REQUIRED",
                        PREFIX + "production=true",
                        PREFIX + "allow-zero-cutoff-for-test=true")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void scenario6_startupAndHealthNeverAutoInitializeOrRepairCheckpointInDatabase() {
        EmbeddedDatabase db = new EmbeddedDatabaseBuilder()
                .setType(EmbeddedDatabaseType.H2)
                .generateUniqueName(true)
                .build();
        try {
            org.springframework.jdbc.core.JdbcTemplate jdbc = new org.springframework.jdbc.core.JdbcTemplate(db);
            jdbc.execute("""
                    CREATE TABLE jwt_revocation_checkpoint (
                        checkpoint_key VARCHAR(64) PRIMARY KEY,
                        checkpoint_status VARCHAR(20) NOT NULL DEFAULT 'UNINITIALIZED',
                        min_valid_iat_epoch_ms BIGINT NULL,
                        activated_at TIMESTAMP NULL,
                        updated_by VARCHAR(64) NULL,
                        updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            // Case A: missing row stays missing after failed validation and /api/health
            DurableRevocationStore store = new RealJdbcProbeStore(jdbc);
            RevocationProperties props = new RevocationProperties();
            props.setMode(RevocationProperties.Mode.DURABLE_DB_REQUIRED);
            TokenRevocationService svc = new TokenRevocationService(
                    redisClient(false), props, Clock.systemUTC(), new RevocationEvents(), new MockEnvironment(), store);
            assertThatThrownBy(svc::validateConfiguration).isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation_checkpoint", Integer.class)).isZero();

            HealthController health = new HealthController(
                    db, redisClient(false), props, providerOf(store), new MockEnvironment());
            Map<String, Object> bodyMissing = health.health().data();
            assertThat(bodyMissing)
                    .containsEntry("revocationMode", "DURABLE_DB_REQUIRED")
                    .containsEntry("revocationStoreStatus", "DB_CHECKPOINT_NOT_READY");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation_checkpoint", Integer.class)).isZero();

            // Case B: UNINITIALIZED stays UNINITIALIZED + NULL after failed validation and /api/health
            jdbc.update("""
                    INSERT INTO jwt_revocation_checkpoint(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, updated_by)
                    VALUES ('GLOBAL_JWT_CUTOFF', 'UNINITIALIZED', NULL, 'V4_020_DDL_INIT')
                    """);
            assertThatThrownBy(svc::validateConfiguration).isInstanceOf(IllegalStateException.class);
            Map<String, Object> bodyUninit = health.health().data();
            assertThat(bodyUninit).containsEntry("revocationStoreStatus", "DB_CHECKPOINT_NOT_READY");
            Map<String, Object> row = jdbc.queryForMap(
                    "SELECT checkpoint_status, min_valid_iat_epoch_ms FROM jwt_revocation_checkpoint WHERE checkpoint_key='GLOBAL_JWT_CUTOFF'");
            assertThat(row.get("checkpoint_status")).isEqualTo("UNINITIALIZED");
            assertThat(row.get("min_valid_iat_epoch_ms")).isNull();
        } finally {
            db.shutdown();
        }
    }

    @Test
    void scenario7_legacyModesNeverDisguiseAsDurableDbRequiredAndKeepRawJtiKeys() {
        OptionalRedisService redis = redisClient(true);
        DurableRevocationStore durableStore = mock(DurableRevocationStore.class);
        RevocationProperties props = new RevocationProperties();
        props.setMode(RevocationProperties.Mode.SHARED_REDIS_REQUIRED);

        TokenRevocationService sharedSvc = new TokenRevocationService(
                redis, props, Clock.systemUTC(), new RevocationEvents(), new MockEnvironment(), durableStore);
        sharedSvc.validateConfiguration();
        sharedSvc.revoke("raw-legacy-jti", Instant.now().plusSeconds(60));

        // Legacy SHARED_REDIS_REQUIRED uses raw jti key and NEVER touches durableStore
        verify(redis).strictPutMarker(org.mockito.ArgumentMatchers.eq("population:security:jwt:blacklist:raw-legacy-jti"), any());
        verify(durableStore, never()).saveRevocation(anyString(), any(), any(), any(), any(), any());
        verify(durableStore, never()).readAuthoritativeState(anyString(), any());
        verify(durableStore, never()).requireReadyCheckpoint();

        HealthController health = new HealthController(
                mock(DataSource.class), redis, props, providerOf(durableStore), new MockEnvironment());
        when(redis.ping()).thenReturn(true);
        assertThat(health.health().data())
                .containsEntry("revocationMode", "SHARED_REDIS_REQUIRED")
                .containsEntry("revocationStoreStatus", "REDIS_UP");
    }

    private static boolean hasThrowableInChain(Throwable failure, Class<? extends Throwable> type) {
        for (Throwable cur = failure; cur != null; cur = cur.getCause()) {
            if (type.isInstance(cur)) {
                return true;
            }
        }
        return false;
    }

    private static CheckpointSnapshot readySnapshot(long cutoffMs) {
        return new CheckpointSnapshot(
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY,
                DurableRevocationStore.CHECKPOINT_STATUS_READY,
                cutoffMs,
                LocalDateTime.now(),
                "TEST",
                LocalDateTime.now());
    }

    private static OptionalRedisService redisClient(boolean enabled) {
        OptionalRedisService redis = mock(OptionalRedisService.class);
        when(redis.enabled()).thenReturn(enabled);
        when(redis.key(anyString())).thenAnswer(call -> "population:" + call.getArgument(0));
        return redis;
    }

    @SuppressWarnings("unchecked")
    private static <T> ObjectProvider<T> providerOf(T instance) {
        ObjectProvider<T> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(instance);
        return provider;
    }

    private static final class RealJdbcProbeStore extends DurableRevocationStore {
        private final org.springframework.jdbc.core.JdbcTemplate jdbc;

        private RealJdbcProbeStore(org.springframework.jdbc.core.JdbcTemplate jdbc) {
            super(null, Clock.systemUTC());
            this.jdbc = jdbc;
        }

        @Override
        public CheckpointSnapshot requireReadyCheckpoint() {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                    GLOBAL_CHECKPOINT_KEY);
            if (rows.isEmpty()) {
                throw new RevocationStoreUnavailableException(new IllegalStateException("Missing checkpoint row"));
            }
            Map<String, Object> row = rows.get(0);
            String status = (String) row.get("checkpoint_status");
            Number cutoffNum = (Number) row.get("min_valid_iat_epoch_ms");
            Long cutoff = cutoffNum == null ? null : cutoffNum.longValue();
            CheckpointSnapshot snap = new CheckpointSnapshot(GLOBAL_CHECKPOINT_KEY, status, cutoff, null, "TEST", null);
            if (!snap.isReady()) {
                throw new RevocationStoreUnavailableException(
                        new IllegalStateException("Untrusted checkpoint state: " + status));
            }
            return snap;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RevocationProperties.class)
    static class Step2ConfigUnderTest {
        @Bean
        TokenRevocationService tokenRevocationService(
                OptionalRedisService redis,
                RevocationProperties properties,
                Environment environment,
                ObjectProvider<DurableRevocationStore> durableStoreProvider) {
            return new TokenRevocationService(
                    redis, properties, Clock.systemUTC(), new RevocationEvents(), environment, durableStoreProvider);
        }
    }
}
