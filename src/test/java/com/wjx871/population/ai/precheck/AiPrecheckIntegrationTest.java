package com.wjx871.population.ai.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "population.ai.precheck.enabled=true",
        "population.ai.precheck.rabbit-enabled=false"
})
@AutoConfigureMockMvc
class AiPrecheckIntegrationTest {
    private static final String STORAGE_ROOT = Path.of("target", "s3-ai-stage-test-" + UUID.randomUUID())
            .toAbsolutePath().toString();

    @DynamicPropertySource
    static void storage(DynamicPropertyRegistry registry) {
        registry.add("population.ai.precheck.storage-dir", () -> STORAGE_ROOT);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AiPrecheckWorkerService worker;
    @Autowired AiPrecheckProperties properties;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean AiPrivateFileStorageService storage;
    @Autowired AiPrecheckLifecycleReaper lifecycleReaper;

    private String householdToken;
    private String adminToken;
    private String viewerToken;

    @BeforeEach
    void resetS3DataAndAuthenticate() throws Exception {
        jdbc.update("DELETE FROM ai_precheck_inbox");
        jdbc.update("DELETE FROM ai_precheck_audit");
        jdbc.update("DELETE FROM ai_precheck_outbox");
        jdbc.update("DELETE FROM ai_precheck_task_material");
        jdbc.update("DELETE FROM ai_precheck_task");
        jdbc.update("DELETE FROM ai_stage_material");
        jdbc.update("UPDATE ai_precheck_capacity SET outstanding_count=0,max_outstanding=1000 WHERE capacity_key='DOCUMENT_PRECHECK'");
        householdToken = token("household");
        adminToken = token("admin");
        viewerToken = token("viewer");
        properties.setMockFailuresBeforeSuccess(0);
        properties.setMockMode(AiPrecheckProperties.MockMode.SUCCESS);
    }

    @Test
    void uploadIdempotencyUsesUserScopedDigestAndPreservesUnavailableState() throws Exception {
        String id = upload(householdToken, "upload-key-1", "proof.pdf", "application/pdf",
                pdf("safe synthetic file"), "IDENTITY_CARD_FRONT").get("stageMaterialId").asText();
        MvcResult replay = uploadRaw(householdToken, "upload-key-1", "proof.pdf", "application/pdf",
                pdf("safe synthetic file"), "IDENTITY_CARD_FRONT");
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).path("data")
                .path("stageMaterialId").asText()).isEqualTo(id);

        MvcResult reusedKey = uploadRaw(householdToken, "upload-key-1", "proof.pdf", "application/pdf",
                pdf("different synthetic file"), "IDENTITY_CARD_FRONT");
        assertThat(reusedKey.getResponse().getStatus()).isEqualTo(409);

        String cancelId = upload(householdToken, "upload-key-cancel", "cancel.pdf", "application/pdf",
                pdf("cancel test"), "UNKNOWN").path("stageMaterialId").asText();
        mvc.perform(delete("/api/ai/materials/{id}", cancelId)
                        .header("Authorization", bearer(householdToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("CANCELLED"));
        MvcResult cancelledReplay = uploadRaw(householdToken, "upload-key-cancel", "cancel.pdf", "application/pdf",
                pdf("cancel test"), "UNKNOWN");
        assertThat(cancelledReplay.getResponse().getStatus()).isEqualTo(200);
        assertThat(objectMapper.readTree(cancelledReplay.getResponse().getContentAsString())
                .path("data").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, cancelId)).isEqualTo("CANCELLED");
    }

