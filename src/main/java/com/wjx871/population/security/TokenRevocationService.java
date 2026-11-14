package com.wjx871.population.security;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.cache.StrictRedisException;
import com.wjx871.population.common.BusinessException;
import jakarta.annotation.PostConstruct;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
public class TokenRevocationService {
    private final OptionalRedisService redis;
    private final RevocationProperties properties;
    private final Clock clock;
    private final RevocationEvents events;
    private final Environment environment;
    private final DurableRevocationStore durableStore;
    // All access is guarded by local. No iterator survives the lock or a cleanup call.
    private final Map<String, Instant> local = new LinkedHashMap<>();

    public TokenRevocationService(
            OptionalRedisService redis,
            RevocationProperties properties,
            Clock clock,
            RevocationEvents events,
            Environment environment) {
        this(redis, properties, clock, events, environment, (DurableRevocationStore) null);
    }

    @Autowired
    public TokenRevocationService(
            OptionalRedisService redis,
            RevocationProperties properties,
            Clock clock,
            RevocationEvents events,
            Environment environment,
            ObjectProvider<DurableRevocationStore> durableStoreProvider) {
        this(redis, properties, clock, events, environment,
                durableStoreProvider == null ? null : durableStoreProvider.getIfAvailable());
    }

    public TokenRevocationService(
            OptionalRedisService redis,
            RevocationProperties properties,
            Clock clock,
            RevocationEvents events,
            Environment environment,
            DurableRevocationStore durableStore) {
        this.redis = redis;
        this.properties = properties;
        this.clock = clock;
        this.events = events;
        this.environment = environment;
        this.durableStore = durableStore;
    }

    @PostConstruct
    public void validateConfiguration() {
        if (properties.getMode() == null || properties.getCleanupInterval() == null
                || properties.getCleanupInterval().isNegative() || properties.getCleanupInterval().isZero()
                || properties.getCleanupInterval().toMillis() < 1 || properties.getCleanupBatchSize() < 1
                || properties.getCleanupGraceSkew() == null || properties.getCleanupGraceSkew().isNegative()) {
            throw new IllegalStateException("Invalid JWT revocation mode or cleanup configuration");
        }
        boolean production = isProduction();
        if (shared()) {
            if (!redis.enabled()) {
                throw new IllegalStateException("SHARED_REDIS_REQUIRED requires population.redis.enabled=true");
            }
            if (production) {
                log.warn("event=REVOCATION_LEGACY_REDIS_MODE_WARNING mode=SHARED_REDIS_REQUIRED "
                        + "warning=SHARED_REDIS_REQUIRED_DOES_NOT_SURVIVE_EMPTY_REDIS_RECOVERY_USE_DURABLE_DB_REQUIRED");
            }
            return;
        }
        if (durable()) {
            if (durableStore == null) {
                throw new IllegalStateException("DURABLE_DB_REQUIRED requires DurableRevocationStore");
            }
            if (production && properties.isAllowZeroCutoffForTest()) {
                throw new IllegalStateException("DURABLE_DB_REQUIRED forbids allowZeroCutoffForTest in production");
            }
            CheckpointSnapshot snapshot;
            try {
                snapshot = durableStore.requireReadyCheckpoint();
            } catch (RevocationStoreUnavailableException ex) {
                throw new IllegalStateException(
                        "DURABLE_DB_REQUIRED startup failed: GLOBAL_JWT_CUTOFF checkpoint is missing, "
                                + "uninitialized, invalid, or database is unreachable",
                        ex);
            }
            if (snapshot == null || !snapshot.isReady()
                    || (snapshot.minValidIatEpochMs() == 0L && !isZeroCutoffAllowed())) {
                throw new IllegalStateException(
                        "DURABLE_DB_REQUIRED refuses READY+0 or untrusted checkpoint without explicit "
                                + "non-production test fixture switch");
            }
            return;
        }
        if (production) {
            String approval = properties.getProductionSingleInstanceApproval();
            if (!properties.isAllowProductionSingleInstance() || approval == null
                    || !approval.matches("[A-Za-z0-9._:/-]{3,128}")) {
                throw new IllegalStateException(
                        "Production requires DURABLE_DB_REQUIRED or an explicit audited single-instance exception");
            }
            log.warn("event=REVOCATION_PRODUCTION_LOCAL_EXCEPTION mode=SINGLE_INSTANCE_LOCAL approval={}", approval);
        }
    }

