package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.s2.trace.S2TraceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
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
public class AiPrecheckTaskService {
    static final String TASK_TYPE = "DOCUMENT_PRECHECK";
    static final String BUSINESS_TYPE = "MIGRATION_IN";
    static final String REQUEST_VERSION = "1.0";
    static final String RESULT_VERSION = "1.0";
    static final String TASK_ENDPOINT = "POST /api/ai/tasks";
    private static final RowMapper<TaskRow> TASK_ROW_MAPPER = (rs, rowNum) -> new TaskRow(
            rs.getString("task_id"), rs.getLong("owner_user_id"), rs.getString("task_type"),
            rs.getString("business_type"), rs.getString("migration_type"), rs.getString("status"),
            rs.getInt("attempt_count"), rs.getInt("delivery_recovery_count"), rs.getLong("version"), rs.getString("trace_id"),
            rs.getString("request_digest"), rs.getString("result_json"), rs.getString("failure_code"),
            rs.getTimestamp("created_at").toLocalDateTime(),
            rs.getTimestamp("started_at") == null ? null : rs.getTimestamp("started_at").toLocalDateTime(),
            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toLocalDateTime(),
            rs.getTimestamp("next_attempt_at") == null ? null : rs.getTimestamp("next_attempt_at").toLocalDateTime(),
            rs.getString("lease_owner"),
            rs.getTimestamp("lease_expires_at") == null ? null : rs.getTimestamp("lease_expires_at").toLocalDateTime(),
            rs.getTimestamp("last_enqueued_at") == null ? null : rs.getTimestamp("last_enqueued_at").toLocalDateTime());

    private final JdbcTemplate jdbc;
    private final AiPrecheckProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final AiStageMaterialService materialService;

    public AiTaskAcceptedView create(AuthenticatedUser user, AiTaskCreateRequest request, String traceId) {
        validateRequest(request);
        String digest = requestDigest(request);
        TaskRow existing = findByIdempotencyKey(user.userId(), request.idempotencyKey());
        if (existing != null) {
            requireSameRequest(existing, digest);
            return accepted(existing);
        }

        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
        try {
            return transaction(() -> createTransactional(user, request, traceId, digest, taskId));
        } catch (DuplicateKeyException exception) {
            TaskRow raced = findByIdempotencyKey(user.userId(), request.idempotencyKey());
            if (raced == null) throw exception;
            requireSameRequest(raced, digest);
            return accepted(raced);
        }
    }

    public AiTaskView get(AuthenticatedUser user, String taskId) {
        TaskRow task = findOwned(user.userId(), taskId);
        return toView(task);
    }

    private AiTaskAcceptedView createTransactional(AuthenticatedUser user, AiTaskCreateRequest request,
                                                    String traceId, String digest, String taskId) {
        TaskRow existing = findByIdempotencyKey(user.userId(), request.idempotencyKey());
        if (existing != null) {
            requireSameRequest(existing, digest);
            return accepted(existing);
        }

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO ai_precheck_task(task_id, owner_user_id, owner_department_id, owner_region_code, owner_data_scope, task_type, business_type, migration_type, request_schema_version, result_schema_version, status, attempt_count, version, trace_id, request_digest, idempotency_endpoint, idempotency_key, created_at, updated_at, next_attempt_at) VALUES(?,?,?,?,?,?,?,?,'1.0','1.0','PENDING',0,0,?,?,?,?,?,?,?)",
                taskId, user.userId(), user.departmentId(), user.regionCode(),
                user.dataScope() == null ? "SELF" : user.dataScope().name(), TASK_TYPE, BUSINESS_TYPE,
                request.migrationType(), traceId, digest, TASK_ENDPOINT, request.idempotencyKey(),
                Timestamp.valueOf(now), Timestamp.valueOf(now), Timestamp.valueOf(now));

        int reserved = jdbc.update("UPDATE ai_precheck_capacity SET outstanding_count=outstanding_count+1, updated_at=CURRENT_TIMESTAMP WHERE capacity_key='DOCUMENT_PRECHECK' AND outstanding_count < max_outstanding");
        if (reserved != 1) throw new BusinessException(HttpStatus.TOO_MANY_REQUESTS, "AI_PRECHECK_CAPACITY_REACHED");

        List<String> orderedIds = request.stageMaterialIds();
        List<String> lockOrder = orderedIds.stream().sorted(Comparator.naturalOrder()).toList();
        List<AiStageMaterialService.StageRow> materials = new ArrayList<>(orderedIds.size());
        for (String id : lockOrder) {
            AiStageMaterialService.StageRow material = materialService.findOwned(user.userId(), id);
            if (!BUSINESS_TYPE.equals(material.businessType())) throw conflict("MATERIAL_BUSINESS_TYPE_MISMATCH");
            if (!"READY".equals(material.status()) || material.expiresAt() == null
                    || !material.expiresAt().isAfter(LocalDateTime.now())) {
                throw conflict("MATERIAL_NOT_READY");
            }
            materials.add(material);
        }

