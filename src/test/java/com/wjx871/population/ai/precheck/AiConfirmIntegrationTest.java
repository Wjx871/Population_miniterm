package com.wjx871.population.ai.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.doThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.ai.precheck.AiConfirmRequest.MaterialBinding;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.material.AiBoundMaterialCleanupService;
import com.wjx871.population.material.ApplicationMaterial;
import com.wjx871.population.material.ApplicationMaterialMapper;
import com.wjx871.population.material.BoundStageMaterialStorage;
import com.wjx871.population.material.MaterialService;
import com.wjx871.population.migration.MigrationType;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.Mockito;
import org.mockito.ArgumentMatchers;

@SpringBootTest(properties = {
        "population.ai.precheck.enabled=true",
        "population.ai.precheck.rabbit-enabled=false",
        "AI_PRECHECK_CLEANUP_POLL_MS=3600000"
})
@AutoConfigureMockMvc
class AiConfirmIntegrationTest {
    private static final Path STORAGE_ROOT = Path.of("target", "s4b-ai-stage-test-" + UUID.randomUUID())
            .toAbsolutePath().normalize();

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("population.ai.precheck.storage-dir", STORAGE_ROOT::toString);
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;
    @Autowired AiPrecheckTaskService tasks;
    @Autowired AiPrecheckWorkerService worker;
    @Autowired AiPrecheckLifecycleReaper lifecycleReaper;
    @Autowired BoundStageMaterialStorage stageStorage;
    @Autowired ApplicationMaterialMapper materials;
    @Autowired MaterialService materialService;
    @Autowired AuthService authService;
    @Autowired PlatformTransactionManager transactionManager;
    @SpyBean ApplicationMaterialMapper materialMapperSpy;
    @SpyBean BoundStageMaterialStorage stageStorageSpy;

    private String householdToken;
    private String adminToken;
    private String viewerToken;