    public void revoke(String jti, Instant expiresAt) {
        revoke(jti, expiresAt, null);
    }

    public void revoke(String jti, Instant expiresAt, Long userId) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "身份令牌无效");
        }
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            if (shared() || durable()) {
                throw new BusinessException(HttpStatus.UNAUTHORIZED, "身份令牌已过期");
            }
            return;
        }
        long started = System.nanoTime();
        if (durable()) {
            revokeDurable(jti, expiresAt, userId, started);
            return;
        }
        if (shared()) {
            strictWrite(redis.key("security:jwt:blacklist:" + jti), expiresAt, "LOGOUT", started);
        } else {
            synchronized (local) {
                local.merge(jti, expiresAt, (oldValue, newValue) -> oldValue.isAfter(newValue) ? oldValue : newValue);
            }
            redis.putMarker(redis.key("security:jwt:blacklist:" + jti), ttl);
        }
        events.record(RevocationEvents.REVOCATION_CONFIRMED, "LOGOUT", properties.getMode(), "NONE", started);
    }

    public boolean isRevoked(String jti) {
        return isRevoked(jti, null, null);
    }

    public boolean isRevoked(String jti, Instant issuedAt) {
        return isRevoked(jti, issuedAt, null);
    }

    public boolean isRevoked(String jti, Instant issuedAt, Instant expiresAt) {
        if (jti == null || jti.isBlank()) {
            return true;
        }
        long started = System.nanoTime();
        if (durable()) {
            return isRevokedDurable(jti, issuedAt, expiresAt, started);
        }
        boolean revoked;
        if (shared()) {
            revoked = strictRead(redis.key("security:jwt:blacklist:" + jti), "AUTHENTICATE", started);
        } else {
            synchronized (local) {
                Instant expiry = local.get(jti);
                revoked = expiry != null && expiry.isAfter(clock.instant());
                if (expiry != null && !revoked) {
                    local.remove(jti);
                }
            }
            if (!revoked) {
                revoked = redis.contains(redis.key("security:jwt:blacklist:" + jti));
            }
        }
        if (revoked) {
            events.record(RevocationEvents.REVOCATION_REJECTED, "AUTHENTICATE", properties.getMode(), "NONE", started);
        }
        return revoked;
    }

    public void requireLoginStore() {
        if (durable()) {
            long started = System.nanoTime();
            CheckpointSnapshot snapshot = requireTrustedCheckpointForLogin(started);
            long currentWholeSecondMs = clock.instant().getEpochSecond() * 1000L;
            if (currentWholeSecondMs < snapshot.minValidIatEpochMs()) {
                events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGIN_PREFLIGHT",
                        properties.getMode(), "CUTOFF_NOT_REACHED", started);
                throw new RevocationStoreUnavailableException(
                        new IllegalStateException("Current UTC whole second has not reached GLOBAL_JWT_CUTOFF"));
            }
            return;
        }
        if (!shared()) {
            return;
        }
        long started = System.nanoTime();
        String key = redis.key("security:jwt:login-probe:" + UUID.randomUUID());
        strictWrite(key, clock.instant().plusSeconds(5), "LOGIN_PREFLIGHT", started);
        if (!strictRead(key, "LOGIN_PREFLIGHT", started)) {
            StrictRedisException failure = new StrictRedisException("UNCONFIRMED", null);
            events.record(RevocationEvents.REVOCATION_READ_UNAVAILABLE, "LOGIN_PREFLIGHT",
                    properties.getMode(), failure.category(), started);
            throw new RevocationStoreUnavailableException(failure);
        }
    }

    public void verifyIssuedTokenNotSuperseded(Instant issuedAt) {
        if (!durable()) {
            return;
        }
        long started = System.nanoTime();
        CheckpointSnapshot snapshot = requireTrustedCheckpointForLogin(started);
        if (issuedAt == null || issuedAt.toEpochMilli() < snapshot.minValidIatEpochMs()) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGIN_PREFLIGHT",
                    properties.getMode(), "TOKEN_SUPERSEDED", started);
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Issued token was superseded by concurrent GLOBAL_JWT_CUTOFF advance"));
        }
    }

    private void revokeDurable(String jti, Instant expiresAt, Long userId, long started) {
        if (durableStore == null) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGOUT",
                    properties.getMode(), "DB_UNAVAILABLE", started);
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("DurableRevocationStore unavailable"));
        }
        try {
            durableStore.saveRevocation(jti, expiresAt, userId, DurableRevocationStore.DEFAULT_REASON, null, null);
        } catch (RevocationStoreUnavailableException ex) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGOUT",
                    properties.getMode(), "DB_WRITE_ERROR", started);
            throw ex;
        } catch (RuntimeException ex) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGOUT",
                    properties.getMode(), "DB_WRITE_ERROR", started);
            throw new RevocationStoreUnavailableException(ex);
        }
        events.record(RevocationEvents.REVOCATION_DB_CONFIRMED, "LOGOUT", properties.getMode(), "NONE", started);
        backfillRedisMarkerBestEffort(jti, expiresAt, "LOGOUT", started);
    }

    private boolean isRevokedDurable(String jti, Instant issuedAt, Instant expiresAt, long started) {
        if (redis.enabled()) {
            String cacheKey = durableBlacklistKey(jti);
            try {
                if (redis.strictContains(cacheKey)) {
                    events.record(RevocationEvents.REVOCATION_REJECTED_CACHE, "AUTHENTICATE",
                            properties.getMode(), "NONE", started);
                    return true;
                }
            } catch (StrictRedisException ex) {
                events.record(RevocationEvents.REVOCATION_CACHE_READ_UNAVAILABLE, "AUTHENTICATE",
                        properties.getMode(), ex.category(), started);
            } catch (RuntimeException ex) {
                events.record(RevocationEvents.REVOCATION_CACHE_READ_UNAVAILABLE, "AUTHENTICATE",
                        properties.getMode(), "STORE_ERROR", started);
            }
        }
        if (durableStore == null) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "AUTHENTICATE",
                    properties.getMode(), "DB_UNAVAILABLE", started);
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("DurableRevocationStore unavailable"));
        }
        AuthoritativeRevocationState state;
        try {
            state = durableStore.readAuthoritativeState(jti, issuedAt);
        } catch (RuntimeException ex) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "AUTHENTICATE",
                    properties.getMode(), "DB_READ_ERROR", started);
            if (ex instanceof RevocationStoreUnavailableException unavailable) {
                throw unavailable;
            }
            throw new RevocationStoreUnavailableException(ex);
        }
        if (state == null || !state.isAvailable()) {
            String reason = state == null ? "DB_UNAVAILABLE" : state.getFailureReason();
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "AUTHENTICATE",
                    properties.getMode(), reason, started);
            if (state == null) {
                throw new RevocationStoreUnavailableException(
                        new IllegalStateException("Null authoritative revocation state"));
            }
            state.isRevokedOrThrow();
        }
        if (state.getMinValidIatEpochMs() == null
                || state.getMinValidIatEpochMs() < 0L
                || (state.getMinValidIatEpochMs() == 0L && !isZeroCutoffAllowed())) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "AUTHENTICATE",
                    properties.getMode(), "ZERO_CUTOFF_FORBIDDEN", started);
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Untrusted zero cutoff checkpoint in DURABLE_DB_REQUIRED mode"));
        }
        boolean revoked = state.isRevokedOrThrow();
        if (!revoked) {
            return false;
        }
        if (state.isJtiRevoked()) {
            events.record(RevocationEvents.REVOCATION_REJECTED_DB, "AUTHENTICATE",
                    properties.getMode(), "NONE", started);
            if (expiresAt != null && expiresAt.isAfter(clock.instant())) {
                backfillRedisMarkerBestEffort(jti, expiresAt, "AUTHENTICATE", started);
            }
        } else {
            events.record(RevocationEvents.REVOCATION_REJECTED_CUTOFF, "AUTHENTICATE",
                    properties.getMode(), "NONE", started);
        }
        return true;
    }

    private CheckpointSnapshot requireTrustedCheckpointForLogin(long started) {
        if (durableStore == null) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGIN_PREFLIGHT",
                    properties.getMode(), "DB_UNAVAILABLE", started);
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("DurableRevocationStore unavailable"));
        }
        CheckpointSnapshot snapshot;
        try {
            snapshot = durableStore.requireReadyCheckpoint();
        } catch (RevocationStoreUnavailableException ex) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGIN_PREFLIGHT",
                    properties.getMode(), "CHECKPOINT_UNAVAILABLE", started);
            throw ex;
        } catch (RuntimeException ex) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGIN_PREFLIGHT",
                    properties.getMode(), "DB_READ_ERROR", started);
            throw new RevocationStoreUnavailableException(ex);
        }
        if (snapshot == null || !snapshot.isReady()
                || (snapshot.minValidIatEpochMs() == 0L && !isZeroCutoffAllowed())) {
            events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "LOGIN_PREFLIGHT",
                    properties.getMode(), "ZERO_CUTOFF_FORBIDDEN", started);
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Untrusted zero cutoff checkpoint for login preflight"));
        }
        return snapshot;
    }

    private void backfillRedisMarkerBestEffort(String jti, Instant expiresAt, String operation, long started) {
        if (!redis.enabled() || expiresAt == null) {
            return;
        }
        try {
            redis.strictPutMarker(durableBlacklistKey(jti), expiresAt);
        } catch (StrictRedisException ex) {
            events.record(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE, operation,
                    properties.getMode(), ex.category(), started);
        } catch (RuntimeException ex) {
            events.record(RevocationEvents.REVOCATION_CACHE_WRITE_UNAVAILABLE, operation,
                    properties.getMode(), "STORE_ERROR", started);
        }
    }

    private String durableBlacklistKey(String jti) {
        return redis.key("security:jwt:blacklist:" + DurableRevocationStore.sha256Hex(jti));
    }

    private boolean strictRead(String key, String operation, long started) {
        try { return redis.strictContains(key); }
        catch (StrictRedisException ex) {
            events.record(RevocationEvents.REVOCATION_READ_UNAVAILABLE, operation,
                    properties.getMode(), ex.category(), started);
            throw new RevocationStoreUnavailableException(ex);
        }
    }

    private void strictWrite(String key, Instant expiresAt, String operation, long started) {
        try { redis.strictPutMarker(key, expiresAt); }
        catch (StrictRedisException ex) {
            events.record(RevocationEvents.REVOCATION_WRITE_UNAVAILABLE, operation,
                    properties.getMode(), ex.category(), started);
            throw new RevocationStoreUnavailableException(ex);
        }
    }

    @Scheduled(fixedDelayString = "${security.jwt.revocation.cleanup-interval:PT30S}")
    public void cleanupExpired() {
        if (durable()) {
            if (durableStore == null) {
                return;
            }
            long started = System.nanoTime();
            try {
                int deleted = durableStore.cleanupExpiredBatch(
                        properties.getCleanupBatchSize(), properties.getCleanupGraceSkew());
                if (deleted > 0) {
                    events.record(RevocationEvents.REVOCATION_CLEANUP_BATCH, "CLEANUP",
                            properties.getMode(), "NONE", started);
                }
            } catch (RuntimeException ex) {
                events.record(RevocationEvents.REVOCATION_DB_UNAVAILABLE, "CLEANUP",
                        properties.getMode(), "DB_CLEANUP_ERROR", started);
            }
            return;
        }
        if (shared()) {
            return;
        }
        synchronized (local) {
            Instant now = clock.instant();
            var batch = new ArrayList<String>(Math.min(local.size(), properties.getCleanupBatchSize()));
            var iterator = local.keySet().iterator();
            while (iterator.hasNext() && batch.size() < properties.getCleanupBatchSize()) batch.add(iterator.next());
            for (String key : batch) {
                Instant expiry = local.remove(key);
                if (expiry.isAfter(now)) local.put(key, expiry); // rotate live entries fairly
            }
        }
    }

    int localEntryCount() { synchronized (local) { return local.size(); } }

    boolean isZeroCutoffAllowed() {
        return !isProduction() && properties.isAllowZeroCutoffForTest();
    }

    private boolean isProduction() {
        return properties.isProduction()
                || (environment != null && environment.acceptsProfiles(Profiles.of("prod", "production")));
    }

    private boolean shared() { return properties.getMode() == RevocationProperties.Mode.SHARED_REDIS_REQUIRED; }
    private boolean durable() { return properties.getMode() == RevocationProperties.Mode.DURABLE_DB_REQUIRED; }
}
