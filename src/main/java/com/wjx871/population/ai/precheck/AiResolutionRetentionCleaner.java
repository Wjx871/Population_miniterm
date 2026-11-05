package com.wjx871.population.ai.precheck;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Bounded retention cleanup for expired or revoked entity-resolution sessions only. */
@Service
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiResolutionRetentionCleaner {
    private static final Logger log = LoggerFactory.getLogger(AiResolutionRetentionCleaner.class);

    private final JdbcTemplate jdbc;
    private final AiPrecheckProperties properties;
    private final PlatformTransactionManager transactionManager;

    public AiResolutionRetentionCleaner(JdbcTemplate jdbc, AiPrecheckProperties properties,
                                        PlatformTransactionManager transactionManager) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.transactionManager = transactionManager;
    }

    @Scheduled(fixedDelayString = "${population.ai.precheck.resolution-cleanup-poll-ms:3600000}")
    public void scheduledCleanup() {
        if (!properties.isResolutionCleanupEnabled()) return;
        try {
            cleanupExpiredBatch();
        } catch (RuntimeException exception) {
            log.warn("AI resolution retention cleanup batch failed; exceptionType={}",
                    exception.getClass().getSimpleName());
        }
    }

    /** Deletes at most one configured batch; session FK cascade removes only its candidates. */
    public int cleanupExpiredBatch() {
        if (!properties.isResolutionCleanupEnabled()) return 0;
        Integer deleted = new TransactionTemplate(transactionManager).execute(status -> deleteExpiredBatch());
        return deleted == null ? 0 : deleted;
    }

    private int deleteExpiredBatch() {
        LocalDateTime cutoff = LocalDateTime.now().minus(properties.getResolutionSessionRetention());
        Timestamp cutoffTimestamp = Timestamp.valueOf(cutoff);
        List<Long> ids = jdbc.query("SELECT resolution_id FROM ai_resolution_session "
                        + "WHERE (status='ACTIVE' AND expires_at<?) "
                        + "OR (status IN ('REVOKED','CONSUMED') AND updated_at<?) "
                        + "ORDER BY resolution_id LIMIT ?",
                (rs, row) -> rs.getLong(1), cutoffTimestamp, cutoffTimestamp,
                properties.getResolutionCleanupBatchSize());
        if (ids.isEmpty()) return 0;

        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        String sql = "DELETE FROM ai_resolution_session WHERE resolution_id IN (" + placeholders + ") "
                + "AND ((status='ACTIVE' AND expires_at<?) "
                + "OR (status IN ('REVOKED','CONSUMED') AND updated_at<?))";
        List<Object> parameters = new ArrayList<>(ids);
        parameters.add(cutoffTimestamp);
        parameters.add(cutoffTimestamp);
        return jdbc.update(sql, parameters.toArray());
    }
}
