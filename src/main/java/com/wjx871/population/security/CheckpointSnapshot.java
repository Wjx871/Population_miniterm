package com.wjx871.population.security;

import java.time.LocalDateTime;

/**
 * Immutable snapshot of the global JWT revocation checkpoint row.
 */
public record CheckpointSnapshot(
        String checkpointKey,
        String checkpointStatus,
        Long minValidIatEpochMs,
        LocalDateTime activatedAt,
        String updatedBy,
        LocalDateTime updatedAt) {

    public boolean isReady() {
        return DurableRevocationStore.CHECKPOINT_STATUS_READY.equals(checkpointStatus)
                && minValidIatEpochMs != null
                && minValidIatEpochMs >= 0L;
    }
}
