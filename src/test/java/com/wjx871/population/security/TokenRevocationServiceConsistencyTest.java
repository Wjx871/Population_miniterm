package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.cache.StrictRedisException;
import com.wjx871.population.common.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.mock.env.MockEnvironment;

@Tag("unit")
class TokenRevocationServiceConsistencyTest {
    private final MutableClock clock = new MutableClock();
    private final SharedStore store = new SharedStore(clock);
    private final RevocationEvents events = mock(RevocationEvents.class);

    @Test
    void independentInstancesObserveConfirmedRevocationImmediatelyWithoutLocalEntries() {
        OptionalRedisService redisA = store.client(), redisB = store.client();
        TokenRevocationService instanceA = strict(redisA), instanceB = strict(redisB);
        assertThat(instanceB.isRevoked("jti-a")).isFalse();

        instanceA.revoke("jti-a", clock.instant().plusSeconds(60));

        assertThat(instanceB.isRevoked("jti-a")).isTrue();
        assertThat(instanceA.localEntryCount()).isZero();
        assertThat(instanceB.localEntryCount()).isZero();
        verify(redisA, never()).putMarker(anyString(), any());
        verify(redisB, never()).contains(anyString());
    }

    @Test
    void everyStrictReadReachesSharedStoreEvenAfterSuccessfulRevoke() {
        TokenRevocationService service = strict(store.client());
        service.revoke("revoked", clock.instant().plusSeconds(60));
        assertThat(service.isRevoked("revoked")).isTrue();
        store.readFailure = "TIMEOUT";
        unavailable(() -> service.isRevoked("revoked"), "TIMEOUT");
        unavailable(() -> service.isRevoked("unknown"), "TIMEOUT");
        store.readFailure = null;
        assertThat(service.isRevoked("revoked")).isTrue();
        assertThat(service.isRevoked("unknown")).isFalse();
    }

    @Test
    void failedWriteIsUnavailableAndRecoveryCanConfirmRevocation() {
        TokenRevocationService instanceA = strict(store.client()), instanceB = strict(store.client());
        store.writeFailure = "CONNECTION";
        unavailable(() -> instanceA.revoke("retry", clock.instant().plusSeconds(60)), "CONNECTION");
        assertThat(instanceB.isRevoked("retry")).isFalse();
        assertThat(instanceA.localEntryCount()).isZero();
        verify(events, never()).record(org.mockito.ArgumentMatchers.eq("REVOCATION_CONFIRMED"),
                anyString(), any(), anyString(), org.mockito.ArgumentMatchers.anyLong());
        store.writeFailure = null;
        instanceA.revoke("retry", clock.instant().plusSeconds(60));
        assertThat(instanceB.isRevoked("retry")).isTrue();
    }

    @Test
    void jtisAndAbsoluteExpirationsRemainIsolatedAcrossInstances() {
        TokenRevocationService instanceA = strict(store.client()), instanceB = strict(store.client());
        Instant now = clock.instant();
        instanceA.revoke("short", now.plusSeconds(5));
        instanceA.revoke("long", now.plusSeconds(30));
        assertThat(store.entries).containsEntry("population:security:jwt:blacklist:short", now.plusSeconds(5))
                .containsEntry("population:security:jwt:blacklist:long", now.plusSeconds(30));
        clock.advance(Duration.ofSeconds(5));
        assertThat(instanceB.isRevoked("short")).isFalse();
        assertThat(instanceB.isRevoked("long")).isTrue();
        assertThat(instanceB.isRevoked("never-revoked")).isFalse();
        clock.advance(Duration.ofSeconds(25));
        assertThat(instanceB.isRevoked("long")).isFalse();
    }

