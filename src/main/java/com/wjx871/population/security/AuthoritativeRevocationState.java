package com.wjx871.population.security;

import java.time.Instant;
import java.util.Objects;

/**
 * Authoritative security state returned by {@link DurableRevocationStore#readAuthoritativeState}.
 */
public final class AuthoritativeRevocationState {

    public enum Status {
        REVOKED,
        NOT_REVOKED,
        STORE_UNAVAILABLE
    }

    private final Status status;
    private final boolean jtiRevoked;
    private final boolean cutoffRejected;
    private final String checkpointStatus;
    private final Long minValidIatEpochMs;
    private final String failureReason;
    private final Throwable cause;

    private AuthoritativeRevocationState(
            Status status,
            boolean jtiRevoked,
            boolean cutoffRejected,
            String checkpointStatus,
            Long minValidIatEpochMs,
            String failureReason,
            Throwable cause) {
        this.status = Objects.requireNonNull(status, "status");
        this.jtiRevoked = jtiRevoked;
        this.cutoffRejected = cutoffRejected;
        this.checkpointStatus = checkpointStatus;
        this.minValidIatEpochMs = minValidIatEpochMs;
        this.failureReason = failureReason;
        this.cause = cause;
    }

    public static AuthoritativeRevocationState revokedByJti(String checkpointStatus, Long minValidIatEpochMs) {
        return new AuthoritativeRevocationState(
                Status.REVOKED, true, false, checkpointStatus, minValidIatEpochMs, null, null);
    }

    public static AuthoritativeRevocationState revokedByCutoff(String checkpointStatus, Long minValidIatEpochMs) {
        return new AuthoritativeRevocationState(
                Status.REVOKED, false, true, checkpointStatus, minValidIatEpochMs, null, null);
    }

    public static AuthoritativeRevocationState notRevoked(String checkpointStatus, long minValidIatEpochMs) {
        return new AuthoritativeRevocationState(
                Status.NOT_REVOKED, false, false, checkpointStatus, minValidIatEpochMs, null, null);
    }

    public static AuthoritativeRevocationState unavailable(
            String failureReason, String checkpointStatus, Long minValidIatEpochMs, Throwable cause) {
        return new AuthoritativeRevocationState(
                Status.STORE_UNAVAILABLE, false, false, checkpointStatus, minValidIatEpochMs, failureReason, cause);
    }

    public Status getStatus() {
        return status;
    }

    public boolean isJtiRevoked() {
        return jtiRevoked;
    }

    public boolean isCutoffRejected() {
        return cutoffRejected;
    }

    public String getCheckpointStatus() {
        return checkpointStatus;
    }

    public Long getMinValidIatEpochMs() {
        return minValidIatEpochMs;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public Throwable getCause() {
        return cause;
    }

    public boolean isAvailable() {
        return status != Status.STORE_UNAVAILABLE;
    }

    /**
     * Converts this authoritative state to a strict fail-closed boolean for Step 2 auth callers.
     *
     * @throws RevocationStoreUnavailableException if the store or checkpoint is untrusted
     */
    public boolean isRevokedOrThrow() {
        if (status == Status.STORE_UNAVAILABLE) {
            Throwable effectiveCause = cause != null
                    ? cause
                    : new IllegalStateException("Revocation store unavailable: " + failureReason);
            throw new RevocationStoreUnavailableException(effectiveCause);
        }
        return status == Status.REVOKED;
    }

    /**
     * Evaluates revocation with an issued-at timestamp against the trusted cutoff watermark.
     *
     * @throws RevocationStoreUnavailableException if the store or checkpoint is untrusted
     */
    public boolean isRevokedForIssuedAtOrThrow(Instant issuedAt) {
        if (isRevokedOrThrow()) {
            return true;
        }
        return issuedAt == null || minValidIatEpochMs == null || issuedAt.toEpochMilli() < minValidIatEpochMs;
    }
}
