package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DurableRevocationCleanupTest {

    @Autowired
    private DurableRevocationTxExecutor txExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MutableClock clock;
    private DurableRevocationStore store;

    @BeforeEach
    void setup() {
        jdbcTemplate.update("DELETE FROM jwt_revocation");
        clock = new MutableClock(Instant.parse("2030-05-01T12:00:00.000Z"));
        store = new DurableRevocationStore(txExecutor, clock);
    }

    @AfterEach
    void teardown() {
        jdbcTemplate.update("DELETE FROM jwt_revocation");
    }

    @Test
    void cleanupPreservesRecordsWithinGraceSkewAndDeletesExpiredPastGraceSkew() {
        Instant now = clock.instant();
        // Grace skew default is 300,000 ms (5 minutes).
        // Cutoff = now - 300_000 ms.
        String jtiBeyondGrace = "jti-old-beyond-grace";
        String jtiExactCutoff = "jti-old-exact-cutoff";
        String jtiInsideGrace = "jti-expired-inside-grace";
        String jtiStillValid = "jti-still-valid";

        store.saveRevocation(jtiBeyondGrace, now.minusMillis(300_001L));
        store.saveRevocation(jtiExactCutoff, now.minusMillis(300_000L));
        store.saveRevocation(jtiInsideGrace, now.minusMillis(299_999L));
        store.saveRevocation(jtiStillValid, now.plusSeconds(300));

        int deleted = store.cleanupExpiredBatch(100);
        assertThat(deleted).isEqualTo(2);

        Integer remainingTotal = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class);
        assertThat(remainingTotal).isEqualTo(2);

        Integer insideGraceCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?",
                Integer.class, DurableRevocationStore.sha256Hex(jtiInsideGrace));
        Integer stillValidCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?",
                Integer.class, DurableRevocationStore.sha256Hex(jtiStillValid));
        assertThat(insideGraceCount).isEqualTo(1);
        assertThat(stillValidCount).isEqualTo(1);

        // Advance clock by 1 ms -> jtiInsideGrace crosses 300,000 ms grace boundary and becomes eligible
        clock.setInstant(now.plusMillis(1));
        assertThat(store.cleanupExpiredBatch(100)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(1);
    }

    @Test
    void cleanupStrictlyHonorsBatchLimitAcrossMultipleIterations() {
        Instant now = clock.instant();
        for (int i = 0; i < 10; i++) {
            store.saveRevocation("jti-expired-" + i, now.minusSeconds(400 + i));
        }
        for (int i = 0; i < 3; i++) {
            store.saveRevocation("jti-live-" + i, now.plusSeconds(600));
        }

        assertThat(store.cleanupExpiredBatch(4)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(9);

        assertThat(store.cleanupExpiredBatch(4)).isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(5);

        assertThat(store.cleanupExpiredBatch(4)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(3);

        assertThat(store.cleanupExpiredBatch(4)).isZero();
    }

    @Test
    void cleanupRejectsInvalidArgumentsAndPropagatesDbFailureAsStoreUnavailable() {
        assertThatThrownBy(() -> store.cleanupExpiredBatch(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.cleanupExpiredBatch(-5)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> store.cleanupExpiredBatch(10, -1L)).isInstanceOf(IllegalArgumentException.class);

        DurableRevocationTxExecutor failingExecutor = mock(DurableRevocationTxExecutor.class);
        when(failingExecutor.executeCleanupBatch(anyLong(), anyInt(), any()))
                .thenThrow(new DataAccessResourceFailureException("simulated cleanup DB failure"));
        DurableRevocationStore failingStore = new DurableRevocationStore(failingExecutor, clock);

        assertThatThrownBy(() -> failingStore.cleanupExpiredBatch(10))
                .isInstanceOf(RevocationStoreUnavailableException.class);
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;

        private MutableClock(Instant initial) {
            this.current = new AtomicReference<>(initial);
        }

        void setInstant(Instant next) {
            current.set(next);
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
