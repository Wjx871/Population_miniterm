package com.wjx871.population.security;

import com.wjx871.population.s2.trace.S2TraceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import lombok.RequiredArgsConstructor;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionException;

/**
 * Authoritative database persistence store for Issue #64 durable JWT revocation.
 *
 * <p>Design invariants (ADR Sections 4, 5, 6, 7):
 * <ul>
 *   <li>Never stores raw JWTs or raw {@code jti} claims; persists only lowercase 64-char SHA-256 hex digests.</li>
 *   <li>All writes execute in {@code REQUIRES_NEW} transactions via {@link DurableRevocationTxExecutor}
 *       with {@code rollbackFor = Exception.class} and {@code afterCommit} verification.</li>
 *   <li>All authoritative reads execute in a single round-trip against the Primary RW {@code DataSource}
 *       with NO {@code COALESCE(..., 0)} and NO JVM-local checkpoint caching.</li>
 *   <li>Missing, {@code UNINITIALIZED}, {@code INVALID}, negative, or null-cutoff checkpoints, and all
 *       database read errors fail closed as {@link AuthoritativeRevocationState.Status#STORE_UNAVAILABLE}
 *       or {@link RevocationStoreUnavailableException}.</li>
 * </ul>
 *
 * <p><strong>Primary DataSource routing invariant:</strong> This application currently configures a
 * single Primary RW {@code DataSource}. If read/write splitting is added later, all methods on this
 * store MUST remain bound to the Primary RW {@code DataSource} (never a read-only replica).
 */
@Service
@RequiredArgsConstructor
public class DurableRevocationStore {

    public static final String GLOBAL_CHECKPOINT_KEY = "GLOBAL_JWT_CUTOFF";
    public static final String CHECKPOINT_STATUS_UNINITIALIZED = "UNINITIALIZED";
    public static final String CHECKPOINT_STATUS_READY = "READY";
    public static final String CHECKPOINT_STATUS_INVALID = "INVALID";
    public static final String DEFAULT_REASON = "LOGOUT";
    public static final long DEFAULT_CLEANUP_GRACE_SKEW_MS = 300_000L;

    private static final int MAX_REASON_LENGTH = 32;
    private static final int MAX_TRACE_FIELD_LENGTH = 64;
    private static final int MAX_SAVE_RETRY_ATTEMPTS = 6;

    private final DurableRevocationTxExecutor txExecutor;
    private final Clock clock;

    /**
     * Computes lowercase 64-character SHA-256 hex digest of a non-blank {@code jti}.
     */
    public static String sha256Hex(String jti) {
        if (jti == null || jti.isBlank()) {
            throw new IllegalArgumentException("jti must not be null or blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(jti.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", ex);
        }
    }

    /**
     * Aligns {@code now} to the next whole UTC second in epoch milliseconds, matching RFC 7519
     * integer-second JWT {@code iat} precision without same-second escape or false rejection.
     */
    public static long nextWholeUtcSecondCutoffMillis(Instant now) {
        if (now == null) {
            throw new IllegalArgumentException("now must not be null");
        }
        long epochSeconds = Math.floorDiv(now.toEpochMilli(), 1000L);
        return (epochSeconds + 1L) * 1000L;
    }

    /**
     * Persists a revoked {@code jti} idempotently using its SHA-256 digest in an independent
     * {@code REQUIRES_NEW} transaction, proving commit before returning.
     */
    public void saveRevocation(String jti, Instant expiresAt) {
        saveRevocation(jti, expiresAt, null, DEFAULT_REASON, null, null);
    }

    /**
     * Persists a revoked {@code jti} idempotently with audit metadata in an independent
     * {@code REQUIRES_NEW} transaction, proving commit before returning.
     */
    public void saveRevocation(
            String jti,
            Instant expiresAt,
            Long userId,
            String reason,
            String instanceId,
            String traceId) {
        if (expiresAt == null) {
            throw new IllegalArgumentException("expiresAt must not be null");
        }
        String jtiSha256 = sha256Hex(jti);
        Instant now = clock.instant();
        long expiresAtEpochMs = expiresAt.toEpochMilli();
        LocalDateTime tokenExpiresAtUtc = LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC);
        LocalDateTime revokedAtUtc = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        String effectiveReason = normalizeBounded(
                reason == null || reason.isBlank() ? DEFAULT_REASON : reason.trim(), MAX_REASON_LENGTH);
        String effectiveInstanceId = normalizeBounded(
                instanceId != null && !instanceId.isBlank() ? instanceId.trim() : S2TraceContext.instanceId(),
                MAX_TRACE_FIELD_LENGTH);
        String effectiveTraceId = normalizeBounded(
                traceId != null && !traceId.isBlank()
                        ? traceId.trim()
                        : S2TraceContext.resolveTraceId(ThreadContext.get("traceId")),
                MAX_TRACE_FIELD_LENGTH);

