package com.wjx871.population.ai.precheck;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiPrecheckLifecycleReaper {
    private final JdbcTemplate jdbc;
    private final AiPrecheckProperties properties;
    private final AiPrivateFileStorageService storage;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${AI_PRECHECK_CLEANUP_POLL_MS:60000}")
    public void reapMaterials() {
        markExpiredReady();
        recoverStaleUploads();
        deleteUnavailableObjects();
        deleteUnreferencedTempFiles();
    }

    private void markExpiredReady() {
        List<StageCandidate> candidates = jdbc.query("SELECT stage_material_id,stored_object_key,temp_object_key,owner_user_id,version,status FROM ai_stage_material WHERE status='READY' AND expires_at<=CURRENT_TIMESTAMP ORDER BY expires_at LIMIT 100",
                (rs, rowNum) -> new StageCandidate(rs.getString("stage_material_id"),
                        rs.getString("stored_object_key"), rs.getString("temp_object_key"),
                        rs.getLong("owner_user_id"), rs.getLong("version"), rs.getString("status")));
        for (StageCandidate candidate : candidates) {
            transaction(() -> {
                int changed = jdbc.update("UPDATE ai_stage_material SET status='EXPIRED', updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE stage_material_id=? AND status='READY' AND version=? AND expires_at<=CURRENT_TIMESTAMP",
                        candidate.stageId(), candidate.version());
                if (changed == 1) {
                    audit(candidate.stageId(), "MATERIAL_EXPIRED", "READY", "EXPIRED", null);
                }
                return null;
            });
        }
    }

    private void recoverStaleUploads() {
        List<StageCandidate> candidates = jdbc.query("SELECT stage_material_id,stored_object_key,temp_object_key,owner_user_id,version,status FROM ai_stage_material WHERE status='UPLOADING' AND updated_at<=? ORDER BY updated_at LIMIT 100",
                (rs, rowNum) -> new StageCandidate(rs.getString("stage_material_id"),
                        rs.getString("stored_object_key"), rs.getString("temp_object_key"),
                        rs.getLong("owner_user_id"), rs.getLong("version"), rs.getString("status")),
                Timestamp.valueOf(LocalDateTime.now().minus(Duration.ofMinutes(5))));
        for (StageCandidate candidate : candidates) {
            try {
                if (!storage.objectExists(candidate.objectKey()) && storage.tempExists(candidate.tempKey())) {
                    storage.promote(new AiPrivateFileStorageService.PreparedFile("material", candidate.tempKey(),
                            candidate.objectKey(), "application/octet-stream", "bin", 0, ""));
                }
                transaction(() -> {
                    if (storage.objectExists(candidate.objectKey())) {
                        LocalDateTime now = LocalDateTime.now();
                        int updated = jdbc.update("UPDATE ai_stage_material SET status='READY', temp_object_key=NULL, uploaded_at=?, expires_at=?, updated_at=?, version=version+1 WHERE stage_material_id=? AND status='UPLOADING' AND version=?",
                                Timestamp.valueOf(now), Timestamp.valueOf(now.plus(properties.getReadyRetention())),
                                Timestamp.valueOf(now), candidate.stageId(), candidate.version());
                        if (updated == 1) audit(candidate.stageId(), "MATERIAL_UPLOAD_RECOVERED", "UPLOADING", "READY", null);
                    } else {
                        LocalDateTime now = LocalDateTime.now();
                        int updated = jdbc.update("UPDATE ai_stage_material SET status='UPLOAD_FAILED', temp_object_key=NULL, expires_at=?, updated_at=?, version=version+1 WHERE stage_material_id=? AND status='UPLOADING' AND version=?",
                                Timestamp.valueOf(now), Timestamp.valueOf(now), candidate.stageId(), candidate.version());
                        if (updated == 1) audit(candidate.stageId(), "MATERIAL_UPLOAD_RECOVERY_FAILED", "UPLOADING",
                                "UPLOAD_FAILED", "UPLOAD_RECOVERY_FAILED");
                    }
                    return null;
                });
            } catch (Exception exception) {
                log.warn("AI staged upload recovery deferred stageMaterialId={} errorClass={}",
                        candidate.stageId(), exception.getClass().getSimpleName());
            }
        }
    }

    private void deleteUnavailableObjects() {
        List<FileCandidate> candidates = jdbc.query("SELECT stage_material_id,stored_object_key,temp_object_key FROM ai_stage_material WHERE status IN ('UPLOAD_FAILED','CANCELLED','EXPIRED') AND file_deleted_at IS NULL ORDER BY updated_at LIMIT 100",
                (rs, rowNum) -> new FileCandidate(rs.getString("stage_material_id"),
                        rs.getString("stored_object_key"), rs.getString("temp_object_key")));
        for (FileCandidate candidate : candidates) {
            try {
                storage.deleteObject(candidate.objectKey());
                storage.deleteTemp(candidate.tempKey());
                jdbc.update("UPDATE ai_stage_material SET file_deleted_at=CURRENT_TIMESTAMP, updated_at=CURRENT_TIMESTAMP WHERE stage_material_id=? AND status IN ('UPLOAD_FAILED','CANCELLED','EXPIRED') AND file_deleted_at IS NULL",
                        candidate.stageId());
            } catch (IOException exception) {
                log.warn("AI staged file cleanup deferred stageMaterialId={} errorClass={}", candidate.stageId(),
                        exception.getClass().getSimpleName());
            }
        }
    }

    private void deleteUnreferencedTempFiles() {
        try {
            long cutoff = System.currentTimeMillis() - Duration.ofHours(1).toMillis();
            storage.forEachTempKey((key, modifiedAt) -> {
                if (modifiedAt >= cutoff) return;
                Integer refs = jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE temp_object_key=?",
                        Integer.class, key);
                if (refs != null && refs == 0) storage.deleteTempQuietly(key);
            });
        } catch (IOException | RuntimeException exception) {
            log.warn("AI staged orphan scan deferred errorClass={}", exception.getClass().getSimpleName());
        }
    }

    private void audit(String stageId, String event, String from, String to, String code) {
        jdbc.update("INSERT INTO ai_precheck_audit(stage_material_id, instance_id, event_type, from_status, to_status, safe_error_code, created_at) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                stageId, com.wjx871.population.s2.trace.S2TraceContext.instanceId(), event, from, to, code);
    }

    private <T> T transaction(java.util.concurrent.Callable<T> action) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template.execute(status -> {
            try {
                return action.call();
            } catch (RuntimeException exception) {
                throw exception;
            } catch (Exception exception) {
                throw new IllegalStateException(exception);
            }
        });
    }

    private record StageCandidate(String stageId, String objectKey, String tempKey, long ownerId, long version,
                                  String status) {
    }

    private record FileCandidate(String stageId, String objectKey, String tempKey) {
    }
}
