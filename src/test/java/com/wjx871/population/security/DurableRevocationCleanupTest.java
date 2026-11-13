package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class DurableRevocationCleanupTest {

    private static final AtomicReference<BeforeDeleteHook> BEFORE_DELETE_HOOK = new AtomicReference<>();

    @TestConfiguration
    static class CleanupMapperInterceptionConfig {
        @Bean
        static BeanPostProcessor cleanupMapperPauseProxy() {
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
                                if ("deleteByRevocationIds".equals(method.getName())) {
                                    BeforeDeleteHook hook = BEFORE_DELETE_HOOK.getAndSet(null);
                                    if (hook != null) {
                                        @SuppressWarnings("unchecked")
                                        List<Long> ids = (List<Long>) args[0];
                                        long cutoff = (Long) args[1];
                                        hook.beforeDelete(ids, cutoff);
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

    @FunctionalInterface
    private interface BeforeDeleteHook {
        void beforeDelete(List<Long> selectedIds, long cutoffEpochMs) throws Exception;
    }

    @Autowired
    private DurableRevocationTxExecutor txExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MutableClock clock;
    private DurableRevocationStore store;

    @BeforeEach
    void setup() {
        BEFORE_DELETE_HOOK.set(null);
        jdbcTemplate.update("DELETE FROM jwt_revocation");
        jdbcTemplate.update(
                "UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = 0 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        clock = new MutableClock(Instant.parse("2030-05-01T12:00:00.000Z"));
        store = new DurableRevocationStore(txExecutor, clock);
    }

    @AfterEach
    void teardown() {
        BEFORE_DELETE_HOOK.set(null);
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

    @Test
    void concurrentExtendBetweenSelectAndDeleteDoesNotDeleteExtendedRevocation() throws Exception {
        Instant now = clock.instant();
        String jtiT1 = "jti-race-extended-" + UUID.randomUUID();
        String shaT1 = DurableRevocationStore.sha256Hex(jtiT1);
        String jtiT2 = "jti-race-expired-" + UUID.randomUUID();
        String shaT2 = DurableRevocationStore.sha256Hex(jtiT2);

        store.saveRevocation(jtiT1, now.minusMillis(400_000L));
        store.saveRevocation(jtiT2, now.minusMillis(450_000L));

        Instant extendedExp = now.plusSeconds(900);
        CountDownLatch cleanupSelectedIds = new CountDownLatch(1);
        CountDownLatch extendCommitted = new CountDownLatch(1);
        AtomicReference<List<Long>> selectedIdsRef = new AtomicReference<>();

        BEFORE_DELETE_HOOK.set((ids, cutoffEpochMs) -> {
            selectedIdsRef.set(List.copyOf(ids));
            cleanupSelectedIds.countDown();
            if (!extendCommitted.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for concurrent extend transaction commit");
            }
        });

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<Integer> cleanupFuture = pool.submit(() -> store.cleanupExpiredBatch(10));
            assertThat(cleanupSelectedIds.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(selectedIdsRef.get()).hasSize(2);

            Future<?> extendFuture = pool.submit(() -> {
                store.saveRevocation(jtiT1, extendedExp, 42L, "ADMIN_REVOKE", "inst-b", "trace-b");
                return null;
            });
            extendFuture.get(5, TimeUnit.SECONDS);
            extendCommitted.countDown();

            int deletedCount = cleanupFuture.get(5, TimeUnit.SECONDS);
            assertThat(deletedCount).isEqualTo(1);
        } finally {
            extendCommitted.countDown();
            pool.shutdownNow();
        }

        // T2 (still expired) was deleted, while T1 (extended concurrently) survived intact
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, shaT2)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, shaT1)).isEqualTo(1);

        Map<String, Object> t1Row = jdbcTemplate.queryForMap(
                "SELECT expires_at_epoch_ms, token_expires_at FROM jwt_revocation WHERE jti_sha256 = ?", shaT1);
        assertThat(((Number) t1Row.get("expires_at_epoch_ms")).longValue()).isEqualTo(extendedExp.toEpochMilli());
        Object rawDt = t1Row.get("token_expires_at");
        LocalDateTime storedUtc = rawDt instanceof LocalDateTime ldt ? ldt : ((Timestamp) rawDt).toLocalDateTime();
        assertThat(storedUtc).isEqualTo(LocalDateTime.ofInstant(extendedExp, ZoneOffset.UTC));

        assertThat(store.readAuthoritativeState(jtiT1).getStatus())
                .isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
    }

    @Test
    void concurrentCleanupAndSaveStressCompletesWithoutDeadlocksOrDuplicates() throws Exception {
        Instant now = clock.instant();
        String hotJti = "jti-hot-race-" + UUID.randomUUID();
        String hotSha = DurableRevocationStore.sha256Hex(hotJti);
        store.saveRevocation(hotJti, now.minusMillis(400_000L));
        for (int i = 0; i < 12; i++) {
            store.saveRevocation("jti-bulk-exp-" + i, now.minusMillis(400_000L + i * 1000L));
        }

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        Instant finalHotExp = now.plusSeconds(1200);

        try {
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    store.saveRevocation(hotJti, finalHotExp.minusSeconds((3 - idx) * 60L));
                    return null;
                }));
            }
            for (int i = 0; i < 4; i++) {
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    store.cleanupExpiredBatch(4);
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

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, hotSha)).isEqualTo(1);
        assertThat(store.readAuthoritativeState(hotJti).getStatus())
                .isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
    }

    @Test
    void deletePhaseDbFailurePropagatesStoreUnavailableWithoutReturningZero() {
        Instant now = clock.instant();
        String jti = "jti-delete-fail-" + UUID.randomUUID();
        store.saveRevocation(jti, now.minusMillis(400_000L));

        BEFORE_DELETE_HOOK.set((ids, cutoffEpochMs) -> {
            throw new DataAccessResourceFailureException("simulated deleteByRevocationIds failure");
        });

        assertThatThrownBy(() -> store.cleanupExpiredBatch(10))
                .isInstanceOf(RevocationStoreUnavailableException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(1);
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
