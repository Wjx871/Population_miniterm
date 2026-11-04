package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Request;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Response;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.s2.trace.S2TraceContext;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiPrecheckWorkerService {
    private final JdbcTemplate jdbc;
    private final AiPrecheckProperties properties;
    private final PlatformTransactionManager transactionManager;
    private final ObjectMapper objectMapper;
    private final DocumentAnalysisClient analysisClient;
    private final DocumentAnalysisRequestFactory requestFactory;
    private final DocumentAnalysisResponseValidator responseValidator;
    private final AiPrivateFileStorageService storage;

    public void process(AiPrecheckTaskService.EventEnvelope event) {
        if (!"DOCUMENT_PRECHECK".equals(event.taskType())) {
            failMalformedKnownEvent(event, "MESSAGE_TASK_TYPE_UNSUPPORTED");
            return;
        }
        if (!"1.0".equals(event.schemaVersion())) {
            failMalformedKnownEvent(event, "MESSAGE_SCHEMA_UNSUPPORTED");
            return;
        }

        long startedNanos = System.nanoTime();
        ClaimResult claim = transaction(() -> claim(event));
        if (claim.context() == null) return;
        AiPrecheckTaskService.WorkContext context = claim.context();
        int attempt = context.task().attemptCount();
        try {
            for (AiPrecheckTaskService.TaskMaterial material : context.materials()) {
                if (!storage.objectExists(material.storedObjectKey())) {
                    throw new AnalysisFailure("MATERIAL_OBJECT_MISSING", false);
                }
            }
            Request request = requestFactory.build(context);
            Response response = responseValidator.validate(request, analysisClient.analyze(request));
            String resultJson = objectMapper.writeValueAsString(response);
            completeSuccess(event, context.task(), resultJson, attempt, elapsed(startedNanos));
        } catch (AnalysisFailure failure) {
            handleFailure(event, context.task(), failure.safeCode(), failure.retryable(), attempt,
                    elapsed(startedNanos));
        } catch (JsonProcessingException exception) {
            handleFailure(event, context.task(), "AI_RESULT_SERIALIZATION_FAILED", false, attempt,
                    elapsed(startedNanos));
        } catch (RuntimeException exception) {
            log.warn("AI precheck worker failed taskId={} eventId={} traceId={} attempt={} errorClass={}",
                    context.task().taskId(), event.eventId(), event.traceId(), attempt,
                    exception.getClass().getSimpleName());
            throw exception;
        }
    }

    public void recordPoison(String eventId, String traceId, String safeCode) {
        if (eventId == null || !eventId.matches("evt_[A-Fa-f0-9]{32}")) return;
        transaction(() -> {
            upsertInbox(eventId, null, traceId, "POISON_DLQ", safeCode);
            jdbc.update("INSERT INTO ai_precheck_audit(event_id, instance_id, trace_id, event_type, safe_error_code, created_at) VALUES(?,?,?,'POISON_MESSAGE_DLQ',?,CURRENT_TIMESTAMP)",
                    eventId, S2TraceContext.instanceId(), safeTrace(traceId), safeCode);
            return null;
        });
    }

    public void recoverExpiredLeases() {
        List<String> taskIds = jdbc.query("SELECT task_id FROM ai_precheck_task WHERE status='RUNNING' AND lease_expires_at<=CURRENT_TIMESTAMP ORDER BY lease_expires_at LIMIT 50",
                (rs, rowNum) -> rs.getString(1));
        for (String taskId : taskIds) {
            try {
                transaction(() -> {
                    recoverOne(taskId);
                    return null;
                });
            } catch (RuntimeException exception) {
                log.warn("AI precheck lease recovery failed taskId={} errorClass={}", taskId,
                        exception.getClass().getSimpleName());
            }
        }
        recoverStalePendingDeliveries();
    }

    @Scheduled(fixedDelayString = "${AI_PRECHECK_LEASE_RECOVERY_POLL_MS:5000}")
    public void scheduledLeaseRecovery() {
        recoverExpiredLeases();
    }

    private ClaimResult claim(AiPrecheckTaskService.EventEnvelope event) {
        String expectedTask = jdbc.query("SELECT task_id FROM ai_precheck_outbox WHERE event_id=? AND task_id=? AND destination='MAIN'",
                (rs, rowNum) -> rs.getString(1), event.eventId(), event.taskId()).stream().findFirst()
                .orElseThrow(() -> new AiMessagePoisonException("MESSAGE_EVENT_NOT_FOUND"));
        if (alreadyInInbox(event.eventId())) return new ClaimResult(null);
        AiPrecheckTaskService.TaskRow task = findTask(expectedTask);
        if (task == null) throw new AiMessagePoisonException("MESSAGE_TASK_NOT_FOUND");
        if (isTerminal(task.status())) {
            upsertInbox(event.eventId(), task.taskId(), event.traceId(), "DUPLICATE_TERMINAL", null);
            return new ClaimResult(null);
        }
        LocalDateTime now = LocalDateTime.now();
        if ("RUNNING".equals(task.status())) {
            upsertInbox(event.eventId(), task.taskId(), event.traceId(), "DUPLICATE_ACTIVE_LEASE", null);
            return new ClaimResult(null);
        }
        if (!"PENDING".equals(task.status())) {
            upsertInbox(event.eventId(), task.taskId(), event.traceId(), "DUPLICATE_NON_PENDING", null);
            return new ClaimResult(null);
        }
        if (task.nextAttemptAt() != null && task.nextAttemptAt().isAfter(now)) {
            scheduleDuplicateDelivery(event, task, task.nextAttemptAt());
            return new ClaimResult(null);
        }

        String owner = S2TraceContext.instanceId();
        LocalDateTime leaseUntil = now.plus(properties.getWorkerLeaseDuration());
        int updated = jdbc.update("UPDATE ai_precheck_task SET status='RUNNING', attempt_count=attempt_count+1, version=version+1, started_at=COALESCE(started_at,?), lease_owner=?, lease_expires_at=?, updated_at=? WHERE task_id=? AND status='PENDING' AND version=? AND (next_attempt_at IS NULL OR next_attempt_at<=?)",
                Timestamp.valueOf(now), owner, Timestamp.valueOf(leaseUntil), Timestamp.valueOf(now), task.taskId(),
                task.version(), Timestamp.valueOf(now));
        if (updated != 1) {
            upsertInbox(event.eventId(), task.taskId(), event.traceId(), "CLAIM_LOST", null);
            return new ClaimResult(null);
        }
        AiPrecheckTaskService.TaskRow claimed = findTask(task.taskId());
        List<AiPrecheckTaskService.TaskMaterial> materials = loadMaterials(claimed);
        upsertInbox(event.eventId(), task.taskId(), event.traceId(), "CLAIMED", null);
        audit(task.taskId(), null, event.eventId(), "TASK_STARTED", "PENDING", "RUNNING", claimed.attemptCount(),
                null, event.traceId(), 0);
        log.info("AI precheck task started taskId={} eventId={} instanceId={} attempt={} traceId={}",
                task.taskId(), event.eventId(), owner, claimed.attemptCount(), event.traceId());
        return new ClaimResult(new AiPrecheckTaskService.WorkContext(claimed, materials));
    }

    private List<AiPrecheckTaskService.TaskMaterial> loadMaterials(AiPrecheckTaskService.TaskRow task) {
        return jdbc.query("SELECT tm.stage_material_id, tm.document_role_hint, tm.content_type, tm.file_size, tm.sha256, tm.original_filename, sm.stored_object_key, tm.material_order FROM ai_precheck_task_material tm JOIN ai_stage_material sm ON sm.stage_material_id=tm.stage_material_id WHERE tm.task_id=? AND sm.owner_user_id=? AND sm.status='IN_USE' AND sm.active_task_id=? ORDER BY tm.material_order",
                (rs, rowNum) -> new AiPrecheckTaskService.TaskMaterial(rs.getString("stage_material_id"),
                        rs.getString("document_role_hint"), rs.getString("content_type"), rs.getLong("file_size"),
                        rs.getString("sha256"), rs.getString("original_filename"),
                        rs.getString("stored_object_key"), rs.getInt("material_order")),
                task.taskId(), task.ownerUserId(), task.taskId());
    }

    private void completeSuccess(AiPrecheckTaskService.EventEnvelope event, AiPrecheckTaskService.TaskRow claimed,
                                 String resultJson, int attempt, long elapsedMs) {
        transaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            int updated = jdbc.update("UPDATE ai_precheck_task SET status='SUCCEEDED', result_json=?, failure_code=NULL, completed_at=?, updated_at=?, lease_owner=NULL, lease_expires_at=NULL, next_attempt_at=NULL, version=version+1 WHERE task_id=? AND status='RUNNING' AND version=? AND lease_owner=? AND lease_expires_at>CURRENT_TIMESTAMP",
                    resultJson, Timestamp.valueOf(now), Timestamp.valueOf(now), claimed.taskId(), claimed.version(),
                    claimed.leaseOwner());
            if (updated != 1) {
                upsertInbox(event.eventId(), claimed.taskId(), event.traceId(), "STALE_RESULT_SUPPRESSED", null);
                audit(claimed.taskId(), null, event.eventId(), "STALE_RESULT_SUPPRESSED", "RUNNING", null, attempt,
                        "STALE_LEASE", event.traceId(), elapsedMs);
                return null;
            }
            restoreMaterials(claimed.taskId(), now);
            releaseCapacity();
            upsertInbox(event.eventId(), claimed.taskId(), event.traceId(), "SUCCEEDED", null);
            audit(claimed.taskId(), null, event.eventId(), "TASK_SUCCEEDED", "RUNNING", "SUCCEEDED", attempt,
                    null, event.traceId(), elapsedMs);
            log.info("AI precheck task completed taskId={} eventId={} instanceId={} attempt={} status=SUCCEEDED elapsedMs={} traceId={}",
                    claimed.taskId(), event.eventId(), S2TraceContext.instanceId(), attempt, elapsedMs,
                    event.traceId());
            return null;
        });
    }

    private void handleFailure(AiPrecheckTaskService.EventEnvelope event, AiPrecheckTaskService.TaskRow claimed,
                               String safeCode, boolean retryable, int attempt, long elapsedMs) {
        transaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            boolean shouldRetry = retryable && attempt < properties.getMaxProcessingAttempts();
            if (shouldRetry) {
                Duration delay = backoffForAttempt(attempt);
                LocalDateTime nextAttempt = now.plus(delay);
                int updated = jdbc.update("UPDATE ai_precheck_task SET status='PENDING', next_attempt_at=?, failure_code=?, lease_owner=NULL, lease_expires_at=NULL, updated_at=?, version=version+1 WHERE task_id=? AND status='RUNNING' AND version=? AND lease_owner=? AND lease_expires_at>CURRENT_TIMESTAMP",
                        Timestamp.valueOf(nextAttempt), safeCode, Timestamp.valueOf(now), claimed.taskId(),
                        claimed.version(), claimed.leaseOwner());
                if (updated != 1) {
                    upsertInbox(event.eventId(), claimed.taskId(), event.traceId(), "STALE_FAILURE_SUPPRESSED", null);
                    return null;
                }
                String retryEventId = AiPrecheckTaskService.eventId();
                String payload = AiPrecheckTaskService.eventPayload(retryEventId, claimed.taskId(), event.traceId(), now);
                jdbc.update("INSERT INTO ai_precheck_outbox(event_id, task_id, event_type, schema_version, destination, payload_json, status, available_at, created_at) VALUES(?,?,?,'1.0','MAIN',?,'PENDING',?,?)",
                        retryEventId, claimed.taskId(), "DOCUMENT_PRECHECK_REQUESTED", payload,
                        Timestamp.valueOf(nextAttempt), Timestamp.valueOf(now));
                upsertInbox(event.eventId(), claimed.taskId(), event.traceId(), "RETRY_SCHEDULED", safeCode);
                audit(claimed.taskId(), null, event.eventId(), "TASK_RETRY_SCHEDULED", "RUNNING", "PENDING",
                        attempt, safeCode, event.traceId(), elapsedMs);
                log.warn("AI precheck retry scheduled taskId={} eventId={} nextEventId={} attempt={} errorCode={} traceId={}",
                        claimed.taskId(), event.eventId(), retryEventId, attempt, safeCode, event.traceId());
                return null;
            }
            persistFailure(event, claimed, safeCode, attempt, elapsedMs, now);
            return null;
        });
    }

    public void failMalformedKnownEvent(AiPrecheckTaskService.EventEnvelope event, String code) {
        transaction(() -> {
            boolean eventExists = jdbc.query("SELECT task_id FROM ai_precheck_outbox WHERE event_id=? AND task_id=? AND destination='MAIN'",
                    (rs, rowNum) -> rs.getString(1), event.eventId(), event.taskId()).stream().findFirst().isPresent();
            if (!eventExists) throw new AiMessagePoisonException("MESSAGE_EVENT_NOT_FOUND");
            AiPrecheckTaskService.TaskRow task = findTask(event.taskId());
            if (task == null || isTerminal(task.status())) {
                upsertInbox(event.eventId(), event.taskId(), event.traceId(), "INVALID_DUPLICATE", code);
                return null;
            }
            if (!"PENDING".equals(task.status())) {
                upsertInbox(event.eventId(), event.taskId(), event.traceId(), "INVALID_ACTIVE", code);
                return null;
            }
            persistFailure(event, task, code, task.attemptCount(), 0, LocalDateTime.now());
            return null;
        });
    }

    private void persistFailure(AiPrecheckTaskService.EventEnvelope event, AiPrecheckTaskService.TaskRow task,
                                String code, int attempt, long elapsedMs, LocalDateTime now) {
        int updated = jdbc.update("UPDATE ai_precheck_task SET status='FAILED', result_json=NULL, failure_code=?, completed_at=?, updated_at=?, lease_owner=NULL, lease_expires_at=NULL, next_attempt_at=NULL, version=version+1 WHERE task_id=? AND status IN ('PENDING','RUNNING') AND version=?",
                code, Timestamp.valueOf(now), Timestamp.valueOf(now), task.taskId(), task.version());
        if (updated != 1) {
            upsertInbox(event.eventId(), task.taskId(), event.traceId(), "STALE_FAILURE_SUPPRESSED", null);
            return;
        }
        restoreMaterials(task.taskId(), now);
        releaseCapacity();
        String dlqEventId = AiPrecheckTaskService.eventId();
        String payload = AiPrecheckTaskService.eventPayload(dlqEventId, task.taskId(), task.traceId(), now);
        jdbc.update("INSERT INTO ai_precheck_outbox(event_id, task_id, event_type, schema_version, destination, payload_json, status, available_at, created_at, last_error_code) VALUES(?,?,?,'1.0','DLQ',?,'PENDING',?,?,?)",
                dlqEventId, task.taskId(), "DOCUMENT_PRECHECK_FAILED", payload, Timestamp.valueOf(now),
                Timestamp.valueOf(now), code);
        upsertInbox(event.eventId(), task.taskId(), event.traceId(), "FAILED", code);
        audit(task.taskId(), null, event.eventId(), "TASK_FAILED", task.status(), "FAILED", attempt, code,
                event.traceId(), elapsedMs);
        log.warn("AI precheck task failed taskId={} eventId={} dlqEventId={} attempt={} errorCode={} traceId={}",
                task.taskId(), event.eventId(), dlqEventId, attempt, code, event.traceId());
    }

    private void recoverOne(String taskId) {
        AiPrecheckTaskService.TaskRow task = findTask(taskId);
        if (task == null || !"RUNNING".equals(task.status()) || task.leaseExpiresAt() == null
                || task.leaseExpiresAt().isAfter(LocalDateTime.now())) return;
        String lastEventId = jdbc.query("SELECT event_id FROM ai_precheck_inbox WHERE task_id=? ORDER BY processed_at DESC LIMIT 1",
                (rs, rowNum) -> rs.getString(1), taskId).stream().findFirst().orElse("evt_" + UUID.randomUUID().toString().replace("-", ""));
        AiPrecheckTaskService.EventEnvelope event = new AiPrecheckTaskService.EventEnvelope(lastEventId, taskId,
                task.taskType(), "1.0", task.traceId(), LocalDateTime.now().toString(), "population-java");
        if (task.attemptCount() >= properties.getMaxProcessingAttempts()) {
            persistFailure(event, task, "WORKER_LEASE_EXPIRED", task.attemptCount(), 0, LocalDateTime.now());
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextAttempt = now.plus(backoffForAttempt(task.attemptCount()));
        int updated = jdbc.update("UPDATE ai_precheck_task SET status='PENDING', next_attempt_at=?, failure_code='WORKER_LEASE_EXPIRED', lease_owner=NULL, lease_expires_at=NULL, updated_at=?, version=version+1 WHERE task_id=? AND status='RUNNING' AND version=? AND lease_expires_at<=CURRENT_TIMESTAMP",
                Timestamp.valueOf(nextAttempt), Timestamp.valueOf(now), task.taskId(), task.version());
        if (updated != 1) return;
        String eventId = AiPrecheckTaskService.eventId();
        String payload = AiPrecheckTaskService.eventPayload(eventId, taskId, task.traceId(), now);
        jdbc.update("INSERT INTO ai_precheck_outbox(event_id, task_id, event_type, schema_version, destination, payload_json, status, available_at, created_at) VALUES(?,?,?,'1.0','MAIN',?,'PENDING',?,?)",
                eventId, taskId, "DOCUMENT_PRECHECK_REQUESTED", payload, Timestamp.valueOf(nextAttempt),
                Timestamp.valueOf(now));
        upsertInbox(lastEventId, taskId, task.traceId(), "LEASE_RECOVERED", "WORKER_LEASE_EXPIRED");
        audit(taskId, null, lastEventId, "TASK_LEASE_RECOVERED", "RUNNING", "PENDING", task.attemptCount(),
                "WORKER_LEASE_EXPIRED", task.traceId(), 0);
        log.warn("AI precheck lease recovered taskId={} nextEventId={} attempt={} traceId={}", taskId, eventId,
                task.attemptCount(), task.traceId());
    }

    private void recoverStalePendingDeliveries() {
        long drainSeconds = Math.max(60, (long) Math.ceil((double) properties.getQueueCapacity()
                / properties.getConsumerConcurrency()) * properties.getProcessingTimeout().toSeconds() + 60);
        Timestamp staleBefore = Timestamp.valueOf(LocalDateTime.now().minusSeconds(drainSeconds));
        List<String> taskIds = jdbc.query("SELECT task_id FROM ai_precheck_task WHERE status='PENDING' AND last_enqueued_at<=? AND delivery_recovery_count<3 ORDER BY last_enqueued_at LIMIT 50",
                (rs, rowNum) -> rs.getString(1), staleBefore);
        for (String taskId : taskIds) {
            transaction(() -> {
                AiPrecheckTaskService.TaskRow task = findTask(taskId);
                if (task == null || !"PENDING".equals(task.status()) || task.lastEnqueuedAt() == null
                        || task.lastEnqueuedAt().isAfter(LocalDateTime.now().minusSeconds(drainSeconds))) return null;
                LocalDateTime now = LocalDateTime.now();
                int changed = jdbc.update("UPDATE ai_precheck_task SET delivery_recovery_count=delivery_recovery_count+1, last_enqueued_at=?, updated_at=?, version=version+1 WHERE task_id=? AND status='PENDING' AND version=? AND last_enqueued_at<=? AND delivery_recovery_count<3",
                        Timestamp.valueOf(now), Timestamp.valueOf(now), taskId, task.version(), staleBefore);
                if (changed != 1) return null;
                String eventId = AiPrecheckTaskService.eventId();
                String payload = AiPrecheckTaskService.eventPayload(eventId, taskId, task.traceId(), now);
                jdbc.update("INSERT INTO ai_precheck_outbox(event_id, task_id, event_type, schema_version, destination, payload_json, status, available_at, created_at) VALUES(?,?,?,'1.0','MAIN',?,'PENDING',?,?)",
                        eventId, taskId, "DOCUMENT_PRECHECK_REQUESTED", payload, Timestamp.valueOf(now),
                        Timestamp.valueOf(now));
                audit(taskId, null, eventId, "PENDING_DELIVERY_RECOVERED", "PENDING", "PENDING",
                        task.attemptCount(), "DELIVERY_ACK_UNCONFIRMED", task.traceId(), 0);
                return null;
            });
        }

        List<String> exhausted = jdbc.query("SELECT task_id FROM ai_precheck_task WHERE status='PENDING' AND delivery_recovery_count>=3 AND last_enqueued_at<=? ORDER BY last_enqueued_at LIMIT 50",
                (rs, rowNum) -> rs.getString(1), staleBefore);
        for (String taskId : exhausted) {
            transaction(() -> {
                AiPrecheckTaskService.TaskRow task = findTask(taskId);
                if (task != null && "PENDING".equals(task.status()) && task.deliveryRecoveryCount() >= 3
                        && task.lastEnqueuedAt() != null
                        && !task.lastEnqueuedAt().isAfter(LocalDateTime.now().minusSeconds(drainSeconds))) {
                    AiPrecheckTaskService.EventEnvelope event = new AiPrecheckTaskService.EventEnvelope(
                            AiPrecheckTaskService.eventId(), taskId, task.taskType(), "1.0", task.traceId(),
                            LocalDateTime.now().toString(), "population-java");
                    persistFailure(event, task, "DELIVERY_RECOVERY_EXHAUSTED", task.attemptCount(), 0,
                            LocalDateTime.now());
                }
                return null;
            });
        }
    }

    private void scheduleDuplicateDelivery(AiPrecheckTaskService.EventEnvelope event,
                                          AiPrecheckTaskService.TaskRow task, LocalDateTime nextAttempt) {
        LocalDateTime now = LocalDateTime.now();
        String eventId = AiPrecheckTaskService.eventId();
        String payload = AiPrecheckTaskService.eventPayload(eventId, task.taskId(), task.traceId(), now);
        jdbc.update("INSERT INTO ai_precheck_outbox(event_id, task_id, event_type, schema_version, destination, payload_json, status, available_at, created_at) VALUES(?,?,?,'1.0','MAIN',?,'PENDING',?,?)",
                eventId, task.taskId(), "DOCUMENT_PRECHECK_REQUESTED", payload,
                Timestamp.valueOf(nextAttempt), Timestamp.valueOf(now));
        upsertInbox(event.eventId(), task.taskId(), event.traceId(), "EARLY_DELIVERY_RESCHEDULED", null);
    }

    private void restoreMaterials(String taskId, LocalDateTime now) {
        LocalDateTime expiresAt = now.plus(properties.getCompletedRetention());
        jdbc.update("UPDATE ai_stage_material SET status='READY', active_task_id=NULL, expires_at=?, updated_at=?, version=version+1 WHERE active_task_id=? AND status='IN_USE'",
                Timestamp.valueOf(expiresAt), Timestamp.valueOf(now), taskId);
    }

    private void releaseCapacity() {
        int changed = jdbc.update("UPDATE ai_precheck_capacity SET outstanding_count=outstanding_count-1, updated_at=CURRENT_TIMESTAMP WHERE capacity_key='DOCUMENT_PRECHECK' AND outstanding_count>0");
        if (changed != 1) throw new IllegalStateException("AI precheck capacity counter underflow");
    }

    private Duration backoffForAttempt(int attempt) {
        int index = Math.max(0, Math.min(attempt - 1, properties.getBackoff().size() - 1));
        return properties.getBackoff().get(index);
    }

    private boolean alreadyInInbox(String eventId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT COUNT(*)>0 FROM ai_precheck_inbox WHERE event_id=?",
                Boolean.class, eventId));
    }

    private void upsertInbox(String eventId, String taskId, String traceId, String outcome, String safeErrorCode) {
        int updated = jdbc.update("UPDATE ai_precheck_inbox SET task_id=?, trace_id=?, outcome=?, safe_error_code=?, processed_at=CURRENT_TIMESTAMP WHERE event_id=?",
                taskId, safeTrace(traceId), outcome, safeErrorCode, eventId);
        if (updated == 0) {
            try {
                jdbc.update("INSERT INTO ai_precheck_inbox(event_id, task_id, trace_id, outcome, safe_error_code, processed_at) VALUES(?,?,?,?,?,CURRENT_TIMESTAMP)",
                        eventId, taskId, safeTrace(traceId), outcome, safeErrorCode);
            } catch (DuplicateKeyException ignored) {
                jdbc.update("UPDATE ai_precheck_inbox SET task_id=?, trace_id=?, outcome=?, safe_error_code=?, processed_at=CURRENT_TIMESTAMP WHERE event_id=?",
                        taskId, safeTrace(traceId), outcome, safeErrorCode, eventId);
            }
        }
    }

    private AiPrecheckTaskService.TaskRow findTask(String id) {
        return jdbc.query("SELECT * FROM ai_precheck_task WHERE task_id=?", (rs, rowNum) -> new AiPrecheckTaskService.TaskRow(
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
                rs.getTimestamp("last_enqueued_at") == null ? null : rs.getTimestamp("last_enqueued_at").toLocalDateTime()), id)
                .stream().findFirst().orElse(null);
    }

    private boolean isTerminal(String status) {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status);
    }

    private long elapsed(long startedNanos) {
        return Math.max(0, (System.nanoTime() - startedNanos) / 1_000_000);
    }

    private String safeTrace(String traceId) {
        return traceId != null && traceId.matches("[A-Za-z0-9._-]{1,64}") ? traceId : null;
    }

    private void audit(String taskId, String stageId, String eventId, String event, String from, String to,
                       int attempt, String safeErrorCode, String traceId, long elapsedMs) {
        jdbc.update("INSERT INTO ai_precheck_audit(task_id, stage_material_id, event_id, instance_id, trace_id, event_type, from_status, to_status, attempt_count, safe_error_code, elapsed_ms, created_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                taskId, stageId, eventId, S2TraceContext.instanceId(), safeTrace(traceId), event, from, to,
                attempt, safeErrorCode, elapsedMs);
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

    private record ClaimResult(AiPrecheckTaskService.WorkContext context) {
    }
}
