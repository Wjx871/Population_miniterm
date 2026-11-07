package com.wjx871.population.keypopulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.approval.ApprovalService;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Real JWT filter, database-backed AuthService, method security and H2; no mocked identity/RBAC. */
@SpringBootTest(properties = "issue43.test-isolation=true")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Issue43KeyPopulationSubmitPermissionIntegrationTest {
    private static final long FIXTURE_ID = 430001;
    private static final String USERNAME = "issue43-applicant";
    private static final String APPLY = "key-population:apply";
    private static final String SUBMIT = "application:submit";
    private static final List<String> OBSERVED_TABLES = List.of("business_application",
            "key_population_application", "sys_approval_request", "sys_approval_log",
            "operation_log", "application_material", "key_population", "key_population_history",
            "person", "residence", "household");

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService auth;
    @Autowired ApprovalService approvals;
    private String bearer;
    private long firstLogId;

    @BeforeEach
    void setup() throws Exception {
        // A distinct Spring context gives this class its own random H2 database. Never edit shared roles.
        firstLogId = jdbc.queryForObject("SELECT COALESCE(MAX(log_id),0) FROM operation_log", Long.class);
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,role_level,data_scope,status) "
                + "VALUES(?,'ISSUE43_APPLICANT','专项提交测试','L2','ALL','ENABLED')", FIXTURE_ID);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,permission_id "
                + "FROM sys_permission WHERE permission_code IN (?,?, 'application:create')", FIXTURE_ID, APPLY, SUBMIT);
        jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) "
                + "SELECT ?,?,password_hash,?,2,'专项提交测试用户','ENABLED' FROM sys_user WHERE user_id=2",
                FIXTURE_ID, USERNAME, FIXTURE_ID);
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,status,current_status_code) "
                + "VALUES(?,'专项提交测试人员','M','110105198001010016','正常','REGISTERED')", FIXTURE_ID);
        jdbc.update("INSERT INTO household(household_id,household_no,address,region_code,household_type,establish_date,status,version) "
                + "VALUES(?,'ISSUE43-HOUSEHOLD','合成地址','120101','FAMILY',DATE '2020-01-01','ACTIVE',0)", FIXTURE_ID);
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,version) "
                + "VALUES(?,?,'合成地址','120101','OTHER',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',0)", FIXTURE_ID, FIXTURE_ID);
        bearer = token(USERNAME);
        assertPermissions(true, true);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        String ownApps = "SELECT application_id FROM business_application WHERE applicant_user_id=" + FIXTURE_ID;
        for (String table : List.of("sys_approval_log", "sys_approval_request", "application_material", "key_population_application")) {
            jdbc.update("DELETE FROM " + table + " WHERE application_id IN (" + ownApps + ")");
        }
        jdbc.update("DELETE FROM key_population_history WHERE person_id=?", FIXTURE_ID);
        jdbc.update("DELETE FROM key_population WHERE person_id=?", FIXTURE_ID);
        jdbc.update("DELETE FROM business_application WHERE applicant_user_id=?", FIXTURE_ID);
        // Includes login/approval logs from seeded operators, confined to this class's isolated DB/run.
        jdbc.update("DELETE FROM operation_log WHERE log_id>?", firstLogId);
        jdbc.update("DELETE FROM residence WHERE person_id=?", FIXTURE_ID);
        jdbc.update("DELETE FROM household WHERE household_id=?", FIXTURE_ID);
        jdbc.update("DELETE FROM person WHERE person_id=?", FIXTURE_ID);
        jdbc.update("DELETE FROM sys_user WHERE user_id=?", FIXTURE_ID);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", FIXTURE_ID);
        jdbc.update("DELETE FROM sys_role WHERE role_id=?", FIXTURE_ID);
    }

    @ParameterizedTest
    @EnumSource(KeyOperationType.class)
    void revokedApplyDraftCannotUseGenericSubmit(KeyOperationType operation) throws Exception {
        long app = draft(operation, true);
        revoke(APPLY);
        assertPermissions(false, true); // SAME pre-revocation JWT, freshly loaded database permissions.
        assertThat(jdbc.queryForObject("SELECT applicant_user_id FROM business_application WHERE application_id=?", Long.class, app))
                .isEqualTo(FIXTURE_ID);
        assertThat(auth.loadUser(USERNAME).dataScope().name()).isEqualTo("ALL");
        deniedWithoutEffects(app, generic(app), "revoked-apply-draft-" + operation);
    }

    @ParameterizedTest
    @EnumSource(KeyOperationType.class)
    void revokedApplyReturnedCannotResubmit(KeyOperationType operation) throws Exception {
        long app = draft(operation, true);
        submit(generic(app), 200);
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, app);
        mvc.perform(post("/api/approvals/" + approval + "/approve").header("Authorization", token("approver"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"同意\",\"version\":0}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/applications/" + app + "/return").header("Authorization", token("household"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"补正\",\"version\":2}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, app)).isEqualTo("RETURNED");
        revoke(APPLY);
        assertPermissions(false, true);
        deniedWithoutEffects(app, generic(app), "revoked-apply-returned-" + operation);
    }

    @ParameterizedTest
    @CsvSource({"REGISTER,true", "RELEASE,true", "REGISTER,false", "RELEASE,false"})
    void bothPermissionsAllowEitherRoute(KeyOperationType operation, boolean generic) throws Exception {
        long app = draft(operation, true);
        submit(generic ? generic(app) : specialized(operation, app), 200);
        assertSubmitted(app);
        System.out.printf("ISSUE43_AUTHORIZED operation=%s generic=%s HTTP=200%n", operation, generic);
    }

    @ParameterizedTest
    @EnumSource(KeyOperationType.class)
    void applyRevocationAlsoRemainsForbiddenOnSpecializedRoute(KeyOperationType operation) throws Exception {
        long app = draft(operation, true);
        revoke(APPLY);
        assertPermissions(false, true);
        deniedWithoutEffects(app, specialized(operation, app), "specialized-apply-rbac-" + operation);
    }

    @ParameterizedTest
    @CsvSource({"REGISTER,true", "RELEASE,true", "REGISTER,false", "RELEASE,false"})
    void missingSubmitCannotUseEitherRoute(KeyOperationType operation, boolean generic) throws Exception {
        long app = draft(operation, true);
        revoke(SUBMIT);
        assertPermissions(true, false);
        deniedWithoutEffects(app, generic ? generic(app) : specialized(operation, app), "missing-submit-rbac-" + operation + "-" + generic);
    }

    @ParameterizedTest
    @CsvSource({"REGISTER,key-population:apply", "RELEASE,key-population:apply", "REGISTER,application:submit", "RELEASE,application:submit"})
    void approvalServiceRequiresBothPermissionsFromRealLoadedUser(KeyOperationType operation, String permission) throws Exception {
        long app = draft(operation, true);
        revoke(permission);
        AuthenticatedUser current = auth.loadUser(USERNAME);
        assertThat(current.permissions()).doesNotContain(permission);
        var before = snapshot();
        // Service-only call has no HTTP controller; principal comes from the real DB-backed AuthService.
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(current, null, current.getAuthorities()));
        try {
            assertThatThrownBy(() -> approvals.submit(app, new MockHttpServletRequest("POST", generic(app))))
                    .isInstanceOfSatisfying(BusinessException.class, ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.FORBIDDEN));
        } finally {
            SecurityContextHolder.clearContext();
        }
        assertThat(snapshot()).as("service denial leaves every observed row unchanged").isEqualTo(before);
        System.out.printf("ISSUE43_SERVICE_DENIED operation=%s missing=%s unchangedTables=%d%n", operation, permission, before.size());
    }

    @ParameterizedTest
    @CsvSource({"REGISTER,true", "RELEASE,true", "REGISTER,false", "RELEASE,false"})
    void bothPermissionsStillEnforcePersonDataScope(KeyOperationType operation, boolean generic) throws Exception {
        long app = draft(operation, true);
        jdbc.update("UPDATE sys_role SET data_scope='REGION' WHERE role_id=?", FIXTURE_ID);
        assertPermissions(true, true);
        assertThat(auth.loadUser(USERNAME).regionCodes()).isNotEmpty().doesNotContain("120101");
        deniedWithoutEffects(app, generic ? generic(app) : specialized(operation, app), "person-scope-" + operation + "-" + generic);
    }

    @ParameterizedTest
    @EnumSource(KeyOperationType.class)
    void bothPermissionsStillRequireOriginalMaterials(KeyOperationType operation) throws Exception {
        long app = draft(operation, false);
        var before = snapshot();
        submit(generic(app), 409);
        assertThat(snapshot()).isEqualTo(before);
    }

    @ParameterizedTest
    @EnumSource(KeyOperationType.class)
    void restoringPermissionAllowsSameJwtAndDraftToSubmit(KeyOperationType operation) throws Exception {
        long app = draft(operation, true);
        revoke(APPLY);
        assertPermissions(false, true);
        deniedWithoutEffects(app, generic(app), "before-restore-" + operation);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,permission_id FROM sys_permission WHERE permission_code=?", FIXTURE_ID, APPLY);
        assertPermissions(true, true);
        submit(generic(app), 200);
        assertSubmitted(app);
        System.out.printf("ISSUE43_RESTORED operation=%s sameJWT=true HTTP=200%n", operation);
    }

    @org.junit.jupiter.api.Test
    void genericOtherBusinessStillSubmitsWithoutKeyPopulationApply() throws Exception {
        revoke(APPLY);
        assertPermissions(false, true);
        String result = mvc.perform(post("/api/applications").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"businessType\":\"GENERAL_SERVICE\",\"title\":\"普通申请\",\"reason\":\"普通业务\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long app = json.readTree(result).path("data").path("applicationId").asLong();
        material(app, "OTHER");
        submit(generic(app), 200);
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, app)).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_population_application WHERE application_id=?", Integer.class, app)).isZero();
    }

    private long draft(KeyOperationType operation, boolean withMaterials) throws Exception {
        String path;
        String body;
        if (operation == KeyOperationType.REGISTER) {
            path = "/api/key-populations/register-applications";
            body = "{\"personId\":" + FIXTURE_ID + ",\"populationType\":\"OTHER\",\"attentionLevel\":\"MEDIUM\",\"registerReason\":\"合成建档\",\"registerDate\":\"2026-07-13\",\"title\":\"建档申请\"}";
        } else {
            jdbc.update("INSERT INTO key_population(key_id,person_id,key_type,management_level,register_reason,register_date,responsible_department_id,responsible_user_id,status,version) "
                    + "VALUES(?,?,'OTHER','MEDIUM','合成建档',DATE '2026-01-01',2,?,'ACTIVE',0)", FIXTURE_ID, FIXTURE_ID, FIXTURE_ID);
            path = "/api/key-populations/" + FIXTURE_ID + "/release-applications";
            body = "{\"releaseReason\":\"合成解除\",\"releaseDate\":\"2026-07-13\",\"title\":\"解除申请\"}";
        }
        String result = mvc.perform(post(path).header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long app = json.readTree(result).path("data").path("application").path("applicationId").asLong();
        assertThat(app).isPositive();
        if (withMaterials) {
            material(app, "KEY_POPULATION_BASIS");
            material(app, "SITUATION_DESCRIPTION");
        }
        return app;
    }

    private void material(long app, String type) {
        String id = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,uploaded_by) "
                + "VALUES(?,?,?,'test.pdf',?,'synthetic-no-file','application/pdf',1,?,1,'VERIFIED',?)", app, type, type, id + ".pdf", id + id, FIXTURE_ID);
    }

    private void revoke(String permission) {
        assertThat(jdbc.update("DELETE FROM sys_role_permission WHERE role_id=? AND permission_id IN "
                + "(SELECT permission_id FROM sys_permission WHERE permission_code=?)", FIXTURE_ID, permission)).isOne();
    }

    private void assertPermissions(boolean apply, boolean submit) throws Exception {
        JsonNode current = json.readTree(mvc.perform(get("/api/auth/me").header("Authorization", bearer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).path("data");
        assertThat(current.path("userId").asLong()).isEqualTo(FIXTURE_ID);
        List<String> permissions = new ArrayList<>();
        current.path("permissions").forEach(p -> permissions.add(p.asText()));
        assertThat(permissions.contains(APPLY)).isEqualTo(apply);
        assertThat(permissions.contains(SUBMIT)).isEqualTo(submit);
        System.out.printf("ISSUE43_CURRENT_PERMISSIONS apply=%s submit=%s sameJWT=true%n", apply, submit);
    }

    private void deniedWithoutEffects(long app, String path, String scenario) throws Exception {
        var before = snapshot();
        int actual = mvc.perform(post(path).header("Authorization", bearer)).andReturn().getResponse().getStatus();
        boolean unchanged = snapshot().equals(before);
        System.out.printf("ISSUE43_DENIAL scenario=%s app=%d HTTP=%d unchanged=%s observedTables=%d%n", scenario, app, actual, unchanged, before.size());
        // On the old code, this records HTTP 200 + changed rows before the expected RED assertion.
        assertThat(actual).as(scenario + " HTTP").isEqualTo(403);
        assertThat(unchanged).as(scenario + " business/status/version/approval/material/person/operation rows unchanged").isTrue();
    }

    private Map<String, List<List<String>>> snapshot() {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (String table : OBSERVED_TABLES) {
            // HTTP method-security denials may legitimately append ACCESS_DENIED/FAILED audit rows.
            String where = table.equals("operation_log") ? " WHERE operation_result='SUCCESS'" : "";
            result.put(table, jdbc.query("SELECT * FROM " + table + where + " ORDER BY 1", (rs, row) -> {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) values.add(rs.getString(i));
                return values;
            }));
        }
        return result;
    }

    private void assertSubmitted(long app) {
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, app)).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT version FROM business_application WHERE application_id=?", Integer.class, app)).isOne();
        assertThat(jdbc.queryForObject("SELECT business_status FROM key_population_application WHERE application_id=?", String.class, app)).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT version FROM key_population_application WHERE application_id=?", Integer.class, app)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_request WHERE application_id=?", Integer.class, app)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='SUBMIT'", Integer.class, app)).isOne();
    }

    private void submit(String path, int expected) throws Exception {
        mvc.perform(post(path).header("Authorization", bearer)).andExpect(status().is(expected));
    }
    private String token(String username) throws Exception {
        String result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", "123456"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(result).path("data").path("token").asText();
    }
    private String generic(long app) { return "/api/applications/" + app + "/submit"; }
    private String specialized(KeyOperationType op, long app) {
        return "/api/key-populations/" + op.name().toLowerCase(java.util.Locale.ROOT) + "-applications/" + app + "/submit";
    }
}