    @Test
    void uploadRejectsMismatchedFileAndProtectsOwnerReads() throws Exception {
        MockMultipartFile mismatched = new MockMultipartFile("file", "fake.png", "image/png",
                pdf("this is not a png"));
        mvc.perform(multipart("/api/ai/materials").file(mismatched)
                        .header("Authorization", bearer(householdToken)).header("Idempotency-Key", "bad-file-1")
                        .param("businessType", "MIGRATION_IN").param("documentRoleHint", "UNKNOWN"))
                .andExpect(status().isBadRequest());

        MockMultipartFile invalidSignature = new MockMultipartFile("file", "fake.pdf", "application/pdf",
                "not a pdf".getBytes(StandardCharsets.UTF_8));
        mvc.perform(multipart("/api/ai/materials").file(invalidSignature)
                        .header("Authorization", bearer(householdToken)).header("Idempotency-Key", "bad-file-2")
                        .param("businessType", "MIGRATION_IN").param("documentRoleHint", "UNKNOWN"))
                .andExpect(status().isBadRequest());

        String id = upload(householdToken, "owner-check", "private.pdf", "application/pdf",
                pdf("private synthetic file"), "UNKNOWN").path("stageMaterialId").asText();
        MvcResult foreign = mvc.perform(get("/api/ai/materials/{id}", id)
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound()).andReturn();
        JsonNode foreignBody = objectMapper.readTree(foreign.getResponse().getContentAsString());
        JsonNode missingBody = objectMapper.readTree(mvc.perform(get("/api/ai/materials/{id}", "stg_00000000000000000000000000000000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString());
        assertThat(foreignBody.path("code").asInt()).isEqualTo(missingBody.path("code").asInt());
        assertThat(foreignBody.path("message").asText()).isEqualTo(missingBody.path("message").asText());
        assertThat(foreignBody.path("data").isNull()).isTrue();
        assertThat(missingBody.path("data").isNull()).isTrue();
        assertThat(foreign.getResponse().getContentAsString()).doesNotContain(STORAGE_ROOT);
    }

    @Test
    void anonymousAndUnprivilegedUploadAreRejected() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "anonymous.pdf", "application/pdf", pdf("test"));
        mvc.perform(multipart("/api/ai/materials").file(file).header("Idempotency-Key", "anonymous-1")
                        .param("businessType", "MIGRATION_IN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(multipart("/api/ai/materials").file(file).header("Authorization", bearer(viewerToken))
                        .header("Idempotency-Key", "viewer-1").param("businessType", "MIGRATION_IN"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material", Integer.class)).isZero();
    }

    @Test
    void taskAcceptanceCommitsTaskAssociationAndOutboxTogetherThenMockCompletes() throws Exception {
        String stageId = upload(householdToken, "task-stage", "household-book.pdf", "application/pdf",
                pdf("synthetic household material"), "HOUSEHOLD_REGISTER_PAGE").path("stageMaterialId").asText();
        AiTaskCreateRequest request = taskRequest(List.of(stageId), "task-idempotency-1", "OUTSIDE_CITY");
        MvcResult accepted = createTask(householdToken, request).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.status").value("PENDING")).andReturn();
        JsonNode acceptedJson = objectMapper.readTree(accepted.getResponse().getContentAsString()).path("data");
        String taskId = acceptedJson.path("taskId").asText();
        String eventBody = jdbc.queryForObject("SELECT payload_json FROM ai_precheck_outbox WHERE task_id=?",
                String.class, taskId);
        assertThat(eventBody).doesNotContain(stageId, "household-book.pdf", "IDENTITY");
        var fields = new HashSet<String>();
        objectMapper.readTree(eventBody).fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("eventId", "taskId", "taskType", "schemaVersion", "traceId",
                "occurredAt", "producer");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("IN_USE");
        mvc.perform(delete("/api/ai/materials/{id}", stageId)
                        .header("Authorization", bearer(householdToken)))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_task_material WHERE task_id=?",
                Integer.class, taskId)).isEqualTo(1);

        createTask(householdToken, request).andExpect(status().isAccepted());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_task WHERE owner_user_id=6",
                Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox WHERE task_id=?",
                Integer.class, taskId)).isEqualTo(1);

        AiTaskCreateRequest changed = taskRequest(List.of(stageId), "task-idempotency-1", "IN_CITY_CROSS_DISTRICT");
        createTask(householdToken, changed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("IDEMPOTENCY_KEY_REUSED"));

        AiPrecheckTaskService.EventEnvelope event = objectMapper.readValue(eventBody,
                AiPrecheckTaskService.EventEnvelope.class);
        jdbc.update("UPDATE ai_precheck_outbox SET status='PUBLISHED' WHERE event_id=?", event.eventId());
        worker.process(event);
        worker.process(event);
        mvc.perform(get("/api/ai/tasks/{id}", taskId).header("Authorization", bearer(householdToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.result.mode").value("MOCK"))
                .andExpect(jsonPath("$.data.result.analysisDisposition").value("INSUFFICIENT_INPUT"));
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT outstanding_count FROM ai_precheck_capacity WHERE capacity_key='DOCUMENT_PRECHECK'",
                Integer.class)).isZero();

        mvc.perform(get("/api/ai/tasks/{id}", taskId).header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/ai/tasks/{id}", "task_00000000000000000000000000000000")
                        .header("Authorization", bearer(adminToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void capacityRejectionRollsBackTaskAndLeavesMaterialReady() throws Exception {
        String stageId = upload(householdToken, "capacity-stage", "capacity.pdf", "application/pdf",
                pdf("capacity"), "UNKNOWN").path("stageMaterialId").asText();
        jdbc.update("UPDATE ai_precheck_capacity SET outstanding_count=max_outstanding WHERE capacity_key='DOCUMENT_PRECHECK'");
        AiTaskCreateRequest request = taskRequest(List.of(stageId), "capacity-full-key", "OUTSIDE_CITY");
        createTask(householdToken, request).andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("AI_PRECHECK_CAPACITY_REACHED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_task WHERE idempotency_key='capacity-full-key'",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("READY");
    }

    @Test
    void brokerUnavailableLeavesAcceptedTaskAndOutboxRetryable() throws Exception {
        String stageId = upload(householdToken, "offline-broker-stage", "offline.pdf", "application/pdf",
                pdf("offline broker"), "UNKNOWN").path("stageMaterialId").asText();
        AiTaskCreateRequest request = taskRequest(List.of(stageId), "offline-broker-task", "OUTSIDE_CITY");
        JsonNode accepted = objectMapper.readTree(createTask(householdToken, request)
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString()).path("data");
        String taskId = accepted.path("taskId").asText();

        RabbitTemplate unavailableBroker = mock(RabbitTemplate.class);
        doThrow(new IllegalStateException("simulated broker connection failure"))
                .when(unavailableBroker).convertAndSend(anyString(), anyString(), any(Message.class), any(CorrelationData.class));
        new AiPrecheckOutboxPublisher(jdbc, properties, unavailableBroker, transactionManager).publishDue();

        assertThat(jdbc.queryForObject("SELECT status FROM ai_precheck_task WHERE task_id=?", String.class, taskId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_precheck_outbox WHERE task_id=?", String.class, taskId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT last_error_code FROM ai_precheck_outbox WHERE task_id=?",
                String.class, taskId)).isEqualTo("BROKER_UNAVAILABLE");
        assertThat(accepted.path("taskId").asText()).isEqualTo(taskId);
    }

    @Test
    void retryableAnalysisFailurePersistsRetryIntentBeforeReturning() throws Exception {
        String stageId = upload(householdToken, "retry-stage", "retry.pdf", "application/pdf",
                pdf("retry"), "UNKNOWN").path("stageMaterialId").asText();
        AiTaskCreateRequest request = taskRequest(List.of(stageId), "retry-key", "OUTSIDE_CITY");
        String taskId = objectMapper.readTree(createTask(householdToken, request).andReturn().getResponse()
                .getContentAsString()).path("data").path("taskId").asText();
        String firstPayload = jdbc.queryForObject("SELECT payload_json FROM ai_precheck_outbox WHERE task_id=?",
                String.class, taskId);
        AiPrecheckTaskService.EventEnvelope first = objectMapper.readValue(firstPayload,
                AiPrecheckTaskService.EventEnvelope.class);
        jdbc.update("UPDATE ai_precheck_outbox SET status='PUBLISHED' WHERE event_id=?", first.eventId());
        properties.setMockFailuresBeforeSuccess(1);
        worker.process(first);
        properties.setMockFailuresBeforeSuccess(0);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_precheck_task WHERE task_id=?", String.class, taskId))
                .isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT attempt_count FROM ai_precheck_task WHERE task_id=?", Integer.class,
                taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox WHERE task_id=? AND status='PENDING'",
                Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_inbox WHERE event_id=? AND outcome='RETRY_SCHEDULED'",
                Integer.class, first.eventId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT next_attempt_at FROM ai_precheck_task WHERE task_id=?", java.sql.Timestamp.class,
                taskId)).isNotNull();
    }

    @Test
    void permanentAnalysisFailurePersistsFailedAndDlqIntentTogether() throws Exception {
        String stageId = upload(householdToken, "permanent-stage", "permanent.pdf", "application/pdf",
                pdf("permanent"), "UNKNOWN").path("stageMaterialId").asText();
        AiTaskCreateRequest request = taskRequest(List.of(stageId), "permanent-key", "OUTSIDE_CITY");
        String taskId = objectMapper.readTree(createTask(householdToken, request).andReturn().getResponse()
                .getContentAsString()).path("data").path("taskId").asText();
        String payload = jdbc.queryForObject("SELECT payload_json FROM ai_precheck_outbox WHERE task_id=?",
                String.class, taskId);
        AiPrecheckTaskService.EventEnvelope event = objectMapper.readValue(payload,
                AiPrecheckTaskService.EventEnvelope.class);
        jdbc.update("UPDATE ai_precheck_outbox SET status='PUBLISHED' WHERE event_id=?", event.eventId());
        properties.setMockMode(AiPrecheckProperties.MockMode.PERMANENT_FAILURE);
        worker.process(event);
        properties.setMockMode(AiPrecheckProperties.MockMode.SUCCESS);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_precheck_task WHERE task_id=?", String.class, taskId))
                .isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("SELECT failure_code FROM ai_precheck_task WHERE task_id=?", String.class,
                taskId)).isEqualTo("AI_MOCK_PERMANENT_FAILURE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox WHERE task_id=? AND destination='DLQ' AND status='PENDING'",
                Integer.class, taskId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("READY");
        assertThat(jdbc.queryForObject("SELECT outstanding_count FROM ai_precheck_capacity WHERE capacity_key='DOCUMENT_PRECHECK'",
                Integer.class)).isZero();
    }

    @Test
    void outboxInsertFailureRollsBackTaskCapacityAndMaterialReservation() throws Exception {
        String stageId = upload(householdToken, "rollback-stage", "rollback.pdf", "application/pdf",
                pdf("rollback"), "UNKNOWN").path("stageMaterialId").asText();
        jdbc.execute("ALTER TABLE ai_precheck_outbox ADD CONSTRAINT chk_s3_test_outbox_fail CHECK (destination='BLOCKED')");
        try {
            AiTaskCreateRequest request = taskRequest(List.of(stageId), "outbox-failure-key", "OUTSIDE_CITY");
            createTask(householdToken, request).andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_task WHERE idempotency_key='outbox-failure-key'",
                    Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("READY");
            assertThat(jdbc.queryForObject("SELECT outstanding_count FROM ai_precheck_capacity WHERE capacity_key='DOCUMENT_PRECHECK'",
                    Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE ai_precheck_outbox DROP CONSTRAINT chk_s3_test_outbox_fail");
        }
    }

    @Test
    void lifecycleExpiresReadyFilesAndNeverDeletesBoundFiles() throws Exception {
        String boundId = upload(householdToken, "bound-stage", "bound.pdf", "application/pdf",
                pdf("bound"), "UNKNOWN").path("stageMaterialId").asText();
        String boundKey = jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                String.class, boundId);
        jdbc.update("UPDATE ai_stage_material SET status='BOUND' WHERE stage_material_id=?", boundId);
        lifecycleReaper.reapMaterials();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?", String.class,
                boundId)).isEqualTo("BOUND");
        assertThat(storage.objectExists(boundKey)).isTrue();
        mvc.perform(delete("/api/ai/materials/{id}", boundId).header("Authorization", bearer(householdToken)))
                .andExpect(status().isConflict());

        String expiredId = upload(householdToken, "expired-stage", "expired.pdf", "application/pdf",
                pdf("expired"), "UNKNOWN").path("stageMaterialId").asText();
        String expiredKey = jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                String.class, expiredId);
        jdbc.update("UPDATE ai_stage_material SET expires_at=? WHERE stage_material_id=?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1)), expiredId);
        lifecycleReaper.reapMaterials();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?", String.class,
                expiredId)).isEqualTo("EXPIRED");
        assertThat(storage.objectExists(expiredKey)).isFalse();
    }

    @Test
    void concurrentSameTaskIdempotencyKeyCreatesOneTaskAndOneOutbox() throws Exception {
        String stageId = upload(householdToken, "concurrent-stage", "concurrent.pdf", "application/pdf",
                pdf("concurrent"), "UNKNOWN").path("stageMaterialId").asText();
        AiTaskCreateRequest request = taskRequest(List.of(stageId), "same-concurrent-key", "OUTSIDE_CITY");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> call = () -> createTask(householdToken, request).andReturn();
            Future<MvcResult> first = executor.submit(call);
            Future<MvcResult> second = executor.submit(call);
            JsonNode a = objectMapper.readTree(first.get().getResponse().getContentAsString()).path("data");
            JsonNode b = objectMapper.readTree(second.get().getResponse().getContentAsString()).path("data");
            assertThat(first.get().getResponse().getStatus()).isEqualTo(202);
            assertThat(second.get().getResponse().getStatus()).isEqualTo(202);
            assertThat(a.path("taskId").asText()).isEqualTo(b.path("taskId").asText());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_task WHERE idempotency_key='same-concurrent-key'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox WHERE task_id=?",
                    Integer.class, a.path("taskId").asText())).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT outstanding_count FROM ai_precheck_capacity WHERE capacity_key='DOCUMENT_PRECHECK'",
                    Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentCapacityAdmissionNeverExceedsDatabaseLimit() throws Exception {
        String stageA = upload(householdToken, "capacity-race-a", "race-a.pdf", "application/pdf",
                pdf("race a"), "UNKNOWN").path("stageMaterialId").asText();
        String stageB = upload(householdToken, "capacity-race-b", "race-b.pdf", "application/pdf",
                pdf("race b"), "UNKNOWN").path("stageMaterialId").asText();
        jdbc.update("UPDATE ai_precheck_capacity SET outstanding_count=0,max_outstanding=1 WHERE capacity_key='DOCUMENT_PRECHECK'");
        AiTaskCreateRequest requestA = taskRequest(List.of(stageA), "capacity-race-task-a", "OUTSIDE_CITY");
        AiTaskCreateRequest requestB = taskRequest(List.of(stageB), "capacity-race-task-b", "OUTSIDE_CITY");
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<MvcResult> first = executor.submit(() -> createTask(householdToken, requestA).andReturn());
            Future<MvcResult> second = executor.submit(() -> createTask(householdToken, requestB).andReturn());
            List<Integer> statuses = List.of(first.get().getResponse().getStatus(), second.get().getResponse().getStatus());
            assertThat(statuses).containsExactlyInAnyOrder(202, 429);
            assertThat(jdbc.queryForObject("SELECT outstanding_count FROM ai_precheck_capacity WHERE capacity_key='DOCUMENT_PRECHECK'",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_task WHERE status IN ('PENDING','RUNNING')",
                    Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE status='IN_USE'",
                    Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentUploadKeyCreatesOneStageMaterial() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<MvcResult> call = () -> uploadRaw(householdToken, "parallel-upload-key", "same.pdf",
                    "application/pdf", pdf("parallel upload"), "ADDRESS_PROOF");
            Future<MvcResult> first = executor.submit(call);
            Future<MvcResult> second = executor.submit(call);
            JsonNode a = objectMapper.readTree(first.get().getResponse().getContentAsString()).path("data");
            JsonNode b = objectMapper.readTree(second.get().getResponse().getContentAsString()).path("data");
            assertThat(first.get().getResponse().getStatus()).isIn(200, 201);
            assertThat(second.get().getResponse().getStatus()).isIn(200, 201);
            assertThat(a.path("stageMaterialId").asText()).isEqualTo(b.path("stageMaterialId").asText());
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key='parallel-upload-key'",
                    Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedPhysicalUploadLeavesNoReadyReference() throws Exception {
        doThrow(new IOException("test-only failure")).when(storage).promote(any());
        MockMultipartFile file = new MockMultipartFile("file", "failed.pdf", "application/pdf", pdf("upload fail"));
        mvc.perform(multipart("/api/ai/materials").file(file).header("Authorization", bearer(householdToken))
                        .header("Idempotency-Key", "upload-failure-key").param("businessType", "MIGRATION_IN")
                        .param("documentRoleHint", "UNKNOWN"))
                .andExpect(status().isInternalServerError());
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE idempotency_key='upload-failure-key'",
                String.class)).isEqualTo("UPLOAD_FAILED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key='upload-failure-key' AND status='READY'",
                Integer.class)).isZero();
    }

