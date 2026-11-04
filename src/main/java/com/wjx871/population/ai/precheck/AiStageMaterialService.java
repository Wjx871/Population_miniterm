package com.wjx871.population.ai.precheck;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiStageMaterialService {
    private static final String ENDPOINT = "POST /api/ai/materials";
    private static final RowMapper<StageRow> ROW_MAPPER = (rs, rowNum) -> new StageRow(
            rs.getString("stage_material_id"), rs.getLong("owner_user_id"), rs.getString("business_type"),
            rs.getString("document_role_hint"), rs.getString("original_filename"),
            rs.getString("stored_object_key"), rs.getString("temp_object_key"), rs.getString("content_type"),
            rs.getString("file_extension"), rs.getLong("file_size"), rs.getString("sha256"),
            rs.getString("request_digest"), rs.getString("status"), rs.getLong("version"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime());

    private final JdbcTemplate jdbc;
    private final AiPrecheckProperties properties;
    private final AiPrivateFileStorageService storage;
    private final PlatformTransactionManager transactionManager;

    public UploadResult upload(AuthenticatedUser user, String idempotencyKey, String businessType,
                               DocumentRoleHint roleHint, org.springframework.web.multipart.MultipartFile file,
                               String traceId) {
        if (!"MIGRATION_IN".equals(businessType)) throw bad("S3 仅支持 MIGRATION_IN 材料");
        if (roleHint == null) roleHint = DocumentRoleHint.UNKNOWN;
        validateKey(idempotencyKey);
        AiPrivateFileStorageService.PreparedFile prepared = storage.prepare(file);
        String requestDigest = digest(prepared.sha256() + "\nMIGRATION_IN\n" + roleHint.name()
                + "\n" + prepared.contentType());
        String stageId = "stg_" + UUID.randomUUID().toString().replace("-", "");
        StageRow claimed;
        boolean inserted;
        try {
            ClaimResult result = insertClaim(user, idempotencyKey, stageId, prepared, requestDigest, roleHint, traceId);
            claimed = result.row();
            inserted = result.inserted();
        } catch (DuplicateKeyException exception) {
            storage.deleteTempQuietly(prepared.tempObjectKey());
            claimed = findByKey(user.userId(), idempotencyKey);
            if (claimed == null) throw conflict("IDEMPOTENCY_KEY_REUSED");
            requireSameRequest(claimed, requestDigest);
            return new UploadResult(view(refreshAvailability(claimed)), true);
        } catch (RuntimeException exception) {
            storage.deleteTempQuietly(prepared.tempObjectKey());
            throw exception;
        }

        if (!inserted) {
            storage.deleteTempQuietly(prepared.tempObjectKey());
            requireSameRequest(claimed, requestDigest);
            return new UploadResult(view(refreshAvailability(claimed)), true);
        }

        try {
            storage.promote(prepared);
            StageRow ready = transaction(() -> {
                LocalDateTime now = LocalDateTime.now();
                LocalDateTime expiry = now.plus(properties.getReadyRetention());
                int updated = jdbc.update("UPDATE ai_stage_material SET status='READY', temp_object_key=NULL, uploaded_at=?, expires_at=?, updated_at=?, version=version+1 WHERE stage_material_id=? AND status='UPLOADING' AND version=0",
                        java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(expiry),
                        java.sql.Timestamp.valueOf(now), stageId);
                if (updated != 1) throw new IllegalStateException("AI staged upload claim changed before completion");
                audit(null, stageId, user.userId(), "MATERIAL_UPLOADED", "UPLOADING", "READY", null, traceId, 0);
                return findOwned(user.userId(), stageId);
            });
            log.info("AI stage material ready stageMaterialId={} ownerUserId={} instanceId={} sizeBytes={} traceId={}",
                    stageId, user.userId(), com.wjx871.population.s2.trace.S2TraceContext.instanceId(),
                    prepared.fileSize(), traceId);
            return new UploadResult(view(ready), false);
        } catch (Exception exception) {
            storage.deleteTempQuietly(prepared.tempObjectKey());
            LocalDateTime now = LocalDateTime.now();
            transaction(() -> {
                jdbc.update("UPDATE ai_stage_material SET status='UPLOAD_FAILED', temp_object_key=NULL, expires_at=?, updated_at=?, version=version+1 WHERE stage_material_id=? AND status='UPLOADING'",
                        java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now), stageId);
                audit(null, stageId, user.userId(), "MATERIAL_UPLOAD_FAILED", "UPLOADING", "UPLOAD_FAILED",
                        "UPLOAD_FAILED", traceId, 0);
                return null;
            });
            storage.deleteObjectQuietly(prepared.storedObjectKey());
            if (exception instanceof BusinessException businessException) throw businessException;
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "预审材料暂存失败");
        }
    }

    public AiStageMaterialView get(AuthenticatedUser user, String stageId) {
        StageRow row = findOwned(user.userId(), stageId);
        return view(refreshAvailability(row));
    }

    public AiStageMaterialView delete(AuthenticatedUser user, String stageId, String traceId) {
        StageRow cancelled = transaction(() -> {
            StageRow row = findOwned(user.userId(), stageId);
            if ("CANCELLED".equals(row.status()) || "EXPIRED".equals(row.status())) return row;
            if (!"READY".equals(row.status()) && !"UPLOAD_FAILED".equals(row.status())) {
                throw conflict("MATERIAL_IN_USE_OR_UNAVAILABLE");
            }
            LocalDateTime now = LocalDateTime.now();
            int updated = jdbc.update("UPDATE ai_stage_material SET status='CANCELLED', active_task_id=NULL, deleted_at=?, expires_at=?, updated_at=?, version=version+1 WHERE stage_material_id=? AND owner_user_id=? AND status=? AND version=?",
                    java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now),
                    stageId, user.userId(), row.status(), row.version());
            if (updated != 1) throw conflict("MATERIAL_STATE_CHANGED");
            audit(null, stageId, user.userId(), "MATERIAL_CANCELLED", row.status(), "CANCELLED", null, traceId, 0);
            return findOwned(user.userId(), stageId);
        });
        deletePhysicalFiles(cancelled);
        return view(cancelled);
    }

    StageRow findOwned(long ownerUserId, String stageId) {
        return jdbc.query("SELECT * FROM ai_stage_material WHERE stage_material_id=? AND owner_user_id=?",
                        ROW_MAPPER, stageId, ownerUserId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "材料不存在"));
    }

    StageRow findByKey(long ownerUserId, String key) {
        return jdbc.query("SELECT * FROM ai_stage_material WHERE owner_user_id=? AND idempotency_endpoint=? AND idempotency_key=?",
                ROW_MAPPER, ownerUserId, ENDPOINT, key).stream().findFirst().orElse(null);
    }

    private ClaimResult insertClaim(AuthenticatedUser user, String idempotencyKey, String stageId,
                                    AiPrivateFileStorageService.PreparedFile file, String requestDigest,
                                    DocumentRoleHint roleHint, String traceId) {
        return transaction(() -> {
            StageRow existing = findByKey(user.userId(), idempotencyKey);
            if (existing != null) return new ClaimResult(existing, false);
            jdbc.update("INSERT INTO ai_stage_material(stage_material_id, owner_user_id, owner_department_id, owner_region_code, owner_data_scope, business_type, document_role_hint, original_filename, stored_object_key, temp_object_key, content_type, file_extension, file_size, sha256, request_digest, idempotency_endpoint, idempotency_key, status, created_at, updated_at, version) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,'UPLOADING',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,0)",
                    stageId, user.userId(), user.departmentId(), user.regionCode(),
                    user.dataScope() == null ? "SELF" : user.dataScope().name(), "MIGRATION_IN", roleHint.name(),
                    file.originalFilename(), file.storedObjectKey(), file.tempObjectKey(), file.contentType(),
                    file.fileExtension(), file.fileSize(), file.sha256(), requestDigest, ENDPOINT, idempotencyKey);
            StageRow inserted = findOwned(user.userId(), stageId);
            audit(null, stageId, user.userId(), "MATERIAL_UPLOAD_STARTED", null, "UPLOADING", null, traceId, 0);
            return new ClaimResult(inserted, true);
        });
    }

    private StageRow refreshAvailability(StageRow row) {
        if (!"READY".equals(row.status())) return row;
        LocalDateTime now = LocalDateTime.now();
        if (row.expiresAt() != null && !row.expiresAt().isAfter(now)) {
            StageRow expired = transaction(() -> {
                int updated = jdbc.update("UPDATE ai_stage_material SET status='EXPIRED', deleted_at=?, updated_at=?, version=version+1 WHERE stage_material_id=? AND status='READY' AND version=? AND expires_at<=?",
                        java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now), row.stageMaterialId(),
                        row.version(), java.sql.Timestamp.valueOf(now));
                if (updated == 1) audit(null, row.stageMaterialId(), row.ownerUserId(), "MATERIAL_EXPIRED",
                        "READY", "EXPIRED", null, null, 0);
                return jdbc.query("SELECT * FROM ai_stage_material WHERE stage_material_id=?", ROW_MAPPER,
                        row.stageMaterialId()).stream().findFirst().orElse(row);
            });
            if ("EXPIRED".equals(expired.status())) storage.deleteObjectQuietly(expired.storedObjectKey());
            return expired;
        }
        if (!storage.objectExists(row.storedObjectKey())) {
            return transaction(() -> {
                int updated = jdbc.update("UPDATE ai_stage_material SET status='UPLOAD_FAILED', updated_at=?, version=version+1 WHERE stage_material_id=? AND status='READY' AND version=?",
                        java.sql.Timestamp.valueOf(now), row.stageMaterialId(), row.version());
                if (updated == 1) audit(null, row.stageMaterialId(), row.ownerUserId(), "MATERIAL_OBJECT_MISSING",
                        "READY", "UPLOAD_FAILED", "MATERIAL_OBJECT_MISSING", null, 0);
                return jdbc.query("SELECT * FROM ai_stage_material WHERE stage_material_id=?", ROW_MAPPER,
                        row.stageMaterialId()).stream().findFirst().orElse(row);
            });
        }
        return row;
    }

    private void deletePhysicalFiles(StageRow row) {
        try {
            storage.deleteObject(row.storedObjectKey());
            storage.deleteTemp(row.tempObjectKey());
            LocalDateTime now = LocalDateTime.now();
            jdbc.update("UPDATE ai_stage_material SET file_deleted_at=?, updated_at=? WHERE stage_material_id=? AND status IN ('UPLOAD_FAILED','CANCELLED','EXPIRED')",
                    java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now), row.stageMaterialId());
        } catch (IOException ignored) {
            // The lifecycle reaper retries only server-generated object keys in this private store.
        }
    }

    private void requireSameRequest(StageRow existing, String requestDigest) {
        if (!existing.requestDigest().equals(requestDigest)) throw conflict("IDEMPOTENCY_KEY_REUSED");
    }

    private void audit(String taskId, String stageId, Long actorId, String event, String from, String to,
                       String errorCode, String traceId, long elapsedMs) {
        jdbc.update("INSERT INTO ai_precheck_audit(task_id, stage_material_id, actor_user_id, instance_id, trace_id, event_type, from_status, to_status, safe_error_code, elapsed_ms, created_at) VALUES(?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                taskId, stageId, actorId, com.wjx871.population.s2.trace.S2TraceContext.instanceId(), traceId,
                event, from, to, errorCode, elapsedMs);
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

    private AiStageMaterialView view(StageRow row) {
        return new AiStageMaterialView(row.stageMaterialId(), row.status(), row.businessType(),
                row.documentRoleHint(), row.originalFilename(), row.contentType(), row.fileSize(), row.sha256(),
                row.createdAt(), row.expiresAt());
    }

    private void validateKey(String key) {
        if (key == null || !key.matches("[A-Za-z0-9._-]{1,128}")) throw bad("Idempotency-Key 格式不合法");
    }

    private String digest(String text) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private BusinessException bad(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, message);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    private record ClaimResult(StageRow row, boolean inserted) {
    }

    record StageRow(String stageMaterialId, long ownerUserId, String businessType, String documentRoleHint,
                    String originalFilename, String storedObjectKey, String tempObjectKey, String contentType,
                    String fileExtension, long fileSize, String sha256, String requestDigest, String status,
                    long version, LocalDateTime createdAt, LocalDateTime expiresAt) {
    }

    public record UploadResult(AiStageMaterialView material, boolean replayed) {
    }
}