        AtomicBoolean committed = new AtomicBoolean(false);
        boolean tryInsertFirst = true;
        Throwable lastTransient = null;
        for (int attempt = 0; attempt < MAX_SAVE_RETRY_ATTEMPTS; attempt++) {
            committed.set(false);
            try {
                if (tryInsertFirst) {
                    txExecutor.executeSaveRevocation(
                            jtiSha256,
                            userId,
                            expiresAtEpochMs,
                            tokenExpiresAtUtc,
                            revokedAtUtc,
                            effectiveReason,
                            effectiveInstanceId,
                            effectiveTraceId,
                            revokedAtUtc,
                            committed);
                    if (!committed.get()) {
                        throw new RevocationStoreUnavailableException(
                                new IllegalStateException("Revocation write finished without commit confirmation"));
                    }
                    return;
                }
                boolean extended = txExecutor.executeExtendExistingRevocation(
                        jtiSha256, expiresAtEpochMs, tokenExpiresAtUtc, committed);
                if (extended) {
                    if (!committed.get()) {
                        throw new RevocationStoreUnavailableException(
                                new IllegalStateException("Revocation extend finished without commit confirmation"));
                    }
                    return;
                }
                tryInsertFirst = true;
            } catch (DuplicateKeyException dup) {
                lastTransient = dup;
                tryInsertFirst = false;
            } catch (ConcurrencyFailureException concurrencyEx) {
                lastTransient = concurrencyEx;
                tryInsertFirst = false;
                LockSupport.parkNanos(2_000_000L * (attempt + 1L));
            } catch (RevocationStoreUnavailableException ex) {
                throw ex;
            } catch (DataAccessException | TransactionException | PersistenceException ex) {
                throw new RevocationStoreUnavailableException(ex);
            }
        }
        throw new RevocationStoreUnavailableException(
                lastTransient != null
                        ? lastTransient
                        : new IllegalStateException("Exhausted retries persisting authoritative revocation"));
    }

    /**
     * Executes a single primary-DB lookup for {@code jti_sha256} and {@code GLOBAL_JWT_CUTOFF}.
     * Never throws raw DB errors as {@code NOT_REVOKED}; returns {@code STORE_UNAVAILABLE} on any
     * DB failure or untrusted checkpoint state.
     */
    public AuthoritativeRevocationState readAuthoritativeState(String jti) {
        String jtiSha256 = sha256Hex(jti);
        return queryAuthoritativeState(jtiSha256, false, null);
    }

    /**
     * Executes a single primary-DB lookup for {@code jti_sha256} and {@code GLOBAL_JWT_CUTOFF},
     * also enforcing {@code issuedAt < min_valid_iat_epoch_ms} (or null {@code issuedAt}) rejection.
     */
    public AuthoritativeRevocationState readAuthoritativeState(String jti, Instant issuedAt) {
        String jtiSha256 = sha256Hex(jti);
        return queryAuthoritativeState(jtiSha256, true, issuedAt);
    }

    private AuthoritativeRevocationState queryAuthoritativeState(
            String jtiSha256, boolean checkIssuedAt, Instant issuedAt) {
        long nowEpochMs = clock.millis();
        JwtRevocationMapper.AuthoritativeLookupRow row;
        try {
            row = txExecutor.executeReadAuthoritativeState(GLOBAL_CHECKPOINT_KEY, jtiSha256, nowEpochMs);
        } catch (RuntimeException ex) {
            return AuthoritativeRevocationState.unavailable("DB_READ_ERROR", null, null, ex);
        }

        if (row == null) {
            return AuthoritativeRevocationState.unavailable("MISSING_CHECKPOINT", null, null, null);
        }

        String cpStatus = row.getCheckpointStatus();
        Long minValidIat = row.getMinValidIatEpochMs();
        if (!CHECKPOINT_STATUS_READY.equals(cpStatus)) {
            return AuthoritativeRevocationState.unavailable("CHECKPOINT_NOT_READY", cpStatus, minValidIat, null);
        }
        if (minValidIat == null || minValidIat < 0L) {
            return AuthoritativeRevocationState.unavailable("INVALID_CHECKPOINT_CUTOFF", cpStatus, minValidIat, null);
        }

        if (row.isJtiRevoked()) {
            return AuthoritativeRevocationState.revokedByJti(cpStatus, minValidIat);
        }
        if (checkIssuedAt && (issuedAt == null || issuedAt.toEpochMilli() < minValidIat)) {
            return AuthoritativeRevocationState.revokedByCutoff(cpStatus, minValidIat);
        }
        return AuthoritativeRevocationState.notRevoked(cpStatus, minValidIat);
    }

    /**
     * Verifies that {@code GLOBAL_JWT_CUTOFF} exists, is {@code READY}, and has non-negative
     * {@code min_valid_iat_epoch_ms}. Fails closed with {@link RevocationStoreUnavailableException}.
     */
    public CheckpointSnapshot requireReadyCheckpoint() {
        JwtRevocationMapper.CheckpointRow row;
        try {
            row = txExecutor.executeReadCheckpoint(GLOBAL_CHECKPOINT_KEY);
        } catch (RuntimeException ex) {
            throw new RevocationStoreUnavailableException(ex);
        }
        if (row == null) {
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Missing checkpoint row: " + GLOBAL_CHECKPOINT_KEY));
        }
        CheckpointSnapshot snapshot = toSnapshot(row);
        if (!snapshot.isReady()) {
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Untrusted checkpoint state: status="
                            + snapshot.checkpointStatus() + ", cutoff=" + snapshot.minValidIatEpochMs()));
        }
        return snapshot;
    }

    /**
     * Explicitly activates ({@code UNINITIALIZED -> READY}) or monotonically advances the global
     * {@code min_valid_iat_epoch_ms} watermark. Never auto-creates missing rows, never repairs
     * {@code INVALID} state, and never lowers an existing watermark.
     */
    public CheckpointSnapshot advanceCheckpoint(long candidateMinValidIatEpochMs, String updatedBy) {
        if (candidateMinValidIatEpochMs < 0L) {
            throw new IllegalArgumentException("candidateMinValidIatEpochMs must not be negative");
        }
        if (updatedBy == null || updatedBy.isBlank()) {
            throw new IllegalArgumentException("updatedBy must not be null or blank");
        }
        String boundedActor = normalizeBounded(updatedBy.trim(), MAX_TRACE_FIELD_LENGTH);
        LocalDateTime nowUtc = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        AtomicBoolean committed = new AtomicBoolean(false);

        JwtRevocationMapper.CheckpointRow row;
        try {
            row = txExecutor.executeAdvanceCheckpoint(
                    GLOBAL_CHECKPOINT_KEY, candidateMinValidIatEpochMs, nowUtc, boundedActor, committed);
        } catch (RevocationStoreUnavailableException ex) {
            throw ex;
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            throw new RevocationStoreUnavailableException(ex);
        }
        if (!committed.get()) {
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Checkpoint advance finished without commit confirmation"));
        }
        return toSnapshot(row);
    }

    /**
     * Deletes up to {@code batchSize} expired revocation records whose expiration is at or before
     * {@code clock.millis() - DEFAULT_CLEANUP_GRACE_SKEW_MS} (300,000 ms).
     */
    public int cleanupExpiredBatch(int batchSize) {
        return cleanupExpiredBatch(batchSize, DEFAULT_CLEANUP_GRACE_SKEW_MS);
    }

    /**
     * Deletes up to {@code batchSize} expired revocation records using the specified non-negative grace skew.
     */
    public int cleanupExpiredBatch(int batchSize, Duration graceSkew) {
        if (graceSkew == null) {
            throw new IllegalArgumentException("graceSkew must not be null");
        }
        return cleanupExpiredBatch(batchSize, graceSkew.toMillis());
    }

    /**
     * Deletes up to {@code batchSize} expired revocation records whose {@code expires_at_epoch_ms <= now - graceSkewMs}.
     */
    public int cleanupExpiredBatch(int batchSize, long graceSkewMs) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (graceSkewMs < 0L) {
            throw new IllegalArgumentException("graceSkewMs must not be negative");
        }
        long cutoffEpochMs = clock.millis() - graceSkewMs;
        AtomicBoolean committed = new AtomicBoolean(false);
        try {
            int deleted = txExecutor.executeCleanupBatch(cutoffEpochMs, batchSize, committed);
            if (!committed.get()) {
                throw new RevocationStoreUnavailableException(
                        new IllegalStateException("Cleanup batch finished without commit confirmation"));
            }
            return deleted;
        } catch (RevocationStoreUnavailableException ex) {
            throw ex;
        } catch (DataAccessException | TransactionException | PersistenceException ex) {
            throw new RevocationStoreUnavailableException(ex);
        }
    }

    private static CheckpointSnapshot toSnapshot(JwtRevocationMapper.CheckpointRow row) {
        return new CheckpointSnapshot(
                row.getCheckpointKey(),
                row.getCheckpointStatus(),
                row.getMinValidIatEpochMs(),
                row.getActivatedAt(),
                row.getUpdatedBy(),
                row.getUpdatedAt());
    }

    private static String normalizeBounded(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