    @BeforeEach
    void prepare() throws Exception {
        jdbc.update("DELETE FROM ai_stage_material_binding");
        jdbc.update("DELETE FROM ai_confirmation_receipt");
        jdbc.update("DELETE FROM ai_resolution_candidate");
        jdbc.update("DELETE FROM ai_resolution_session");
        jdbc.update("DELETE FROM ai_precheck_inbox");
        jdbc.update("DELETE FROM ai_precheck_audit");
        jdbc.update("DELETE FROM ai_precheck_outbox");
        jdbc.update("DELETE FROM ai_precheck_task_material");
        jdbc.update("DELETE FROM ai_precheck_task");
        jdbc.update("DELETE FROM ai_stage_material");
        jdbc.update("UPDATE ai_precheck_capacity SET outstanding_count=0,max_outstanding=1000 "
                + "WHERE capacity_key='DOCUMENT_PRECHECK'");
        ensureResolutionFixture();
        householdToken = login("household");
        adminToken = login("admin");
        viewerToken = login("viewer");
        reset(materialMapperSpy);
        reset(stageStorageSpy);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void confirmationCreatesOneDraftAndReplaysAfterResolutionSessionIsConsumed() throws Exception {
        Scenario scenario = scenario("confirm-replay", List.of("UNKNOWN"));
        Map<String, Object> body = confirmBody(scenario, "confirm-replay-key", List.of(), List.of(), null);

        MvcResult first = confirm(scenario.taskId(), householdToken, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.creationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.currentStatus").value("DRAFT"))
                .andReturn();
        JsonNode created = objectMapper.readTree(first.getResponse().getContentAsString()).path("data");
        long applicationId = created.path("applicationId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, applicationId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_resolution_session WHERE task_id=?",
                String.class, scenario.taskId())).isEqualTo("CONSUMED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isEqualTo(1);

        jdbc.update("UPDATE ai_resolution_session SET expires_at=? WHERE task_id=?",
                Timestamp.valueOf(LocalDateTime.now().minusMinutes(10)), scenario.taskId());
        MvcResult replay = confirm(scenario.taskId(), householdToken, body)
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.replayed").value(true)).andReturn();
        JsonNode replayed = objectMapper.readTree(replay.getResponse().getContentAsString()).path("data");
        assertThat(replayed.path("applicationId").asLong()).isEqualTo(applicationId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isEqualTo(1);

        Map<String, Object> changed = confirmBody(scenario, "confirm-replay-key", List.of(), List.of(), null);
        replaceField(changed, "migration.reason", "另一个请求");
        confirm(scenario.taskId(), householdToken, changed).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("IDEMPOTENCY_KEY_REUSED"));
        Map<String, Object> differentKey = confirmBody(scenario, "confirm-replay-new-key", List.of(), List.of(), null);
        confirm(scenario.taskId(), householdToken, differentKey).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("TASK_ALREADY_CONSUMED"));
    }

    @Test
    void confirmationRejectsChangedResolutionInputAndChangedStoredOcrBeforeCreatingAnything() throws Exception {
        Scenario scenario = scenario("confirm-input-change", List.of("UNKNOWN"));
        Map<String, Object> changedCorrection = confirmBody(scenario, "input-change-key", List.of(), List.of(), null);
        replaceField(changedCorrection, "person.name", "另一个人");
        confirm(scenario.taskId(), householdToken, changedCorrection).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("RESOLUTION_INPUT_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isZero();

        String rawStage = upload("UNKNOWN", "ocr-digest-stage", 9);
        String rawTask = createSuccessfulTask("ocr-digest", List.of(rawStage));
        String originalResult = ocrResult("解析测试甲", "110105198001010016", "S4A-H-ALPHA");
        jdbc.update("UPDATE ai_precheck_task SET result_json=? WHERE task_id=?", originalResult, rawTask);
        JsonNode preview = previewData(rawTask, List.of());
        Scenario rawScenario = new Scenario(rawTask, taskVersion(rawTask), preview, List.of(rawStage));
        Map<String, Object> rawBody = confirmBody(rawScenario, "raw-ocr-change", List.of(), List.of(), null);
        jdbc.update("UPDATE ai_precheck_task SET result_json=? WHERE task_id=?",
                ocrResult("解析测试甲", "110105198001010016", "S4A-H-OTHER"), rawTask);
        confirm(rawTask, householdToken, rawBody).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("RESOLUTION_INPUT_CHANGED"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, rawTask)).isZero();
    }

    @Test
    void concurrentSameKeyConfirmationCreatesOneDraftAndReturnsOneReplay() throws Exception {
        Scenario scenario = scenario("confirm-concurrent", List.of("UNKNOWN"));
        Map<String, Object> body = confirmBody(scenario, "concurrent-confirm-key", List.of(), List.of(), null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return confirm(scenario.taskId(), householdToken, body).andReturn().getResponse().getStatus();
            });
            var second = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return confirm(scenario.taskId(), householdToken, body).andReturn().getResponse().getStatus();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(new HashSet<>(List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))))
                    .containsExactlyInAnyOrder(201, 200);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE person_id=9801 "
                + "AND application_id=(SELECT application_id FROM ai_confirmation_receipt WHERE task_id=?)",
                Integer.class, scenario.taskId())).isEqualTo(1);
    }

    @Test
    void materialInsertFailureRollsBackDraftAndBindingThenSameRequestCanRetry() throws Exception {
        Scenario scenario = scenario("confirm-material-rollback", List.of("IDENTITY_CARD_FRONT", "ADDRESS_PROOF"));
        List<Map<String, Object>> bindings = List.of(
                material(scenario.stageIds().get(0), "IDENTITY_PROOF"),
                material(scenario.stageIds().get(1), "ADDRESS_PROOF"));
        Map<String, Object> body = confirmBody(scenario, "material-rollback-key", bindings, List.of(), null);
        int priorMigrationCount = jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE person_id=9801",
                Integer.class);
        doThrow(new IllegalStateException("test-only material insert failure"))
                .when(materialMapperSpy).insert(any(ApplicationMaterial.class));
        confirm(scenario.taskId(), householdToken, body).andExpect(status().isInternalServerError());
        reset(materialMapperSpy);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE person_id=9801",
                Integer.class)).isEqualTo(priorMigrationCount);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_resolution_session WHERE task_id=?",
                String.class, scenario.taskId())).isEqualTo("ACTIVE");
        for (String stageId : scenario.stageIds()) {
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("READY");
            assertThat(stageStorage.verify(stageId, objectKey(stageId), rowSize(stageId), rowSha(stageId))).isTrue();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material_binding b "
                + "JOIN ai_stage_material sm ON sm.stage_material_id=b.stage_material_id "
                + "JOIN ai_precheck_task_material tm ON tm.stage_material_id=sm.stage_material_id "
                + "WHERE tm.task_id=?", Integer.class, scenario.taskId())).isZero();
        confirm(scenario.taskId(), householdToken, body).andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isEqualTo(1);
    }

    @Test
    void needsCorrectionSessionCannotBeConfirmedEvenWithBothCandidateTokens() throws Exception {
        Scenario scenario = scenario("confirm-needs-correction", List.of("UNKNOWN"));
        jdbc.update("UPDATE ai_resolution_session SET confirmable=FALSE,resolution_status='NEEDS_CORRECTION' "
                        + "WHERE resolution_token_sha256=?",
                sha256(scenario.preview().path("resolutionToken").asText()));
        confirm(scenario.taskId(), householdToken,
                confirmBody(scenario, "needs-correction-key", List.of(), List.of(), null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("RESOLUTION_NOT_CONFIRMABLE"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isZero();
    }

    @Test
    void strictJsonAndAuthorizationRejectUnknownDuplicateAndUnauthorizedRequests() throws Exception {
        Scenario scenario = scenario("confirm-strict", List.of("UNKNOWN"));
        Map<String, Object> body = confirmBody(scenario, "strict-key", List.of(), List.of(), null);
        String valid = objectMapper.writeValueAsString(body);
        String unknown = valid.substring(0, valid.length() - 1) + ",\"personId\":9801}";
        mvc.perform(post("/api/ai/tasks/{id}/confirm", scenario.taskId())
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON).content(unknown))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));
        String duplicate = valid.replace("\"taskVersion\":", "\"taskVersion\":1,\"taskVersion\":");
        mvc.perform(post("/api/ai/tasks/{id}/confirm", scenario.taskId())
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON).content(duplicate))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));
        Map<String, Object> noPerson = new LinkedHashMap<>(body);
        noPerson.put("selectedCandidates", Map.of("person", "", "targetHousehold", token(scenario.preview(), "household")));
        confirm(scenario.taskId(), householdToken, noPerson).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));
        confirm(scenario.taskId(), viewerToken, body).andExpect(status().isForbidden());
        confirm(scenario.taskId(), adminToken, body).andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isZero();
    }

    @Test
    void malformedNestedInputsReturnClientErrorsBeforeCreatingDraft() throws Exception {
        Scenario scenario = scenario("confirm-invalid-nested", List.of("UNKNOWN"));
        int applicationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);

        Map<String, Object> nullBinding = confirmBody(scenario, "invalid-null-binding", List.of(), List.of(), null);
        nullBinding.put("materialBindings", java.util.Collections.singletonList(null));
        confirm(scenario.taskId(), householdToken, nullBinding).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));

        Map<String, Object> missingStageId = confirmBody(scenario, "invalid-missing-stage", List.of(), List.of(), null);
        missingStageId.put("materialBindings", List.of(Map.of("materialType", "IDENTITY_PROOF")));
        confirm(scenario.taskId(), householdToken, missingStageId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));

        Map<String, Object> duplicateStageId = confirmBody(scenario, "invalid-duplicate-stage",
                List.of(material(scenario.stageIds().get(0), "IDENTITY_PROOF"),
                        material(scenario.stageIds().get(0), "IDENTITY_PROOF")), List.of(), null);
        confirm(scenario.taskId(), householdToken, duplicateStageId).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("DUPLICATE_STAGE_MATERIAL_ID"));

        Map<String, Object> wrongSelectedType = confirmBody(scenario, "invalid-selected-type", List.of(), List.of(), null);
        wrongSelectedType.put("selectedCandidates", 42);
        confirm(scenario.taskId(), householdToken, wrongSelectedType).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));

        Map<String, Object> invalidSelectedToken = confirmBody(scenario, "invalid-selected-token", List.of(), List.of(), null);
        invalidSelectedToken.put("selectedCandidates", Map.of("person", "not-a-token",
                "targetHousehold", token(scenario.preview(), "household")));
        confirm(scenario.taskId(), householdToken, invalidSelectedToken).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));

        Map<String, Object> wrongBindingsType = confirmBody(scenario, "invalid-bindings-type", List.of(), List.of(), null);
        wrongBindingsType.put("materialBindings", "not-an-array");
        confirm(scenario.taskId(), householdToken, wrongBindingsType).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("INVALID_CONFIRM_REQUEST"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class))
                .isEqualTo(applicationsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isZero();
    }

    @Test
    void stageBindingRejectsForeignOwnerOtherTaskExpiredAndUnavailableStates() throws Exception {
        Scenario scenario = scenario("confirm-stage-ownership", List.of("UNKNOWN", "UNKNOWN", "UNKNOWN"));
        int applicationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);

        String foreignOwnerStage = scenario.stageIds().get(0);
        Long otherOwnerId = authService.loadUser("admin").userId();
        jdbc.update("UPDATE ai_stage_material SET owner_user_id=? WHERE stage_material_id=?", otherOwnerId, foreignOwnerStage);
        confirmWithStage(scenario, foreignOwnerStage, "foreign-owner-stage")
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE ai_stage_material SET owner_user_id=? WHERE stage_material_id=?",
                authService.loadUser("household").userId(), foreignOwnerStage);

        Scenario otherTask = scenario("confirm-stage-other-task", List.of("UNKNOWN"));
        confirmWithStage(scenario, otherTask.stageIds().get(0), "foreign-task-stage")
                .andExpect(status().isNotFound());

        String unavailableStage = scenario.stageIds().get(1);
        for (String invalidStatus : List.of("BOUND", "CANCELLED", "IN_USE")) {
            jdbc.update("UPDATE ai_stage_material SET status=? WHERE stage_material_id=?", invalidStatus, unavailableStage);
            confirmWithStage(scenario, unavailableStage, "unavailable-" + invalidStatus)
                    .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MATERIAL_NOT_READY"));
            jdbc.update("UPDATE ai_stage_material SET status='READY' WHERE stage_material_id=?", unavailableStage);
        }

        String expiredStage = scenario.stageIds().get(2);
        jdbc.update("UPDATE ai_stage_material SET expires_at=? WHERE stage_material_id=?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), expiredStage);
        confirmWithStage(scenario, expiredStage, "expired-stage")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MATERIAL_NOT_READY"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class))
                .isEqualTo(applicationsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isZero();
    }

    @Test
    void stagedFileMustExistAndMatchRecordedSizeAndShaBeforeDraftCreation() throws Exception {
        Scenario scenario = scenario("confirm-stage-file-integrity", List.of("UNKNOWN", "UNKNOWN", "UNKNOWN"));
        int applicationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);

        String missingStage = scenario.stageIds().get(0);
        String originalKey = objectKey(missingStage);
        jdbc.update("UPDATE ai_stage_material SET stored_object_key=? WHERE stage_material_id=?",
                generatedObjectKey(), missingStage);
        confirmWithStage(scenario, missingStage, "missing-stage-file")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MATERIAL_NOT_READY"));
        jdbc.update("UPDATE ai_stage_material SET stored_object_key=? WHERE stage_material_id=?", originalKey, missingStage);

        String wrongSizeStage = scenario.stageIds().get(1);
        jdbc.update("UPDATE ai_stage_material SET file_size=file_size+1 WHERE stage_material_id=?", wrongSizeStage);
        jdbc.update("UPDATE ai_precheck_task_material SET file_size=file_size+1 WHERE task_id=? AND stage_material_id=?",
                scenario.taskId(), wrongSizeStage);
        confirmWithStage(scenario, wrongSizeStage, "wrong-stage-size")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MATERIAL_NOT_READY"));

        String hashMismatchStage = scenario.stageIds().get(2);
        String otherFileKey = objectKey(hashMismatchStage);
        assertThat(rowSize(missingStage)).isEqualTo(rowSize(hashMismatchStage));
        assertThat(rowSha(missingStage)).isNotEqualTo(rowSha(hashMismatchStage));
        jdbc.update("UPDATE ai_stage_material SET stored_object_key=? WHERE stage_material_id=?",
                generatedObjectKey(), hashMismatchStage);
        jdbc.update("UPDATE ai_stage_material SET stored_object_key=? WHERE stage_material_id=?",
                otherFileKey, missingStage);
        confirmWithStage(scenario, missingStage, "wrong-stage-sha")
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("MATERIAL_NOT_READY"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class))
                .isEqualTo(applicationsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isZero();
    }

    @Test
    void unknownAndApplicationFormRolesCannotBypassMaterialClassification() throws Exception {
        int applicationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);

        Scenario applicationForm = scenario("confirm-application-form-role", List.of("APPLICATION_FORM"));
        confirmWithBindings(applicationForm, "application-form-as-migration-proof",
                List.of(material(applicationForm.stageIds().get(0), "MIGRATION_PROOF")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("APPLICATION_FORM_TYPE_CANNOT_BE_RECLASSIFIED"));

        Scenario unknown = scenario("confirm-unknown-role", List.of("UNKNOWN"));
        confirmWithBindings(unknown, "unknown-without-classification",
                List.of(Map.of("stageMaterialId", unknown.stageIds().get(0))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("MATERIAL_TYPE_REQUIRED_OR_UNSUPPORTED"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class))
                .isEqualTo(applicationsBefore);

        String manualJson = "{\"personId\":9801,\"migrationType\":\"OUTSIDE_CITY\","
                + "\"fromAddress\":\"人工迁出地\",\"toRegionCode\":\"110105\",\"toHouseholdId\":9801,"
                + "\"inDate\":\"2026-11-05\",\"reason\":\"人工迁入入口回归\",\"title\":\"人工迁入回归\"}";
        MvcResult manual = mvc.perform(post("/api/migrations/in/applications")
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(manualJson))
                .andExpect(status().isCreated()).andReturn();
        long applicationId = objectMapper.readTree(manual.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId)).isEqualTo("DRAFT");
    }

    @Test
    void crossDistrictDraftWarnsAboutMissingTransferProofAndExistingSubmitRejectsIt() throws Exception {
        String transferBatchNo = "S4B-BATCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        seedCompletedTransferBatch(transferBatchNo);
        Scenario scenario = scenario("confirm-cross-district-missing-proof",
                List.of("IDENTITY_CARD_FRONT", "ADDRESS_PROOF"), MigrationType.IN_CITY_CROSS_DISTRICT,
                transferBatchNo);
        List<Map<String, Object>> bindings = List.of(
                material(scenario.stageIds().get(0), "IDENTITY_PROOF"),
                material(scenario.stageIds().get(1), "ADDRESS_PROOF"));
        Map<String, Object> body = confirmBody(scenario, "cross-district-no-transfer-proof", bindings, List.of(), null);
        replaceField(body, "migration.migrationType", MigrationType.IN_CITY_CROSS_DISTRICT.name());
        addField(body, "migration.transferBatchNo", transferBatchNo);

        MvcResult result = confirm(scenario.taskId(), householdToken, body).andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.warnings").value(org.hamcrest.Matchers.hasItem("MISSING_MIGRATION_PROOF")))
                .andReturn();
        long applicationId = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
        mvc.perform(post("/api/applications/{id}/submit", applicationId)
                        .header("Authorization", bearer(householdToken)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("缺少必需材料：MIGRATION_PROOF"));
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_request WHERE application_id=?",
                Integer.class, applicationId)).isZero();
    }

    @Test
    void cancelledTransferBatchCanBeReusedThroughAiPrecheckAndConfirmation() throws Exception {
        String batchNo = "S4B-REUSE-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        seedCompletedTransferBatch(batchNo);

        Scenario first = scenario("issue19-ai-reuse-first",
                List.of("IDENTITY_CARD_FRONT", "ADDRESS_PROOF"), MigrationType.IN_CITY_CROSS_DISTRICT, batchNo);
        System.out.println("ISSUE19_AI_REUSE_FIRST warnings=" + first.preview().path("warnings")
                + " confirmable=" + first.preview().path("confirmable").asBoolean());
        assertThat(first.preview().path("warnings").toString()).doesNotContain("TRANSFER_BATCH_INVALID_OR_USED");
        assertThat(first.preview().path("confirmable").asBoolean()).isTrue();
        Map<String, Object> firstBody = confirmBody(first, "issue19-ai-reuse-first-key",
                List.of(material(first.stageIds().get(0), "IDENTITY_PROOF"),
                        material(first.stageIds().get(1), "ADDRESS_PROOF")), List.of(), null);
        replaceField(firstBody, "migration.migrationType", MigrationType.IN_CITY_CROSS_DISTRICT.name());
        addField(firstBody, "migration.transferBatchNo", batchNo);
        MvcResult firstResult = confirm(first.taskId(), householdToken, firstBody)
                .andExpect(status().isCreated()).andReturn();
        long firstApplication = objectMapper.readTree(firstResult.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, firstApplication)).isEqualTo("DRAFT");

        mvc.perform(delete("/api/applications/{id}", firstApplication)
                        .param("version", String.valueOf(jdbc.queryForObject("SELECT version FROM business_application WHERE application_id=?", Integer.class, firstApplication)))
                        .header("Authorization", bearer(householdToken)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, firstApplication)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, firstApplication)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT transfer_batch_no FROM migration_in WHERE application_id=?",
                String.class, firstApplication)).isEqualTo(batchNo);

        Scenario second = scenario("issue19-ai-reuse-second",
                List.of("IDENTITY_CARD_FRONT", "ADDRESS_PROOF"), MigrationType.IN_CITY_CROSS_DISTRICT, batchNo);
        System.out.println("ISSUE19_AI_REUSE_SECOND warnings=" + second.preview().path("warnings")
                + " confirmable=" + second.preview().path("confirmable").asBoolean());
        assertThat(second.preview().path("warnings").toString()).doesNotContain("TRANSFER_BATCH_INVALID_OR_USED");
        assertThat(second.preview().path("confirmable").asBoolean()).isTrue();
        Map<String, Object> secondBody = confirmBody(second, "issue19-ai-reuse-second-key",
                List.of(material(second.stageIds().get(0), "IDENTITY_PROOF"),
                        material(second.stageIds().get(1), "ADDRESS_PROOF")), List.of(), null);
        replaceField(secondBody, "migration.migrationType", MigrationType.IN_CITY_CROSS_DISTRICT.name());
        addField(secondBody, "migration.transferBatchNo", batchNo);
        MvcResult secondResult = confirm(second.taskId(), householdToken, secondBody)
                .andExpect(status().isCreated()).andReturn();
        long secondApplication = objectMapper.readTree(secondResult.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, secondApplication)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT transfer_batch_no FROM migration_in WHERE application_id=?",
                String.class, secondApplication)).isEqualTo(batchNo);
    }

    @Test
    void sameTaskDifferentIdempotencyKeysInParallelCreateAtMostOneDraft() throws Exception {
        Scenario scenario = scenario("confirm-concurrent-different-keys", List.of("UNKNOWN"));
        Map<String, Object> firstBody = confirmBody(scenario, "different-key-first", List.of(), List.of(), null);
        Map<String, Object> secondBody = confirmBody(scenario, "different-key-second", List.of(), List.of(), null);
        int applicationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return confirm(scenario.taskId(), householdToken, firstBody).andReturn();
            });
            var second = executor.submit(() -> {
                ready.countDown();
                assertThat(start.await(5, TimeUnit.SECONDS)).isTrue();
                return confirm(scenario.taskId(), householdToken, secondBody).andReturn();
            });
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            MvcResult firstResult = first.get(15, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(15, TimeUnit.SECONDS);
            assertThat(List.of(firstResult.getResponse().getStatus(), secondResult.getResponse().getStatus()))
                    .containsExactlyInAnyOrder(201, 409);
            MvcResult rejected = firstResult.getResponse().getStatus() == 409 ? firstResult : secondResult;
            assertThat(objectMapper.readTree(rejected.getResponse().getContentAsString()).path("message").asText())
                    .isEqualTo("TASK_ALREADY_CONSUMED");
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class))
                .isEqualTo(applicationsBefore + 1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_confirmation_receipt WHERE task_id=?",
                Integer.class, scenario.taskId())).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE application_id IN "
                        + "(SELECT application_id FROM ai_confirmation_receipt WHERE task_id=?)",
                Integer.class, scenario.taskId())).isOne();
    }

    @Test
    void confirmedDraftUsesExistingSubmissionAndIndependentL3MaterialVerification() throws Exception {
        Scenario scenario = scenario("confirm-submit-l3", List.of("IDENTITY_CARD_FRONT", "ADDRESS_PROOF"));
        List<Map<String, Object>> bindings = List.of(
                material(scenario.stageIds().get(0), "IDENTITY_PROOF"),
                material(scenario.stageIds().get(1), "ADDRESS_PROOF"));
        Map<String, Object> confirmRequest = confirmBody(scenario, "confirm-submit-l3-key", bindings, List.of(), null);
        MvcResult confirmation = confirm(scenario.taskId(), householdToken, confirmRequest)
                .andExpect(status().isCreated()).andReturn();
        long applicationId = objectMapper.readTree(confirmation.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, applicationId)).isEqualTo("DRAFT");

        mvc.perform(post("/api/applications/{id}/submit", applicationId)
                        .header("Authorization", bearer(householdToken)))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId)).isEqualTo("UNDER_REVIEW");
        List<ApplicationMaterial> required = materials.selectByApplicationId(applicationId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getRequiredFlag())).toList();
        assertThat(required).hasSize(2).allSatisfy(material ->
                assertThat(material.getVerifyStatus()).isEqualTo(com.wjx871.population.material.MaterialVerifyStatus.PENDING));
        mvc.perform(post("/api/materials/{id}/verify", required.get(0).getMaterialId())
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"VERIFIED\",\"comment\":\"L3 测试\"}"))
                .andExpect(status().isForbidden());
        for (ApplicationMaterial material : required) {
            mvc.perform(post("/api/materials/{id}/verify", material.getMaterialId())
                            .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"result\":\"VERIFIED\",\"comment\":\"L3 测试\"}"))
                    .andExpect(status().isOk());
        }
        assertThat(materials.selectByApplicationId(applicationId).stream()
                .filter(m -> Boolean.TRUE.equals(m.getRequiredFlag()))
                .allMatch(m -> m.getVerifyStatus() == com.wjx871.population.material.MaterialVerifyStatus.VERIFIED)).isTrue();
        confirm(scenario.taskId(), householdToken, confirmRequest).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creationStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.currentStatus").value("UNDER_REVIEW"));
    }

    @Test
    void boundMaterialUsesExplicitAlternativeChoiceSurvivesReaperAndDeletionIsTransactionalAndRestartRecoverable() throws Exception {
        Scenario scenario = scenario("confirm-materials", List.of("IDENTITY_CARD_FRONT",
                "HOUSEHOLD_REGISTER_PAGE", "ADDRESS_PROOF"));
        List<Map<String, Object>> bindings = List.of(
                material(scenario.stageIds().get(0), "IDENTITY_PROOF"),
                material(scenario.stageIds().get(1), "HOUSEHOLD_BOOK"),
                material(scenario.stageIds().get(2), "ADDRESS_PROOF"));
        Map<String, Object> noChoice = confirmBody(scenario, "materials-no-choice", bindings, List.of(), null);
        confirm(scenario.taskId(), householdToken, noChoice).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("HOUSEHOLD_EVIDENCE_CHOICE_REQUIRED"));
        for (String stageId : scenario.stageIds()) {
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("READY");
        }

        Map<String, Object> body = confirmBody(scenario, "materials-choice-address", bindings, List.of(), "ADDRESS_PROOF");
        MvcResult confirmResult = confirm(scenario.taskId(), householdToken, body)
                .andExpect(status().isCreated()).andExpect(jsonPath("$.data.materials.length()").value(3)).andReturn();
        long applicationId = objectMapper.readTree(confirmResult.getResponse().getContentAsString())
                .path("data").path("applicationId").asLong();
        List<ApplicationMaterial> applicationMaterials = materials.selectByApplicationId(applicationId);
        assertThat(applicationMaterials).hasSize(3);
        ApplicationMaterial requiredIdentity = applicationMaterials.stream()
                .filter(m -> "IDENTITY_PROOF".equals(m.getMaterialType())).findFirst().orElseThrow();
        ApplicationMaterial requiredAddress = applicationMaterials.stream()
                .filter(m -> "ADDRESS_PROOF".equals(m.getMaterialType())).findFirst().orElseThrow();
        ApplicationMaterial optionalBook = applicationMaterials.stream()
                .filter(m -> "HOUSEHOLD_BOOK".equals(m.getMaterialType())).findFirst().orElseThrow();
        assertThat(requiredIdentity.getRequiredFlag()).isTrue();
        assertThat(requiredAddress.getRequiredFlag()).isTrue();
        assertThat(optionalBook.getRequiredFlag()).isFalse();
        assertThat(applicationMaterials).allSatisfy(m -> {
            assertThat(m.getVerifyStatus()).isEqualTo(com.wjx871.population.material.MaterialVerifyStatus.PENDING);
            assertThat(m.getStorageKind()).isEqualTo("AI_STAGE");
        });
        for (String stageId : scenario.stageIds()) {
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("BOUND");
        }

        Long boundMaterialId = requiredIdentity.getMaterialId();
        MvcResult downloaded = mvc.perform(get("/api/materials/{id}/download", boundMaterialId)
                        .header("Authorization", bearer(householdToken)))
                .andExpect(status().isOk()).andReturn();
        assertThat(downloaded.getResponse().getContentAsByteArray()).isEqualTo(pdf("synthetic material 0"));

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> cleaner = executor.submit(() -> { start.await(); lifecycleReaper.reapMaterials(); return null; });
            Future<?> reader = executor.submit(() -> {
                start.await();
                assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                        String.class, scenario.stageIds().get(0))).isEqualTo("BOUND");
                assertThat(stageStorage.verify(scenario.stageIds().get(0), objectKey(scenario.stageIds().get(0)),
                        rowSize(scenario.stageIds().get(0)), rowSha(scenario.stageIds().get(0)))).isTrue();
                return null;
            });
            start.countDown();
            cleaner.get(10, TimeUnit.SECONDS);
            reader.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, scenario.stageIds().get(0))).isEqualTo("BOUND");

        doThrow(new IllegalStateException("test-only rollback")).when(materialMapperSpy).deleteById(boundMaterialId);
        mvc.perform(delete("/api/materials/{id}", boundMaterialId).header("Authorization", bearer(householdToken)))
                .andExpect(status().isInternalServerError());
        reset(materialMapperSpy);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE material_id=?",
                Integer.class, boundMaterialId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, scenario.stageIds().get(0))).isEqualTo("BOUND");
        assertThat(stageStorage.verify(scenario.stageIds().get(0), objectKey(scenario.stageIds().get(0)),
                rowSize(scenario.stageIds().get(0)), rowSha(scenario.stageIds().get(0)))).isTrue();

        String stageId = scenario.stageIds().get(0);
        String objectKey = objectKey(stageId);
        doThrow(new IOException("test-only cleanup failure")).when(stageStorageSpy).delete(stageId, objectKey);
        mvc.perform(delete("/api/materials/{id}", boundMaterialId).header("Authorization", bearer(householdToken)))
                .andExpect(status().isOk());
        reset(stageStorageSpy);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE material_id=?",
                Integer.class, boundMaterialId)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material_binding WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("RELEASE_PENDING");
        assertThat(stageStorage.verify(stageId, objectKey, rowSize(stageId), rowSha(stageId))).isTrue();
        AiBoundMaterialCleanupService restartedCleanup = new AiBoundMaterialCleanupService(jdbc, stageStorage,
                transactionManager);
        assertThat(restartedCleanup.cleanupBatch()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material_binding WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("RELEASED");
        assertThat(jdbc.queryForObject("SELECT file_deleted_at IS NOT NULL FROM ai_stage_material WHERE stage_material_id=?",
                Boolean.class, stageId)).isTrue();
        assertThat(Files.exists(STORAGE_ROOT.resolve("objects").resolve(objectKey))).isFalse();

        AuthenticatedUser user = authService.loadUser("household");
        withUser(user, () -> {
            var created = materialService.upload(applicationId, "LOCAL_TEST", "人工上传回归",
                    false, new MockMultipartFile("file", "manual.pdf", "application/pdf", pdf("manual material")));
            ApplicationMaterial local = materials.selectById(created.materialId()).orElseThrow();
            assertThat(local.getStorageKind()).isEqualTo("LOCAL");
            try (var input = materialService.download(created.materialId()).resource().getInputStream()) {
                assertThat(input.readAllBytes()).isEqualTo(pdf("manual material"));
            }
            materialService.delete(created.materialId());
            assertThat(materials.selectById(created.materialId())).isEmpty();
            return null;
        });
    }

    private Scenario scenario(String key, List<String> roles) throws Exception {
        return scenario(key, roles, MigrationType.OUTSIDE_CITY, null);
    }

    private Scenario scenario(String key, List<String> roles, MigrationType migrationType, String transferBatchNo)
            throws Exception {
        List<String> stageIds = new ArrayList<>();
        int index = 0;
        for (String role : roles) stageIds.add(upload(role, key + "-" + index, index++));
        String taskId = createSuccessfulTask(key, stageIds, migrationType);
        long version = taskVersion(taskId);
        JsonNode preview = previewData(taskId, List.of(
                correction("person.name", "解析测试甲"),
                correction("person.idCard", "110105198001010016"),
                correction("household.householdNo", "S4A-H-ALPHA")), migrationType, "110105", transferBatchNo);
        return new Scenario(taskId, version, preview, List.copyOf(stageIds));
    }

    private String upload(String role, String key, int index) throws Exception {
        MvcResult result = mvc.perform(multipart("/api/ai/materials")
                        .file(new MockMultipartFile("file", "synthetic-" + index + ".pdf", "application/pdf",
                                pdf("synthetic material " + index)))
                        .header("Authorization", bearer(householdToken)).header("Idempotency-Key", key)
                        .param("businessType", "MIGRATION_IN").param("documentRoleHint", role))
                .andExpect(status().isCreated()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data")
                .path("stageMaterialId").asText();
    }

    private String createSuccessfulTask(String key, List<String> stageIds) throws Exception {
        return createSuccessfulTask(key, stageIds, MigrationType.OUTSIDE_CITY);
    }

    private String createSuccessfulTask(String key, List<String> stageIds, MigrationType migrationType) throws Exception {
        AiTaskCreateRequest body = new AiTaskCreateRequest("1.0", "DOCUMENT_PRECHECK", "MIGRATION_IN",
                migrationType.name(), stageIds, key + "-task-key");
        MvcResult accepted = mvc.perform(post("/api/ai/tasks").header("Authorization", bearer(householdToken))
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isAccepted()).andReturn();
        String taskId = objectMapper.readTree(accepted.getResponse().getContentAsString())
                .path("data").path("taskId").asText();
        jdbc.update("UPDATE ai_precheck_task SET next_attempt_at=? WHERE task_id=?",
                Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)), taskId);
        String payload = jdbc.queryForObject("SELECT payload_json FROM ai_precheck_outbox WHERE task_id=?",
                String.class, taskId);
        worker.process(objectMapper.readValue(payload, AiPrecheckTaskService.EventEnvelope.class));
        assertThat(jdbc.queryForObject("SELECT status FROM ai_precheck_task WHERE task_id=?", String.class, taskId))
                .isEqualTo("SUCCEEDED");
        return taskId;
    }

    private JsonNode previewData(String taskId, List<AiResolutionPreviewRequest.Correction> corrections) throws Exception {
        return previewData(taskId, corrections, MigrationType.OUTSIDE_CITY, "110105", null);
    }

    private JsonNode previewData(String taskId, List<AiResolutionPreviewRequest.Correction> corrections,
                                 MigrationType migrationType, String toRegionCode, String transferBatchNo)
            throws Exception {
        AiResolutionPreviewRequest request = new AiResolutionPreviewRequest("1.0", taskVersion(taskId), corrections,
                migrationType, toRegionCode, transferBatchNo);
        MvcResult result = mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", taskId)
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private Map<String, Object> confirmBody(Scenario scenario, String key, List<Map<String, Object>> bindings,
                                            List<String> acknowledged, String householdEvidenceType) {
        Map<String, Object> selected = Map.of("person", token(scenario.preview(), "person"),
                "targetHousehold", token(scenario.preview(), "household"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("requestSchemaVersion", "1.0");
        body.put("taskVersion", scenario.taskVersion());
        body.put("resolutionToken", scenario.preview().path("resolutionToken").asText());
        body.put("selectedCandidates", selected);
        body.put("idempotencyKey", key);
        body.put("confirmedFields", List.of(
                field("person.name", "解析测试甲"),
                field("person.idCard", "110105198001010016"),
                field("household.householdNo", "S4A-H-ALPHA"),
                field("migration.migrationType", "OUTSIDE_CITY"),
                field("migration.toRegionCode", "110105"),
                field("migration.fromAddress", "迁出地示例地址"),
                field("migration.inDate", "2026-11-05"),
                field("migration.reason", "用户确认的迁入事由"),
                field("migration.title", "AI 迁入草稿")));
        body.put("acknowledgedAlerts", acknowledged);
        body.put("materialBindings", bindings);
        if (householdEvidenceType != null) body.put("householdEvidenceType", householdEvidenceType);
        return body;
    }

    private ResultActions confirm(String taskId, String token, Map<String, Object> body) throws Exception {
        return mvc.perform(post("/api/ai/tasks/{id}/confirm", taskId).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)));
    }

    private ResultActions confirmWithStage(Scenario scenario, String stageId, String key) throws Exception {
        return confirmWithBindings(scenario, key, List.of(material(stageId, "IDENTITY_PROOF")));
    }

    private ResultActions confirmWithBindings(Scenario scenario, String key, List<Map<String, Object>> bindings)
            throws Exception {
        return confirm(scenario.taskId(), householdToken, confirmBody(scenario, key, bindings, List.of(), null));
    }

    private String generatedObjectKey() {
        return "obj_" + UUID.randomUUID().toString().replace("-", "") + ".pdf";
    }

    private void seedCompletedTransferBatch(String batchNo) {
        AuthenticatedUser owner = authService.loadUser("household");
        String applicationNo = "S4BBATCH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,"
                        + "applicant_department_id,applicant_region_code,target_person_id,target_household_id,status,reason,"
                        + "version,created_at,updated_at) VALUES(?,'MIGRATION_OUT','synthetic completed transfer',?,?,?,?,9801,"
                        + "'COMPLETED','S4-B synthetic transfer fixture',1,?,?)",
                applicationNo, owner.userId(), owner.departmentId(), owner.regionCode(), 9801L,
                Timestamp.valueOf(now), Timestamp.valueOf(now));
        Long applicationId = jdbc.queryForObject("SELECT application_id FROM business_application WHERE application_no=?",
                Long.class, applicationNo);
        jdbc.update("INSERT INTO migration_out(application_id,person_id,migration_type,from_region_code,"
                        + "from_household_id,from_address_snapshot,to_region_code,to_address,out_date,reason,"
                        + "transfer_batch_no,business_status,version) VALUES(?,9801,'IN_CITY_CROSS_DISTRICT','110105',"
                        + "9801,'synthetic previous address','110105','synthetic destination',DATE '2026-11-01',"
                        + "'synthetic transfer',?,'COMPLETED',1)", applicationId, batchNo);
    }

    private void replaceField(Map<String, Object> body, String fieldCode, String value) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) body.get("confirmedFields");
        fields.stream().filter(field -> fieldCode.equals(field.get("fieldCode"))).findFirst()
                .orElseThrow().put("value", value);
    }

    private void addField(Map<String, Object> body, String fieldCode, String value) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> fields = (List<Map<String, Object>>) body.get("confirmedFields");
        List<Map<String, Object>> changed = new ArrayList<>(fields);
        changed.add(field(fieldCode, value));
        body.put("confirmedFields", changed);
    }

    private Map<String, Object> material(String stageId, String type) {
        return Map.of("stageMaterialId", stageId, "materialType", type);
    }

    private Map<String, Object> field(String code, String value) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("fieldCode", code);
        field.put("value", value);
        return field;
    }

    private AiResolutionPreviewRequest.Correction correction(String code, String value) {
        return new AiResolutionPreviewRequest.Correction(code, value);
    }

    private String token(JsonNode preview, String kind) {
        String candidateType = "person".equals(kind) ? "personCandidates" : "targetHouseholdCandidates";
        return preview.path(candidateType).get(0).path("candidateToken").asText();
    }

    private long taskVersion(String taskId) {
        return jdbc.queryForObject("SELECT version FROM ai_precheck_task WHERE task_id=?", Long.class, taskId);
    }

    private void ensureResolutionFixture() {
        if (jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE person_id=9801", Integer.class) == 0) {
            jdbc.update("INSERT INTO person(person_id,name,gender,id_card,birth_date,status,current_status_code,"
                            + "initial_scope_region_code,version) VALUES(9801,'解析测试甲','M','110105198001010016',"
                            + "DATE '1980-01-01','正常','PENDING','110000',0)");
        }
        if (jdbc.queryForObject("SELECT COUNT(*) FROM household WHERE household_id=9801", Integer.class) == 0) {
            jdbc.update("INSERT INTO household(household_id,household_no,address,establish_date,status,region_code,version) "
                            + "VALUES(9801,'S4A-H-ALPHA','示例市示例区测试路1号',DATE '2020-01-01','ACTIVE','110105',0)");
        }
    }

    private String ocrResult(String name, String idCard, String householdNo) throws Exception {
        return objectMapper.writeValueAsString(Map.of("materials", List.of(Map.of("fields", List.of(
                ocrField("person.name", name), ocrField("person.idCard", idCard),
                ocrField("household.householdNo", householdNo))))));
    }

    private Map<String, Object> ocrField(String code, String value) {
        return Map.of("fieldCode", code, "value", value, "fieldStatus", "PRESENT");
    }

    private long rowSize(String stageId) {
        return jdbc.queryForObject("SELECT file_size FROM ai_stage_material WHERE stage_material_id=?",
                Long.class, stageId);
    }

    private String rowSha(String stageId) {
        return jdbc.queryForObject("SELECT sha256 FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId);
    }

    private String sha256(String value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
    }

    private String objectKey(String stageId) {
        return jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId);
    }

    private void withUser(AuthenticatedUser user, ThrowingSupplier<?> action) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        try {
            new TransactionTemplate(transactionManager).execute(status -> {
                try { return action.get(); } catch (RuntimeException exception) { throw exception; }
                catch (Exception exception) { throw new IllegalStateException(exception); }
            });
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String login(String username) throws Exception {
        MvcResult result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private byte[] pdf(String contents) {
        return ("%PDF-1.7\n% synthetic test input\n" + contents + "\n%%EOF").getBytes(StandardCharsets.UTF_8);
    }

    private record Scenario(String taskId, long taskVersion, JsonNode preview, List<String> stageIds) { }

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }
}
