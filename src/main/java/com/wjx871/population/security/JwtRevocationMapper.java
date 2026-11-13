package com.wjx871.population.security;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for authoritative JWT revocation records and global cutover checkpoint.
 *
 * <p>All reads and writes MUST execute against the Primary RW {@code DataSource}; never route
 * these queries to read replicas.
 */
@Mapper
public interface JwtRevocationMapper {

    int insertRevocation(
            @Param("jtiSha256") String jtiSha256,
            @Param("userId") Long userId,
            @Param("expiresAtEpochMs") long expiresAtEpochMs,
            @Param("tokenExpiresAt") LocalDateTime tokenExpiresAt,
            @Param("revokedAt") LocalDateTime revokedAt,
            @Param("revocationReason") String revocationReason,
            @Param("instanceId") String instanceId,
            @Param("traceId") String traceId,
            @Param("createdAt") LocalDateTime createdAt);

    int extendRevocationExpiryIfLater(
            @Param("jtiSha256") String jtiSha256,
            @Param("expiresAtEpochMs") long expiresAtEpochMs,
            @Param("tokenExpiresAt") LocalDateTime tokenExpiresAt);

    Long findRevocationIdByJtiSha256ForUpdate(@Param("jtiSha256") String jtiSha256);

    AuthoritativeLookupRow selectAuthoritativeState(
            @Param("checkpointKey") String checkpointKey,
            @Param("jtiSha256") String jtiSha256,
            @Param("nowEpochMs") long nowEpochMs);

    CheckpointRow selectCheckpoint(@Param("checkpointKey") String checkpointKey);

    int advanceCheckpointMonotonic(
            @Param("checkpointKey") String checkpointKey,
            @Param("newMinValidIatEpochMs") long newMinValidIatEpochMs,
            @Param("nowUtc") LocalDateTime nowUtc,
            @Param("updatedBy") String updatedBy);

    List<Long> selectExpiredRevocationIds(
            @Param("cutoffEpochMs") long cutoffEpochMs,
            @Param("batchSize") int batchSize);

    int deleteByRevocationIds(@Param("ids") List<Long> ids);

    /**
     * Raw single-round-trip row returned by {@link #selectAuthoritativeState}.
     */
    class AuthoritativeLookupRow {
        private boolean jtiRevoked;
        private String checkpointStatus;
        private Long minValidIatEpochMs;

        public boolean isJtiRevoked() {
            return jtiRevoked;
        }

        public void setJtiRevoked(boolean jtiRevoked) {
            this.jtiRevoked = jtiRevoked;
        }

        public String getCheckpointStatus() {
            return checkpointStatus;
        }

        public void setCheckpointStatus(String checkpointStatus) {
            this.checkpointStatus = checkpointStatus;
        }

        public Long getMinValidIatEpochMs() {
            return minValidIatEpochMs;
        }

        public void setMinValidIatEpochMs(Long minValidIatEpochMs) {
            this.minValidIatEpochMs = minValidIatEpochMs;
        }
    }

    /**
     * Raw checkpoint row returned by {@link #selectCheckpoint}.
     */
    class CheckpointRow {
        private String checkpointKey;
        private String checkpointStatus;
        private Long minValidIatEpochMs;
        private LocalDateTime activatedAt;
        private String updatedBy;
        private LocalDateTime updatedAt;

        public String getCheckpointKey() {
            return checkpointKey;
        }

        public void setCheckpointKey(String checkpointKey) {
            this.checkpointKey = checkpointKey;
        }

        public String getCheckpointStatus() {
            return checkpointStatus;
        }

        public void setCheckpointStatus(String checkpointStatus) {
            this.checkpointStatus = checkpointStatus;
        }

        public Long getMinValidIatEpochMs() {
            return minValidIatEpochMs;
        }

        public void setMinValidIatEpochMs(Long minValidIatEpochMs) {
            this.minValidIatEpochMs = minValidIatEpochMs;
        }

        public LocalDateTime getActivatedAt() {
            return activatedAt;
        }

        public void setActivatedAt(LocalDateTime activatedAt) {
            this.activatedAt = activatedAt;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
