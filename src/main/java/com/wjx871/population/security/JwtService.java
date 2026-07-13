package com.wjx871.population.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Creates and validates stateless HMAC JWT access tokens. */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final long expireSeconds;

    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expire-minutes}") long expireMinutes) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 必须至少包含 32 个 UTF-8 字节");
        }
        if (expireMinutes <= 0) {
            throw new IllegalStateException("JWT_EXPIRE_MINUTES 必须大于 0");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = Math.multiplyExact(expireMinutes, 60L);
    }

    public String createToken(AuthenticatedUser user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(expireSeconds)))
                .signWith(signingKey)
                .compact();
    }

    public String parseUsername(String token) throws ExpiredJwtException {
        return parseClaims(token).getSubject();
    }

    public Claims parseClaims(String token) throws ExpiredJwtException {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
