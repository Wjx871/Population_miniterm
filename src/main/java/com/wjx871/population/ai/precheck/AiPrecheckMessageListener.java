package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.rabbitmq.client.Channel;
import com.wjx871.population.s2.trace.S2TraceContext;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = {"enabled", "rabbit-enabled"}, havingValue = "true")
public class AiPrecheckMessageListener {
    private static final Set<String> ALLOWED_FIELDS = Set.of(
            "eventId", "taskId", "taskType", "schemaVersion", "traceId", "occurredAt", "producer");
    private final ObjectMapper objectMapper;
    private final AiPrecheckWorkerService worker;

    @RabbitListener(queues = "${population.ai.precheck.queue}", containerFactory = "aiPrecheckListenerContainerFactory")
    public void receive(Message message, Channel channel) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        String messageId = message.getMessageProperties().getMessageId();
        String traceId = null;
        try {
            JsonNode root;
            try {
                root = objectMapper.readTree(message.getBody());
            } catch (JsonProcessingException exception) {
                worker.recordPoison(messageId, null, "MESSAGE_JSON_INVALID");
                channel.basicReject(deliveryTag, false);
                return;
            }
            AiPrecheckTaskService.EventEnvelope event = parse(root);
            traceId = event.traceId();
            try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
                worker.process(event);
            }
            channel.basicAck(deliveryTag, false);
        } catch (AiMessagePoisonException poison) {
            if (poison.event() != null) {
                try {
                    worker.failMalformedKnownEvent(poison.event(), poison.safeCode());
                    channel.basicAck(deliveryTag, false);
                    return;
                } catch (RuntimeException ignored) {
                    // Unmatched or unavailable task state is retained by the Broker DLQ below.
                }
            }
            try {
                worker.recordPoison(messageId, traceId, poison.safeCode());
            } catch (RuntimeException ignored) {
                // The Broker DLQ remains the durable fallback when the database is unavailable.
            }
            log.warn("AI precheck poison message routed to DLQ messageId={} errorCode={}", messageId,
                    poison.safeCode());
            channel.basicReject(deliveryTag, false);
        } catch (Exception exception) {
            log.warn("AI precheck message processing failed messageId={} errorClass={}", messageId,
                    exception.getClass().getSimpleName());
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private AiPrecheckTaskService.EventEnvelope parse(JsonNode root) {
        if (root == null || !root.isObject()) throw new AiMessagePoisonException("MESSAGE_SCHEMA_INVALID");
        Set<String> fields = new java.util.HashSet<>();
        root.fieldNames().forEachRemaining(fields::add);
        try {
            String eventId = textOrEmpty(root, "eventId");
            String taskId = textOrEmpty(root, "taskId");
            String taskType = textOrEmpty(root, "taskType");
            String schema = textOrEmpty(root, "schemaVersion");
            String traceId = textOrEmpty(root, "traceId");
            String occurredAt = textOrEmpty(root, "occurredAt");
            String producer = textOrEmpty(root, "producer");
            AiPrecheckTaskService.EventEnvelope event = new AiPrecheckTaskService.EventEnvelope(eventId, taskId,
                    taskType, schema, traceId, occurredAt, producer);
            if (!eventId.matches("evt_[A-Fa-f0-9]{32}") || !taskId.matches("task_[A-Fa-f0-9]{32}")
                    || !traceId.matches("[A-Za-z0-9._-]{1,64}")) {
                throw new AiMessagePoisonException("MESSAGE_SCHEMA_INVALID");
            }
            if (!fields.equals(ALLOWED_FIELDS) || !taskType.matches("[A-Z_]{1,40}")
                    || !schema.matches("[0-9]+\\.[0-9]+") || !"population-java".equals(producer)) {
                throw new AiMessagePoisonException("MESSAGE_SCHEMA_INVALID", event);
            }
            try {
                java.time.OffsetDateTime.parse(occurredAt);
            } catch (RuntimeException exception) {
                try {
                    LocalDateTime.parse(occurredAt);
                } catch (RuntimeException ignored) {
                    throw new AiMessagePoisonException("MESSAGE_SCHEMA_INVALID", event);
                }
            }
            return event;
        } catch (AiMessagePoisonException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new AiMessagePoisonException("MESSAGE_SCHEMA_INVALID");
        }
    }

    private String textOrEmpty(JsonNode root, String field) {
        JsonNode value = root.get(field);
        return value != null && value.isTextual() ? value.asText() : "";
    }
}
