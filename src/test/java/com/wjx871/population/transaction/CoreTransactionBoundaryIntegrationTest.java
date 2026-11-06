package com.wjx871.population.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.ai.precheck.AiConfirmService;
import com.wjx871.population.ai.precheck.AiPrecheckProperties;
import com.wjx871.population.ai.precheck.AiPrecheckTaskService;
import com.wjx871.population.ai.precheck.AiResolutionService;
import com.wjx871.population.ai.precheck.AiStageMaterialService;
import com.wjx871.population.application.ApplicationService;
import com.wjx871.population.approval.ApprovalService;
import com.wjx871.population.household.HouseholdService;
import com.wjx871.population.material.MaterialService;
import com.wjx871.population.migration.MigrationService;
import com.wjx871.population.migration.MigrationTransactionHook;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Real Spring proxies + H2 + MyBatis, without a test-managed transaction. SQL interception only
 * observes real calls (and optionally throws after a real write); no mapper results are stubbed.
 * AI confirmation's annotation contract is checked statically here; its full business flow remains
 * the responsibility of AiConfirmIntegrationTest. S3's private programmatic templates are exercised
 * with real transactions below, without claiming that this is an upload/worker end-to-end test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(CoreTransactionBoundaryIntegrationTest.ProbeConfiguration.class)
class CoreTransactionBoundaryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlTransactionProbe probe;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired TransactionAttributeSource transactionAttributes;
    @MockBean MigrationTransactionHook hook;

    private String householdToken;
    private String approverToken;

    @BeforeEach
    void seedRealDatabase() throws Exception {
        probe.stop();
        reset(hook);
        // This configuration creates its own random in-memory H2 database via test properties.
        // Reset only database fixtures, never any project or uploaded files.
        for (String table : List.of("residence_archive", "migration_in", "migration_out", "residence",
                "household_member", "sys_approval_log", "sys_approval_request", "application_material",
                "cancellation_record", "business_application", "operation_log")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM household WHERE household_id>=101");
        jdbc.update("DELETE FROM person WHERE person_id>=101");
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) "
                + "VALUES(101,'事务甲','1','110101199001010011','旧址','正常','REGISTERED'),"
                + "(102,'事务乙','2','110101199202020022',NULL,'正常','PENDING'),"
                + "(103,'事务丙','1','110101199303030033','旧址','正常','REGISTERED')");
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,"
                + "establish_date,status,version) VALUES(101,'TX-H101',101,'事务旧址','110101',"
                + "DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) "
                + "VALUES(101,101,'HEAD',DATE '2020-01-01','ACTIVE',0),"
                + "(101,103,'CHILD',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,"
                + "register_date,start_date,status,created_by,version) VALUES"
                + "(101,101,'事务旧址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0),"
                + "(103,101,'事务旧址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)");
        householdToken = token("household");
        approverToken = token("approver");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @AfterEach
    void stopObservation() {
        probe.stop();
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }

    @Test
    void standaloneMemberExecutesActualForUpdateInWritableTransaction() throws Exception {
        long member = memberId(103);
        Recording recording = probe.start();
        JsonNode body = request(get("/api/households/101/members/" + member), householdToken, 200);
        probe.stop();

        assertThat(body.path("data").path("memberId").asLong()).isEqualTo(member);
        assertOneWritableTransaction(recording, "HouseholdMapper.findScoped", "HouseholdMapper.lockMember");
        assertThat(recording.calls.stream().filter(c -> c.physicalSql && c.method.equals("HouseholdMapper.lockMember")))
                .singleElement().satisfies(call -> assertThat(call.sql.toUpperCase()).contains("FOR UPDATE"));
        assertThat(count("household_member", "person_id", 103)).isOne();
    }

    @Test
    void addInternalMemberReadInheritsOuterWriteTransactionAndConnection() throws Exception {
        Recording recording = probe.start();
        JsonNode body = request(post("/api/households/101/members").content("""
                {"personId":102,"relationship":"OTHER","joinDate":"2026-11-06"}
                """), householdToken, 201);
        probe.stop();

        assertThat(body.path("data").path("personId").asLong()).isEqualTo(102);
        assertThat(body.path("data").path("relationship").asText()).isEqualTo("OTHER");
        assertOneWritableTransaction(recording, "HouseholdMapper.lock", "HouseholdMapper.insertMember",
                "OperationLogMapper.insert", "HouseholdMapper.findScoped", "HouseholdMapper.lockMember");
        assertOrdered(recording, "HouseholdMapper.insertMember", "HouseholdMapper.findScoped", "HouseholdMapper.lockMember");
        assertThat(count("household_member", "person_id", 102)).isOne();
        // member() is a same-instance call in add(); this proves the inherited outer transaction,
        // not a second invocation of Spring's member() transaction advice.
    }

    @Test
    void updateInternalMemberReadSeesNewVersionInsideSameOuterTransaction() throws Exception {
        long member = memberId(103);
        Recording recording = probe.start();
        JsonNode body = request(put("/api/households/101/members/" + member)
                .content("{\"relationship\":\"OTHER\",\"version\":0}"), householdToken, 200);
        probe.stop();

        assertThat(body.path("data").path("relationship").asText()).isEqualTo("OTHER");
        assertThat(body.path("data").path("version").asInt()).isEqualTo(1);
        assertOneWritableTransaction(recording, "HouseholdMapper.lock", "HouseholdMapper.lockMember",
                "HouseholdMapper.updateRelationship", "HouseholdMapper.findScoped", "OperationLogMapper.insert");
        assertOrdered(recording, "HouseholdMapper.updateRelationship", "HouseholdMapper.findScoped", "HouseholdMapper.lockMember");
        assertThat(recording.calls.stream().filter(c -> !c.physicalSql && c.method.equals("HouseholdMapper.lockMember")))
                .hasSize(2);
        assertThat(jdbc.queryForObject("SELECT version FROM household_member WHERE member_id=?", Integer.class, member)).isEqualTo(1);
    }

    @Test
    void migrationInValidatesWritesAndCompletesBothStatesOnOneConnection() throws Exception {
        long id = approved("in");
        Recording recording = probe.start();
        execute("in", id, 2, 200);
        probe.stop();

        assertOneWritableTransaction(recording, "BusinessApplicationMapper.selectById", "MigrationMapper.findIn",
                "MigrationMapper.lockHousehold", "MigrationMapper.lockIn", "MigrationMapper.lockResidence",
                "ApplicationMaterialMapper.countRequired", "ApplicationMaterialMapper.countRequiredNotVerified", "MigrationMapper.insertResidence",
                "MigrationMapper.insertMember", "MigrationMapper.updatePersonResidence", "MigrationMapper.updateInStatus",
                "BusinessApplicationMapper.updateStatus", "ApprovalLogMapper.insert", "OperationLogMapper.insert");
        assertOrdered(recording, "MigrationMapper.lockIn", "MigrationMapper.insertResidence",
                "MigrationMapper.updateInStatus", "BusinessApplicationMapper.updateStatus");
        assertInCompleted(id);
        assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=102", String.class)).isEqualTo("REGISTERED");
        assertThat(jdbc.queryForObject("SELECT current_address FROM person WHERE person_id=102", String.class)).isEqualTo("事务旧址");
    }

    @Test
    void migrationOutValidatesArchivesAndCompletesBothStatesOnOneConnection() throws Exception {
        long id = approved("out");
        Recording recording = probe.start();
        execute("out", id, 2, 200);
        probe.stop();

        assertOneWritableTransaction(recording, "BusinessApplicationMapper.selectById", "MigrationMapper.lockOut",
                "MigrationMapper.lockResidence", "MigrationMapper.lockHousehold", "ApplicationMaterialMapper.countRequired", "ApplicationMaterialMapper.countRequiredNotVerified",
                "MigrationMapper.insertArchive", "MigrationMapper.deleteResidence", "MigrationMapper.leaveMember",
                "MigrationMapper.promoteMemberToHead", "MigrationMapper.changeHead", "MigrationMapper.updatePersonResidence",
                "MigrationMapper.updateOutStatus", "BusinessApplicationMapper.updateStatus",
                "ApprovalLogMapper.insert", "OperationLogMapper.insert");
        assertOrdered(recording, "MigrationMapper.lockOut", "MigrationMapper.insertArchive",
                "MigrationMapper.deleteResidence", "MigrationMapper.updateOutStatus", "BusinessApplicationMapper.updateStatus");
        assertOutCompleted(id);
    }

    @Test
    void staleAndAlreadyExecutedRequestsAreBoundToCurrentMigrationState() throws Exception {
        for (String direction : List.of("in", "out")) {
            long id = approved(direction);
            Recording stale = probe.start();
            execute(direction, id, 1, 409);
            probe.stop();
            assertOneWritableTransaction(stale, "BusinessApplicationMapper.selectById", "MigrationMapper.lock" + title(direction));
            assertThat(stale.calls).noneMatch(c -> !c.physicalSql && c.method.startsWith("MigrationMapper.insert"));
            assertThat(applicationStatus(id)).isEqualTo("APPROVED");
            execute(direction, id, 2, 200);
            long logsBefore = jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'", Long.class, id);
            execute(direction, id, 2, 409);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'", Long.class, id))
                    .isEqualTo(logsBefore).isEqualTo(1L);
            if (direction.equals("in")) assertInCompleted(id); else assertOutCompleted(id);
        }
    }

    @Test
    void actualPersonVersionPredicateRejectsLateConflictAndRollsBackEarlierWrites() throws Exception {
        // Fault injection changes the real DB row after Person was read. This proves the actual
        // optimistic UPDATE predicate and rollback, not concurrent locking behavior of MySQL.
        doAnswer(invocation -> {
            assertWritableNow();
            jdbc.update("UPDATE person SET version=version+1 WHERE person_id=102");
            return null;
        }).when(hook).afterResidenceInserted();
        doAnswer(invocation -> {
            assertWritableNow();
            jdbc.update("UPDATE person SET version=version+1 WHERE person_id=101");
            return null;
        }).when(hook).afterResidenceDeleted();
        for (String direction : List.of("in", "out")) {
            long id = approved(direction);
            Recording recording = probe.start();
            JsonNode response = request(post("/api/migrations/" + direction + "/applications/" + id + "/execute")
                    .content("{\"version\":2}"), householdToken, 409);
            probe.stop();
            assertThat(response.path("message").asText()).isEqualTo("PERSON_VERSION_CONFLICT");
            assertOneWritableTransaction(recording, "MigrationMapper.updatePersonResidence");
            if (direction.equals("in")) {
                assertOneWritableTransaction(recording, "MigrationMapper.insertResidence", "MigrationMapper.insertMember");
                assertInUnchanged(id);
            } else {
                assertOneWritableTransaction(recording, "MigrationMapper.insertArchive", "MigrationMapper.deleteResidence",
                        "MigrationMapper.leaveMember", "MigrationMapper.promoteMemberToHead", "MigrationMapper.changeHead");
                assertOutUnchanged(id);
            }
            assertThat(executionLogs(id)).isZero();
        }
    }

    @Test
    void migrationInLateAuditFailureRollsBackAllAlreadyWrittenTables() throws Exception {
        long id = approved("in");
        Recording recording = probe.start();
        recording.after = method -> {
            if (!method.equals("OperationLogMapper.insert")) return;
            // These reads use the transaction-bound JDBC connection, before throwing and rollback.
            assertInCompleted(id);
            assertThat(executionLogs(id)).isOne();
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='MIGRATION_IN_EXECUTE'", Integer.class)).isOne();
            throw new IllegalStateException("S5_A2_AFTER_REAL_IN_AUDIT_INSERT");
        };
        execute("in", id, 2, 500);
        probe.stop();

        assertOneWritableTransaction(recording, "MigrationMapper.insertResidence", "MigrationMapper.insertMember",
                "MigrationMapper.updatePersonResidence", "MigrationMapper.updateInStatus", "BusinessApplicationMapper.updateStatus",
                "ApprovalLogMapper.insert", "OperationLogMapper.insert");
        assertInUnchanged(id);
        assertThat(executionLogs(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='MIGRATION_IN_EXECUTE'", Integer.class)).isZero();
    }

    @Test
    void migrationOutLateAuditFailureRollsBackArchiveMembershipHeadPersonAndStates() throws Exception {
        long id = approved("out");
        Recording recording = probe.start();
        recording.after = method -> {
            if (!method.equals("OperationLogMapper.insert")) return;
            assertOutCompleted(id);
            assertThat(executionLogs(id)).isOne();
            throw new IllegalStateException("S5_A2_AFTER_REAL_OUT_AUDIT_INSERT");
        };
        execute("out", id, 2, 500);
        probe.stop();

        assertOneWritableTransaction(recording, "MigrationMapper.insertArchive", "MigrationMapper.deleteResidence",
                "MigrationMapper.leaveMember", "MigrationMapper.promoteMemberToHead", "MigrationMapper.changeHead",
                "MigrationMapper.updatePersonResidence", "MigrationMapper.updateOutStatus", "BusinessApplicationMapper.updateStatus",
                "ApprovalLogMapper.insert", "OperationLogMapper.insert");
        assertOutUnchanged(id);
        assertThat(executionLogs(id)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='MIGRATION_OUT_EXECUTE'", Integer.class)).isZero();
    }

    @Test
    void migrationInHookSeesOwnUncommittedResidenceAndRollsBack() throws Exception {
        long id = approved("in");
        doAnswer(invocation -> {
            assertWritableNow();
            assertThat(count("residence", "person_id", 102)).isOne();
            assertThat(count("household_member", "person_id", 102)).isZero();
            throw new IllegalStateException("S5_A2_AFTER_RESIDENCE_INSERT");
        }).when(hook).afterResidenceInserted();
        Recording recording = probe.start();
        execute("in", id, 2, 500);
        probe.stop();
        assertOneWritableTransaction(recording, "MigrationMapper.lockIn", "MigrationMapper.insertResidence");
        assertInUnchanged(id);
    }

    @Test
    void migrationOutHookReadsArchiveThenDeletedResidenceBeforeRollback() throws Exception {
        long id = approved("out");
        List<String> boundaries = new ArrayList<>();
        doAnswer(invocation -> {
            assertWritableNow();
            assertThat(count("residence_archive", "application_id", id)).isOne();
            assertThat(count("residence", "person_id", 101)).isOne();
            boundaries.add("archive");
            return null;
        }).when(hook).afterArchiveInserted();
        doAnswer(invocation -> {
            assertWritableNow();
            assertThat(count("residence_archive", "application_id", id)).isOne();
            assertThat(count("residence", "person_id", 101)).isZero();
            boundaries.add("residence-deleted");
            throw new IllegalStateException("S5_A2_AFTER_RESIDENCE_DELETE");
        }).when(hook).afterResidenceDeleted();
        Recording recording = probe.start();
        execute("out", id, 2, 500);
        probe.stop();
        assertThat(boundaries).containsExactly("archive", "residence-deleted");
        assertOneWritableTransaction(recording, "MigrationMapper.insertArchive", "MigrationMapper.deleteResidence");
        assertOutUnchanged(id);
    }

    @Test
    void submitMaterialVerificationAndApprovalEachUseOneRealWriteTransaction() throws Exception {
        long id = create("in");
        long identity = material(id, "IDENTITY_PROOF", "PENDING");
        material(id, "ADDRESS_PROOF", "VERIFIED");
        Recording submission = probe.start();
        request(post("/api/applications/" + id + "/submit"), householdToken, 200);
        probe.stop();
        assertOneWritableTransaction(submission, "BusinessApplicationMapper.selectByIdForUpdate", "BusinessApplicationMapper.updateStatus",
                "MigrationMapper.updateInStatus", "ApprovalRequestMapper.insert", "ApprovalLogMapper.insert", "OperationLogMapper.insert");
        assertThat(applicationStatus(id)).isEqualTo("UNDER_REVIEW");

        Recording verification = probe.start();
        request(post("/api/materials/" + identity + "/verify").content("{\"result\":\"VERIFIED\",\"comment\":\"事务核验\"}"), approverToken, 200);
        probe.stop();
        assertOneWritableTransaction(verification, "ApplicationMaterialMapper.selectById", "BusinessApplicationMapper.selectById",
                "ApplicationMaterialMapper.verify", "ApprovalLogMapper.insert");
        assertThat(jdbc.queryForObject("SELECT verify_status FROM application_material WHERE material_id=?", String.class, identity)).isEqualTo("VERIFIED");

        Recording approval = probe.start();
        approve(id);
        probe.stop();
        assertOneWritableTransaction(approval, "ApprovalRequestMapper.selectById", "ApprovalRequestMapper.decide",
                "BusinessApplicationMapper.updateStatus", "MigrationMapper.updateInStatus", "ApprovalLogMapper.insert", "OperationLogMapper.insert");
        assertThat(applicationStatus(id)).isEqualTo("APPROVED");
        assertThat(count("residence", "person_id", 102)).isZero();
    }

    @Test
    void criticalAnnotatedWriteEntrypointsDeclareWritableTransactionContracts() {
        // Static coverage only: unlike the SQL tests above, annotations do not prove proxy entry or rollback.
        assertWriteContracts(HouseholdService.class, "member", "create", "update", "add", "updateMember", "leave", "changeHead");
        assertWriteContracts(MigrationService.class, "createIn", "createOut", "updateIn", "updateOut", "executeIn", "executeOut");
        assertWriteContracts(ApplicationService.class, "create", "createSpecialized", "update", "updateSpecialized", "cancelDraft", "returnApplication", "requireForUpdate");
        assertWriteContracts(ApprovalService.class, "submit", "withdraw", "approve", "approveAndCreatePerson", "reject");
        assertWriteContracts(MaterialService.class, "upload", "delete", "verify");
        assertWriteContracts(AiConfirmService.class, "confirm");
        assertWriteContracts(AiResolutionService.class, "preview", "validateForConfirmation", "markConsumed");
    }

    @Test
    void s3ProgrammaticTemplatesReallySuspendOuterReadOnlyTransaction() {
        // S3 deliberately uses TransactionTemplate(REQUIRES_NEW), not @Transactional on upload/create.
        // Exercise those actual template helpers, but do not claim this covers their full business flows.
        AiPrecheckProperties properties = new AiPrecheckProperties();
        AiStageMaterialService stage = new AiStageMaterialService(jdbc, properties, null, transactionManager);
        AiPrecheckTaskService tasks = new AiPrecheckTaskService(jdbc, properties, transactionManager, json, stage);
        for (Object service : List.of(stage, tasks)) {
            TransactionTemplate outer = new TransactionTemplate(transactionManager);
            outer.setReadOnly(true);
            outer.executeWithoutResult(status -> {
                Connection suspended = DataSourceUtils.getConnection(dataSource);
                try {
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                    Callable<Boolean> action = () -> {
                        assertWritableNow();
                        Connection inner = DataSourceUtils.getConnection(dataSource);
                        try {
                            assertThat(inner).isNotSameAs(suspended);
                            assertThat(inner.getAutoCommit()).isFalse();
                            assertThat(DataSourceUtils.isConnectionTransactional(inner, dataSource)).isTrue();
                            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE person_id=102", Integer.class)).isOne();
                            return true;
                        } finally {
                            DataSourceUtils.releaseConnection(inner, dataSource);
                        }
                    };
                    assertThat((Boolean) ReflectionTestUtils.invokeMethod(service, "transaction", action)).isTrue();
                    assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isTrue();
                    Connection resumed = DataSourceUtils.getConnection(dataSource);
                    try {
                        assertThat(resumed).isSameAs(suspended);
                    } finally {
                        DataSourceUtils.releaseConnection(resumed, dataSource);
                    }
                } finally {
                    DataSourceUtils.releaseConnection(suspended, dataSource);
                }
            });
        }
    }

    private void assertWriteContracts(Class<?> type, String... names) {
        for (String name : names) {
            List<Method> methods = Arrays.stream(type.getMethods()).filter(method -> method.getName().equals(name)).toList();
            assertThat(methods).as(type.getSimpleName() + "." + name).isNotEmpty();
            for (Method method : methods) {
                TransactionAttribute attribute = transactionAttributes.getTransactionAttribute(method, type);
                assertThat(attribute).as(method.toString()).isNotNull();
                assertThat(attribute.isReadOnly()).as(method.toString()).isFalse();
                assertThat(attribute.getPropagationBehavior()).isIn(TransactionDefinition.PROPAGATION_REQUIRED, TransactionDefinition.PROPAGATION_MANDATORY);
                assertThat(attribute.rollbackOn(new IllegalStateException("probe"))).isTrue();
            }
        }
    }

    private static void assertWritableNow() {
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
        assertThat(TransactionSynchronizationManager.isCurrentTransactionReadOnly()).isFalse();
    }

    private static void assertOneWritableTransaction(Recording recording, String... expectedMethods) {
        List<Call> mapperCalls = recording.calls.stream().filter(call -> !call.physicalSql).toList();
        assertThat(mapperCalls).isNotEmpty();
        assertThat(mapperCalls.stream().map(call -> call.method)).contains(expectedMethods);
        Call first = mapperCalls.get(0);
        assertThat(first.transactionResource).isNotNull();
        assertThat(first.connection).isNotNull();
        for (Call call : recording.calls) {
            assertThat(call.active).as(call.method).isTrue();
            assertThat(call.readOnly).as(call.method).isFalse();
            assertThat(call.autoCommit).as(call.method).isFalse();
            assertThat(call.boundConnection).as(call.method).isTrue();
            assertThat(call.transactionResource).as(call.method).isSameAs(first.transactionResource);
            assertThat(call.connection).as(call.method).isSameAs(first.connection);
        }
    }

    private static void assertOrdered(Recording recording, String... methods) {
        List<String> names = recording.calls.stream().filter(call -> !call.physicalSql).map(call -> call.method).toList();
        int position = -1;
        for (String method : methods) {
            int relative = names.subList(position + 1, names.size()).indexOf(method);
            assertThat(relative).as(method + " after " + position + " in " + names).isGreaterThanOrEqualTo(0);
            position += relative + 1;
        }
    }

    private long create(String direction) throws Exception {
        String body = direction.equals("in") ? """
                {"personId":102,"migrationType":"OUTSIDE_CITY","fromRegionCode":"370100","fromAddress":"济南",
                 "toRegionCode":"110101","toHouseholdId":101,"inDate":"2026-11-06","reason":"投靠","title":"事务迁入"}
                """ : """
                {"personId":101,"migrationType":"OUTSIDE_CITY","toRegionCode":"310000","toAddress":"上海",
                 "outDate":"2026-11-06","reason":"工作","newHeadPersonId":103,"title":"事务迁出"}
                """;
        return request(post("/api/migrations/" + direction + "/applications").content(body), householdToken, 201)
                .path("data").path("applicationId").asLong();
    }

    private long approved(String direction) throws Exception {
        long id = create(direction);
        material(id, "IDENTITY_PROOF", "VERIFIED");
        material(id, direction.equals("in") ? "ADDRESS_PROOF" : "HOUSEHOLD_BOOK", "VERIFIED");
        request(post("/api/applications/" + id + "/submit"), householdToken, 200);
        approve(id);
        assertThat(applicationStatus(id)).isEqualTo("APPROVED");
        return id;
    }

    private void approve(long id) throws Exception {
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, id);
        request(post("/api/approvals/" + approval + "/approve").content("{\"comment\":\"同意\",\"version\":0}"), approverToken, 200);
    }

    private long material(long id, String type, String verify) {
        String object = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,"
                + "storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) "
                + "VALUES(?,?,?,'fixture.pdf',?,'transaction-fixture','application/pdf',1,?,1,?,7,6)", id, type, type,
                object + ".pdf", object.replace("-", "").repeat(2), verify);
        return jdbc.queryForObject("SELECT material_id FROM application_material WHERE stored_filename=?", Long.class, object + ".pdf");
    }

    private void execute(String direction, long id, int version, int expected) throws Exception {
        request(post("/api/migrations/" + direction + "/applications/" + id + "/execute")
                .content("{\"version\":" + version + "}"), householdToken, expected);
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, String token, int expected) throws Exception {
        var response = mvc.perform(builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage("HTTP %s: %s", response.getStatus(), response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private long memberId(long person) {
        return jdbc.queryForObject("SELECT member_id FROM household_member WHERE household_id=101 AND person_id=?", Long.class, person);
    }

    private int count(String table, String key, long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + key + "=?", Integer.class, id);
    }

    private String applicationStatus(long id) {
        return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, id);
    }

    private int executionLogs(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'", Integer.class, id);
    }

    private void assertInCompleted(long id) {
        assertThat(count("residence", "person_id", 102)).isOne();
        assertThat(count("household_member", "person_id", 102)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM household_member WHERE person_id=102", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT version FROM person WHERE person_id=102", Long.class)).isEqualTo(1L);
        assertThat(applicationStatus(id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, id)).isEqualTo("COMPLETED");
    }

    private void assertInUnchanged(long id) {
        assertThat(count("residence", "person_id", 102)).isZero();
        assertThat(count("household_member", "person_id", 102)).isZero();
        assertThat(jdbc.queryForObject("SELECT version FROM person WHERE person_id=102", Long.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=102", String.class)).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT current_address FROM person WHERE person_id=102", String.class)).isNull();
        assertThat(applicationStatus(id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT version FROM business_application WHERE application_id=?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT version FROM migration_in WHERE application_id=?", Integer.class, id)).isEqualTo(2);
    }

    private void assertOutCompleted(long id) {
        assertThat(count("residence_archive", "application_id", id)).isOne();
        assertThat(count("residence", "person_id", 101)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM household_member WHERE person_id=101", String.class)).isEqualTo("LEFT");
        assertThat(jdbc.queryForObject("SELECT relationship FROM household_member WHERE person_id=103", String.class)).isEqualTo("HEAD");
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=101", Long.class)).isEqualTo(103L);
        assertThat(jdbc.queryForObject("SELECT version FROM household WHERE household_id=101", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=101", String.class)).isEqualTo("MIGRATED_OUT");
        assertThat(jdbc.queryForObject("SELECT version FROM person WHERE person_id=101", Long.class)).isEqualTo(1L);
        assertThat(applicationStatus(id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, id)).isEqualTo("COMPLETED");
    }

    private void assertOutUnchanged(long id) {
        assertThat(count("residence_archive", "application_id", id)).isZero();
        assertThat(count("residence", "person_id", 101)).isOne();
        assertThat(jdbc.queryForObject("SELECT status FROM household_member WHERE person_id=101", String.class)).isEqualTo("ACTIVE");
        assertThat(jdbc.queryForObject("SELECT relationship FROM household_member WHERE person_id=101", String.class)).isEqualTo("HEAD");
        assertThat(jdbc.queryForObject("SELECT relationship FROM household_member WHERE person_id=103", String.class)).isEqualTo("CHILD");
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=101", Long.class)).isEqualTo(101L);
        assertThat(jdbc.queryForObject("SELECT version FROM household WHERE household_id=101", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT SUM(version) FROM household_member WHERE household_id=101", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=101", String.class)).isEqualTo("REGISTERED");
        assertThat(jdbc.queryForObject("SELECT current_address FROM person WHERE person_id=101", String.class)).isEqualTo("旧址");
        assertThat(jdbc.queryForObject("SELECT version FROM person WHERE person_id=101", Long.class)).isZero();
        assertThat(applicationStatus(id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT version FROM business_application WHERE application_id=?", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, id)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT version FROM migration_out WHERE application_id=?", Integer.class, id)).isEqualTo(2);
    }

    private static String title(String direction) {
        return direction.equals("in") ? "In" : "Out";
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        SqlTransactionProbe coreTransactionSqlProbe(DataSource dataSource) {
            return new SqlTransactionProbe(dataSource);
        }
    }

    static final class Recording {
        final List<Call> calls = new ArrayList<>();
        Consumer<String> after = method -> { };
    }

    record Call(String method, boolean physicalSql, String sql, boolean active, boolean readOnly,
                boolean autoCommit, boolean boundConnection, Object transactionResource, Connection connection) { }

    @Intercepts({
            @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
            @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
            @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class, CacheKey.class, BoundSql.class}),
            @Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class})
    })
    static final class SqlTransactionProbe implements Interceptor {
        private final DataSource dataSource;
        private final ThreadLocal<Recording> current = new ThreadLocal<>();
        private final ThreadLocal<String> currentMethod = new ThreadLocal<>();

        SqlTransactionProbe(DataSource dataSource) {
            this.dataSource = dataSource;
        }

        Recording start() {
            Recording recording = new Recording();
            current.set(recording);
            return recording;
        }

        void stop() {
            current.remove();
            currentMethod.remove();
        }

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            Recording recording = current.get();
            if (recording == null) return invocation.proceed();
            if (invocation.getTarget() instanceof StatementHandler handler) {
                String method = currentMethod.get();
                if (method != null) capture(recording, method, true, handler.getBoundSql().getSql(), (Connection) invocation.getArgs()[0]);
                return invocation.proceed();
            }
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            String id = statement.getId();
            if (!id.startsWith("com.wjx871.population.")) return invocation.proceed();
            String method = id.substring(id.lastIndexOf('.', id.lastIndexOf('.') - 1) + 1);
            if (!(method.startsWith("HouseholdMapper.") || method.startsWith("MigrationMapper.")
                    || method.startsWith("BusinessApplicationMapper.") || method.startsWith("ApplicationMaterialMapper.")
                    || method.startsWith("ApprovalRequestMapper.") || method.startsWith("ApprovalLogMapper.")
                    || method.startsWith("PersonMapper.") || method.startsWith("OperationLogMapper."))) {
                return invocation.proceed();
            }
            String previous = currentMethod.get();
            currentMethod.set(method);
            try {
                Connection connection = ((Executor) invocation.getTarget()).getTransaction().getConnection();
                capture(recording, method, false, statement.getBoundSql(invocation.getArgs()[1]).getSql(), connection);
                Object result = invocation.proceed();
                recording.after.accept(method);
                return result;
            } finally {
                if (previous == null) currentMethod.remove(); else currentMethod.set(previous);
            }
        }

        private void capture(Recording recording, String method, boolean physical, String sql, Connection connection) throws Exception {
            recording.calls.add(new Call(method, physical, sql,
                    TransactionSynchronizationManager.isActualTransactionActive(),
                    TransactionSynchronizationManager.isCurrentTransactionReadOnly(), connection.getAutoCommit(),
                    DataSourceUtils.isConnectionTransactional(connection, dataSource),
                    TransactionSynchronizationManager.getResource(dataSource), connection));
        }
    }
}
