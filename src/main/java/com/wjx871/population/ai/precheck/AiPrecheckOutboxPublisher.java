package com.wjx871.population.ai.precheck;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
@ConditionalOnProperty(prefix = "population.ai.precheck", name = {"enabled", "rabbit-enabled"}, havingValue = "true")
public class AiPrecheckOutboxPublisher {
    private final JdbcTemplate jdbc;
    private final AiPrecheckProperties properties;
    private final RabbitTemplate rabbitTemplate;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(fixedDelayString = "${AI_PRECHECK_OUTBOX_POLL_MS:1000}")
    public void publishDue() {
        List<Long> candidates = jdbc.query("SELECT outbox_id FROM ai_precheck_outbox WHERE (status='PENDING' AND available_at<=CURRENT_TIMESTAMP) OR (status='PUBLISHING' AND lease_expires_at<=CURRENT_TIMESTAMP) ORDER BY available_at, outbox_id LIMIT 25",
                (rs, rowNum) -> rs.getLong(1));
        for (Long id : candidates) {
            OutboxRow row = claim(id);
            if (row == null) continue;
            try {
                publish(row);
            } catch (Exception exception) {
                try {
                    markPublishFailed(row, "BROKER_UNAVAILABLE");
                } catch (RuntimeException ignored) {
                    // The durable PUBLISHING lease expires and another publisher can reclaim the row.
                }
                log.warn("AI outbox publish deferred outboxId={} eventId={} taskId={} instanceId={} attempt={} errorCode=BROKER_UNAVAILABLE errorClass={} traceId={}",
                        row.outboxId(), row.eventId(), row.taskId(), com.wjx871.population.s2.trace.S2TraceContext.instanceId(),
                        row.attemptCount(), exception.getClass().getSimpleName(), row.traceId());
                continue;
            }
            try {
                if (markPublished(row)) {
                    log.info("AI outbox publish confirmed outboxId={} eventId={} taskId={} instanceId={} attempt={} status=PUBLISHED traceId={}",
                            row.outboxId(), row.eventId(), row.taskId(), com.wjx871.population.s2.trace.S2TraceContext.instanceId(),
                            row.attemptCount(), row.traceId());
                } else {
                    log.warn("AI outbox confirm received after lease changed outboxId={} eventId={} taskId={} instanceId={} attempt={} traceId={}",
                            row.outboxId(), row.eventId(), row.taskId(), com.wjx871.population.s2.trace.S2TraceContext.instanceId(),
                            row.attemptCount(), row.traceId());
                }
            } catch (RuntimeException exception) {
                try {
                    markPublishFailed(row, "OUTBOX_STATUS_WRITE_FAILED");
                } catch (RuntimeException ignored) {
                    // The Broker may have accepted the event; lease recovery allows safe duplicate publication.
                }
                log.warn("AI outbox confirm persistence deferred outboxId={} eventId={} taskId={} instanceId={} attempt={} errorClass={} traceId={}",
                        row.outboxId(), row.eventId(), row.taskId(), com.wjx871.population.s2.trace.S2TraceContext.instanceId(),
                        row.attemptCount(), exception.getClass().getSimpleName(), row.traceId());
            }
        }
    }

    private OutboxRow claim(long id) {
        String lease = com.wjx871.population.s2.trace.S2TraceContext.instanceId() + ":"
                + UUID.randomUUID().toString().replace("-", "");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expires = now.plusSeconds(30);
        return transaction(() -> {
            int updated = jdbc.update("UPDATE ai_precheck_outbox SET status='PUBLISHING', lease_owner=?, lease_expires_at=?, attempt_count=attempt_count+1 WHERE outbox_id=? AND ((status='PENDING' AND available_at<=CURRENT_TIMESTAMP) OR (status='PUBLISHING' AND lease_expires_at<=CURRENT_TIMESTAMP))",
                    lease, Timestamp.valueOf(expires), id);
            if (updated != 1) return null;
            return jdbc.query("SELECT o.outbox_id,o.event_id,o.task_id,o.event_type,o.schema_version,o.destination,o.payload_json,o.lease_owner,o.attempt_count,t.trace_id FROM ai_precheck_outbox o JOIN ai_precheck_task t ON t.task_id=o.task_id WHERE o.outbox_id=? AND o.lease_owner=?",
                    (rs, rowNum) -> new OutboxRow(rs.getLong("outbox_id"), rs.getString("event_id"),
                            rs.getString("task_id"), rs.getString("event_type"), rs.getString("schema_version"),
                            rs.getString("destination"), rs.getString("payload_json"), rs.getString("lease_owner"),
                            rs.getInt("attempt_count"), rs.getString("trace_id")),
                    id, lease).stream().findFirst().orElse(null);
        });
    }

