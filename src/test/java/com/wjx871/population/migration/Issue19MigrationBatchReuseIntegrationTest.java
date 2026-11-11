package com.wjx871.population.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.application.ApplicationDraftCancellationListener;
import com.wjx871.population.application.BusinessApplication;
import com.wjx871.population.application.BusinessType;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Issue #19 integration test: a cancelled MIGRATION_IN draft must release its transfer batch.
 *
 * <p>All assertions describe the fixed contract. The test deliberately drives the full Spring MVC
 * + Security/JWT + Service + MyBatis/H2 stack: it never mocks the migration service and never
 * fabricates the duplicate-key failure. On the baseline implementation the main test fails because
 * the cancelled draft keeps {@code migration_in.transfer_batch_no} occupied by the global
 * {@code UNIQUE} constraint, so creating a new draft with the same batch returns 409/500 instead of
 * 201.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Issue19MigrationBatchReuseIntegrationTest.FaultInjectionConfiguration.class)
class Issue19MigrationBatchReuseIntegrationTest {

    /**
     * The normal H2 gate. The MySQL 8.4 {@code Issue19MySql84IT} subclass supplies its own
     * DynamicPropertySource and sets MYSQL84_GATE_DB_URL, so this base H2 URL is skipped there.
     */
    @DynamicPropertySource
    static void h2Datasource(DynamicPropertyRegistry properties) {
        if (System.getenv("MYSQL84_GATE_DB_URL") == null) {
            properties.add("spring.datasource.url",
                    () -> "jdbc:h2:mem:issue19_migration_batch_reuse;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        }
    }

    private static final long PERSON_OUT = 93001L;
    private static final long PERSON_TARGET_HEAD = 93002L;
    private static final long PERSON_TARGET_HEAD_2 = 93004L;
    private static final long PERSON_IN = 93003L;
    private static final long HOUSEHOLD_SOURCE = 93001L;
    private static final long HOUSEHOLD_TARGET = 93002L;
    private static final long HOUSEHOLD_TARGET_2 = 93004L;
    private static final String SOURCE_REGION = "110101";
    private static final String TARGET_REGION = "110105";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;

    private final Map<String, String> tokens = new LinkedHashMap<>();

    @BeforeEach
    void resetDatabaseAndLogin() throws Exception {
        for (String table : List.of(
                "residence_archive", "migration_in", "migration_out", "residence", "household_member",
                "application_material", "sys_approval_log", "sys_approval_request", "cancellation_record",
                "business_application", "operation_log")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM household WHERE household_id BETWEEN 93001 AND 93020");
        jdbc.update("DELETE FROM person WHERE person_id BETWEEN 93001 AND 93020");
        seedPeopleAndHouseholds();
        tokens.clear();
        for (String user : List.of("household", "approver", "admin", "viewer")) {
            String response = mvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            tokens.put(user, json.readTree(response).path("data").path("token").asText());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Main RED scenario (matrix A): completed cross-district out -> DRAFT A -> cancel -> same batch B.
    // ---------------------------------------------------------------------------------------------
    @Test
    void cancelDraftReleasesBatchAndNewDraftCanBeCreated() throws Exception {
        CompletedOut completedOut = createCompletedCrossDistrictOutByHttp();
        System.out.println("ISSUE19_RED_COMPLETED_OUT_BATCH=" + completedOut);

        MvcResult createdA = createInRequest(PERSON_OUT, HOUSEHOLD_TARGET, completedOut.batchNo(),
                "Issue19 跨区迁入草稿 A");
        assertHttp(createdA, 201, "创建迁入草稿 A");
        long draftA = data(createdA).path("applicationId").asLong();
        System.out.println("ISSUE19_RED_DRAFT_A_AFTER_CREATE=" + snapshot(draftA));

        MvcResult cancelled = mvc.perform(delete("/api/applications/" + draftA)
                        .param("version", String.valueOf(applicationVersion(draftA)))
                        .header("Authorization", bearer(token("household"))))
                .andReturn();
        assertHttp(cancelled, 200, "取消迁入草稿 A");
        System.out.println("ISSUE19_RED_DRAFT_A_AFTER_CANCEL=" + snapshot(draftA));
        System.out.println("ISSUE19_RED_CANCEL_APPROVAL_LOGS=" + jdbc.queryForList(
                "SELECT action,from_status,to_status,operator_user_id FROM sys_approval_log "
                        + "WHERE application_id=? ORDER BY log_id", draftA));
        System.out.println("ISSUE19_RED_CANCEL_OPERATION_LOGS=" + jdbc.queryForList(
                "SELECT operation_type,module_name,request_path,operation_result FROM operation_log "
                        + "WHERE target_table='business_application' AND target_id=?", String.valueOf(draftA)));
        System.out.println("ISSUE19_RED_OPERATION_LOG_TOTAL=" + jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log", Integer.class));

        dumpH2MigrationInMetadata();
        printDuplicateConstraintDiagnostic(PERSON_OUT, HOUSEHOLD_TARGET, completedOut.batchNo());

        MvcResult createdB = createInRequest(PERSON_OUT, HOUSEHOLD_TARGET, completedOut.batchNo(),
                "Issue19 跨区迁入草稿 B");
        int statusB = createdB.getResponse().getStatus();
        String bodyB = createdB.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("ISSUE19_RED_DRAFT_B_HTTP status=" + statusB + " body=" + bodyB);
        System.out.println("ISSUE19_RED_AFTER_B businessApplicationCount=" + jdbc.queryForObject(
                "SELECT COUNT(*) FROM business_application WHERE business_type='MIGRATION_IN'", Integer.class)
                + " migrationInRowsForBatch=" + jdbc.queryForObject(
                "SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=?", Integer.class,
                completedOut.batchNo()));

        assertThat(statusB)
                .withFailMessage("取消草稿 A 后，用同一批次号创建迁入草稿 B 应返回 201，实际 HTTP %s，响应体: %s",
                        statusB, bodyB)
                .isEqualTo(201);

        long draftB = data(createdB).path("applicationId").asLong();
        assertThat(statusOf(draftB)).isEqualTo("DRAFT");
        assertThat(migrationStatusOf(draftB)).isEqualTo("DRAFT");
        assertThat(migrationBatchOf(draftB)).isEqualTo(completedOut.batchNo());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=?",
                Integer.class, completedOut.batchNo())).isEqualTo(2);
        assertThat(statusOf(draftA)).isEqualTo("CANCELLED");
        assertThat(migrationStatusOf(draftA)).isEqualTo("CANCELLED");
        assertThat(migrationBatchOf(draftA)).isEqualTo(completedOut.batchNo());
    }

    @Test
    void failedDuplicateDraftCreationLeavesNoOrphanApplicationOrDetail() throws Exception {
        String batch = syntheticCompletedOutBatch(PERSON_IN, "ORPHAN");
        createIn(PERSON_IN, HOUSEHOLD_TARGET, batch, "Issue19 占用中的草稿");
        int applicationsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);
        int detailsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM migration_in", Integer.class);
        MvcResult second = createInRequest(PERSON_IN, HOUSEHOLD_TARGET_2, batch, "Issue19 重复创建应回滚");
        int secondStatus = second.getResponse().getStatus();
        String secondBody = second.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("ISSUE19_DUPLICATE_CREATE_HTTP=" + secondStatus + " body=" + secondBody);
        assertThat(secondStatus).isEqualTo(409);
        assertThat(secondBody).contains("迁移批次");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class))
                .isEqualTo(applicationsBefore);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in", Integer.class))
                .isEqualTo(detailsBefore);
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix D part 1: the cancelled application/detail/log must stay traceable after releasing.
    // ---------------------------------------------------------------------------------------------
    @Test
    void cancelledMigrationRecordAndLogsRemainTraceable() throws Exception {
        CompletedOut completedOut = createCompletedCrossDistrictOutByHttp();
        MvcResult created = createInRequest(PERSON_OUT, HOUSEHOLD_TARGET, completedOut.batchNo(),
                "Issue19 取消可追溯");
        assertHttp(created, 201, "创建迁入草稿 A（可追溯场景）");
        long draftA = data(created).path("applicationId").asLong();

        MvcResult cancelled = mvc.perform(delete("/api/applications/" + draftA)
                        .param("version", String.valueOf(applicationVersion(draftA)))
                        .header("Authorization", bearer(token("household"))))
                .andReturn();
        assertHttp(cancelled, 200, "取消迁入草稿 A（可追溯场景）");

        assertThat(statusOf(draftA)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, draftA)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT transfer_batch_no FROM migration_in WHERE application_id=?",
                String.class, draftA)).isEqualTo(completedOut.batchNo());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? "
                        + "AND action='CANCEL' AND from_status='DRAFT' AND to_status='CANCELLED'",
                Integer.class, draftA)).isOne();

        MvcResult detail = mvc.perform(get("/api/migrations/applications/" + draftA)
                        .header("Authorization", bearer(token("household"))))
                .andReturn();
        assertHttp(detail, 200, "查询取消后的迁入详情");
        JsonNode detailData = data(detail);
        assertThat(detailData.path("application").path("status").asText()).isEqualTo("CANCELLED");
        assertThat(detailData.path("migrationIn").path("businessStatus").asText()).isEqualTo("CANCELLED");
        assertThat(detailData.path("migrationIn").path("transferBatchNo").asText())
                .isEqualTo(completedOut.batchNo());
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix C: a completed migration permanently consumes its batch.
    // ---------------------------------------------------------------------------------------------
    @Test
    void completedMigrationConsumesBatchPermanently() throws Exception {
        CompletedOut completedOut = createCompletedCrossDistrictOutByHttp();
        long completedIn = createIn(PERSON_OUT, HOUSEHOLD_TARGET, completedOut.batchNo(), "Issue19 完成消费");
        insertVerifiedMaterials(completedIn, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submit(completedIn);
        approveApprovalOf(completedIn);
        int version = jdbc.queryForObject("SELECT version FROM migration_in WHERE application_id=?",
                Integer.class, completedIn);
        assertHttp(executeRequest("in", completedIn, version), 200, "执行迁入并消费批次");

        System.out.println("ISSUE19_RED_COMPLETED_IN=" + snapshot(completedIn));
        assertThat(statusOf(completedIn)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, completedIn)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=? "
                + "AND business_status='COMPLETED'", Integer.class, completedOut.batchNo())).isOne();

        MvcResult second = createInRequest(PERSON_IN, HOUSEHOLD_TARGET, completedOut.batchNo(),
                "Issue19 已消费批次再次创建");
        int secondStatus = second.getResponse().getStatus();
        String secondBody = second.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("ISSUE19_RED_COMPLETED_BATCH_REUSE_HTTP status=" + secondStatus
                + " body=" + secondBody);
        assertThat(secondStatus).withFailMessage("已完成迁入的批次不可再创建，应返回 409，实际 %s，响应体: %s",
                secondStatus, secondBody).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE target_person_id=?",
                Integer.class, PERSON_IN)).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Observation matrix: DRAFT / UNDER_REVIEW / APPROVED / REJECTED / WITHDRAWN / RETURNED all keep
    // occupying the batch in the baseline (the fix must not widen the release scope beyond CANCELLED).
    // Completed-out rows are inserted synthetically only for this observation matrix; the main RED
    // scenario above uses a full HTTP migration-out execution.
    // ---------------------------------------------------------------------------------------------
    @Test
    void existingStatusOccupancyObservationMatrix() throws Exception {
        for (String targetStatus : List.of("DRAFT", "UNDER_REVIEW", "APPROVED", "REJECTED", "WITHDRAWN",
                "RETURNED")) {
            String batch = syntheticCompletedOutBatch(PERSON_IN, targetStatus);
            MvcResult created = createInRequest(PERSON_IN, HOUSEHOLD_TARGET, batch,
                    "Issue19 状态占用观察 " + targetStatus);
            assertHttp(created, 201, "创建状态观察草稿 " + targetStatus);
            long applicationId = data(created).path("applicationId").asLong();
            driveToStatus(applicationId, targetStatus);

            String expectedApplicationStatus = targetStatus;
            String expectedMigrationStatus = "RETURNED".equals(targetStatus) ? "APPROVED" : targetStatus;
            assertThat(statusOf(applicationId)).isEqualTo(expectedApplicationStatus);
            assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                    String.class, applicationId)).isEqualTo(expectedMigrationStatus);

            MvcResult second = createInRequest(PERSON_IN, HOUSEHOLD_TARGET, batch,
                    "Issue19 状态占用第二次创建 " + targetStatus);
            int secondStatus = second.getResponse().getStatus();
            String secondBody = second.getResponse().getContentAsString(StandardCharsets.UTF_8);
            System.out.println("ISSUE19_RED_STATUS_OCCUPANCY status=" + targetStatus
                    + " appStatus=" + statusOf(applicationId)
                    + " migrationStatus=" + expectedMigrationStatus
                    + " secondHttp=" + secondStatus + " secondBody=" + secondBody);
            assertThat(secondStatus)
                    .withFailMessage("%s 状态的迁入仍应占用批次，第二次创建应为 409，实际 %s，响应体: %s",
                            targetStatus, secondStatus, secondBody)
                    .isEqualTo(409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=?",
                    Integer.class, batch)).isOne();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix B (edit branch): changing a DRAFT from batch1 to batch2 releases batch1 and claims batch2.
    // ---------------------------------------------------------------------------------------------
    @Test
    void updateDraftReleasesOldBatchAndClaimsNewBatch() throws Exception {
        String oldBatch = syntheticCompletedOutBatch(PERSON_IN, "EDIT-OLD");
        String newBatch = syntheticCompletedOutBatch(PERSON_IN, "EDIT-NEW");
        long draft = createIn(PERSON_IN, HOUSEHOLD_TARGET, oldBatch, "Issue19 编辑换批次");
        int version = jdbc.queryForObject("SELECT version FROM migration_in WHERE application_id=?",
                Integer.class, draft);
        MvcResult updated = mvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put(
                                        "/api/migrations/in/applications/" + draft)
                                .header("Authorization", bearer(token("household")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(inBody(PERSON_IN, HOUSEHOLD_TARGET, newBatch, "Issue19 编辑换批次", version)))
                .andReturn();
        assertHttp(updated, 200, "编辑迁入草稿换批次");
        assertThat(migrationBatchOf(draft)).isEqualTo(newBatch);

        MvcResult reuseOld = createInRequest(PERSON_IN, HOUSEHOLD_TARGET, oldBatch, "Issue19 旧批次复用");
        assertHttp(reuseOld, 201, "旧批次释放后重新创建");
        MvcResult reuseNew = createInRequest(PERSON_IN, HOUSEHOLD_TARGET_2, newBatch, "Issue19 新批次重复");
        int reuseNewStatus = reuseNew.getResponse().getStatus();
        System.out.println("ISSUE19_EDIT_BATCH old=" + oldBatch + " new=" + newBatch
                + " reuseOldHttp=" + reuseOld.getResponse().getStatus() + " reuseNewHttp=" + reuseNewStatus);
        assertThat(reuseNewStatus).isEqualTo(409);
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix E: a completed batch linked to the wrong person is rejected at execution.
    // ---------------------------------------------------------------------------------------------
    @Test
    void batchMismatchIsRejectedOnExecution() throws Exception {
        CompletedOut out = createCompletedCrossDistrictOutByHttp();
        long draft = createIn(PERSON_IN, HOUSEHOLD_TARGET, out.batchNo(), "Issue19 错人员批次");
        insertVerifiedMaterials(draft, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submit(draft);
        approveApprovalOf(draft);
        int version = jdbc.queryForObject("SELECT version FROM migration_in WHERE application_id=?",
                Integer.class, draft);
        MvcResult executed = executeRequest("in", draft, version);
        assertHttp(executed, 409, "错人员批次执行");
        assertThat(statusOf(draft)).isEqualTo("APPROVED");
        assertThat(migrationStatusOf(draft)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?", Integer.class,
                PERSON_IN)).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix F: recreating after cancellation does not bypass materials/approval/execute permission.
    // ---------------------------------------------------------------------------------------------
    @Test
    void cancelRecreateCannotBypassMaterialsApprovalOrExecutePermission() throws Exception {
        CompletedOut out = createCompletedCrossDistrictOutByHttp();
        long draftA = createIn(PERSON_OUT, HOUSEHOLD_TARGET, out.batchNo(), "Issue19 绕过校验 A");
        assertHttp(deleteDraft(draftA), 200, "取消 A（绕过校验场景）");
        MvcResult createdB = createInRequest(PERSON_OUT, HOUSEHOLD_TARGET, out.batchNo(), "Issue19 绕过校验 B");
        assertHttp(createdB, 201, "取消后创建 B");
        long draftB = data(createdB).path("applicationId").asLong();

        MvcResult executeBeforeSubmit = executeRequest("in", draftB, 0);
        assertHttp(executeBeforeSubmit, 409, "未提交直接执行");
        MvcResult submitWithoutMaterials = mvc.perform(post("/api/applications/" + draftB + "/submit")
                        .header("Authorization", bearer(token("household"))))
                .andReturn();
        assertHttp(submitWithoutMaterials, 409, "缺材料提交");
        MvcResult executeWithoutPermission = mvc.perform(
                        post("/api/migrations/in/applications/" + draftB + "/execute")
                                .header("Authorization", bearer(token("viewer")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"version\":0}"))
                .andReturn();
        assertHttp(executeWithoutPermission, 403, "无执行权限执行");
        assertThat(statusOf(draftB)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?", Integer.class,
                PERSON_OUT)).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix G: two real HTTP requests concurrently create the same batch; only one may succeed.
    // ---------------------------------------------------------------------------------------------
    @Test
    void concurrentClaimsAllowOnlyOneActiveDraft() throws Exception {
        CompletedOut out = createCompletedCrossDistrictOutByHttp();
        String bodyOne = inBody(PERSON_OUT, HOUSEHOLD_TARGET, out.batchNo(), "Issue19 并发创建一", null);
        String bodyTwo = inBody(PERSON_OUT, HOUSEHOLD_TARGET_2, out.batchNo(), "Issue19 并发创建二", null);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> first = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mvc.perform(post("/api/migrations/in/applications")
                                .header("Authorization", bearer(token("household")))
                                .contentType(MediaType.APPLICATION_JSON).content(bodyOne))
                        .andReturn().getResponse().getStatus();
            };
            Callable<Integer> second = () -> {
                ready.countDown();
                start.await(5, TimeUnit.SECONDS);
                return mvc.perform(post("/api/migrations/in/applications")
                                .header("Authorization", bearer(token("admin")))
                                .contentType(MediaType.APPLICATION_JSON).content(bodyTwo))
                        .andReturn().getResponse().getStatus();
            };
            Future<Integer> firstFuture = executor.submit(first);
            Future<Integer> secondFuture = executor.submit(second);
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            int firstStatus = firstFuture.get(15, TimeUnit.SECONDS);
            int secondStatus = secondFuture.get(15, TimeUnit.SECONDS);
            System.out.println("ISSUE19_CONCURRENT_CLAIM statuses=" + List.of(firstStatus, secondStatus));
            assertThat(List.of(firstStatus, secondStatus)).containsExactlyInAnyOrder(201, 409);
        } finally {
            executor.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=? "
                        + "AND business_status<>'CANCELLED'", Integer.class, out.batchNo())).isOne();
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix H: cancel vs create interleaving must never leave two active claims.
    // ---------------------------------------------------------------------------------------------
    @Test
    void cancelAndCreateInterleavingHasNoDoubleActiveClaim() throws Exception {
        for (int round = 0; round < 2; round++) {
            String batch = syntheticCompletedOutBatch(PERSON_IN, "INTERLEAVE-" + round);
            long draftA = createIn(PERSON_IN, HOUSEHOLD_TARGET, batch, "Issue19 交错 A " + round);
            String bodyB = inBody(PERSON_IN, HOUSEHOLD_TARGET_2, batch, "Issue19 交错 B " + round, null);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            int cancelStatus;
            int createStatus;
            try {
                Callable<Integer> cancel = () -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return mvc.perform(delete("/api/applications/" + draftA)
                                    .param("version", String.valueOf(applicationVersion(draftA)))
                                    .header("Authorization", bearer(token("household"))))
                            .andReturn().getResponse().getStatus();
                };
                Callable<Integer> create = () -> {
                    ready.countDown();
                    start.await(5, TimeUnit.SECONDS);
                    return mvc.perform(post("/api/migrations/in/applications")
                                    .header("Authorization", bearer(token("household")))
                                    .contentType(MediaType.APPLICATION_JSON).content(bodyB))
                            .andReturn().getResponse().getStatus();
                };
                Future<Integer> cancelFuture = executor.submit(cancel);
                Future<Integer> createFuture = executor.submit(create);
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();
                cancelStatus = cancelFuture.get(15, TimeUnit.SECONDS);
                createStatus = createFuture.get(15, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
            }
            System.out.println("ISSUE19_INTERLEAVE round=" + round + " cancelHttp=" + cancelStatus
                    + " createHttp=" + createStatus);
            assertThat(cancelStatus).isEqualTo(200);
            assertThat(createStatus).isIn(201, 409);
            assertThat(statusOf(draftA)).isEqualTo("CANCELLED");
            int active = jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=? "
                    + "AND business_status<>'CANCELLED'", Integer.class, batch);
            assertThat(active).isEqualTo(createStatus == 201 ? 1 : 0);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix I: a real failure in the draft-cancellation listener must roll back BOTH tables.
    // ---------------------------------------------------------------------------------------------
    @Test
    void cancellationListenerFailureRollsBackApplicationAndDetail() throws Exception {
        CompletedOut out = createCompletedCrossDistrictOutByHttp();
        long draft = createIn(PERSON_OUT, HOUSEHOLD_TARGET, out.batchNo(), "ISSUE19_CANCEL_FAIL");
        MvcResult cancelled = deleteDraft(draft);
        System.out.println("ISSUE19_CANCEL_FAILURE_HTTP=" + cancelled.getResponse().getStatus()
                + " body=" + cancelled.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(cancelled.getResponse().getStatus()).isEqualTo(500);
        assertThat(statusOf(draft)).isEqualTo("DRAFT");
        assertThat(migrationStatusOf(draft)).isEqualTo("DRAFT");
        assertThat(migrationBatchOf(draft)).isEqualTo(out.batchNo());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? "
                + "AND action='CANCEL'", Integer.class, draft)).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Matrix J: repeated cancel and non-DRAFT cancel are rejected without releasing anything new.
    // ---------------------------------------------------------------------------------------------
    @Test
    void repeatedCancelAndNonDraftCancelAreRejected() throws Exception {
        CompletedOut out = createCompletedCrossDistrictOutByHttp();
        long draftA = createIn(PERSON_OUT, HOUSEHOLD_TARGET, out.batchNo(), "Issue19 重复取消");
        assertHttp(deleteDraft(draftA), 200, "第一次取消");
        assertHttp(deleteDraft(draftA), 409, "重复取消");
        assertThat(statusOf(draftA)).isEqualTo("CANCELLED");
        assertThat(migrationStatusOf(draftA)).isEqualTo("CANCELLED");
        assertThat(migrationBatchOf(draftA)).isEqualTo(out.batchNo());

        String secondBatch = syntheticCompletedOutBatch(PERSON_OUT, "NON-DRAFT");
        long draftB = createIn(PERSON_OUT, HOUSEHOLD_TARGET, secondBatch, "Issue19 非草稿取消");
        insertVerifiedMaterials(draftB, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submit(draftB);
        assertThat(statusOf(draftB)).isEqualTo("UNDER_REVIEW");
        assertHttp(deleteDraft(draftB), 409, "非草稿取消");
        assertThat(statusOf(draftB)).isEqualTo("UNDER_REVIEW");
        assertThat(migrationStatusOf(draftB)).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=? "
                + "AND business_status<>'CANCELLED'", Integer.class, secondBatch)).isOne();
    }

    // ---------------------------------------------------------------------------------------------
    // Opt-in safety: generic/specialized non-migration drafts must still cancel normally and must
    // never receive a CANCELLED broadcast (no CANCELLED value exists in their status enums).
    // ---------------------------------------------------------------------------------------------
    @Test
    void genericDraftCancellationDoesNotBroadcastToSpecializedListeners() throws Exception {
        MvcResult created = mvc.perform(post("/api/applications")
                        .header("Authorization", bearer(token("household")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessType\":\"GENERAL_SERVICE\",\"title\":\"Issue19 通用草稿\","
                                + "\"reason\":\"验证取消不广播\",\"remark\":\"opt-in check\"}"))
                .andReturn();
        assertHttp(created, 201, "创建通用业务草稿");
        long draft = data(created).path("applicationId").asLong();
        assertHttp(deleteDraft(draft), 200, "取消通用业务草稿");
        assertThat(statusOf(draft)).isEqualTo("CANCELLED");
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private void seedPeopleAndHouseholds() {
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) "
                        + "VALUES(93001,'迁出人甲','1','110101199001010011','北京市东城区旧址','正常','REGISTERED'),"
                        + "(93002,'目标户户主','1','110105199002020022','北京市朝阳区目标地址','正常','REGISTERED'),"
                        + "(93003,'待迁入乙','2','110101199303030033',NULL,'正常','PENDING'),"
                        + "(93004,'目标户户主二','1','110105199404040044','北京市朝阳区目标地址二','正常','REGISTERED')");
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,"
                        + "establish_date,status,version) VALUES"
                        + "(93001,'H-93001',93001,'北京市东城区旧址','110101',DATE '2020-01-01','ACTIVE',0),"
                        + "(93002,'H-93002',93002,'北京市朝阳区目标地址','110105',DATE '2020-01-01','ACTIVE',0),"
                        + "(93004,'H-93004',93004,'北京市朝阳区目标地址二','110105',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) "
                        + "VALUES(93001,93001,'HEAD',DATE '2020-01-01','ACTIVE',0),"
                        + "(93002,93002,'HEAD',DATE '2020-01-01','ACTIVE',0),"
                        + "(93004,93004,'HEAD',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,"
                        + "register_type_code,register_date,start_date,status,created_by,version) VALUES"
                        + "(93001,93001,'北京市东城区旧址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0),"
                        + "(93002,93002,'北京市朝阳区目标地址','110105','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0),"
                        + "(93004,93004,'北京市朝阳区目标地址二','110105','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)");
    }

    private CompletedOut createCompletedCrossDistrictOutByHttp() throws Exception {
        String body = "{\"personId\":" + PERSON_OUT
                + ",\"migrationType\":\"IN_CITY_CROSS_DISTRICT\""
                + ",\"toRegionCode\":\"" + TARGET_REGION + "\""
                + ",\"toAddress\":\"北京市朝阳区新址\""
                + ",\"outDate\":\"2026-11-01\""
                + ",\"reason\":\"同市跨区迁出\""
                + ",\"title\":\"Issue19 跨区迁出\"}";
        MvcResult created = mvc.perform(post("/api/migrations/out/applications")
                        .header("Authorization", bearer(token("household")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn();
        assertHttp(created, 201, "HTTP 创建同市跨区迁出申请");
        long applicationId = data(created).path("applicationId").asLong();
        insertVerifiedMaterials(applicationId, "IDENTITY_PROOF", "HOUSEHOLD_BOOK");
        submit(applicationId);
        approveApprovalOf(applicationId);
        int version = jdbc.queryForObject("SELECT version FROM migration_out WHERE application_id=?",
                Integer.class, applicationId);
        assertHttp(executeRequest("out", applicationId, version), 200, "HTTP 执行同市跨区迁出");

        Map<String, Object> row = jdbc.queryForMap("SELECT out_id,application_id,person_id,migration_type,"
                + "from_region_code,to_region_code,business_status,transfer_batch_no,version "
                + "FROM migration_out WHERE application_id=?", applicationId);
        assertThat(row.get("business_status")).isEqualTo("COMPLETED");
        assertThat(row.get("migration_type")).isEqualTo("IN_CITY_CROSS_DISTRICT");
        assertThat(row.get("to_region_code")).isEqualTo(TARGET_REGION);
        String batch = String.valueOf(row.get("transfer_batch_no"));
        assertThat(batch).startsWith("TR-");
        System.out.println("ISSUE19_RED_COMPLETED_OUT_ROW=" + row);
        return new CompletedOut(applicationId, batch);
    }

    private MvcResult createInRequest(long personId, long householdId, String batchNo, String title)
            throws Exception {
        return mvc.perform(post("/api/migrations/in/applications")
                        .header("Authorization", bearer(token("household")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(inBody(personId, householdId, batchNo, title, null)))
                .andReturn();
    }

    private String inBody(long personId, long householdId, String batchNo, String title, Integer version) {
        return "{\"personId\":" + personId
                + ",\"migrationType\":\"IN_CITY_CROSS_DISTRICT\""
                + ",\"fromRegionCode\":\"" + SOURCE_REGION + "\""
                + ",\"fromAddress\":\"北京市东城区旧址\""
                + ",\"toRegionCode\":\"" + TARGET_REGION + "\""
                + ",\"toHouseholdId\":" + householdId
                + ",\"inDate\":\"2026-11-02\""
                + ",\"reason\":\"同市跨区迁入\""
                + ",\"title\":\"" + title + "\""
                + ",\"transferBatchNo\":\"" + batchNo + "\""
                + (version == null ? "" : ",\"version\":" + version)
                + "}";
    }

    private MvcResult deleteDraft(long applicationId) throws Exception {
        return mvc.perform(delete("/api/applications/" + applicationId)
                        .param("version", String.valueOf(applicationVersion(applicationId)))
                        .header("Authorization", bearer(token("household"))))
                .andReturn();
    }

    private int applicationVersion(long applicationId) {
        return jdbc.queryForObject("SELECT version FROM business_application WHERE application_id=?", Integer.class, applicationId);
    }

    private String migrationStatusOf(long applicationId) {
        return jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                String.class, applicationId);
    }

    private String migrationBatchOf(long applicationId) {
        return jdbc.queryForObject("SELECT transfer_batch_no FROM migration_in WHERE application_id=?",
                String.class, applicationId);
    }

    private long createIn(long personId, long householdId, String batchNo, String title) throws Exception {
        MvcResult result = createInRequest(personId, householdId, batchNo, title);
        assertHttp(result, 201, "创建迁入草稿 " + title);
        return data(result).path("applicationId").asLong();
    }

    private void insertVerifiedMaterials(long applicationId, String... materialTypes) {
        for (String materialType : materialTypes) {
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,"
                            + "original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,"
                            + "required_flag,verify_status,verify_user_id,uploaded_by) "
                            + "VALUES(?,?,?,'synthetic.pdf',?,'h2-synthetic','application/pdf',1,?,1,'VERIFIED',7,6)",
                    applicationId, materialType, materialType, UUID.randomUUID() + ".pdf",
                    UUID.randomUUID().toString().replace("-", "")
                            + UUID.randomUUID().toString().replace("-", ""));
        }
    }

    private void submit(long applicationId) throws Exception {
        MvcResult result = mvc.perform(post("/api/applications/" + applicationId + "/submit")
                        .header("Authorization", bearer(token("household"))))
                .andReturn();
        assertHttp(result, 200, "提交申请 " + applicationId);
    }

    private void approveApprovalOf(long applicationId) throws Exception {
        long approvalId = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request "
                + "WHERE application_id=?", Long.class, applicationId);
        MvcResult result = mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", bearer(token("approver")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\",\"version\":0}"))
                .andReturn();
        assertHttp(result, 200, "审批通过 " + approvalId);
    }

    private MvcResult executeRequest(String direction, long applicationId, int version) throws Exception {
        return mvc.perform(post("/api/migrations/" + direction + "/applications/" + applicationId + "/execute")
                        .header("Authorization", bearer(token("household")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                .andReturn();
    }

    private void driveToStatus(long applicationId, String targetStatus) throws Exception {
        if ("DRAFT".equals(targetStatus)) {
            return;
        }
        insertVerifiedMaterials(applicationId, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submit(applicationId);
        if ("UNDER_REVIEW".equals(targetStatus)) {
            return;
        }
        long approvalId = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request "
                + "WHERE application_id=?", Long.class, applicationId);
        switch (targetStatus) {
            case "APPROVED" -> approveApprovalOf(applicationId);
            case "REJECTED" -> {
                MvcResult result = mvc.perform(post("/api/approvals/" + approvalId + "/reject")
                                .header("Authorization", bearer(token("approver")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"材料不符合要求\",\"version\":0}"))
                        .andReturn();
                assertHttp(result, 200, "审批驳回 " + approvalId);
            }
            case "WITHDRAWN" -> {
                MvcResult result = mvc.perform(post("/api/applications/" + applicationId + "/withdraw")
                                .header("Authorization", bearer(token("household"))))
                        .andReturn();
                assertHttp(result, 200, "撤回申请 " + applicationId);
            }
            case "RETURNED" -> {
                approveApprovalOf(applicationId);
                int applicationVersion = jdbc.queryForObject("SELECT version FROM business_application "
                        + "WHERE application_id=?", Integer.class, applicationId);
                MvcResult result = mvc.perform(post("/api/applications/" + applicationId + "/return")
                                .header("Authorization", bearer(token("household")))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"comment\":\"请补充材料\",\"version\":" + applicationVersion + "}"))
                        .andReturn();
                assertHttp(result, 200, "退回申请 " + applicationId);
            }
            default -> throw new IllegalArgumentException("Unsupported target status: " + targetStatus);
        }
    }

    private String syntheticCompletedOutBatch(long personId, String label) {
        String applicationNo = "APP-OBS-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,"
                        + "status,reason,version) VALUES(?, 'MIGRATION_OUT', ?, 6, 'COMPLETED', 'synthetic', 0)",
                applicationNo, "Issue19 synthetic completed out " + label);
        long applicationId = jdbc.queryForObject("SELECT application_id FROM business_application "
                + "WHERE application_no=?", Long.class, applicationNo);
        String batch = "TR-OBS-" + label + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        jdbc.update("INSERT INTO migration_out(application_id,person_id,migration_type,from_region_code,"
                        + "from_household_id,from_address_snapshot,to_region_code,to_address,out_date,reason,"
                        + "transfer_batch_no,business_status,operator_id,executed_at,version) "
                        + "VALUES(?,?,'IN_CITY_CROSS_DISTRICT',?,?,?,?,?,?,?,?,'COMPLETED',6,CURRENT_TIMESTAMP,0)",
                applicationId, personId, SOURCE_REGION, HOUSEHOLD_SOURCE, "synthetic source", TARGET_REGION,
                "synthetic target", java.sql.Date.valueOf("2026-11-01"), "synthetic out", batch);
        return batch;
    }

    private void dumpH2MigrationInMetadata() {
        if (!isH2Database()) {
            return;
        }
        System.out.println("ISSUE19_H2_VERSION=" + jdbc.queryForObject("SELECT H2VERSION()", String.class));
        System.out.println("ISSUE19_H2_MIGRATION_IN_COLUMNS=" + jdbc.queryForList(
                "SELECT COLUMN_NAME,DATA_TYPE,CHARACTER_MAXIMUM_LENGTH,IS_NULLABLE "
                        + "FROM INFORMATION_SCHEMA.COLUMNS WHERE UPPER(TABLE_NAME)='MIGRATION_IN' "
                        + "ORDER BY ORDINAL_POSITION"));
        System.out.println("ISSUE19_H2_MIGRATION_IN_CONSTRAINTS=" + jdbc.queryForList(
                "SELECT CONSTRAINT_NAME,CONSTRAINT_TYPE FROM INFORMATION_SCHEMA.TABLE_CONSTRAINTS "
                        + "WHERE UPPER(TABLE_NAME)='MIGRATION_IN' ORDER BY CONSTRAINT_TYPE,CONSTRAINT_NAME"));
        System.out.println("ISSUE19_H2_MIGRATION_IN_INDEXES=" + jdbc.queryForList(
                "SELECT * FROM INFORMATION_SCHEMA.INDEXES WHERE UPPER(TABLE_NAME)='MIGRATION_IN' "
                        + "ORDER BY INDEX_NAME"));
        System.out.println("ISSUE19_H2_MIGRATION_IN_INDEX_COLUMNS=" + jdbc.queryForList(
                "SELECT * FROM INFORMATION_SCHEMA.INDEX_COLUMNS WHERE UPPER(TABLE_NAME)='MIGRATION_IN' "
                        + "ORDER BY INDEX_NAME,ORDINAL_POSITION"));
    }

    /** Keep the H2-only diagnostic out of the real MySQL gate subclass. */
    private boolean isH2Database() {
        var dataSource = jdbc.getDataSource();
        if (dataSource == null) {
            return false;
        }
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(java.util.Locale.ROOT).contains("h2");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to determine test database product", exception);
        }
    }

    /**
     * Supporting diagnostic only: a real direct INSERT that hits the same H2 unique constraint as the
     * HTTP path, so the RED evidence contains the database exception stack (the HTTP handler maps
     * DuplicateKeyException to a plain 409 and does not log it). This is NOT used to fake the failure:
     * the actual RED failure is the real HTTP request below.
     */
    private void printDuplicateConstraintDiagnostic(long personId, long householdId, String batch) {
        String applicationNo = "APP-DIAG-" + UUID.randomUUID().toString().replace("-", "")
                .substring(0, 12).toUpperCase();
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,"
                        + "status,reason,version) VALUES(?, 'MIGRATION_IN', 'Issue19 duplicate diagnostic', 6, "
                        + "'DRAFT', 'diagnostic', 0)", applicationNo);
        Long dummyApplicationId = jdbc.queryForObject("SELECT application_id FROM business_application "
                + "WHERE application_no=?", Long.class, applicationNo);
        try {
            jdbc.update("INSERT INTO migration_in(application_id,person_id,migration_type,from_region_code,"
                            + "from_address,to_region_code,to_household_id,in_date,reason,transfer_batch_no,"
                            + "business_status,version) VALUES(?,?,'IN_CITY_CROSS_DISTRICT',?,?,?,?,?,?,?,?,0)",
                    dummyApplicationId, personId, SOURCE_REGION, "diagnostic source", TARGET_REGION,
                    householdId, java.sql.Date.valueOf("2026-11-02"), "diagnostic", batch, "DRAFT");
            System.out.println("ISSUE19_DIRECT_DUPLICATE unexpected-success");
        } catch (DataIntegrityViolationException exception) {
            System.out.println("ISSUE19_DIRECT_DUPLICATE exception=" + exception.getClass().getName()
                    + " root=" + exception.getMostSpecificCause().getMessage());
            exception.printStackTrace(System.out);
        } finally {
            jdbc.update("DELETE FROM migration_in WHERE application_id=?", dummyApplicationId);
            jdbc.update("DELETE FROM business_application WHERE application_id=?", dummyApplicationId);
        }
    }

    private Map<String, Object> snapshot(long applicationId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("business_application.status", statusOf(applicationId));
        snapshot.put("migration_in", jdbc.queryForMap("SELECT in_id,business_status,transfer_batch_no,"
                + "version FROM migration_in WHERE application_id=?", applicationId));
        snapshot.put("approved_logs", jdbc.queryForList("SELECT action,from_status,to_status FROM sys_approval_log "
                + "WHERE application_id=? ORDER BY log_id", applicationId));
        return snapshot;
    }

    private String statusOf(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId);
    }

    private String token(String username) {
        return tokens.get(username);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private void assertHttp(MvcResult result, int expected, String action) throws Exception {
        int status = result.getResponse().getStatus();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        System.out.println("ISSUE19_HTTP action=" + action + " status=" + status + " body=" + body);
        assertThat(status).withFailMessage("%s 期望 HTTP %s，实际 %s，响应体: %s", action, expected, status, body)
                .isEqualTo(expected);
    }

    @TestConfiguration
    static class FaultInjectionConfiguration {
        @Bean
        ApplicationDraftCancellationListener failingDraftCancellationListener() {
            return new ApplicationDraftCancellationListener() {
                @Override
                public boolean supports(BusinessType businessType) {
                    return businessType == BusinessType.MIGRATION_IN;
                }

                @Override
                public void onDraftCancelled(BusinessApplication application) {
                    if (application.getTitle() != null && application.getTitle().contains("ISSUE19_CANCEL_FAIL")) {
                        throw new IllegalStateException("simulated draft cancellation listener failure");
                    }
                }
            };
        }
    }

    private record CompletedOut(long applicationId, String batchNo) {
    }
}
