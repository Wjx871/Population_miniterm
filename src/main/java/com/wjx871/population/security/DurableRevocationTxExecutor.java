package com.wjx871.population.security;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring-proxied transactional boundary executor for {@link DurableRevocationStore}.
 *
 * <p>Separated into its own Spring bean so that {@code @Transactional(REQUIRES_NEW)} and
 * {@code @Transactional(NOT_SUPPORTED)} are always invoked through the Spring AOP proxy
 * without self-invocation bypass.
 *
 * <p><strong>Primary DataSource routing invariant:</strong> Currently the application uses a
 * single Primary RW {@code DataSource}. If read/write splitting is introduced in the future,
 * none of these methods may be marked {@code readOnly = true} or routed to a read replica:
 * both writes and authoritative reads MUST execute against the Primary RW {@code DataSource}.
 */
@Service
@RequiredArgsConstructor
public class DurableRevocationTxExecutor {

    private final JwtRevocationMapper mapper;

    /**
     * Attempts an authoritative revocation insert in an independent {@code REQUIRES_NEW} transaction
     * and registers an {@code afterCommit} synchronization hook so the caller can prove commit.
     *
     * <p>If a concurrent caller wins the {@code uk_jwt_revocation_jti_sha256} unique constraint,
     * {@code DuplicateKeyException} intentionally propagates out of this {@code REQUIRES_NEW}
     * boundary so the failed insert transaction rolls back cleanly (releasing InnoDB's duplicate-check
     * S-lock) before {@link #executeExtendExistingRevocation} acquires an X-lock in a fresh transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void executeSaveRevocation(
            String jtiSha256,
            Long userId,
            long expiresAtEpochMs,
            LocalDateTime tokenExpiresAt,
            LocalDateTime revokedAt,
            String revocationReason,
            String instanceId,
            String traceId,
            LocalDateTime createdAt,
            AtomicBoolean committed) {
        registerCommitProof(committed);
        int inserted = mapper.insertRevocation(
                jtiSha256,
                userId,
                expiresAtEpochMs,
                tokenExpiresAt,
                revokedAt,
                revocationReason,
                instanceId,
                traceId,
                createdAt);
        if (inserted != 1) {
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Unexpected insertRevocation row count: " + inserted));
        }
    }

    /**
     * Locks an existing revocation row with {@code FOR UPDATE} in a fresh {@code REQUIRES_NEW}
     * transaction and monotonically extends its expiry if the new expiry is later.
     *
     * @return {@code true} if the row existed and was confirmed under X-lock, or {@code false} if
     *     the row disappeared concurrently (e.g., cleanup between duplicate check and lock)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean executeExtendExistingRevocation(
            String jtiSha256,
            long expiresAtEpochMs,
            LocalDateTime tokenExpiresAt,
            AtomicBoolean committed) {
        registerCommitProof(committed);
        Long existingId = mapper.findRevocationIdByJtiSha256ForUpdate(jtiSha256);
        if (existingId == null) {
            return false;
        }
        mapper.extendRevocationExpiryIfLater(jtiSha256, expiresAtEpochMs, tokenExpiresAt);
        return true;
    }

    /**
     * Reads authoritative revocation and checkpoint state outside any ambient outer transaction
     * ({@code NOT_SUPPORTED}, {@code readOnly = false}) so InnoDB {@code REPEATABLE-READ} callers
     * never observe stale snapshots and future DataSource routers stay on Primary RW.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = false)
    public JwtRevocationMapper.AuthoritativeLookupRow executeReadAuthoritativeState(
            String checkpointKey, String jtiSha256, long nowEpochMs) {
        return mapper.selectAuthoritativeState(checkpointKey, jtiSha256, nowEpochMs);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = false)
    public JwtRevocationMapper.CheckpointRow executeReadCheckpoint(String checkpointKey) {
        return mapper.selectCheckpoint(checkpointKey);
    }

    /**
     * Explicitly activates or monotonically advances the checkpoint in an independent {@code REQUIRES_NEW} transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public JwtRevocationMapper.CheckpointRow executeAdvanceCheckpoint(
            String checkpointKey,
            long newMinValidIatEpochMs,
            LocalDateTime nowUtc,
            String updatedBy,
            AtomicBoolean committed) {
        registerCommitProof(committed);
        int updated = mapper.advanceCheckpointMonotonic(checkpointKey, newMinValidIatEpochMs, nowUtc, updatedBy);
        if (updated != 1) {
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Checkpoint missing, INVALID, or corrupted: " + checkpointKey));
        }
        JwtRevocationMapper.CheckpointRow row = mapper.selectCheckpoint(checkpointKey);
        if (row == null) {
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Checkpoint disappeared after advance: " + checkpointKey));
        }
        return row;
    }

    /**
     * Deletes a single bounded batch of expired revocations in an independent {@code REQUIRES_NEW} transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int executeCleanupBatch(long cutoffEpochMs, int batchSize, AtomicBoolean committed) {
        registerCommitProof(committed);
        List<Long> ids = mapper.selectExpiredRevocationIds(cutoffEpochMs, batchSize);
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        return mapper.deleteByRevocationIds(ids);
    }

    private void registerCommitProof(AtomicBoolean committed) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new RevocationStoreUnavailableException(
                    new IllegalStateException("Durable revocation write requires active Spring transaction proxy"));
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                committed.set(true);
            }
        });
    }
}