    @Test
    void concurrentReadersNeverAllowJtiAfterAcknowledgedLogout() throws Exception {
        TokenRevocationService instanceA = strict(store.client()), instanceB = strict(store.client());
        CountDownLatch confirmed = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<Boolean>> reads = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                reads.add(executor.submit(() -> {
                    if (!confirmed.await(5, TimeUnit.SECONDS)) throw new AssertionError("Logout was not confirmed");
                    for (int attempt = 0; attempt < 20; attempt++) {
                        if (!instanceB.isRevoked("concurrent")) return false;
                    }
                    return true;
                }));
            }
            instanceA.revoke("concurrent", clock.instant().plusSeconds(60));
            confirmed.countDown();
            for (Future<Boolean> result : reads) assertThat(result.get(5, TimeUnit.SECONDS)).isTrue();
        } finally {
            confirmed.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void loginPreflightUsesUniqueShortLivedMarkersThatExpireWithoutTokenMaterial() {
        TokenRevocationService service = strict(store.client());
        Instant now = clock.instant();
        service.requireLoginStore();
        service.requireLoginStore();
        assertThat(store.entries).hasSize(2);
        assertThat(store.entries.keySet()).allSatisfy(key -> assertThat(key)
                .matches("population:security:jwt:login-probe:[0-9a-f-]{36}")
                .doesNotContain("blacklist", "Bearer", "eyJ"));
        assertThat(store.entries.values()).containsOnly(now.plusSeconds(5));
        clock.advance(Duration.ofSeconds(5));
        for (String key : List.copyOf(store.entries.keySet())) assertThat(store.contains(key)).isFalse();
        assertThat(store.entries).isEmpty();
        assertThat(service.localEntryCount()).isZero();
    }

    @Test
    void loginPreflightFailsForWriteReadOrMissingAcknowledgementAndRecovers() {
        TokenRevocationService service = strict(store.client());
        store.writeFailure = "TIMEOUT";
        unavailable(service::requireLoginStore, "TIMEOUT");
        store.writeFailure = null;
        store.readFailure = "CONNECTION";
        unavailable(service::requireLoginStore, "CONNECTION");
        store.readFailure = null;
        store.discardWrites = true;
        unavailable(service::requireLoginStore, "UNCONFIRMED");
        store.discardWrites = false;
        service.requireLoginStore();
    }

    @Test
    void redisDisabledLocalModeSupportsLogoutAndSkipsLoginPreflight() {
        OptionalRedisService redis = disabledClient();
        TokenRevocationService service = local(redis, 2);
        service.validateConfiguration();
        service.requireLoginStore();
        service.revoke("local", clock.instant().plusSeconds(10));
        assertThat(service.isRevoked("local")).isTrue();
        assertThat(service.localEntryCount()).isEqualTo(1);
        clock.advance(Duration.ofSeconds(10));
        assertThat(service.isRevoked("local")).isFalse();
        assertThat(service.localEntryCount()).isZero();
        verify(redis, never()).strictContains(anyString());
        verify(redis, never()).strictPutMarker(anyString(), any());
    }

    @Test
    void localInstancesHaveSeparateMapsWhenNoSharedStoreIsEnabled() {
        TokenRevocationService instanceA = local(disabledClient(), 2), instanceB = local(disabledClient(), 2);
        instanceA.revoke("local-only", clock.instant().plusSeconds(60));
        assertThat(instanceA.isRevoked("local-only")).isTrue();
        assertThat(instanceB.isRevoked("local-only")).isFalse();
        assertThat(instanceA.localEntryCount()).isEqualTo(1);
        assertThat(instanceB.localEntryCount()).isZero();
    }

    @Test
    void cleanupProcessesAtMostConfiguredBatchAndExpiresAtBoundary() {
        TokenRevocationService service = local(disabledClient(), 2);
        for (int i = 0; i < 5; i++) service.revoke("expired-" + i, clock.instant().plusSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        service.cleanupExpired();
        assertThat(service.localEntryCount()).isEqualTo(3);
        service.cleanupExpired();
        assertThat(service.localEntryCount()).isEqualTo(1);
        service.cleanupExpired();
        assertThat(service.localEntryCount()).isZero();
        service.cleanupExpired();
        assertThat(service.localEntryCount()).isZero();
    }

    @Test
    void cleanupRotatesLivePrefixFairlyAndPreservesUnexpiredEntries() {
        TokenRevocationService service = local(disabledClient(), 2);
        service.revoke("live-a", clock.instant().plusSeconds(60));
        service.revoke("live-b", clock.instant().plusSeconds(60));
        for (int i = 0; i < 3; i++) service.revoke("expired-" + i, clock.instant().plusSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        service.cleanupExpired();
        assertThat(service.localEntryCount()).isEqualTo(5);
        service.cleanupExpired();
        assertThat(service.localEntryCount()).isEqualTo(3);
        service.cleanupExpired();
        assertThat(service.localEntryCount()).isEqualTo(2);
        assertThat(service.isRevoked("live-a")).isTrue();
        assertThat(service.isRevoked("live-b")).isTrue();
    }

    @Test
    void shorterRepeatedLocalRevocationCannotReduceExistingLifetime() {
        TokenRevocationService service = local(disabledClient(), 1);
        service.revoke("repeat", clock.instant().plusSeconds(60));
        service.revoke("repeat", clock.instant().plusSeconds(2));
        clock.advance(Duration.ofSeconds(3));
        service.cleanupExpired();
        assertThat(service.isRevoked("repeat")).isTrue();
    }

    @Test
    void cleanupCanRunConcurrentlyWithReadsAndRevocations() throws Exception {
        TokenRevocationService service = local(disabledClient(), 7);
        for (int i = 0; i < 100; i++) service.revoke("expired-" + i, clock.instant().plusSeconds(1));
        clock.advance(Duration.ofSeconds(1));
        ExecutorService executor = Executors.newFixedThreadPool(3);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<?> cleanup = executor.submit(() -> {
                await(start);
                for (int i = 0; i < 200; i++) service.cleanupExpired();
            });
            Future<?> writes = executor.submit(() -> {
                await(start);
                for (int i = 0; i < 100; i++) service.revoke("live-" + i, clock.instant().plusSeconds(60));
            });
            Future<?> reads = executor.submit(() -> {
                await(start);
                for (int i = 0; i < 100; i++) assertThat(service.isRevoked("expired-" + i)).isFalse();
            });
            start.countDown();
            cleanup.get(5, TimeUnit.SECONDS);
            writes.get(5, TimeUnit.SECONDS);
            reads.get(5, TimeUnit.SECONDS);
            for (int i = 0; i < 100; i++) assertThat(service.isRevoked("live-" + i)).isTrue();
            assertThat(service.localEntryCount()).isEqualTo(100);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void invalidJtiIsRejectedWithoutSharedAccess(String jti) {
        OptionalRedisService redis = store.client();
        TokenRevocationService service = strict(redis);
        assertThat(service.isRevoked(jti)).isTrue();
        assertThatExceptionOfType(BusinessException.class)
                .isThrownBy(() -> service.revoke(jti, clock.instant().plusSeconds(60)))
                .satisfies(ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(redis, never()).strictContains(anyString());
        verify(redis, never()).strictPutMarker(anyString(), any());
    }

    @Test
    void nullExpiryAndExpiredStrictTokenNeverReceiveAcknowledgement() {
        OptionalRedisService redis = store.client();
        TokenRevocationService service = strict(redis);
        for (Instant expiry : new Instant[] {null, clock.instant(), clock.instant().minusSeconds(1)}) {
            assertThatExceptionOfType(BusinessException.class)
                    .isThrownBy(() -> service.revoke("expired", expiry))
                    .satisfies(ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED));
        }
        verify(redis, never()).strictPutMarker(anyString(), any());
    }

    private TokenRevocationService strict(OptionalRedisService redis) {
        RevocationProperties properties = new RevocationProperties();
        properties.setMode(RevocationProperties.Mode.SHARED_REDIS_REQUIRED);
        TokenRevocationService service = new TokenRevocationService(redis, properties, clock, events, new MockEnvironment());
        service.validateConfiguration();
        return service;
    }

    private TokenRevocationService local(OptionalRedisService redis, int batchSize) {
        RevocationProperties properties = new RevocationProperties();
        properties.setCleanupBatchSize(batchSize);
        return new TokenRevocationService(redis, properties, clock, events, new MockEnvironment());
    }

    private OptionalRedisService disabledClient() {
        OptionalRedisService redis = mock(OptionalRedisService.class);
        when(redis.key(anyString())).thenAnswer(call -> "population:" + call.getArgument(0));
        return redis;
    }

    private void unavailable(Runnable action, String category) {
        assertThatExceptionOfType(RevocationStoreUnavailableException.class).isThrownBy(action::run)
                .withMessageContaining("REVOCATION_STORE_UNAVAILABLE")
                .satisfies(ex -> {
                    assertThat(ex.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(ex.getCause()).isInstanceOf(StrictRedisException.class);
                    assertThat(((StrictRedisException) ex.getCause()).category()).isEqualTo(category);
                });
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent test did not start");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new AssertionError(ex);
        }
    }

    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-06T00:00:00Z"));
        void advance(Duration duration) { now.updateAndGet(value -> value.plus(duration)); }
        @Override public Instant instant() { return now.get(); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
    }

    /** Each mock is a separate instance/client; only this time-driven Redis model is shared. */
    private static final class SharedStore {
        private final Clock clock;
        private final Map<String, Instant> entries = new ConcurrentHashMap<>();
        private volatile String readFailure;
        private volatile String writeFailure;
        private volatile boolean discardWrites;
        private SharedStore(Clock clock) { this.clock = clock; }

        private OptionalRedisService client() {
            OptionalRedisService redis = mock(OptionalRedisService.class);
            when(redis.enabled()).thenReturn(true);
            when(redis.key(anyString())).thenAnswer(call -> "population:" + call.getArgument(0));
            when(redis.strictContains(anyString())).thenAnswer(call -> {
                if (readFailure != null) throw new StrictRedisException(readFailure, null);
                return contains(call.getArgument(0));
            });
            doAnswer(call -> {
                if (writeFailure != null) throw new StrictRedisException(writeFailure, null);
                if (!discardWrites) entries.put(call.getArgument(0), call.getArgument(1));
                return null;
            }).when(redis).strictPutMarker(anyString(), any(Instant.class));
            return redis;
        }

        private boolean contains(String key) {
            return entries.computeIfPresent(key, (ignored, expiry) -> expiry.isAfter(clock.instant()) ? expiry : null) != null;
        }
    }
}
