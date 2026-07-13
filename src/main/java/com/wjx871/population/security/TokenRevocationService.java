package com.wjx871.population.security;

import com.wjx871.population.cache.OptionalRedisService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {
    private final OptionalRedisService redis;
    private final Map<String, Instant> local = new ConcurrentHashMap<>();

    public void revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (jti == null || ttl.isNegative() || ttl.isZero()) return;
        local.put(jti, expiresAt);
        redis.putMarker(redis.key("security:jwt:blacklist:" + jti), ttl);
    }
    public boolean isRevoked(String jti) {
        if (jti == null) return true;
        Instant expiry = local.get(jti);
        if (expiry != null && expiry.isAfter(Instant.now())) return true;
        if (expiry != null) local.remove(jti, expiry);
        return redis.contains(redis.key("security:jwt:blacklist:" + jti));
    }
}
