package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
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
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;

@SpringBootTest
class DurableRevocationStoreTest {

    private static final AtomicReference<Throwable> AFTER_INSERT_FAULT = new AtomicReference<>();
    private static final AtomicReference<Throwable> READ_FAULT = new AtomicReference<>();

    @TestConfiguration
    static class MapperFaultInjectionConfig {
        @Bean
        static BeanPostProcessor jwtRevocationMapperFaultProxy() {
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
                                    Throwable readErr = READ_FAULT.getAndSet(null);
                                    if (readErr != null) {
                                        sneakyThrow(readErr);
                                    }
                                }
                                Object result;
                                try {
                                    result = method.invoke(target, args);
                                } catch (InvocationTargetException ex) {
                                    throw ex.getTargetException();
                                }
                                if ("insertRevocation".equals(method.getName())) {
                                    Throwable fault = AFTER_INSERT_FAULT.getAndSet(null);
                                    if (fault != null) {
                                        sneakyThrow(fault);
                                    }
                                }
                                return result;
                            });
                }
            };
        }

        @SuppressWarnings("unchecked")
        private static <E extends Throwable> void sneakyThrow(Throwable ex) throws E {
            throw (E) ex;
        }
    }

    @Autowired
    private DurableRevocationStore store;

    @Autowired
    private DurableRevocationTxExecutor txExecutor;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void resetTables() {
        AFTER_INSERT_FAULT.set(null);
        READ_FAULT.set(null);
        jdbcTemplate.update("DELETE FROM jwt_revocation");
        jdbcTemplate.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbcTemplate.update(
                "INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "VALUES (?, 'READY', 0, CURRENT_TIMESTAMP, 'TEST_RESET')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
    }

    @AfterEach
    void cleanupTables() {
        resetTables();
    }

    @Test
    void sha256HexProducesStandardLowercase64Hex() {
        String digest = DurableRevocationStore.sha256Hex("abc");
        assertThat(digest)
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad")
                .matches("^[0-9a-f]{64}$");
        assertThatThrownBy(() -> DurableRevocationStore.sha256Hex(null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DurableRevocationStore.sha256Hex("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveRevocationNeverPersistsPlaintextJtiOrJwt() {
        AuthenticatedUser syntheticUser = new AuthenticatedUser(
                42L, "population", "", "Test User", "ENABLED",
                1L, "ADMIN", "Admin", RoleLevel.L3, DataScope.ALL, "ENABLED",
                1L, "Dept", "110000", List.of());
        String token = jwtService.createToken(syntheticUser);
        String jti = jwtService.parseClaims(token).getId();
        Instant expiresAt = Instant.now().plusSeconds(3600);

        store.saveRevocation(jti, expiresAt, 42L, "LOGOUT", "inst-01", "trace-01");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM jwt_revocation");
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("jti_sha256")).isEqualTo(DurableRevocationStore.sha256Hex(jti));
        assertThat(row.get("user_id")).isEqualTo(42L);
        assertThat(row.get("revocation_reason")).isEqualTo("LOGOUT");
        assertThat(row.get("instance_id")).isEqualTo("inst-01");
        assertThat(row.get("trace_id")).isEqualTo("trace-01");

        String serializedRow = row.values().toString();
        assertThat(serializedRow).doesNotContain(jti);
        assertThat(serializedRow).doesNotContain(token);
        assertThat(serializedRow).doesNotContain("eyJ");
    }

    @Test
    void saveAndReadSingleAndIndependentJtis() {
        String jti1 = "jti-one-" + UUID.randomUUID();
        String jti2 = "jti-two-" + UUID.randomUUID();
        Instant exp1 = Instant.now().plusSeconds(1200);

        store.saveRevocation(jti1, exp1, 7L, "ADMIN_REVOKE", "inst-a", "tr-a");

        AuthoritativeRevocationState state1 = store.readAuthoritativeState(jti1);
        assertThat(state1.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
        assertThat(state1.isJtiRevoked()).isTrue();
        assertThat(state1.isRevokedOrThrow()).isTrue();

        AuthoritativeRevocationState state2 = store.readAuthoritativeState(jti2);
        assertThat(state2.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);
        assertThat(state2.isJtiRevoked()).isFalse();
        assertThat(state2.isRevokedOrThrow()).isFalse();
    }

    @Test
    void duplicateSaveIsIdempotentAndMonotonicOnExpiration() {
        String jti = "jti-dup-" + UUID.randomUUID();
        String sha = DurableRevocationStore.sha256Hex(jti);
        Instant t1 = Instant.parse("2030-01-01T10:00:00.123Z");
        Instant earlier = Instant.parse("2030-01-01T09:00:00.000Z");
        Instant later = Instant.parse("2030-01-01T12:30:00.456Z");

        store.saveRevocation(jti, t1);
        store.saveRevocation(jti, earlier);

        Map<String, Object> afterShorter = jdbcTemplate.queryForMap(
                "SELECT expires_at_epoch_ms, token_expires_at FROM jwt_revocation WHERE jti_sha256 = ?", sha);
        assertThat(((Number) afterShorter.get("expires_at_epoch_ms")).longValue()).isEqualTo(t1.toEpochMilli());
        LocalDateTime storedTs1 = ((Timestamp) afterShorter.get("token_expires_at")).toLocalDateTime();
        assertThat(storedTs1).isEqualTo(LocalDateTime.ofInstant(t1, ZoneOffset.UTC));

        store.saveRevocation(jti, later);

        Integer rowCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha);
        assertThat(rowCount).isEqualTo(1);

        Map<String, Object> afterLater = jdbcTemplate.queryForMap(
                "SELECT expires_at_epoch_ms, token_expires_at FROM jwt_revocation WHERE jti_sha256 = ?", sha);
        assertThat(((Number) afterLater.get("expires_at_epoch_ms")).longValue()).isEqualTo(later.toEpochMilli());
        LocalDateTime storedTs2 = ((Timestamp) afterLater.get("token_expires_at")).toLocalDateTime();
        assertThat(storedTs2).isEqualTo(LocalDateTime.ofInstant(later, ZoneOffset.UTC));
    }

    @Test
    void concurrentDuplicateSaveRacesUniqueKeySafelyToSingleRow() throws Exception {
        String jti = "jti-race-" + UUID.randomUUID();
        String sha = DurableRevocationStore.sha256Hex(jti);
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        Instant baseExp = Instant.now().plusSeconds(1800);

        try {
            for (int i = 0; i < threads; i++) {
                final int idx = i;
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    store.saveRevocation(jti, baseExp.plusSeconds(idx * 30L));
                    return null;
                }));
            }
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            for (Future<?> future : futures) {
                future.get(15, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha);
        Long maxExp = jdbcTemplate.queryForObject(
                "SELECT expires_at_epoch_ms FROM jwt_revocation WHERE jti_sha256 = ?", Long.class, sha);
        assertThat(count).isEqualTo(1);
        assertThat(maxExp).isEqualTo(baseExp.plusSeconds((threads - 1) * 30L).toEpochMilli());
        assertThat(store.readAuthoritativeState(jti).getStatus())
                .isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
    }

    @Test
    void requiresNewTransactionWorksViaSpringProxyAndSurvivesOuterRollback() throws Exception {
        assertThat(AopUtils.isAopProxy(txExecutor)).isTrue();
        Method saveMethod = DurableRevocationTxExecutor.class.getMethod(
                "executeSaveRevocation",
                String.class, Long.class, long.class, LocalDateTime.class, LocalDateTime.class,
                String.class, String.class, String.class, LocalDateTime.class, java.util.concurrent.atomic.AtomicBoolean.class);
        Transactional txAnnotation = saveMethod.getAnnotation(Transactional.class);
        assertThat(txAnnotation).isNotNull();
        assertThat(txAnnotation.propagation()).isEqualTo(org.springframework.transaction.annotation.Propagation.REQUIRES_NEW);
        assertThat(txAnnotation.rollbackFor()).containsExactly(Exception.class);

        String jti = "jti-req-new-" + UUID.randomUUID();
        String sha = DurableRevocationStore.sha256Hex(jti);
        Instant exp = Instant.now().plusSeconds(900);

        TransactionStatus outerTx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            store.saveRevocation(jti, exp);
        } finally {
            transactionManager.rollback(outerTx);
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha);
        assertThat(count).isEqualTo(1);
        assertThat(store.readAuthoritativeState(jti).getStatus())
                .isEqualTo(AuthoritativeRevocationState.Status.REVOKED);

        // Unproxied direct instantiation without Spring @Transactional proxy must fail closed
        DurableRevocationTxExecutor unproxiedExecutor = new DurableRevocationTxExecutor(mock(JwtRevocationMapper.class));
        DurableRevocationStore unproxiedStore = new DurableRevocationStore(unproxiedExecutor, Clock.systemUTC());
        assertThatThrownBy(() -> unproxiedStore.saveRevocation("jti-no-proxy", exp))
                .isInstanceOf(RevocationStoreUnavailableException.class);
    }

    @Test
    void writeFailureRollsBackCleanlyWithoutPartialRowForRuntimeAndCheckedExceptions() {
        String jtiRuntime = "jti-rollback-rt-" + UUID.randomUUID();
        String shaRuntime = DurableRevocationStore.sha256Hex(jtiRuntime);

        // 1. Real INSERT inside REQUIRES_NEW proxy followed by RuntimeException -> must roll back
        AFTER_INSERT_FAULT.set(new DataRetrievalFailureException("simulated failure after insert before commit"));
        assertThatThrownBy(() -> store.saveRevocation(jtiRuntime, Instant.now().plusSeconds(600)))
                .isInstanceOf(RevocationStoreUnavailableException.class);

        Integer runtimeCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, shaRuntime);
        assertThat(runtimeCount).isZero();
        assertThat(store.readAuthoritativeState(jtiRuntime).getStatus())
                .isEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);

        // 2. Real INSERT inside REQUIRES_NEW proxy followed by checked Exception -> rollbackFor=Exception.class must roll back
        String jtiChecked = "jti-rollback-chk-" + UUID.randomUUID();
        String shaChecked = DurableRevocationStore.sha256Hex(jtiChecked);
        AFTER_INSERT_FAULT.set(new Exception("simulated checked exception after insert"));
        assertThatThrownBy(() -> store.saveRevocation(jtiChecked, Instant.now().plusSeconds(600)))
                .isInstanceOf(Exception.class);

        Integer checkedCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, shaChecked);
        assertThat(checkedCount).isZero();
    }

    @Test
    void databaseReadFailureNeverReturnsNotRevoked() {
        READ_FAULT.set(new DataAccessResourceFailureException("simulated DB outage"));

        AuthoritativeRevocationState state = store.readAuthoritativeState("any-jti");
        assertThat(state.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.STORE_UNAVAILABLE);
        assertThat(state.getStatus()).isNotEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);
        assertThat(state.isAvailable()).isFalse();
        assertThatThrownBy(state::isRevokedOrThrow)
                .isInstanceOf(RevocationStoreUnavailableException.class);
    }

    @Test
    void newlyConstructedStoreInstanceReadsCommittedRevocationsAcrossRestartBoundary() {
        String jti = "jti-restart-" + UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(1800);
        store.saveRevocation(jti, exp);

        DurableRevocationStore recreatedStore = new DurableRevocationStore(txExecutor, Clock.systemUTC());
        AuthoritativeRevocationState state = recreatedStore.readAuthoritativeState(jti);
        assertThat(state.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
        assertThat(state.isJtiRevoked()).isTrue();
    }

    @Test
    void expiredRevocationRecordIsNotTreatedAsActiveRevocation() {
        String jti = "jti-already-expired-" + UUID.randomUUID();
        store.saveRevocation(jti, Instant.now().minusSeconds(5));

        AuthoritativeRevocationState state = store.readAuthoritativeState(jti);
        assertThat(state.getStatus()).isEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);
        assertThat(state.isJtiRevoked()).isFalse();
    }

    @Test
    void readAuthoritativeStateSuspendsOuterTransactionToObserveRequiresNewCommit() {
        String jti = "jti-outer-tx-vis-" + UUID.randomUUID();
        Instant exp = Instant.now().plusSeconds(1800);

        TransactionStatus outerTx = transactionManager.getTransaction(new DefaultTransactionDefinition());
        try {
            // Establish outer transaction snapshot before revocation
            assertThat(store.readAuthoritativeState(jti).getStatus())
                    .isEqualTo(AuthoritativeRevocationState.Status.NOT_REVOKED);

            // REQUIRES_NEW commits independently while outerTx is still open
            store.saveRevocation(jti, exp);

            // NOT_SUPPORTED suspends outerTx and immediately observes committed revocation
            assertThat(store.readAuthoritativeState(jti).getStatus())
                    .isEqualTo(AuthoritativeRevocationState.Status.REVOKED);
        } finally {
            transactionManager.rollback(outerTx);
        }
    }
}
