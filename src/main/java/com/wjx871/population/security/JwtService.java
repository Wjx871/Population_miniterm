package com.wjx871.population.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Creates and validates stateless HMAC JWT access tokens. */
@Service
public class JwtService {

    public record IssuedJwt(String token, String jti, Instant issuedAt, Instant expiresAt) {}

    private final SecretKey signingKey;
    private final long expireSeconds;
    private final Clock clock;

    public JwtService(String secret, long expireMinutes) {
        this(secret, expireMinutes, Clock.systemUTC());
    }

    @Autowired
    public JwtService(@Value("${security.jwt.secret}") String secret,
                      @Value("${security.jwt.expire-minutes}") long expireMinutes,
                      Clock clock) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 必须至少包含 32 个 UTF-8 字节");
        }
        if (expireMinutes <= 0) {
            throw new IllegalStateException("JWT_EXPIRE_MINUTES 必须大于 0");
        }
        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = Math.multiplyExact(expireMinutes, 60L);
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    public String createToken(AuthenticatedUser user) {
        return createIssuedToken(user).token();
    }

    public IssuedJwt createIssuedToken(AuthenticatedUser user) {
        Instant issuedAt = Instant.ofEpochSecond(clock.instant().getEpochSecond());
        Instant expiresAt = issuedAt.plusSeconds(expireSeconds);
        String jti = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .id(jti)
                .subject(user.username())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedJwt(token, jti, issuedAt, expiresAt);
    }

    public String parseUsername(String token) throws ExpiredJwtException {
        return parseClaims(token).getSubject();
    }

    public Claims parseClaims(String token) throws ExpiredJwtException {
        return Jwts.parser()
                .verifyWith(signingKey)
                .clock(() -> Date.from(clock.instant()))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public long getExpireSeconds() {
        return expireSeconds;
    }
}
