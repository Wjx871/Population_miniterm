package com.wjx871.population.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Always uses the physical primary, including calls originating in replica contexts. */
@Repository
public class JdbcTokenRevocationStore {
    private final JdbcTemplate primary;
    public JdbcTokenRevocationStore(@Qualifier("primaryDataSource") DataSource primary) {
        this.primary = new JdbcTemplate(primary);
    }
    public void revoke(String jti, Instant expiresAt) {
        try {
            primary.update("INSERT INTO security_token_revocation(token_hash, expires_at) VALUES (?, ?)",
                    hash(jti), Timestamp.from(expiresAt));
        } catch (DuplicateKeyException ignored) {
            // A JWT's signed jti/expiry are fixed. Concurrent repeated revocation is idempotent.
        }
    }
    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(primary.queryForObject(
                "SELECT COUNT(*) > 0 FROM security_token_revocation WHERE token_hash=? AND expires_at>?",
                Boolean.class, hash(jti), Timestamp.from(Instant.now())));
    }
    public int deleteExpired() {
        return primary.update("DELETE FROM security_token_revocation WHERE expires_at<=?", Timestamp.from(Instant.now()));
    }
    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) { throw new IllegalStateException(ex); }
    }
}