    private void publish(OutboxRow row) throws Exception {
        boolean deadLetter = "DLQ".equals(row.destination());
        String exchange = deadLetter ? properties.getDeadLetterExchange() : properties.getExchange();
        String routingKey = deadLetter ? properties.getDeadLetterRoutingKey() : properties.getRoutingKey();
        Message message = MessageBuilder.withBody(row.payloadJson().getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_JSON)
                .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
                .setMessageId(row.eventId())
                .build();
        CorrelationData correlation = new CorrelationData(row.eventId());
        rabbitTemplate.convertAndSend(exchange, routingKey, message, correlation);
        CorrelationData.Confirm confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
        if (!confirm.isAck() || correlation.getReturned() != null) {
            throw new IllegalStateException("publisher confirm rejected or message returned");
        }
    }

    private boolean markPublished(OutboxRow row) {
        return transaction(() -> {
            LocalDateTime now = LocalDateTime.now();
            int updated = jdbc.update("UPDATE ai_precheck_outbox SET status='PUBLISHED', published_at=?, lease_owner=NULL, lease_expires_at=NULL, last_error_code=NULL WHERE outbox_id=? AND status='PUBLISHING' AND lease_owner=?",
                    Timestamp.valueOf(now), row.outboxId(), row.leaseOwner());
            if (updated == 1 && "MAIN".equals(row.destination())) {
                jdbc.update("UPDATE ai_precheck_task SET last_enqueued_at=?, updated_at=? WHERE task_id=? AND status='PENDING'",
                        Timestamp.valueOf(now), Timestamp.valueOf(now), row.taskId());
            }
            if (updated == 1) audit(row, "OUTBOX_PUBLISHED", "PUBLISHING", "PUBLISHED", null);
            return updated == 1;
        });
    }

    private void markPublishFailed(OutboxRow row, String failureCode) {
        transaction(() -> {
            LocalDateTime next = LocalDateTime.now().plusSeconds(2);
            int updated = jdbc.update("UPDATE ai_precheck_outbox SET status='PENDING', available_at=?, lease_owner=NULL, lease_expires_at=NULL, last_error_code=? WHERE outbox_id=? AND status='PUBLISHING' AND lease_owner=?",
                    Timestamp.valueOf(next), failureCode, row.outboxId(), row.leaseOwner());
            if (updated == 1) audit(row, "OUTBOX_PUBLISH_FAILED", "PUBLISHING", "PENDING", failureCode);
            return null;
        });
    }

    private void audit(OutboxRow row, String eventType, String fromStatus, String toStatus, String safeErrorCode) {
        jdbc.update("INSERT INTO ai_precheck_audit(task_id,event_id,instance_id,trace_id,event_type,from_status,to_status,attempt_count,safe_error_code,created_at) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                row.taskId(), row.eventId(), com.wjx871.population.s2.trace.S2TraceContext.instanceId(),
                row.traceId(), eventType, fromStatus, toStatus, row.attemptCount(), safeErrorCode);
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

    private record OutboxRow(long outboxId, String eventId, String taskId, String eventType, String schemaVersion,
                             String destination, String payloadJson, String leaseOwner, int attemptCount,
                             String traceId) {
    }
}
