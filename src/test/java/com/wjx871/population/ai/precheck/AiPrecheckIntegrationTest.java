package com.wjx871.population.ai.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
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
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.migration.MigrationType;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.RoleLevel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
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
    @Autowired AiResolutionService resolutionService;
    @Autowired AuthService authService;

    private String householdToken;
    private String adminToken;
    private String viewerToken;

    @BeforeEach
    void resetS3DataAndAuthenticate() throws Exception {
        jdbc.update("DELETE FROM ai_resolution_candidate");
        jdbc.update("DELETE FROM ai_resolution_session");
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
        resetResolutionFixture();
    }

    @Test
    void resolutionPreviewRequiresOwnedSucceededTaskAndCurrentPermissions() throws Exception {
        resetResolutionFixture();
        String taskId = createTaskWithoutProcessing(householdToken, "resolution-pending-task", "OUTSIDE_CITY");
        AiResolutionPreviewRequest body = resolutionRequest(0L, completeCorrections(), MigrationType.OUTSIDE_CITY,
                "110105", null);
        mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", taskId)
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("TASK_NOT_READY_FOR_RESOLUTION"));
        jdbc.update("UPDATE ai_precheck_task SET status='FAILED' WHERE task_id=?", taskId);
        mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", taskId)
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("TASK_NOT_READY_FOR_RESOLUTION"));

        mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", taskId)
                        .contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", taskId)
                        .header("Authorization", bearer(viewerToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", taskId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isNotFound());

        String succeededTaskId = createSuccessfulTask(householdToken, "resolution-task-version");
        long version = taskVersion(succeededTaskId);
        AiResolutionPreviewRequest wrongVersion = resolutionRequest(version + 1, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null);
        mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", succeededTaskId)
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongVersion)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("TASK_VERSION_MISMATCH"));
        mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", succeededTaskId)
                        .header("Authorization", bearer(adminToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolutionRequest(version, completeCorrections(),
                                MigrationType.OUTSIDE_CITY, "110105", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolutionPreviewIssuesScopedOpaqueCandidatesAndRepreviewRevokesOldTokens() throws Exception {
        resetResolutionFixture();
        insertResolutionFixture();
        String taskId = createSuccessfulTask(householdToken, "resolution-success-task");
        long taskVersion = taskVersion(taskId);
        MvcResult firstResult = preview(taskId, resolutionRequest(taskVersion, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
        JsonNode first = objectMapper.readTree(firstResult.getResponse().getContentAsString()).path("data");
        assertThat(first.path("resolutionStatus").asText()).isEqualTo("RESOLVED_SINGLE");
        assertThat(first.path("confirmable").asBoolean()).isTrue();
        assertThat(first.path("personCandidates").size()).isEqualTo(1);
        assertThat(first.path("targetHouseholdCandidates").size()).isEqualTo(1);
        String oldResolutionToken = first.path("resolutionToken").asText();
        String oldPersonToken = first.path("personCandidates").get(0).path("candidateToken").asText();
        String oldHouseholdToken = first.path("targetHouseholdCandidates").get(0).path("candidateToken").asText();
        assertThat(oldResolutionToken).startsWith("res_").hasSizeGreaterThan(40);
        assertThat(oldPersonToken).startsWith("pc_").hasSizeGreaterThan(40);
        assertThat(oldHouseholdToken).startsWith("hc_").hasSizeGreaterThan(40);
        assertThat(firstResult.getResponse().getContentAsString()).doesNotContain("9801", "9802", "110105198001010016");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_resolution_session WHERE resolution_token_sha256=?",
                Integer.class, sha256(oldResolutionToken))).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_resolution_session WHERE resolution_token_sha256=?",
                Integer.class, oldResolutionToken)).isZero();

        List<AiResolutionPreviewRequest.Correction> changed = new ArrayList<>(completeCorrections());
        changed.add(new AiResolutionPreviewRequest.Correction("household.address", "测试路 99 号"));
        JsonNode second = objectMapper.readTree(preview(taskId, resolutionRequest(taskVersion, changed,
                MigrationType.OUTSIDE_CITY, "110105", null)).getResponse().getContentAsString()).path("data");
        assertThat(second.path("resolutionStatus").asText()).isEqualTo("RESOLVED_SINGLE");
        String newResolutionToken = second.path("resolutionToken").asText();
        String newPersonToken = second.path("personCandidates").get(0).path("candidateToken").asText();
        String newHouseholdToken = second.path("targetHouseholdCandidates").get(0).path("candidateToken").asText();
        assertThat(newResolutionToken).isNotEqualTo(oldResolutionToken);
        assertThat(jdbc.queryForObject("SELECT request_digest FROM ai_resolution_session WHERE resolution_token_sha256=?",
                String.class, sha256(newResolutionToken))).isNotEqualTo(jdbc.queryForObject(
                "SELECT request_digest FROM ai_resolution_session WHERE resolution_token_sha256=?", String.class,
                sha256(oldResolutionToken)));
        assertThat(jdbc.queryForObject("SELECT status FROM ai_resolution_session WHERE resolution_token_sha256=?",
                String.class, sha256(oldResolutionToken))).isEqualTo("REVOKED");
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(taskId,
                taskVersion, newResolutionToken, oldPersonToken, newHouseholdToken)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("INVALID_CANDIDATE_TOKEN");
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(taskId,
                taskVersion, newResolutionToken, newHouseholdToken, newPersonToken)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("INVALID_CANDIDATE_TOKEN");
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(taskId,
                taskVersion, oldResolutionToken, oldPersonToken, oldHouseholdToken)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_EXPIRED");
        String otherTaskId = createSuccessfulTask(householdToken, "resolution-other-task");
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(otherTaskId,
                taskVersion(otherTaskId), newResolutionToken, newPersonToken, newHouseholdToken)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_EXPIRED");
        AiResolutionService.ResolutionSelection selection = withHouseholdUser(() -> resolutionService
                .validateForConfirmation(taskId, taskVersion, newResolutionToken, newPersonToken, newHouseholdToken));
        assertThat(selection.personId()).isEqualTo(9801L);
        assertThat(selection.targetHouseholdId()).isEqualTo(9801L);
        jdbc.update("UPDATE ai_resolution_session SET expires_at=? WHERE resolution_token_sha256=?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1)), sha256(newResolutionToken));
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(taskId,
                taskVersion, newResolutionToken, newPersonToken, newHouseholdToken)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE applicant_user_id=6 AND target_person_id=9801",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE person_id=9801",
                Integer.class)).isZero();
    }

    @Test
    void partialResolutionIsExplicitlyNonConfirmableAndCannotCreateDraft() throws Exception {
        resetResolutionFixture();
        insertResolutionFixture();
        String taskId = createSuccessfulTask(householdToken, "resolution-partial-task");
        long version = taskVersion(taskId);
        List<AiResolutionPreviewRequest.Correction> partial = completeCorrections().stream()
                .filter(c -> !"household.householdNo".equals(c.fieldCode())).toList();
        JsonNode data = objectMapper.readTree(preview(taskId, resolutionRequest(version, partial,
                MigrationType.OUTSIDE_CITY, "110105", null)).getResponse().getContentAsString()).path("data");
        assertThat(data.path("resolutionStatus").asText()).isEqualTo("NEEDS_CORRECTION");
        assertThat(data.path("confirmable").asBoolean()).isFalse();
        assertThat(data.path("personCandidates").size()).isEqualTo(1);
        assertThat(data.path("targetHouseholdCandidates").size()).isZero();
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(taskId,
                version, data.path("resolutionToken").asText(), "pc_invalid", "hc_invalid")))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_NOT_CONFIRMABLE");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE target_person_id=9801",
                Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE person_id=9801",
                Integer.class)).isZero();
    }

    @Test
    void candidateSnapshotDetectsMembershipAndPersonVersionChanges() throws Exception {
        resetResolutionFixture();
        insertResolutionFixture();
        String taskId = createSuccessfulTask(householdToken, "resolution-state-task");
        long version = taskVersion(taskId);
        JsonNode first = objectMapper.readTree(preview(taskId, resolutionRequest(version, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null)).getResponse().getContentAsString()).path("data");
        int householdVersion = jdbc.queryForObject("SELECT version FROM household WHERE household_id=9801", Integer.class);
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,status,current_status_code,initial_scope_region_code,version) "
                + "VALUES(9802,'解析成员乙','F','110105198002020024','正常','PENDING','110000',0)");
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) "
                + "VALUES(9801,9802,'MEMBER',DATE '2020-01-01','ACTIVE',0)");
        assertThat(jdbc.queryForObject("SELECT version FROM household WHERE household_id=9801", Integer.class))
                .isEqualTo(householdVersion);
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(taskId, version,
                first.path("resolutionToken").asText(), first.path("personCandidates").get(0).path("candidateToken").asText(),
                first.path("targetHouseholdCandidates").get(0).path("candidateToken").asText())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_ENTITY_CHANGED");

        resetResolutionFixture();
        insertResolutionFixture();
        String secondTask = createSuccessfulTask(householdToken, "resolution-person-version-task");
        long secondVersion = taskVersion(secondTask);
        JsonNode preview = objectMapper.readTree(preview(secondTask, resolutionRequest(secondVersion, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null)).getResponse().getContentAsString()).path("data");
        jdbc.update("UPDATE person SET version=version+1 WHERE person_id=9801");
        assertThatThrownBy(() -> withHouseholdUser(() -> resolutionService.validateForConfirmation(secondTask, secondVersion,
                preview.path("resolutionToken").asText(), preview.path("personCandidates").get(0).path("candidateToken").asText(),
                preview.path("targetHouseholdCandidates").get(0).path("candidateToken").asText())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_ENTITY_CHANGED");
    }

    @Test
    void currentPermissionAndDataScopeChangesInvalidatePreview() throws Exception {
        resetResolutionFixture();
        insertResolutionFixture();
        String taskId = createSuccessfulTask(householdToken, "resolution-scope-task");
        long version = taskVersion(taskId);
        JsonNode preview = objectMapper.readTree(preview(taskId, resolutionRequest(version, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null)).getResponse().getContentAsString()).path("data");
        AuthenticatedUser current = authService.loadUser("household");
        List<String> changedPermissions = new ArrayList<>(current.permissions());
        changedPermissions.add("test:permission-snapshot-change");
        AuthenticatedUser permissionChanged = new AuthenticatedUser(current.userId(), current.username(),
                current.passwordHash(), current.realName(), current.userStatus(), current.roleId(), current.roleCode(),
                current.roleName(), current.roleLevel(), current.dataScope(), current.roleStatus(), current.departmentId(),
                current.departmentName(), current.regionCode(), changedPermissions, current.regionCodes());
        assertThatThrownBy(() -> withUser(permissionChanged, () -> resolutionService.validateForConfirmation(taskId,
                version, preview.path("resolutionToken").asText(), preview.path("personCandidates").get(0).path("candidateToken").asText(),
                preview.path("targetHouseholdCandidates").get(0).path("candidateToken").asText())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_SCOPE_CHANGED");

        AuthenticatedUser scopeChanged = new AuthenticatedUser(current.userId(), current.username(),
                current.passwordHash(), current.realName(), current.userStatus(), current.roleId(), current.roleCode(),
                current.roleName(), current.roleLevel(), DataScope.ALL, current.roleStatus(), current.departmentId(),
                current.departmentName(), current.regionCode(), current.permissions(), List.of());
        assertThatThrownBy(() -> withUser(scopeChanged, () -> resolutionService.validateForConfirmation(taskId,
                version, preview.path("resolutionToken").asText(), preview.path("personCandidates").get(0).path("candidateToken").asText(),
                preview.path("targetHouseholdCandidates").get(0).path("candidateToken").asText())))
                .isInstanceOf(BusinessException.class).hasMessageContaining("RESOLUTION_SCOPE_CHANGED");
    }

    @Test
    void previewHandlesIdentityHouseholdRegionResidenceAndBatchConflicts() throws Exception {
        resetResolutionFixture();
        insertResolutionFixture();
        String taskId = createSuccessfulTask(householdToken, "resolution-warning-task");
        long version = taskVersion(taskId);

        List<AiResolutionPreviewRequest.Correction> unknownIdentity = List.of(
                new AiResolutionPreviewRequest.Correction("person.name", "解析测试甲"),
                new AiResolutionPreviewRequest.Correction("person.idCard", "11010519491231002X"),
                new AiResolutionPreviewRequest.Correction("household.householdNo", "S4A-H-ALPHA"));
        JsonNode noPerson = previewData(taskId, resolutionRequest(version, unknownIdentity,
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(noPerson.path("resolutionStatus").asText()).isEqualTo("NEEDS_CORRECTION");
        assertThat(noPerson.path("personCandidates").size()).isZero();
        assertThat(noPerson.path("warnings").toString()).contains("ENTITY_NOT_FOUND");

        List<AiResolutionPreviewRequest.Correction> nameMismatch = List.of(
                new AiResolutionPreviewRequest.Correction("person.name", "错误姓名"),
                new AiResolutionPreviewRequest.Correction("person.idCard", "110105198001010016"),
                new AiResolutionPreviewRequest.Correction("household.householdNo", "S4A-H-ALPHA"));
        JsonNode mismatch = previewData(taskId, resolutionRequest(version, nameMismatch,
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(mismatch.path("warnings").toString()).contains("PERSON_NAME_MISMATCH");

        jdbc.update("UPDATE household SET status='PENDING_CANCELLATION' WHERE household_id=9801");
        JsonNode inactive = previewData(taskId, resolutionRequest(version, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(inactive.path("warnings").toString()).contains("TARGET_HOUSEHOLD_NOT_ACTIVE");

        jdbc.update("UPDATE household SET status='ACTIVE',region_code='110100' WHERE household_id=9801");
        JsonNode regionMismatch = previewData(taskId, resolutionRequest(version, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(regionMismatch.path("warnings").toString()).contains("TARGET_REGION_MISMATCH");

        jdbc.update("UPDATE household SET region_code='110105' WHERE household_id=9801");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,"
                        + "register_date,start_date,status,version) VALUES(9801,9801,'示例地址','110105','HOUSEHOLD',"
                        + "DATE '2020-01-01',DATE '2020-01-01','ACTIVE',0)");
        JsonNode alreadyRegistered = previewData(taskId, resolutionRequest(version, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(alreadyRegistered.path("warnings").toString()).contains("PERSON_CURRENTLY_REGISTERED");
        jdbc.update("DELETE FROM residence WHERE person_id=9801");

        String crossDistrictTask = createSuccessfulTask(householdToken, "resolution-invalid-batch",
                "IN_CITY_CROSS_DISTRICT");
        JsonNode invalidBatch = previewData(crossDistrictTask, resolutionRequest(taskVersion(crossDistrictTask),
                completeCorrections(), MigrationType.IN_CITY_CROSS_DISTRICT, "110105", "missing-batch"));
        assertThat(invalidBatch.path("warnings").toString()).contains("TRANSFER_BATCH_INVALID_OR_USED");
    }

    @Test
    void addressHintReturnsScopedCandidatesAndOutOfScopePersonIsHidden() throws Exception {
        resetResolutionFixture();
        insertResolutionFixture();
        jdbc.update("INSERT INTO household(household_id,household_no,address,establish_date,status,region_code,version) "
                        + "VALUES(9803,'S4A-H-BETA','示例市示例区测试路2号',DATE '2020-01-01','ACTIVE','110105',0)");
        String taskId = createSuccessfulTask(householdToken, "resolution-multiple-households");
        long version = taskVersion(taskId);
        List<AiResolutionPreviewRequest.Correction> addressHint = List.of(
                new AiResolutionPreviewRequest.Correction("person.name", "解析测试甲"),
                new AiResolutionPreviewRequest.Correction("person.idCard", "110105198001010016"),
                new AiResolutionPreviewRequest.Correction("household.address", "测试路"));
        JsonNode multiple = previewData(taskId, resolutionRequest(version, addressHint,
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(multiple.path("resolutionStatus").asText()).isEqualTo("SELECTION_REQUIRED");
        assertThat(multiple.path("confirmable").asBoolean()).isTrue();
        assertThat(multiple.path("targetHouseholdCandidates").size()).isEqualTo(2);

        jdbc.update("UPDATE person SET initial_scope_region_code='120000' WHERE person_id=9801");
        JsonNode outOfScope = previewData(taskId, resolutionRequest(version, completeCorrections(),
                MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(outOfScope.path("personCandidates").size()).isZero();
        assertThat(outOfScope.path("warnings").toString()).contains("ENTITY_NOT_FOUND");
        assertThat(outOfScope.toString()).doesNotContain("110105198001010016", "personId", "9801");
    }

    @Test
    void previewRejectsClientSuppliedEntityPrimaryKeyFields() throws Exception {
        resetResolutionFixture();
        insertResolutionFixture();
        String taskId = createSuccessfulTask(householdToken, "resolution-primary-key-reject");
        long version = taskVersion(taskId);
        List<AiResolutionPreviewRequest.Correction> forged = new ArrayList<>(completeCorrections());
        forged.add(new AiResolutionPreviewRequest.Correction("person.personId", "9801"));
        MvcResult result = preview(taskId, resolutionRequest(version, forged, MigrationType.OUTSIDE_CITY, "110105", null));
        assertThat(result.getResponse().getStatus()).isEqualTo(400);
        assertThat(result.getResponse().getContentAsString()).contains("UNSUPPORTED_CORRECTION_FIELD");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE target_person_id=9801",
                Integer.class)).isZero();
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
        assertThat(cancelledReplay.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(cancelledReplay.getResponse().getContentAsString())
                .path("message").asText()).isEqualTo("MATERIAL_NOT_AVAILABLE_CANCELLED");
        assertThat(objectMapper.readTree(cancelledReplay.getResponse().getContentAsString()).path("data").isNull())
                .isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, cancelId)).isEqualTo("CANCELLED");
    }

    @Test
    void uploadRequestFingerprintCoversNormalizedDisplayNameAndSemanticFields() throws Exception {
        byte[] bytes = pdf("same synthetic bytes");
        String firstId = upload(householdToken, "semantic-upload-key", "proof.pdf", "application/pdf", bytes,
                "IDENTITY_CARD_FRONT").path("stageMaterialId").asText();

        MvcResult renamed = uploadRaw(householdToken, "semantic-upload-key", "renamed.pdf", "application/pdf",
                bytes, "IDENTITY_CARD_FRONT");
        assertThat(renamed.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(renamed.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo("IDEMPOTENCY_KEY_REUSED");

        MvcResult changedRole = uploadRaw(householdToken, "semantic-upload-key", "proof.pdf", "application/pdf",
                bytes, "IDENTITY_CARD_BACK");
        assertThat(changedRole.getResponse().getStatus()).isEqualTo(409);
        MvcResult changedBusiness = uploadRaw(householdToken, "semantic-upload-key", "proof.pdf", "application/pdf",
                bytes, "IDENTITY_CARD_FRONT", "MIGRATION_OUT");
        assertThat(changedBusiness.getResponse().getStatus()).isEqualTo(409);

        MvcResult equivalentUnicode = uploadRaw(householdToken, "unicode-upload-key", "cafe\u0301.pdf",
                "application/pdf", bytes, "UNKNOWN");
        assertThat(equivalentUnicode.getResponse().getStatus()).isEqualTo(201);
        JsonNode unicodeBody = objectMapper.readTree(equivalentUnicode.getResponse().getContentAsString()).path("data");
        assertThat(unicodeBody.path("originalFilename").asText()).isEqualTo("café.pdf");
        MvcResult normalizedReplay = uploadRaw(householdToken, "unicode-upload-key", "café.pdf",
                "application/pdf", bytes, "UNKNOWN");
        assertThat(normalizedReplay.getResponse().getStatus()).isEqualTo(200);
        JsonNode normalizedBody = objectMapper.readTree(normalizedReplay.getResponse().getContentAsString())
                .path("data");
        assertThat(normalizedBody.path("stageMaterialId").asText())
                .isEqualTo(unicodeBody.path("stageMaterialId").asText());
        assertThat(normalizedBody.path("originalFilename").asText()).isEqualTo("café.pdf");

        MvcResult sameFileNewKey = uploadRaw(householdToken, "same-file-different-key", "proof.pdf",
                "application/pdf", bytes, "IDENTITY_CARD_FRONT");
        assertThat(sameFileNewKey.getResponse().getStatus()).isEqualTo(201);
        assertThat(objectMapper.readTree(sameFileNewKey.getResponse().getContentAsString()).path("data")
                .path("stageMaterialId").asText()).isNotEqualTo(firstId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE sha256=?",
                Integer.class, jdbc.queryForObject("SELECT sha256 FROM ai_stage_material WHERE stage_material_id=?",
                        String.class, firstId))).isEqualTo(3);
    }

    @Test
    void sameUploadKeyIsScopedToOwnerAndUnsafeFilenamesAreRejected() throws Exception {
        byte[] bytes = pdf("user scoped synthetic data");
        MvcResult owner = uploadRaw(householdToken, "owner-scoped-upload-key", "proof.pdf", "application/pdf",
                bytes, "UNKNOWN");
        MvcResult other = uploadRaw(adminToken, "owner-scoped-upload-key", "proof.pdf", "application/pdf",
                bytes, "UNKNOWN");
        assertThat(owner.getResponse().getStatus()).isEqualTo(201);
        assertThat(other.getResponse().getStatus()).isEqualTo(201);
        String ownerId = objectMapper.readTree(owner.getResponse().getContentAsString()).path("data")
                .path("stageMaterialId").asText();
        String otherId = objectMapper.readTree(other.getResponse().getContentAsString()).path("data")
                .path("stageMaterialId").asText();
        assertThat(otherId).isNotEqualTo(ownerId);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key=?",
                Integer.class, "owner-scoped-upload-key")).isEqualTo(2);

        for (String filename : List.of("..\\escape.pdf", "folder/escape.pdf", "control\u0001.pdf", "hidden\u202E.pdf")) {
            MvcResult invalid = uploadRaw(householdToken, "unsafe-name-" + filename.hashCode(), filename,
                    "application/pdf", bytes, "UNKNOWN");
            assertThat(invalid.getResponse().getStatus()).isEqualTo(400);
            assertThat(invalid.getResponse().getContentAsString()).doesNotContain(STORAGE_ROOT, "user scoped synthetic data");
        }
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
        String stagedObject = jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId);
        MvcResult uploadReplay = uploadRaw(householdToken, "task-stage", "household-book.pdf", "application/pdf",
                pdf("synthetic household material"), "HOUSEHOLD_REGISTER_PAGE");
        assertThat(uploadReplay.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(uploadReplay.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo("MATERIAL_IN_USE");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("IN_USE");
        assertThat(storage.objectExists(stagedObject)).isTrue();
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
        MvcResult boundReplay = uploadRaw(householdToken, "bound-stage", "bound.pdf", "application/pdf",
                pdf("bound"), "UNKNOWN");
        assertThat(boundReplay.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(boundReplay.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo("MATERIAL_BOUND");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, boundId)).isEqualTo("BOUND");
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
        MvcResult expiredReplay = uploadRaw(householdToken, "expired-stage", "expired.pdf", "application/pdf",
                pdf("expired"), "UNKNOWN");
        assertThat(expiredReplay.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(expiredReplay.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo("MATERIAL_NOT_AVAILABLE_EXPIRED");
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, expiredId)).isEqualTo("EXPIRED");
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
            MvcResult a = first.get(10, TimeUnit.SECONDS);
            MvcResult b = second.get(10, TimeUnit.SECONDS);
            assertThat(a.getResponse().getStatus()).isIn(200, 201, 409);
            assertThat(b.getResponse().getStatus()).isIn(200, 201, 409);
            assertThat(List.of(a.getResponse().getStatus(), b.getResponse().getStatus())).contains(201);
            List<String> returnedIds = new ArrayList<>();
            for (MvcResult result : List.of(a, b)) {
                JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
                if (result.getResponse().getStatus() == 409) {
                    assertThat(result.getResponse().getHeader("Retry-After")).isEqualTo("1");
                    assertThat(body.path("message").asText()).startsWith("MATERIAL_UPLOAD_IN_PROGRESS");
                    assertThat(body.path("data").isNull()).isTrue();
                } else {
                    returnedIds.add(body.path("data").path("stageMaterialId").asText());
                    assertThat(body.path("data").path("status").asText()).isEqualTo("READY");
                }
            }
            assertThat(returnedIds).isNotEmpty().containsOnly(jdbc.queryForObject(
                    "SELECT stage_material_id FROM ai_stage_material WHERE idempotency_key='parallel-upload-key'",
                    String.class));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key='parallel-upload-key'",
                    Integer.class)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void uploadingReplayWaitsForReadyAndTaskAdmissionStillRequiresReady() throws Exception {
        CountDownLatch promoteEntered = new CountDownLatch(1);
        CountDownLatch releasePromote = new CountDownLatch(1);
        doAnswer(invocation -> {
            promoteEntered.countDown();
            assertThat(releasePromote.await(10, TimeUnit.SECONDS)).isTrue();
            return invocation.callRealMethod();
        }).when(storage).promote(any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Callable<MvcResult> firstCall = () -> uploadRaw(householdToken, "latched-upload-key", "latched.pdf",
                    "application/pdf", pdf("latched upload"), "ADDRESS_PROOF");
            Future<MvcResult> first = executor.submit(firstCall);
            assertThat(promoteEntered.await(10, TimeUnit.SECONDS)).isTrue();

            String stageId = jdbc.queryForObject("SELECT stage_material_id FROM ai_stage_material WHERE idempotency_key=?",
                    String.class, "latched-upload-key");
            String tempKey = jdbc.queryForObject("SELECT temp_object_key FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId);
            String objectKey = jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId);
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("UPLOADING");
            assertThat(storage.tempExists(tempKey)).isTrue();
            assertThat(storage.objectExists(objectKey)).isFalse();

            MvcResult replayWhileUploading = uploadRaw(householdToken, "latched-upload-key", "latched.pdf",
                    "application/pdf", pdf("latched upload"), "ADDRESS_PROOF");
            assertThat(replayWhileUploading.getResponse().getStatus()).isEqualTo(409);
            assertThat(replayWhileUploading.getResponse().getHeader("Retry-After")).isEqualTo("1");
            JsonNode inProgress = objectMapper.readTree(replayWhileUploading.getResponse().getContentAsString());
            assertThat(inProgress.path("message").asText()).startsWith("MATERIAL_UPLOAD_IN_PROGRESS");
            assertThat(inProgress.path("data").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key=?",
                    Integer.class, "latched-upload-key")).isEqualTo(1);

            AiTaskCreateRequest earlyTask = taskRequest(List.of(stageId), "task-before-ready", "OUTSIDE_CITY");
            MvcResult rejectedTask = createTask(householdToken, earlyTask).andExpect(status().isConflict()).andReturn();
            assertThat(objectMapper.readTree(rejectedTask.getResponse().getContentAsString()).path("message").asText())
                    .isEqualTo("MATERIAL_NOT_READY");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_task WHERE idempotency_key=?",
                    Integer.class, "task-before-ready")).isZero();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_precheck_outbox", Integer.class)).isZero();

            lifecycleReaper.reapMaterials();
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("UPLOADING");
            assertThat(storage.tempExists(tempKey)).isTrue();

            releasePromote.countDown();
            MvcResult firstResponse = first.get(10, TimeUnit.SECONDS);
            assertThat(firstResponse.getResponse().getStatus()).isEqualTo(201);
            JsonNode firstData = objectMapper.readTree(firstResponse.getResponse().getContentAsString()).path("data");
            assertThat(firstData.path("stageMaterialId").asText()).isEqualTo(stageId);
            assertThat(firstData.path("status").asText()).isEqualTo("READY");
            assertThat(storage.objectExists(objectKey)).isTrue();
            assertThat(storage.tempExists(tempKey)).isFalse();

            MvcResult replayAfterReady = uploadRaw(householdToken, "latched-upload-key", "latched.pdf",
                    "application/pdf", pdf("latched upload"), "ADDRESS_PROOF");
            assertThat(replayAfterReady.getResponse().getStatus()).isEqualTo(200);
            JsonNode replayData = objectMapper.readTree(replayAfterReady.getResponse().getContentAsString()).path("data");
            assertThat(replayData.path("stageMaterialId").asText()).isEqualTo(stageId);
            assertThat(replayData.path("status").asText()).isEqualTo("READY");
            assertThat(storage.objectExists(objectKey)).isTrue();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key=?",
                    Integer.class, "latched-upload-key")).isEqualTo(1);
        } finally {
            releasePromote.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedFirstUploadNeverMakesUploadingReplayLookSuccessful() throws Exception {
        CountDownLatch promoteEntered = new CountDownLatch(1);
        CountDownLatch releaseFailure = new CountDownLatch(1);
        doAnswer(invocation -> {
            promoteEntered.countDown();
            assertThat(releaseFailure.await(10, TimeUnit.SECONDS)).isTrue();
            throw new IOException("synthetic private upload failure");
        }).when(storage).promote(any());

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> first = executor.submit(() -> uploadRaw(householdToken, "failed-latched-key",
                    "failed-latched.pdf", "application/pdf", pdf("private synthetic failure"), "UNKNOWN"));
            assertThat(promoteEntered.await(10, TimeUnit.SECONDS)).isTrue();
            String stageId = jdbc.queryForObject("SELECT stage_material_id FROM ai_stage_material WHERE idempotency_key=?",
                    String.class, "failed-latched-key");
            String tempKey = jdbc.queryForObject("SELECT temp_object_key FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId);
            String objectKey = jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId);
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("UPLOADING");
            assertThat(storage.tempExists(tempKey)).isTrue();

            MvcResult inProgress = uploadRaw(householdToken, "failed-latched-key", "failed-latched.pdf",
                    "application/pdf", pdf("private synthetic failure"), "UNKNOWN");
            assertThat(inProgress.getResponse().getStatus()).isEqualTo(409);
            assertThat(inProgress.getResponse().getHeader("Retry-After")).isEqualTo("1");
            assertThat(objectMapper.readTree(inProgress.getResponse().getContentAsString()).path("message").asText())
                    .startsWith("MATERIAL_UPLOAD_IN_PROGRESS");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key=?",
                    Integer.class, "failed-latched-key")).isEqualTo(1);

            releaseFailure.countDown();
            MvcResult failedFirst = first.get(10, TimeUnit.SECONDS);
            assertThat(failedFirst.getResponse().getStatus()).isEqualTo(500);
            assertThat(failedFirst.getResponse().getContentAsString()).doesNotContain(STORAGE_ROOT,
                    "private synthetic failure");
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, stageId)).isEqualTo("UPLOAD_FAILED");
            assertThat(storage.tempExists(tempKey)).isFalse();
            assertThat(storage.objectExists(objectKey)).isFalse();

            MvcResult failedReplay = uploadRaw(householdToken, "failed-latched-key", "failed-latched.pdf",
                    "application/pdf", pdf("private synthetic failure"), "UNKNOWN");
            assertThat(failedReplay.getResponse().getStatus()).isEqualTo(409);
            assertThat(objectMapper.readTree(failedReplay.getResponse().getContentAsString()).path("message").asText())
                    .startsWith("MATERIAL_UPLOAD_FAILED");
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key=?",
                    Integer.class, "failed-latched-key")).isEqualTo(1);
        } finally {
            releaseFailure.countDown();
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
    void replayOfReadyRowWithMissingPrivateObjectCannotReturnSuccess() throws Exception {
        byte[] bytes = pdf("object loss synthetic fixture");
        String id = upload(householdToken, "missing-object-key", "missing.pdf", "application/pdf", bytes,
                "UNKNOWN").path("stageMaterialId").asText();
        String objectKey = jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                String.class, id);
        storage.deleteObject(objectKey);

        MvcResult replay = uploadRaw(householdToken, "missing-object-key", "missing.pdf", "application/pdf", bytes,
                "UNKNOWN");
        assertThat(replay.getResponse().getStatus()).isEqualTo(409);
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).path("message").asText())
                .isEqualTo("MATERIAL_OBJECT_MISSING");
        assertThat(objectMapper.readTree(replay.getResponse().getContentAsString()).path("data").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, id)).isEqualTo("UPLOAD_FAILED");
        assertThat(storage.objectExists(objectKey)).isFalse();
    }

    @Test
    void staleUploadingRowRecoveredFromPromotedFileKeepsIdempotencySemantics() throws Exception {
        byte[] bytes = pdf("recovery synthetic fixture");
        String id = upload(householdToken, "recovery-upload-key", "recovery.pdf", "application/pdf", bytes,
                "UNKNOWN").path("stageMaterialId").asText();
        String objectKey = jdbc.queryForObject("SELECT stored_object_key FROM ai_stage_material WHERE stage_material_id=?",
                String.class, id);
        jdbc.update("UPDATE ai_stage_material SET status='UPLOADING', temp_object_key=NULL, uploaded_at=NULL, expires_at=NULL, updated_at=?, version=version+1 WHERE stage_material_id=?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusMinutes(6)), id);

        lifecycleReaper.reapMaterials();
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, id)).isEqualTo("READY");
        assertThat(storage.objectExists(objectKey)).isTrue();

        MvcResult replay = uploadRaw(householdToken, "recovery-upload-key", "recovery.pdf", "application/pdf",
                bytes, "UNKNOWN");
        assertThat(replay.getResponse().getStatus()).isEqualTo(200);
        JsonNode data = objectMapper.readTree(replay.getResponse().getContentAsString()).path("data");
        assertThat(data.path("stageMaterialId").asText()).isEqualTo(id);
        assertThat(data.path("status").asText()).isEqualTo("READY");
        assertThat(storage.objectExists(objectKey)).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM ai_stage_material WHERE idempotency_key=?",
                Integer.class, "recovery-upload-key")).isEqualTo(1);
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
        return uploadRaw(token, key, filename, contentType, data, roleHint, "MIGRATION_IN");
    }

    private MvcResult uploadRaw(String token, String key, String filename, String contentType, byte[] data,
                                String roleHint, String businessType) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", filename, contentType, data);
        return mvc.perform(multipart("/api/ai/materials").file(file)
                        .header("Authorization", bearer(token)).header("Idempotency-Key", key)
                        .param("businessType", businessType).param("documentRoleHint", roleHint))
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

    private String createTaskWithoutProcessing(String token, String key, String migrationType) throws Exception {
        String stageId = upload(token, "stage-" + key, "resolution.pdf", "application/pdf",
                pdf("synthetic resolution document"), "UNKNOWN").path("stageMaterialId").asText();
        MvcResult accepted = createTask(token, taskRequest(List.of(stageId), key, migrationType))
                .andExpect(status().isAccepted()).andReturn();
        return objectMapper.readTree(accepted.getResponse().getContentAsString()).path("data").path("taskId").asText();
    }

    private String createSuccessfulTask(String token, String key) throws Exception {
        return createSuccessfulTask(token, key, "OUTSIDE_CITY");
    }

    private String createSuccessfulTask(String token, String key, String migrationType) throws Exception {
        String taskId = createTaskWithoutProcessing(token, key, migrationType);
        jdbc.update("UPDATE ai_precheck_task SET next_attempt_at=? WHERE task_id=? AND status='PENDING'",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusSeconds(1)), taskId);
        String body = jdbc.queryForObject("SELECT payload_json FROM ai_precheck_outbox WHERE task_id=?",
                String.class, taskId);
        AiPrecheckTaskService.EventEnvelope event = objectMapper.readValue(body, AiPrecheckTaskService.EventEnvelope.class);
        worker.process(event);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_precheck_task WHERE task_id=?", String.class, taskId))
                .isEqualTo("SUCCEEDED");
        return taskId;
    }

    private long taskVersion(String taskId) {
        return jdbc.queryForObject("SELECT version FROM ai_precheck_task WHERE task_id=?", Long.class, taskId);
    }

    private MvcResult preview(String taskId, AiResolutionPreviewRequest body) throws Exception {
        return mvc.perform(post("/api/ai/tasks/{id}/resolution-preview", taskId)
                        .header("Authorization", bearer(householdToken)).contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn();
    }

    private JsonNode previewData(String taskId, AiResolutionPreviewRequest body) throws Exception {
        MvcResult result = preview(taskId, body);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private AiResolutionPreviewRequest resolutionRequest(long version,
            List<AiResolutionPreviewRequest.Correction> corrections, MigrationType type, String region, String batch) {
        return new AiResolutionPreviewRequest("1.0", version, corrections, type, region, batch);
    }

    private List<AiResolutionPreviewRequest.Correction> completeCorrections() {
        return List.of(new AiResolutionPreviewRequest.Correction("person.name", "解析测试甲"),
                new AiResolutionPreviewRequest.Correction("person.idCard", "110105198001010016"),
                new AiResolutionPreviewRequest.Correction("household.householdNo", "S4A-H-ALPHA"));
    }

    private void resetResolutionFixture() {
        jdbc.update("DELETE FROM household_member WHERE person_id IN (9801,9802) OR household_id=9801");
        jdbc.update("DELETE FROM residence WHERE person_id IN (9801,9802) OR household_id=9801");
        jdbc.update("DELETE FROM household WHERE household_id IN (9801,9803)");
        jdbc.update("DELETE FROM person WHERE person_id IN (9801,9802)");
    }

    private void insertResolutionFixture() {
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,birth_date,status,current_status_code,"
                        + "initial_scope_region_code,version) VALUES(9801,'解析测试甲','M','110105198001010016',"
                        + "DATE '1980-01-01','正常','PENDING','110000',0)");
        jdbc.update("INSERT INTO household(household_id,household_no,address,establish_date,status,region_code,version) "
                        + "VALUES(9801,'S4A-H-ALPHA','示例市示例区测试路1号',DATE '2020-01-01','ACTIVE','110105',0)");
    }

    private <T> T withHouseholdUser(Supplier<T> action) {
        return withUser(authService.loadUser("household"), action);
    }

    private <T> T withUser(AuthenticatedUser user, Supplier<T> action) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private String sha256(String value) throws Exception {
        return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8)));
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
