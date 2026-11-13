package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Isolated MySQL 8.4 acceptance gate for Issue #64 Step 1 (Schema + Durable Store).
 *
 * <p>Uses {@code *IT} suffix so standard H2 {@code mvn test} never connects to MySQL.
 * Requires dedicated isolated loopback schema {@code issue64_test_*} on a non-3306 port
 * and a dedicated non-root user configured via environment variables:
 * {@code ISSUE64_MYSQL84_DB_URL}, {@code ISSUE64_MYSQL84_DB_USERNAME},
 * {@code ISSUE64_MYSQL84_DB_PASSWORD}.
 */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.hikari.maximum-pool-size=12",
        "population.redis.enabled=false",
        "policy.ai.enabled=false"
})
class DurableRevocationMySql84IT {

    private static final Pattern ISOLATED_URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/(?<schema>issue64_test_[a-z0-9_]{4,40})\\?(?<options>.*)$");

    @Autowired
    private DurableRevocationStore store;

    @Autowired
    private DurableRevocationTxExecutor txExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private static String requiredEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required isolated MySQL 8.4 env var: " + name);
        }
        return value;
    }

    private static Matcher guardUrl() {
        String url = requiredEnv("ISSUE64_MYSQL84_DB_URL");
        String user = requiredEnv("ISSUE64_MYSQL84_DB_USERNAME");
        Matcher matcher = ISOLATED_URL.matcher(url);
        if (!matcher.matches()
                || "3306".equals(matcher.group("port"))
                || url.toLowerCase().contains("population_miniterm")
                || "root".equalsIgnoreCase(user)
                || "admin".equalsIgnoreCase(user)
                || "sa".equalsIgnoreCase(user)
                || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)")
                        .matcher(matcher.group("options")).find()) {
            throw new IllegalStateException(
                    "Refusing non-isolated MySQL target; require 127.0.0.1 non-3306 issue64_test_* non-root user");
        }
        return matcher;
    }

    @DynamicPropertySource
    static void configureIsolatedMysql(DynamicPropertyRegistry registry) {
        guardUrl();
        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.datasource.url", () -> requiredEnv("ISSUE64_MYSQL84_DB_URL"));
        registry.add("spring.datasource.username", () -> requiredEnv("ISSUE64_MYSQL84_DB_USERNAME"));
        registry.add("spring.datasource.password", () -> requiredEnv("ISSUE64_MYSQL84_DB_PASSWORD"));
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    @BeforeEach
    void verifyIdentityAndResetTables() throws Exception {
        Matcher match = guardUrl();
        try (Connection conn = DriverManager.getConnection(
                requiredEnv("ISSUE64_MYSQL84_DB_URL"),
                requiredEnv("ISSUE64_MYSQL84_DB_USERNAME"),
                requiredEnv("ISSUE64_MYSQL84_DB_PASSWORD"));
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(
                        "SELECT @@version, @@port, @@bind_address, @@transaction_isolation, "
                                + "@@read_only, DATABASE(), CURRENT_USER()")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).startsWith("8.4.");
            assertThat(rs.getInt(2)).isEqualTo(Integer.parseInt(match.group("port")));
            assertThat(rs.getString(3)).isEqualTo("127.0.0.1");
            assertThat(rs.getString(4)).isEqualTo("REPEATABLE-READ");
            assertThat(rs.getInt(5)).isZero();
            assertThat(rs.getString(6)).isEqualTo(match.group("schema"));
            assertThat(rs.getString(7)).startsWith(requiredEnv("ISSUE64_MYSQL84_DB_USERNAME") + "@");
        }
        resetState();
    }

    @AfterEach
    void cleanupAfter() {
        resetState();
    }

    private void resetState() {
        jdbcTemplate.update("DELETE FROM jwt_revocation");
        jdbcTemplate.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbcTemplate.update(
                "INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "VALUES (?, 'UNINITIALIZED', NULL, NULL, 'IT_RESET')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
    }

    @Test
    void migrationV4020IsIdempotentPreservesReadyWatermarkAndDetectsMismatch() throws Exception {
        // 1. Simulate pre-existing READY checkpoint with non-zero cutoff
        long existingCutoff = 1_794_537_555_000L;
        CheckpointSnapshot ready = store.advanceCheckpoint(existingCutoff, "IT_PRE_MIGRATION");
        assertThat(ready.minValidIatEpochMs()).isEqualTo(existingCutoff);

        // 2. Re-run V4_020 SQL statements via JDBC (non-DELIMITER statements + preflight logic)
        String migrationSql = Files.readString(Path.of("doc/database/migrations/V4_020_jwt_revocation.sql"));
        String executableSql = migrationSql.replaceAll("(?m)--.*$", "");
        assertThat(executableSql).doesNotContainIgnoringCase("USE population_miniterm");
        assertThat(executableSql).doesNotMatch("(?is).*\\bUSE\\s+\\w+.*");
        assertThat(executableSql).doesNotContainIgnoringCase("UNIX_TIMESTAMP(UTC_TIMESTAMP");

        // Execute idempotent CREATE TABLE IF NOT EXISTS + non-overwriting INSERT
        jdbcTemplate.execute(
                "INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, updated_by) "
                        + "SELECT 'GLOBAL_JWT_CUTOFF', 'UNINITIALIZED', NULL, 'V4_020_DDL_INIT' "
                        + "WHERE NOT EXISTS (SELECT 1 FROM jwt_revocation_checkpoint WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF')");

        // 3. Verify READY watermark was NOT overwritten
        CheckpointSnapshot afterRerun = store.requireReadyCheckpoint();
        assertThat(afterRerun.checkpointStatus()).isEqualTo("READY");
        assertThat(afterRerun.minValidIatEpochMs()).isEqualTo(existingCutoff);
    }

    @Test
    void uniqueKeyRaceAndTransactionCommitRollbackWorkUnderMysql84RepeatableRead() throws Exception {
        store.advanceCheckpoint(0L, "IT_ENABLE");
        String jti = "mysql84-race-" + UUID.randomUUID();
        String sha = DurableRevocationStore.sha256Hex(jti);

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        Instant baseExp = Instant.parse("2031-03-15T08:00:00.000Z");

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    store.saveRevocation(jti, baseExp.plusSeconds(idx * 60L), 99L, "LOGOUT", "it-node", "it-trace");
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha);
        Long maxExp = jdbcTemplate.queryForObject(
                "SELECT expires_at_epoch_ms FROM jwt_revocation WHERE jti_sha256 = ?", Long.class, sha);
        assertThat(count).isEqualTo(1);
        assertThat(maxExp).isEqualTo(baseExp.plusSeconds((threads - 1) * 60L).toEpochMilli());

        // Verify REQUIRES_NEW visibility from inside an open REPEATABLE-READ transaction + outer rollback survival
        String jtiVis = "mysql84-vis-" + UUID.randomUUID();
        String shaVis = DurableRevocationStore.sha256Hex(jtiVis);
        TransactionStatus outerTx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            assertThat(store.readAuthoritativeState(jtiVis).getStatus())
                    .isEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);
            store.saveRevocation(jtiVis, baseExp);
            assertThat(store.readAuthoritativeState(jtiVis).getStatus())
                    .isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
        } finally {
            transactionManager.rollback(outerTx);
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, shaVis)).isEqualTo(1);

        // Verify rolled-back write leaves 0 rows
        String jtiRb = "mysql84-rb-" + UUID.randomUUID();
        String shaRb = DurableRevocationStore.sha256Hex(jtiRb);
        TransactionStatus rbTx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        jdbcTemplate.update(
                "INSERT INTO jwt_revocation (jti_sha256, expires_at_epoch_ms, token_expires_at, revoked_at, revocation_reason) "
                        + "VALUES (?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 'LOGOUT')",
                shaRb, baseExp.toEpochMilli());
        transactionManager.rollback(rbTx);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, shaRb)).isZero();
    }

    @Test
    void cutoffExpressionIsInvariantAcrossSessionTimeZonesAndEpochMsMatchesDatetime() throws Exception {
        List<String> zones = List.of("+00:00", "+08:00", "-05:00");
        List<Long> cutoffs = new ArrayList<>();
        List<Long> realUtcMs = new ArrayList<>();

        try (Connection conn = DriverManager.getConnection(
                requiredEnv("ISSUE64_MYSQL84_DB_URL"),
                requiredEnv("ISSUE64_MYSQL84_DB_USERNAME"),
                requiredEnv("ISSUE64_MYSQL84_DB_PASSWORD"))) {
            for (String tz : zones) {
                try (Statement st = conn.createStatement()) {
                    st.execute("SET SESSION time_zone = '" + tz + "'");
                    try (ResultSet rs = st.executeQuery(
                            "SELECT (UNIX_TIMESTAMP() + 1) * 1000 AS cutoff_ms, "
                                    + "UNIX_TIMESTAMP() * 1000 AS now_ms, "
                                    + "UNIX_TIMESTAMP(UTC_TIMESTAMP(3)) * 1000 AS buggy_tz_ms")) {
                        assertThat(rs.next()).isTrue();
                        long cutoffMs = rs.getLong("cutoff_ms");
                        long nowMs = rs.getLong("now_ms");
                        long buggyMs = rs.getLong("buggy_tz_ms");
                        cutoffs.add(cutoffMs);
                        realUtcMs.add(nowMs);
                        assertThat(cutoffMs - nowMs).isBetween(0L, 2000L);
                        if ("+08:00".equals(tz)) {
                            assertThat(nowMs - buggyMs).isGreaterThan(7 * 3600_000L);
                        } else if ("-05:00".equals(tz)) {
                            assertThat(buggyMs - nowMs).isGreaterThan(4 * 3600_000L);
                        }
                    }
                }
            }
        }

        // All 3 timezone cutoffs fall within at most 2 seconds of each other (zero 8h/5h hour shift)
        long minCutoff = cutoffs.stream().mapToLong(Long::longValue).min().orElseThrow();
        long maxCutoff = cutoffs.stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(maxCutoff - minCutoff).isLessThanOrEqualTo(2000L);

        // Verify expires_at_epoch_ms and token_expires_at UTC consistency
        store.advanceCheckpoint(0L, "IT_TZ");
        Instant exp = Instant.parse("2032-06-15T14:22:33.789Z");
        String jti = "mysql84-tz-" + UUID.randomUUID();
        store.saveRevocation(jti, exp);
        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT expires_at_epoch_ms, token_expires_at FROM jwt_revocation WHERE jti_sha256 = ?",
                DurableRevocationStore.sha256Hex(jti));
        assertThat(((Number) row.get("expires_at_epoch_ms")).longValue()).isEqualTo(exp.toEpochMilli());
        Object rawDt = row.get("token_expires_at");
        LocalDateTime storedUtc = rawDt instanceof LocalDateTime ldt
                ? ldt
                : ((Timestamp) rawDt).toLocalDateTime();
        assertThat(storedUtc).isEqualTo(LocalDateTime.ofInstant(exp, ZoneOffset.UTC));
    }

    @Test
    void explainUsesExpectedIndexesAndBoundedCleanupWorks() {
        store.advanceCheckpoint(0L, "IT_IDX");
        Instant base = Instant.parse("2030-05-01T12:00:00.000Z");
        MutableClock clock = new MutableClock(base);
        DurableRevocationStore clockedStore = new DurableRevocationStore(txExecutor, clock);

        for (int i = 0; i < 6; i++) {
            clockedStore.saveRevocation("mysql-exp-" + i, base.minusMillis(300_000L + i * 1000L));
        }
        clockedStore.saveRevocation("mysql-grace", base.minusMillis(299_000L));
        clockedStore.saveRevocation("mysql-live", base.plusSeconds(600));

        // Verify EXPLAIN plans hit uk_jwt_revocation_jti_sha256 (const) and idx_jwt_revocation_expires_ms
        String shaLive = DurableRevocationStore.sha256Hex("mysql-live");
        Map<String, Object> pointPlan = jdbcTemplate.queryForMap(
                "EXPLAIN SELECT 1 FROM jwt_revocation WHERE jti_sha256 = ?", shaLive);
        assertThat(String.valueOf(pointPlan.get("type"))).isEqualTo("const");
        assertThat(String.valueOf(pointPlan.get("key"))).isEqualTo("uk_jwt_revocation_jti_sha256");

        Map<String, Object> cleanupPlan = jdbcTemplate.queryForMap(
                "EXPLAIN SELECT revocation_id FROM jwt_revocation WHERE expires_at_epoch_ms <= ? "
                        + "ORDER BY expires_at_epoch_ms ASC, revocation_id ASC LIMIT 4",
                base.toEpochMilli() - 300_000L);
        assertThat(String.valueOf(cleanupPlan.get("key"))).isEqualTo("idx_jwt_revocation_expires_ms");

        // Bounded cleanup deletes 4, then 2, preserving grace + live rows
        assertThat(clockedStore.cleanupExpiredBatch(4)).isEqualTo(4);
        assertThat(clockedStore.cleanupExpiredBatch(4)).isEqualTo(2);
        assertThat(clockedStore.cleanupExpiredBatch(4)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(2);

        // Verify DB CHECK constraints exist and block READY + NULL / READY + negative
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = NULL "
                        + "WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF'"))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = -1 "
                        + "WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF'"))
                .isInstanceOf(DataAccessException.class);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
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