    @Test
    void taskIdempotencyRejectsOtherOwnerAndUsesUnified404() throws Exception {
        String stageId = upload(householdToken, "private-stage", "private.pdf", "application/pdf",
                pdf("private"), "UNKNOWN").path("stageMaterialId").asText();
        AiTaskCreateRequest request = taskRequest(List.of(stageId), "private-task-key", "OUTSIDE_CITY");
        String taskId = objectMapper.readTree(createTask(householdToken, request).andReturn().getResponse()
                .getContentAsString()).path("data").path("taskId").asText();
        MvcResult foreignTask = mvc.perform(get("/api/ai/tasks/{id}", taskId)
                        .header("Authorization", bearer(adminToken))).andExpect(status().isNotFound()).andReturn();
        MvcResult missingTask = mvc.perform(get("/api/ai/tasks/{id}", "task_00000000000000000000000000000000")
                        .header("Authorization", bearer(adminToken))).andExpect(status().isNotFound()).andReturn();
        JsonNode foreignBody = objectMapper.readTree(foreignTask.getResponse().getContentAsString());
        JsonNode missingBody = objectMapper.readTree(missingTask.getResponse().getContentAsString());
        assertThat(foreignBody.path("code").asInt()).isEqualTo(missingBody.path("code").asInt());
        assertThat(foreignBody.path("message").asText()).isEqualTo(missingBody.path("message").asText());
        assertThat(foreignBody.path("data").isNull()).isTrue();
        assertThat(missingBody.path("data").isNull()).isTrue();

        AiTaskCreateRequest foreignMaterial = taskRequest(List.of(stageId), "foreign-material-key", "OUTSIDE_CITY");
        MvcResult denied = createTask(adminToken, foreignMaterial).andExpect(status().isNotFound()).andReturn();
        assertThat(denied.getResponse().getContentAsString()).doesNotContain(stageId);
    }

