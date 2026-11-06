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
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Log4j2
@Service
@RequiredArgsConstructor
public class TokenRevocationService {
    private final OptionalRedisService redis;
    private final RevocationProperties properties;
    private final Clock clock;
    private final RevocationEvents events;
    private final Environment environment;
    // All access is guarded by local. No iterator survives the lock or a cleanup call.
    private final Map<String, Instant> local = new LinkedHashMap<>();

    @PostConstruct
    public void validateConfiguration() {
        if (properties.getMode() == null || properties.getCleanupInterval() == null
                || properties.getCleanupInterval().isNegative() || properties.getCleanupInterval().isZero()
                || properties.getCleanupInterval().toMillis() < 1 || properties.getCleanupBatchSize() < 1) {
            throw new IllegalStateException("Invalid JWT revocation mode or cleanup configuration");
        }
        if (shared() && !redis.enabled()) {
            throw new IllegalStateException("SHARED_REDIS_REQUIRED requires population.redis.enabled=true");
        }
        boolean production = properties.isProduction() || environment.acceptsProfiles(Profiles.of("prod", "production"));
        if (production && !shared()) {
            String approval = properties.getProductionSingleInstanceApproval();
            if (!properties.isAllowProductionSingleInstance() || approval == null
                    || !approval.matches("[A-Za-z0-9._:/-]{3,128}")) {
                throw new IllegalStateException("Production requires SHARED_REDIS_REQUIRED or an explicit audited single-instance exception");
            }
            log.warn("event=REVOCATION_PRODUCTION_LOCAL_EXCEPTION mode=SINGLE_INSTANCE_LOCAL approval={}", approval);
        }
    }

    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || jti.isBlank() || expiresAt == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "身份令牌无效");
        }
        Duration ttl = Duration.between(clock.instant(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) {
            if (shared()) throw new BusinessException(HttpStatus.UNAUTHORIZED, "身份令牌已过期");
            return;
        }
        long started = System.nanoTime();
        if (shared()) {
            strictWrite(redis.key("security:jwt:blacklist:" + jti), expiresAt, "LOGOUT", started);
        } else {
            synchronized (local) {
                local.merge(jti, expiresAt, (oldValue, newValue) -> oldValue.isAfter(newValue) ? oldValue : newValue);
            }
            redis.putMarker(redis.key("security:jwt:blacklist:" + jti), ttl);
        }
        events.record("REVOCATION_CONFIRMED", "LOGOUT", properties.getMode(), "NONE", started);
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isBlank()) return true;
        long started = System.nanoTime();
        boolean revoked;
        if (shared()) {
            revoked = strictRead(redis.key("security:jwt:blacklist:" + jti), "AUTHENTICATE", started);
        } else {
            synchronized (local) {
                Instant expiry = local.get(jti);
                revoked = expiry != null && expiry.isAfter(clock.instant());
                if (expiry != null && !revoked) local.remove(jti);
            }
            if (!revoked) revoked = redis.contains(redis.key("security:jwt:blacklist:" + jti));
        }
        if (revoked) events.record("REVOCATION_REJECTED", "AUTHENTICATE", properties.getMode(), "NONE", started);
        return revoked;
    }

    public void requireLoginStore() {
        if (!shared()) return;
        long started = System.nanoTime();
        String key = redis.key("security:jwt:login-probe:" + UUID.randomUUID());
        strictWrite(key, clock.instant().plusSeconds(5), "LOGIN_PREFLIGHT", started);
        if (!strictRead(key, "LOGIN_PREFLIGHT", started)) {
            StrictRedisException failure = new StrictRedisException("UNCONFIRMED", null);
            events.record("REVOCATION_READ_UNAVAILABLE", "LOGIN_PREFLIGHT", properties.getMode(), failure.category(), started);
            throw new RevocationStoreUnavailableException(failure);
        }
    }

    private boolean strictRead(String key, String operation, long started) {
        try { return redis.strictContains(key); }
        catch (StrictRedisException ex) {
            events.record("REVOCATION_READ_UNAVAILABLE", operation, properties.getMode(), ex.category(), started);
            throw new RevocationStoreUnavailableException(ex);
        }
    }

    private void strictWrite(String key, Instant expiresAt, String operation, long started) {
        try { redis.strictPutMarker(key, expiresAt); }
        catch (StrictRedisException ex) {
            events.record("REVOCATION_WRITE_UNAVAILABLE", operation, properties.getMode(), ex.category(), started);
            throw new RevocationStoreUnavailableException(ex);
        }
    }

    @Scheduled(fixedDelayString = "${security.jwt.revocation.cleanup-interval:PT30S}")
    public void cleanupExpired() {
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
    private boolean shared() { return properties.getMode() == RevocationProperties.Mode.SHARED_REDIS_REQUIRED; }
}
