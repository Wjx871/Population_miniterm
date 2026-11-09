package com.wjx871.population.household;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Clob;
import java.sql.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Issue #15: every written membership join date must be on or after household establishment.
 * Uses real HTTP/JWT, Spring transactions and MyBatis. No mocked service or fabricated failure.
 * The explicit MySQL IT subclass can reuse the same scenarios against its isolated schema.
 */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class Issue15HouseholdDateBoundaryIntegrationTest {

    protected static final long HOUSEHOLD = 95101L;
    protected static final long EMPTY_HOUSEHOLD = 95102L;
    protected static final long FOREIGN_HOUSEHOLD = 95103L;
    protected static final long HEAD = 95101L;
    protected static final long CANDIDATE = 95102L;
    protected static final long HISTORY_PERSON = 95103L;
    protected static final long FREE_HEAD = 95104L;
    protected static final String ESTABLISH_DATE = "2026-01-01";

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected ObjectMapper json;
    protected final Map<String, String> tokens = new LinkedHashMap<>();

    @DynamicPropertySource
    static void h2Datasource(DynamicPropertyRegistry properties) {
        if (System.getenv("MYSQL84_GATE_DB_URL") == null) {
            properties.add("spring.datasource.url", () -> "jdbc:h2:mem:issue15_household_date_boundary;"
                    + "MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        }
    }

    @BeforeEach
    void resetDatabaseAndLogin() throws Exception {
        for (String table : List.of("residence_archive", "household_archive", "cancellation_record",
                "migration_in", "migration_out", "residence", "household_member",
                "sys_approval_log", "sys_approval_request", "application_material",
                "business_application", "operation_log")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM household WHERE household_id BETWEEN 95101 AND 95120");
        jdbc.update("DELETE FROM person WHERE person_id BETWEEN 95101 AND 95120");
        for (long personId : List.of(HEAD, CANDIDATE, HISTORY_PERSON, FREE_HEAD)) {
            jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,"
                            + "current_status_code,version) VALUES(?,?,'1',?,NULL,'正常','PENDING',0)",
                    personId, "Issue15 合成人员 " + personId, "1101011990010" + personId);
        }
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,"
                        + "household_type,establish_date,status,version) VALUES"
                        + "(95101,'ISSUE15-MAIN',95101,'北京市东城区合成地址','110101','FAMILY',?,'ACTIVE',0),"
                        + "(95102,'ISSUE15-EMPTY',NULL,'北京市东城区合成空户','110101','FAMILY',?,'ACTIVE',0),"
                        + "(95103,'ISSUE15-FOREIGN',NULL,'上海市合成地址','310000','FAMILY',?,'ACTIVE',0)",
                Date.valueOf(ESTABLISH_DATE), Date.valueOf(ESTABLISH_DATE), Date.valueOf(ESTABLISH_DATE));
        jdbc.update("INSERT INTO household_member(member_id,household_id,person_id,relationship,join_date,"
                        + "status,version) VALUES(95101,95101,95101,'HEAD',?,'ACTIVE',0)",
                Date.valueOf(ESTABLISH_DATE));
        tokens.clear();
        for (String username : List.of("household", "approver", "viewer", "admin")) {
            MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("username", username, "password", "123456"))))
                    .andReturn();
            assertHttp(login, 200);
            String token = data(login).path("token").asText();
            assertThat(token).as("real JWT for %s", username).isNotBlank();
            tokens.put(username, token);
        }
    }

    @Test
    void addBeforeEstablishmentIsRejectedWithoutMembershipOrAudit() throws Exception {
        Snapshot before = snapshot();
        MvcResult result = addMember(HOUSEHOLD, CANDIDATE, "2025-12-31", "household");
        assertRejectedWithoutMutation("add-before-establish", before, result, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01", "2026-01-02"})
    void addOnOrAfterEstablishmentSucceeds(String joinDate) throws Exception {
        List<Map<String, String>> householdBefore = rows("SELECT * FROM household ORDER BY household_id");
        int auditBefore = successOperations("HOUSEHOLD_MEMBER_ADD");
        MvcResult result = addMember(HOUSEHOLD, CANDIDATE, joinDate, "household");
        evidence("add-legal-" + joinDate, null, result, snapshot());
        assertAll(
                () -> assertHttp(result, 201),
                () -> assertThat(memberDate(CANDIDATE)).isEqualTo(Date.valueOf(joinDate)),
                () -> assertThat(memberVersion(CANDIDATE)).isZero(),
                () -> assertThat(rows("SELECT * FROM household ORDER BY household_id")).isEqualTo(householdBefore),
                () -> assertThat(successOperations("HOUSEHOLD_MEMBER_ADD")).isEqualTo(auditBefore + 1),
                () -> assertThat(count("residence")).isZero(),
                () -> assertThat(count("migration_in")).isZero());
    }

    @Test
    void updateCannotMoveEstablishmentAfterAnActiveMemberJoinDate() throws Exception {
        Snapshot before = snapshot();
        MvcResult result = updateHousehold(HOUSEHOLD, "2026-01-02", 0, "household");
        assertRejectedWithoutMutation("update-after-active-join", before, result, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "MOVED_OUT", "CANCELLED"})
    void updateIncludesHistoricalNonActiveMembersInItsLowerBoundary(String historicalStatus) throws Exception {
        jdbc.update("UPDATE household_member SET join_date=? WHERE household_id=? AND person_id=?",
                Date.valueOf("2026-02-01"), HOUSEHOLD, HEAD);
        insertHistoricalMember(HISTORY_PERSON, "2026-01-01", historicalStatus);
        Snapshot before = snapshot();
        MvcResult result = updateHousehold(HOUSEHOLD, "2026-01-02", 0, "household");
        assertRejectedWithoutMutation("update-after-history-" + historicalStatus, before, result, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01", "2025-12-31"})
    void updateOnOrBeforeEarliestJoinSucceedsWithoutRewritingMembership(String date) throws Exception {
        insertHistoricalMember(HISTORY_PERSON, "2026-01-03", "MOVED_OUT");
        List<Map<String, String>> membersBefore = rows("SELECT * FROM household_member ORDER BY member_id");
        int auditBefore = successOperations("HOUSEHOLD_UPDATE");
        MvcResult result = updateHousehold(HOUSEHOLD, date, 0, "household");
        evidence("update-legal-" + date, null, result, snapshot());
        assertAll(
                () -> assertHttp(result, 200),
                () -> assertThat(establishDate(HOUSEHOLD)).isEqualTo(Date.valueOf(date)),
                () -> assertThat(householdVersion(HOUSEHOLD)).isEqualTo(1),
                () -> assertThat(rows("SELECT * FROM household_member ORDER BY member_id")).isEqualTo(membersBefore),
                () -> assertThat(successOperations("HOUSEHOLD_UPDATE")).isEqualTo(auditBefore + 1));
    }

    @Test
    void emptyHouseholdHasNoMemberDateBoundary() throws Exception {
        MvcResult result = updateHousehold(EMPTY_HOUSEHOLD, "2026-02-01", 0, "household");
        evidence("update-empty-household", null, result, snapshot());
        assertAll(
                () -> assertHttp(result, 200),
                () -> assertThat(establishDate(EMPTY_HOUSEHOLD)).isEqualTo(Date.valueOf("2026-02-01")),
                () -> assertThat(householdVersion(EMPTY_HOUSEHOLD)).isEqualTo(1),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=?",
                        Integer.class, EMPTY_HOUSEHOLD)).isZero());
    }

    @Test
    void creatingInitialHeadUsesEstablishmentDateAndOneConsistentHead() throws Exception {
        MvcResult result = mvc.perform(post("/api/households").header("Authorization", bearer("household"))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "householdNo", "ISSUE15-CREATE", "address", "北京市东城区新户",
                                "regionCode", "110101", "householdType", "FAMILY",
                                "establishDate", ESTABLISH_DATE, "headPersonId", FREE_HEAD))))
                .andReturn();
        evidence("create-initial-head", null, result, snapshot());
        assertHttp(result, 201);
        long householdId = data(result).path("householdId").asLong();
        assertAll(
                () -> assertThat(establishDate(householdId)).isEqualTo(Date.valueOf(ESTABLISH_DATE)),
                () -> assertThat(memberDate(FREE_HEAD)).isEqualTo(Date.valueOf(ESTABLISH_DATE)),
                () -> assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?",
                        Long.class, householdId)).isEqualTo(FREE_HEAD),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? "
                        + "AND person_id=? AND relationship='HEAD' AND status='ACTIVE'", Integer.class,
                        householdId, FREE_HEAD)).isOne(),
                () -> assertThat(householdVersion(householdId)).isZero(),
                () -> assertThat(memberVersion(FREE_HEAD)).isZero());
    }

    @Test
    void legalHouseholdUpdateStillRejectsStaleVersionWithoutMutation() throws Exception {
        Snapshot before = snapshot();
        MvcResult result = updateHousehold(HOUSEHOLD, "2025-12-31", 9, "household");
        assertRejectedWithoutMutation("update-stale-version", before, result, 409);
    }

    @Test
    void viewerCannotAddOrUpdateAndHouseholdUserCannotWriteAnotherRegion() throws Exception {
        Snapshot beforeAdd = snapshot();
        MvcResult viewerAdd = addMember(HOUSEHOLD, CANDIDATE, ESTABLISH_DATE, "viewer");
        assertRejectedWithoutMutation("viewer-add", beforeAdd, viewerAdd, 403);
        Snapshot beforeUpdate = snapshot();
        MvcResult viewerUpdate = updateHousehold(HOUSEHOLD, ESTABLISH_DATE, 0, "viewer");
        assertRejectedWithoutMutation("viewer-update", beforeUpdate, viewerUpdate, 403);
        Snapshot beforeForeignAdd = snapshot();
        MvcResult foreignAdd = addMember(FOREIGN_HOUSEHOLD, CANDIDATE, ESTABLISH_DATE, "household");
        assertRejectedWithoutMutation("foreign-add", beforeForeignAdd, foreignAdd, 403);
        Snapshot beforeForeignUpdate = snapshot();
        MvcResult foreignUpdate = updateHousehold(FOREIGN_HOUSEHOLD, ESTABLISH_DATE, 0, "household");
        assertRejectedWithoutMutation("foreign-update", beforeForeignUpdate, foreignUpdate, 403);
    }

    @Test
    void approvedMigrationWithInDateBeforeEstablishmentCannotExecuteOrPartiallyWrite() throws Exception {
        long applicationId = approvedMigration(CANDIDATE, ESTABLISH_DATE);
        // Represent a previously approved legacy application; creation-time validation cannot protect execution.
        jdbc.update("UPDATE migration_in SET in_date=? WHERE application_id=?",
                Date.valueOf("2025-12-31"), applicationId);
        Snapshot before = snapshot();
        MvcResult result = executeIn(applicationId, migrationVersion(applicationId), "household");
        assertRejectedWithoutMutation("execute-in-before-establish", before, result, 400);
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-01-01", "2026-01-02"})
    void approvedMigrationOnOrAfterEstablishmentWritesOneConsistentMembership(String inDate) throws Exception {
        long applicationId = approvedMigration(CANDIDATE, inDate);
        int version = migrationVersion(applicationId);
        Snapshot before = snapshot();
        MvcResult result = executeIn(applicationId, version, "household");
        evidence("execute-in-legal-" + inDate, before, result, snapshot());
        assertSuccessfulMigration(result, applicationId, CANDIDATE, inDate, version, 0);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "MOVED_OUT"})
    void historicalJoinBeforeEstablishmentBlocksRestorationWithoutSilentRepair(String status) throws Exception {
        insertHistoricalMember(CANDIDATE, "2025-12-31", status);
        long applicationId = approvedMigration(CANDIDATE, "2026-02-01");
        Snapshot before = snapshot();
        MvcResult result = executeIn(applicationId, migrationVersion(applicationId), "household");
        assertRejectedWithoutMutation("restore-invalid-history-" + status, before, result, 409);
    }

    @ParameterizedTest
    @ValueSource(strings = {"LEFT", "MOVED_OUT"})
    void legalHistoricalMembershipIsRestoredWithTheNewInDate(String status) throws Exception {
        insertHistoricalMember(CANDIDATE, ESTABLISH_DATE, status);
        long memberId = jdbc.queryForObject("SELECT member_id FROM household_member WHERE person_id=?",
                Long.class, CANDIDATE);
        long applicationId = approvedMigration(CANDIDATE, "2026-02-01");
        int version = migrationVersion(applicationId);
        Snapshot before = snapshot();
        MvcResult result = executeIn(applicationId, version, "household");
        evidence("restore-legal-" + status, before, result, snapshot());
        assertSuccessfulMigration(result, applicationId, CANDIDATE, "2026-02-01", version, 4);
        assertAll(
                () -> assertThat(jdbc.queryForObject("SELECT member_id FROM household_member WHERE person_id=?",
                        Long.class, CANDIDATE)).isEqualTo(memberId),
                () -> assertThat(jdbc.queryForObject("SELECT leave_date FROM household_member WHERE member_id=?",
                        Date.class, memberId)).isNull());
    }

    @Test
    void migrationExecutionStillRejectsStaleVersionAndViewerWithoutMutation() throws Exception {
        long applicationId = approvedMigration(CANDIDATE, ESTABLISH_DATE);
        Snapshot beforeStale = snapshot();
        MvcResult stale = executeIn(applicationId, migrationVersion(applicationId) + 1, "household");
        assertRejectedWithoutMutation("execute-in-stale-version", beforeStale, stale, 409);
        Snapshot beforeViewer = snapshot();
        MvcResult viewer = executeIn(applicationId, migrationVersion(applicationId), "viewer");
        assertRejectedWithoutMutation("execute-in-viewer", beforeViewer, viewer, 403);
    }

    @Test
    void householdWithOnlyHistoricalMembersStillUsesTheirJoinDateBoundary() throws Exception {
        jdbc.update("UPDATE household_member SET status='MOVED_OUT',leave_date=? "
                        + "WHERE household_id=? AND person_id=?",
                Date.valueOf("2026-01-10"), HOUSEHOLD, HEAD);
        jdbc.update("UPDATE household SET head_person_id=NULL WHERE household_id=?", HOUSEHOLD);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? "
                + "AND status='ACTIVE'", Integer.class, HOUSEHOLD)).isZero();
        Snapshot before = snapshot();
        MvcResult invalid = updateHousehold(HOUSEHOLD, "2026-01-02", 0, "household");
        assertRejectedWithoutMutation("update-only-historical-members-invalid", before, invalid, 400);

        int auditBefore = successOperations("HOUSEHOLD_UPDATE");
        MvcResult equal = updateHousehold(HOUSEHOLD, ESTABLISH_DATE, 0, "household");
        Snapshot after = snapshot();
        evidence("update-only-historical-members-equal", before, equal, after);
        assertAll(
                () -> assertHttp(equal, 200),
                () -> assertThat(establishDate(HOUSEHOLD)).isEqualTo(Date.valueOf(ESTABLISH_DATE)),
                () -> assertThat(householdVersion(HOUSEHOLD)).isEqualTo(1),
                () -> assertThat(after.rows().get("household_member"))
                        .isEqualTo(before.rows().get("household_member")),
                () -> assertThat(successOperations("HOUSEHOLD_UPDATE")).isEqualTo(auditBefore + 1));
    }

    @Test
    void allScopeAdminStillAppliesDateBoundaryWhenAddingInAnotherRegion() throws Exception {
        Snapshot beforeInvalid = snapshot();
        MvcResult invalid = addMember(FOREIGN_HOUSEHOLD, CANDIDATE, "2025-12-31", "admin");
        assertRejectedWithoutMutation("all-scope-foreign-add-invalid", beforeInvalid, invalid, 400);

        Snapshot beforeEqual = snapshot();
        int auditBefore = successOperations("HOUSEHOLD_MEMBER_ADD");
        MvcResult equal = addMember(FOREIGN_HOUSEHOLD, CANDIDATE, ESTABLISH_DATE, "admin");
        Snapshot after = snapshot();
        evidence("all-scope-foreign-add-equal", beforeEqual, equal, after);
        assertAll(
                () -> assertHttp(equal, 201),
                () -> assertThat(memberDate(CANDIDATE)).isEqualTo(Date.valueOf(ESTABLISH_DATE)),
                () -> assertThat(memberVersion(CANDIDATE)).isZero(),
                () -> assertThat(jdbc.queryForObject("SELECT household_id FROM household_member WHERE person_id=?",
                        Long.class, CANDIDATE)).isEqualTo(FOREIGN_HOUSEHOLD),
                () -> assertThat(after.rows().get("household")).isEqualTo(beforeEqual.rows().get("household")),
                () -> assertThat(successOperations("HOUSEHOLD_MEMBER_ADD")).isEqualTo(auditBefore + 1),
                () -> assertThat(count("residence")).isZero(),
                () -> assertThat(count("migration_in")).isZero());
    }

    @Test
    void validHistoricalMemberCannotBeRestoredWithInDateBeforeEstablishment() throws Exception {
        insertHistoricalMember(CANDIDATE, ESTABLISH_DATE, "MOVED_OUT");
        long applicationId = approvedMigration(CANDIDATE, ESTABLISH_DATE);
        jdbc.update("UPDATE migration_in SET in_date=? WHERE application_id=?",
                Date.valueOf("2025-12-31"), applicationId);
        Snapshot before = snapshot();
        MvcResult result = executeIn(applicationId, migrationVersion(applicationId), "household");
        assertRejectedWithoutMutation("restore-valid-history-invalid-in-date", before, result, 400);
    }

    protected MvcResult addMember(long householdId, long personId, String date, String user) throws Exception {
        return mvc.perform(post("/api/households/" + householdId + "/members")
                        .header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("personId", personId,
                                "relationship", "OTHER", "joinDate", date))))
                .andReturn();
    }

    protected MvcResult updateHousehold(long householdId, String date, int version, String user) throws Exception {
        String region = householdId == FOREIGN_HOUSEHOLD ? "310000" : "110101";
        return mvc.perform(put("/api/households/" + householdId).header("Authorization", bearer(user))
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of(
                                "address", "Issue15 更新地址", "regionCode", region,
                                "householdType", "FAMILY", "establishDate", date,
                                "status", "ACTIVE", "version", version))))
                .andReturn();
    }

    protected void insertHistoricalMember(long personId, String joinDate, String status) {
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,leave_date,"
                        + "status,version) VALUES(?,?,'OTHER',?,?,?,3)",
                HOUSEHOLD, personId, Date.valueOf(joinDate), Date.valueOf("2026-01-10"), status);
    }

    protected long approvedMigration(long personId, String inDate) throws Exception {
        MvcResult created = mvc.perform(post("/api/migrations/in/applications")
                        .header("Authorization", bearer("household")).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("personId", personId,
                                "migrationType", "OUTSIDE_CITY", "fromRegionCode", "310000",
                                "fromAddress", "上海市合成来源地址", "toRegionCode", "110101",
                                "toHouseholdId", HOUSEHOLD, "inDate", inDate,
                                "reason", "Issue15 日期边界测试", "title", "Issue15 迁入申请"))))
                .andReturn();
        assertHttp(created, 201);
        long applicationId = data(created).path("applicationId").asLong();
        insertVerifiedMaterials(applicationId);
        MvcResult submitted = mvc.perform(post("/api/applications/" + applicationId + "/submit")
                        .header("Authorization", bearer("household"))).andReturn();
        assertHttp(submitted, 200);
        long approvalId = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",
                Long.class, applicationId);
        MvcResult approved = mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", bearer("approver")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意合成测试\",\"version\":0}"))
                .andReturn();
        assertHttp(approved, 200);
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId)).isEqualTo("APPROVED");
        return applicationId;
    }

    protected void insertVerifiedMaterials(long applicationId) {
        for (String type : List.of("IDENTITY_PROOF", "ADDRESS_PROOF")) {
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,"
                            + "original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,"
                            + "required_flag,verify_status,verify_user_id,uploaded_by) "
                            + "VALUES(?,?,?,'synthetic.pdf',?,'issue15-synthetic','application/pdf',1,?,1,'VERIFIED',7,6)",
                    applicationId, type, type, UUID.randomUUID() + ".pdf", "1".repeat(64));
        }
    }

    protected MvcResult executeIn(long applicationId, int version, String user) throws Exception {
        return mvc.perform(post("/api/migrations/in/applications/" + applicationId + "/execute")
                        .header("Authorization", bearer(user)).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("version", version))))
                .andReturn();
    }

    protected void assertSuccessfulMigration(MvcResult result, long applicationId, long personId,
            String inDate, int previousMigrationVersion, int expectedMemberVersion) throws Exception {
        assertAll(
                () -> assertHttp(result, 200),
                () -> assertThat(memberDate(personId)).isEqualTo(Date.valueOf(inDate)),
                () -> assertThat(memberVersion(personId)).isEqualTo(expectedMemberVersion),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE household_id=? "
                        + "AND person_id=? AND status='ACTIVE'", Integer.class, HOUSEHOLD, personId)).isOne(),
                () -> assertThat(jdbc.queryForObject("SELECT register_date FROM residence WHERE person_id=?",
                        Date.class, personId)).isEqualTo(Date.valueOf(inDate)),
                () -> assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                        String.class, applicationId)).isEqualTo("COMPLETED"),
                () -> assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?",
                        String.class, applicationId)).isEqualTo("COMPLETED"),
                () -> assertThat(migrationVersion(applicationId)).isEqualTo(previousMigrationVersion + 1),
                () -> assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?",
                        String.class, personId)).isEqualTo("REGISTERED"),
                () -> assertThat(jdbc.queryForObject("SELECT version FROM person WHERE person_id=?",
                        Integer.class, personId)).isEqualTo(1),
                () -> assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? "
                        + "AND action='EXECUTE'", Integer.class, applicationId)).isOne(),
                () -> assertThat(successOperations("MIGRATION_IN_EXECUTE")).isOne(),
                () -> assertThat(householdVersion(HOUSEHOLD)).isZero(),
                () -> assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=?",
                        Long.class, HOUSEHOLD)).isEqualTo(HEAD));
    }

    protected void assertRejectedWithoutMutation(String label, Snapshot before, MvcResult result, int status)
            throws Exception {
        Snapshot after = snapshot();
        evidence(label, before, result, after);
        assertAll(label,
                () -> assertHttp(result, status),
                () -> assertThat(after.rows()).as("business rows, versions and approval logs unchanged")
                        .isEqualTo(before.rows()),
                () -> assertThat(after.successfulOperations()).as("no successful transactional operation audit")
                        .isEqualTo(before.successfulOperations()));
    }

    protected Snapshot snapshot() {
        Map<String, List<Map<String, String>>> state = new LinkedHashMap<>();
        for (String table : List.of("household", "household_member", "person", "residence",
                "business_application", "migration_in", "migration_out", "residence_archive",
                "application_material", "sys_approval_request", "sys_approval_log")) {
            state.put(table, rows("SELECT * FROM " + table + " ORDER BY 1"));
        }
        return new Snapshot(state,
                rows("SELECT * FROM operation_log WHERE operation_result='SUCCESS' ORDER BY log_id"),
                rows("SELECT log_id,operation_type,operation_result,request_path FROM operation_log ORDER BY log_id"));
    }

    protected List<Map<String, String>> rows(String sql) {
        return jdbc.query(sql, (rs, row) -> {
            Map<String, String> values = new LinkedHashMap<>();
            for (int column = 1; column <= rs.getMetaData().getColumnCount(); column++) {
                Object value = rs.getObject(column);
                values.put(rs.getMetaData().getColumnLabel(column).toLowerCase(),
                        value == null ? null : value instanceof Clob clob
                                ? clob.getSubString(1, Math.toIntExact(clob.length())) : value.toString());
            }
            return values;
        });
    }

    protected void evidence(String label, Snapshot before, MvcResult result, Snapshot after) throws Exception {
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("case", label);
        evidence.put("httpStatus", result.getResponse().getStatus());
        evidence.put("message", json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("message").asText());
        evidence.put("before", before);
        evidence.put("after", after);
        evidence.put("beforeOperationLogCount", before == null ? null : before.operations().size());
        evidence.put("afterOperationLogCount", after.operations().size());
        System.out.println("ISSUE15_EVIDENCE " + json.writeValueAsString(evidence));
    }

    protected void assertHttp(MvcResult result, int expected) throws Exception {
        assertThat(result.getResponse().getStatus()).withFailMessage("Expected HTTP %s, got %s: %s", expected,
                result.getResponse().getStatus(), result.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .isEqualTo(expected);
    }

    protected JsonNode data(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    protected String bearer(String user) { return "Bearer " + tokens.get(user); }
    protected int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }
    protected int successOperations(String type) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type=? "
                + "AND operation_result='SUCCESS'", Integer.class, type);
    }
    protected Date establishDate(long id) {
        return jdbc.queryForObject("SELECT establish_date FROM household WHERE household_id=?", Date.class, id);
    }
    protected int householdVersion(long id) {
        return jdbc.queryForObject("SELECT version FROM household WHERE household_id=?", Integer.class, id);
    }
    protected Date memberDate(long personId) {
        return jdbc.queryForObject("SELECT join_date FROM household_member WHERE person_id=?", Date.class, personId);
    }
    protected int memberVersion(long personId) {
        return jdbc.queryForObject("SELECT version FROM household_member WHERE person_id=?", Integer.class, personId);
    }
    protected int migrationVersion(long applicationId) {
        return jdbc.queryForObject("SELECT version FROM migration_in WHERE application_id=?", Integer.class, applicationId);
    }

    protected record Snapshot(Map<String, List<Map<String, String>>> rows,
            List<Map<String, String>> successfulOperations, List<Map<String, String>> operations) {}
}