    private JsonNode upload(String token, String key, String filename, String contentType, byte[] data,
                           String roleHint) throws Exception {
        return objectMapper.readTree(uploadRaw(token, key, filename, contentType, data, roleHint)
                .getResponse().getContentAsString()).path("data");
    }

    private MvcResult uploadRaw(String token, String key, String filename, String contentType, byte[] data,
                                String roleHint) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, data);
        return mvc.perform(multipart("/api/ai/materials").file(file)
                        .header("Authorization", bearer(token)).header("Idempotency-Key", key)
                        .param("businessType", "MIGRATION_IN").param("documentRoleHint", roleHint))
                .andReturn();
    }

    private org.springframework.test.web.servlet.ResultActions createTask(String token,
                                                                         AiTaskCreateRequest request) throws Exception {
        return mvc.perform(post("/api/ai/tasks").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(request)));
    }

    private AiTaskCreateRequest taskRequest(List<String> stageIds, String key, String migrationType) {
        return new AiTaskCreateRequest("1.0", "DOCUMENT_PRECHECK", "MIGRATION_IN", migrationType, stageIds, key);
    }

    private String token(String username) throws Exception {
        String json = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private byte[] pdf(String text) {
        return ("%PDF-1.7\n% synthetic test input\n" + text + "\n%%EOF").getBytes(StandardCharsets.UTF_8);
    }
}
