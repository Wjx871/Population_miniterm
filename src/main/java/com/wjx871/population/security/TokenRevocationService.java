package com.wjx871.population.security;

import com.wjx871.population.cache.OptionalRedisService;
import java.time.Duration;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import com.wjx871.population.common.BusinessException;
import org.springframework.http.HttpStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenRevocationService {
    private final OptionalRedisService redis;
    private final JdbcTokenRevocationStore store;

    public void revoke(String jti, Instant expiresAt) {
        if (jti == null || expiresAt == null) throw new IllegalArgumentException("JWT jti and expiration are required");
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        if (ttl.isNegative() || ttl.isZero()) return;
        // Durable primary commit precedes best-effort Redis. A failed commit must not report logout success.
        try { store.revoke(jti, expiresAt); }
        catch (DataAccessException ex) { throw unavailable(); }
        redis.putMarker(redis.key("security:jwt:blacklist:" + jti), ttl);
    }
    public boolean isRevoked(String jti) {
        if (jti == null) return true;
        if (redis.contains(redis.key("security:jwt:blacklist:" + jti))) return true;
        // Never cache a negative result: another instance may revoke the token immediately afterwards.
        try { return store.isRevoked(jti); }
        catch (DataAccessException ex) { throw unavailable(); }
    }
    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.SERVICE_UNAVAILABLE, "身份令牌撤销校验暂不可用，请稍后重试");
    }
}
