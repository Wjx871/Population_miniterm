package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DurableRevocationCheckpointTest {

    @Autowired
    private DurableRevocationStore store;

    @Autowired
    private DurableRevocationTxExecutor txExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetCheckpoint() {
        restoreReadyCheckpoint(0L);
    }

    @AfterEach
    void cleanupCheckpoint() {
        ensureCheckConstraintsRestored();
        restoreReadyCheckpoint(0L);
    }

    private void restoreReadyCheckpoint(long cutoffMs) {
        jdbcTemplate.update("DELETE FROM jwt_revocation");
        jdbcTemplate.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbcTemplate.update(
                "INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "VALUES (?, 'READY', ?, CURRENT_TIMESTAMP, 'TEST_RESET')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY, cutoffMs);
    }

    private void ensureCheckConstraintsRestored() {
        try {
            jdbcTemplate.execute(
                    "ALTER TABLE jwt_revocation_checkpoint "
                            + "ADD CONSTRAINT IF NOT EXISTS chk_jwt_checkpoint_ready_cutoff "
                            + "CHECK (checkpoint_status <> 'READY' OR (min_valid_iat_epoch_ms IS NOT NULL AND min_valid_iat_epoch_ms >= 0))");
        } catch (DataAccessException ignored) {
            // Constraint already exists
        }
    }

    @Test
    void missingCheckpointFailsClosedAndIsNeverAutoInitialized() {
        jdbcTemplate.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);

        AuthoritativeRevocationState state = store.readAuthoritativeState("jti-" + UUID.randomUUID());
        assertThat(state.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.STORE_UNAVAILABLE);
        assertThat(state.getFailureReason()).isEqualTo("MISSING_CHECKPOINT");
        assertThatThrownBy(state::isRevokedOrThrow).isInstanceOf(RevocationStoreUnavailableException.class);
        assertThatThrownBy(store::requireReadyCheckpoint).isInstanceOf(RevocationStoreUnavailableException.class);
        assertThatThrownBy(() -> store.advanceCheckpoint(1000L, "TEST_OPS"))
                .isInstanceOf(RevocationStoreUnavailableException.class);

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                Integer.class, DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assertThat(count).isZero();
    }

    @Test
    void uninitializedCheckpointFailsClosed() {
        jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'UNINITIALIZED', min_valid_iat_epoch_ms = NULL, activated_at = NULL "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);

        AuthoritativeRevocationState state = store.readAuthoritativeState("jti-" + UUID.randomUUID());
        assertThat(state.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.STORE_UNAVAILABLE);
        assertThat(state.getCheckpointStatus()).isEqualTo("UNINITIALIZED");
        assertThatThrownBy(state::isRevokedOrThrow).isInstanceOf(RevocationStoreUnavailableException.class);
        assertThatThrownBy(store::requireReadyCheckpoint).isInstanceOf(RevocationStoreUnavailableException.class);
    }

    @Test
    void invalidCheckpointFailsClosedAndCannotBePromotedByAdvance() {
        jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'INVALID', min_valid_iat_epoch_ms = 1000 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);

        AuthoritativeRevocationState state = store.readAuthoritativeState("jti-" + UUID.randomUUID());
        assertThat(state.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.STORE_UNAVAILABLE);
        assertThat(state.getCheckpointStatus()).isEqualTo("INVALID");
        assertThatThrownBy(state::isRevokedOrThrow).isInstanceOf(RevocationStoreUnavailableException.class);
        assertThatThrownBy(store::requireReadyCheckpoint).isInstanceOf(RevocationStoreUnavailableException.class);
        assertThatThrownBy(() -> store.advanceCheckpoint(5000L, "TEST_OPS"))
                .isInstanceOf(RevocationStoreUnavailableException.class);
    }

    @Test
    void readyWithNullOrNegativeCutoffIsRejectedByConstraintAndStoreFailClosed() {
        // 1. Verify DDL check constraint itself blocks READY + NULL and READY + negative
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = NULL "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY))
                .isInstanceOf(DataAccessException.class);
        assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = -1 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY))
                .isInstanceOf(DataAccessException.class);

        // 2. Temporarily drop constraint to simulate legacy/corrupted row and prove Store still fails closed
        jdbcTemplate.execute("ALTER TABLE jwt_revocation_checkpoint DROP CONSTRAINT chk_jwt_checkpoint_ready_cutoff");
        try {
            jdbcTemplate.update(
                    "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = NULL "
                            + "WHERE checkpoint_key = ?",
                    DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
            AuthoritativeRevocationState nullState = store.readAuthoritativeState("jti-" + UUID.randomUUID());
            assertThat(nullState.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.STORE_UNAVAILABLE);
            assertThat(nullState.getFailureReason()).isEqualTo("INVALID_CHECKPOINT_CUTOFF");
            assertThatThrownBy(store::requireReadyCheckpoint).isInstanceOf(RevocationStoreUnavailableException.class);

            jdbcTemplate.update(
                    "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = -100 "
                            + "WHERE checkpoint_key = ?",
                    DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
            AuthoritativeRevocationState negState = store.readAuthoritativeState("jti-" + UUID.randomUUID());
            assertThat(negState.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.STORE_UNAVAILABLE);
            assertThat(negState.getFailureReason()).isEqualTo("INVALID_CHECKPOINT_CUTOFF");
            assertThatThrownBy(store::requireReadyCheckpoint).isInstanceOf(RevocationStoreUnavailableException.class);
            assertThatThrownBy(() -> store.advanceCheckpoint(5000L, "TEST_OPS"))
                    .isInstanceOf(RevocationStoreUnavailableException.class);
        } finally {
            restoreReadyCheckpoint(0L);
            ensureCheckConstraintsRestored();
        }
    }

    @Test
    void readyWatermarkEnforcesRfc7519SecondPrecisionBoundary() {
        Instant cutoverInstant = Instant.ofEpochMilli(1_794_537_300_250L);
        long alignedCutoffMs = DurableRevocationStore.nextWholeUtcSecondCutoffMillis(cutoverInstant);
        assertThat(alignedCutoffMs).isEqualTo(1_794_537_301_000L);

        // Transition from UNINITIALIZED -> READY via explicit advanceCheckpoint
        jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'UNINITIALIZED', min_valid_iat_epoch_ms = NULL, activated_at = NULL "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);

        CheckpointSnapshot activated = store.advanceCheckpoint(alignedCutoffMs, "OPS_CUTOVER");
        assertThat(activated.isReady()).isTrue();
        assertThat(activated.minValidIatEpochMs()).isEqualTo(1_794_537_301_000L);
        assertThat(activated.activatedAt()).isNotNull();

        String cleanJti = "jti-clean-" + UUID.randomUUID();

        // Token issued in the same second before cutover (iat = 1794537300s) -> REJECTED
        Instant sameSecondPreCutoverIat = Instant.ofEpochSecond(1_794_537_300L);
        AuthoritativeRevocationState preState = store.readAuthoritativeState(cleanJti, sameSecondPreCutoverIat);
        assertThat(preState.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
        assertThat(preState.isCutoffRejected()).isTrue();

        // Missing iat -> REJECTED
        AuthoritativeRevocationState nullIatState = store.readAuthoritativeState(cleanJti, null);
        assertThat(nullIatState.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
        assertThat(nullIatState.isCutoffRejected()).isTrue();

        // Token issued at exact aligned cutoff second (iat = 1794537301s) -> NOT_REVOKED
        Instant atBoundaryIat = Instant.ofEpochSecond(1_794_537_301L);
        AuthoritativeRevocationState boundaryState = store.readAuthoritativeState(cleanJti, atBoundaryIat);
        assertThat(boundaryState.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);
        assertThat(boundaryState.isRevokedOrThrow()).isFalse();

        // Token issued after cutoff (iat = 1794537302s) -> NOT_REVOKED
        Instant afterBoundaryIat = Instant.ofEpochSecond(1_794_537_302L);
        AuthoritativeRevocationState afterState = store.readAuthoritativeState(cleanJti, afterBoundaryIat);
        assertThat(afterState.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);
    }

    @Test
    void monotonicAdvanceNeverRegressesAndIdempotentInitPreservesExistingReadyCutoff() {
        long initialCutoff = 1_794_537_400_000L;
        CheckpointSnapshot first = store.advanceCheckpoint(initialCutoff, "OPS_1");
        assertThat(first.minValidIatEpochMs()).isEqualTo(initialCutoff);

        // Attempting lower cutoff must NOT reduce watermark
        CheckpointSnapshot attemptedRollback = store.advanceCheckpoint(1_794_537_100_000L, "OPS_LOWER");
        assertThat(attemptedRollback.minValidIatEpochMs()).isEqualTo(initialCutoff);

        // Re-running non-overwriting H2 schema init must NOT reset READY watermark back to 0
        jdbcTemplate.update(
                "INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "SELECT 'GLOBAL_JWT_CUTOFF', 'READY', 0, CURRENT_TIMESTAMP, 'TEST_SCHEMA_REINIT' "
                        + "WHERE NOT EXISTS (SELECT 1 FROM jwt_revocation_checkpoint WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF')");
        CheckpointSnapshot afterReinit = store.requireReadyCheckpoint();
        assertThat(afterReinit.minValidIatEpochMs()).isEqualTo(initialCutoff);

        // Negative candidate rejected
        assertThatThrownBy(() -> store.advanceCheckpoint(-1L, "OPS_BAD"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void concurrentAdvanceCheckpointPreservesMaximumWatermark() throws Exception {
        int threads = 8;
        long base = 1_794_538_000_000L;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<CheckpointSnapshot>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < threads; i++) {
                // Reverse/interleaved cutoffs so threads contend with lower and higher values
                final long candidate = base + ((threads - 1 - i) * 1000L);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return store.advanceCheckpoint(candidate, "CONCURRENT_WORKER");
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<CheckpointSnapshot> future : futures) {
                CheckpointSnapshot snap = future.get(15, TimeUnit.SECONDS);
                assertThat(snap.isReady()).isTrue();
            }
        } finally {
            pool.shutdownNow();
        }

        long expectedMax = base + ((threads - 1) * 1000L);
        assertThat(store.requireReadyCheckpoint().minValidIatEpochMs()).isEqualTo(expectedMax);
    }
}