        for (int i = 0; i < orderedIds.size(); i++) {
            String materialId = orderedIds.get(i);
            AiStageMaterialService.StageRow material = materials.stream()
                    .filter(candidate -> candidate.stageMaterialId().equals(materialId)).findFirst().orElseThrow();
            int occupied = jdbc.update("UPDATE ai_stage_material SET status='IN_USE', active_task_id=?, updated_at=CURRENT_TIMESTAMP, version=version+1 WHERE stage_material_id=? AND owner_user_id=? AND business_type='MIGRATION_IN' AND status='READY' AND expires_at>CURRENT_TIMESTAMP AND version=?",
                    taskId, materialId, user.userId(), material.version());
            if (occupied != 1) throw conflict("MATERIAL_NOT_READY");
            jdbc.update("INSERT INTO ai_precheck_task_material(task_id, stage_material_id, material_order, document_role_hint, content_type, file_size, sha256, original_filename, created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                    taskId, materialId, i, material.documentRoleHint(), material.contentType(), material.fileSize(),
                    material.sha256(), material.originalFilename(), Timestamp.valueOf(now));
            insertAudit(taskId, materialId, null, user.userId(), "MATERIAL_RESERVED", "READY", "IN_USE", null,
                    traceId, 0);
        }

        String eventId = eventId();
        String payload = eventPayload(eventId, taskId, traceId, now);
        jdbc.update("INSERT INTO ai_precheck_outbox(event_id, task_id, event_type, schema_version, destination, payload_json, status, available_at, created_at) VALUES(?,?,?,'1.0','MAIN',?,'PENDING',?,?)",
                eventId, taskId, "DOCUMENT_PRECHECK_REQUESTED", payload, Timestamp.valueOf(now), Timestamp.valueOf(now));
        insertAudit(taskId, null, eventId, user.userId(), "TASK_ACCEPTED", null, "PENDING", null, traceId, 0);
        log.info("AI precheck task accepted taskId={} eventId={} ownerUserId={} instanceId={} traceId={} status=PENDING",
                taskId, eventId, user.userId(), S2TraceContext.instanceId(), traceId);
        return accepted(findOwned(user.userId(), taskId));
    }

    private void validateRequest(AiTaskCreateRequest request) {
        if (!REQUEST_VERSION.equals(request.requestSchemaVersion())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_SCHEMA_VERSION");
        }
        if (!TASK_TYPE.equals(request.taskType())) throw bad("UNSUPPORTED_TASK_TYPE");
        if (!BUSINESS_TYPE.equals(request.businessType())) throw bad("UNSUPPORTED_BUSINESS_TYPE");
        if (!Set.of("OUTSIDE_CITY", "IN_CITY_CROSS_DISTRICT").contains(request.migrationType())) {
            throw bad("UNSUPPORTED_MIGRATION_TYPE");
        }
        if (request.stageMaterialIds() == null || request.stageMaterialIds().isEmpty()
                || request.stageMaterialIds().size() > 10) throw bad("INVALID_STAGE_MATERIAL_COUNT");
        if (request.stageMaterialIds().stream().distinct().count() != request.stageMaterialIds().size()) {
            throw bad("DUPLICATE_STAGE_MATERIAL_ID");
        }
        if (request.stageMaterialIds().stream().anyMatch(id -> !id.matches("stg_[A-Fa-f0-9]{32}"))) {
            throw bad("INVALID_STAGE_MATERIAL_ID");
        }
        if (request.idempotencyKey() == null || !request.idempotencyKey().matches("[A-Za-z0-9._-]{1,128}")) {
            throw bad("INVALID_IDEMPOTENCY_KEY");
        }
    }

    private String requestDigest(AiTaskCreateRequest request) {
        String canonical = REQUEST_VERSION + "\n" + TASK_TYPE + "\n" + BUSINESS_TYPE + "\n"
                + request.migrationType() + "\n" + request.stageMaterialIds().stream().sorted().reduce("", (a, b) -> a + b + "\n");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private void requireSameRequest(TaskRow existing, String digest) {
        if (!existing.requestDigest().equals(digest)) throw conflict("IDEMPOTENCY_KEY_REUSED");
    }

    static String eventPayload(String eventId, String taskId, String traceId, LocalDateTime occurredAt) {
        String occurredAtIso = occurredAt.atZone(java.time.ZoneId.of("Asia/Shanghai")).toOffsetDateTime().toString();
        return "{\"eventId\":\"" + eventId + "\",\"taskId\":\"" + taskId
                + "\",\"taskType\":\"DOCUMENT_PRECHECK\",\"schemaVersion\":\"1.0\",\"traceId\":\""
                + traceId + "\",\"occurredAt\":\"" + occurredAtIso
                + "\",\"producer\":\"population-java\"}";
    }

    static String eventId() {
        return "evt_" + UUID.randomUUID().toString().replace("-", "");
    }

    TaskRow findByIdempotencyKey(long ownerUserId, String key) {
        return jdbc.query("SELECT * FROM ai_precheck_task WHERE owner_user_id=? AND task_type=? AND idempotency_endpoint=? AND idempotency_key=?",
                TASK_ROW_MAPPER, ownerUserId, TASK_TYPE, TASK_ENDPOINT, key).stream().findFirst().orElse(null);
    }

    TaskRow findOwned(long ownerUserId, String taskId) {
        return jdbc.query("SELECT * FROM ai_precheck_task WHERE task_id=? AND owner_user_id=?", TASK_ROW_MAPPER,
                        taskId, ownerUserId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    TaskRow lockOwnedForResolution(long ownerUserId, String taskId) {
        return jdbc.query("SELECT * FROM ai_precheck_task WHERE task_id=? AND owner_user_id=? FOR UPDATE",
                        TASK_ROW_MAPPER, taskId, ownerUserId).stream().findFirst()
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "任务不存在"));
    }

    TaskRow findTask(String taskId) {
        return jdbc.query("SELECT * FROM ai_precheck_task WHERE task_id=?", TASK_ROW_MAPPER, taskId)
                .stream().findFirst().orElse(null);
    }

    private AiTaskAcceptedView accepted(TaskRow row) {
        return new AiTaskAcceptedView(row.taskId(), row.taskType(), row.status(), row.createdAt(),
                "1.0", "1.0", row.traceId(), 1000);
    }

    private AiTaskView toView(TaskRow task) {
        JsonNode result = null;
        AiTaskView.Failure failure = null;
        if ("SUCCEEDED".equals(task.status()) && task.resultJson() != null) {
            try {
                result = objectMapper.readTree(task.resultJson());
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Persisted precheck result is unreadable");
            }
        }
        if ("FAILED".equals(task.status())) {
            failure = new AiTaskView.Failure(task.failureCode(), false, "材料分析任务失败，请查看安全错误码");
        }
        return new AiTaskView(task.taskId(), task.taskType(), task.businessType(), task.migrationType(),
                task.status(), null, result, failure, task.createdAt(), task.startedAt(), task.completedAt(),
                task.traceId(), "1.0", "1.0", task.version());
    }

    private void insertAudit(String taskId, String stageId, String eventId, Long actorId, String event,
                             String from, String to, String safeErrorCode, String traceId, long elapsedMs) {
        jdbc.update("INSERT INTO ai_precheck_audit(task_id, stage_material_id, event_id, actor_user_id, instance_id, trace_id, event_type, from_status, to_status, attempt_count, safe_error_code, elapsed_ms, created_at) VALUES(?,?,?,?,?,?,?,?,?,NULL,?,?,CURRENT_TIMESTAMP)",
                taskId, stageId, eventId, actorId, S2TraceContext.instanceId(), traceId, event, from, to,
                safeErrorCode, elapsedMs);
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

    private BusinessException bad(String code) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code);
    }

    private BusinessException conflict(String code) {
        return new BusinessException(HttpStatus.CONFLICT, code);
    }

    record TaskRow(String taskId, long ownerUserId, String taskType, String businessType,
                   String migrationType, String status, int attemptCount, int deliveryRecoveryCount,
                   long version, String traceId,
                   String requestDigest, String resultJson, String failureCode, LocalDateTime createdAt,
                   LocalDateTime startedAt, LocalDateTime completedAt, LocalDateTime nextAttemptAt,
                   String leaseOwner, LocalDateTime leaseExpiresAt, LocalDateTime lastEnqueuedAt) {
    }

    record EventEnvelope(String eventId, String taskId, String taskType, String schemaVersion,
                         String traceId, String occurredAt, String producer) {
    }

    record TaskMaterial(String stageMaterialId, String documentRoleHint, String contentType, long fileSize,
                        String sha256, String originalFilename, String storedObjectKey, int materialOrder) {
    }

    record WorkContext(TaskRow task, List<TaskMaterial> materials) {
    }
}
