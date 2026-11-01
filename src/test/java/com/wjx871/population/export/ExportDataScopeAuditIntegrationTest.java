package com.wjx871.population.export;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "export.dir=./target/test-exports-audit",
        "export.normal-max-rows=10",
        "export.sensitive-max-rows=20"
})
@AutoConfigureMockMvc
class ExportDataScopeAuditIntegrationTest {
    private static final long SELF_ROLE = 96000;
    private static final long DEPARTMENT_ROLE = 96010;
    private static final long SELF_USER = 96001;
    private static final long DEPARTMENT_USER = 96002;
    private static final long PERSON_SELF = 96001;
    private static final long PERSON_DEPARTMENT = 96002;
    private static final long PERSON_REGION = 96003;
    private static final long BASE_APP_SELF = 96101;
    private static final long BASE_APP_DEPARTMENT = 96102;
    private static final long BASE_APP_REGION = 96103;
    private static final long FLOAT_APP_SELF = 96201;
    private static final long FLOAT_APP_DEPARTMENT = 96202;
    private static final long FLOAT_APP_REGION = 96203;
    private static final long PERMIT_APP_SELF = 96301;
    private static final long PERMIT_APP_DEPARTMENT = 96302;
    private static final long PERMIT_APP_REGION = 96303;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ExportMapper mapper;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        jdbc.update("DELETE FROM data_export_log");
        jdbc.update("DELETE FROM data_export_request");
        jdbc.update("DELETE FROM residence_permit_log");
        jdbc.update("DELETE FROM residence_permit_application");
        jdbc.update("DELETE FROM residence_permit");
        jdbc.update("DELETE FROM floating_population");
        jdbc.update("DELETE FROM floating_registration_application");
        jdbc.update("DELETE FROM residence_archive");
        jdbc.update("DELETE FROM cancellation_record");
        jdbc.update("DELETE FROM migration_in");
        jdbc.update("DELETE FROM migration_out");
        jdbc.update("DELETE FROM residence");
        jdbc.update("DELETE FROM household_member");
        jdbc.update("DELETE FROM sys_approval_log");
        jdbc.update("DELETE FROM sys_approval_request");
        jdbc.update("DELETE FROM application_material");
        jdbc.update("DELETE FROM business_application");
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM household WHERE household_id IN (?,?,?)", PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION);
        jdbc.update("DELETE FROM person WHERE person_id IN (?,?,?)", PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION);
        jdbc.update("DELETE FROM sys_user WHERE user_id IN (?,?)", SELF_USER, DEPARTMENT_USER);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN (?,?)", SELF_ROLE, DEPARTMENT_ROLE);
        jdbc.update("DELETE FROM sys_role WHERE role_id IN (?,?)", SELF_ROLE, DEPARTMENT_ROLE);

        createScopedUsers();
        createScopedRows();
    }

    @Test
    void everyExportModuleAppliesScopeToRowsAndCount() {
        List<String> modules = List.of("PERSON", "HOUSEHOLD", "MIGRATION", "RESIDENCE_ARCHIVE",
                "CANCELLATION", "FLOATING_POPULATION", "RESIDENCE_PERMIT", "OPERATION_LOG");
        List<ScopeExpectation> scopes = List.of(
                new ScopeExpectation("SELF", new DataScopeCriteria(DataScope.SELF, SELF_USER, 2L, "110000"), 1),
                new ScopeExpectation("DEPARTMENT", new DataScopeCriteria(DataScope.DEPARTMENT, DEPARTMENT_USER, 2L, "110000"), 2),
                new ScopeExpectation("REGION", new DataScopeCriteria(DataScope.REGION, 2L, 2L, "110000"), 2),
                new ScopeExpectation("ALL", new DataScopeCriteria(DataScope.ALL, 3L, 3L, "110000"), 3));

        for (String module : modules) {
            for (ScopeExpectation expected : scopes) {
                long expectedRows = "HOUSEHOLD".equals(module) && "SELF".equals(expected.name()) ? 0 : expected.rows();
                long count = mapper.countRows(module, Map.of(), expected.criteria());
                int selected = mapper.selectRows(module, Map.of(), expected.criteria(), 20).size();
                assertThat(count).as(module + " count for " + expected.name()).isEqualTo(expectedRows);
                assertThat(selected).as(module + " selected rows for " + expected.name()).isEqualTo(expectedRows);
            }
        }
    }

    @Test
    void normalExportHonorsSelfDepartmentRegionAndAll() throws Exception {
        assertThat(normalExport("audit-export-self")).isEqualTo(1);
        assertThat(normalExport("audit-export-department")).isEqualTo(2);
        assertThat(normalExport("population")).isEqualTo(2);
        assertThat(normalExport("admin")).isEqualTo(3);
    }

    @Test
    void allScopeAdministratorCannotExpandApprovedApplicantsSelfExport() throws Exception {
        long applicationId = createSensitive("audit-export-self");
        addExportMaterial(applicationId);
        submit(applicationId, "audit-export-self");
        approve(applicationId);

        String body = mvc.perform(post("/api/exports/sensitive/applications/" + applicationId + "/execute")
                        .header("Authorization", bearer(token("admin")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        long exportLogId = json.readTree(body).path("data").path("exportLogId").asLong();

        assertThat(jdbc.queryForObject("SELECT row_count FROM data_export_log WHERE export_log_id=?", Integer.class, exportLogId))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT requested_by FROM data_export_log WHERE export_log_id=?", Long.class, exportLogId))
                .isEqualTo(SELF_USER);
        assertThat(jdbc.queryForObject("SELECT executed_by FROM data_export_log WHERE export_log_id=?", Long.class, exportLogId))
                .isEqualTo(3L);
        assertThat(jdbc.queryForObject("SELECT department_id FROM data_export_log WHERE export_log_id=?", Long.class, exportLogId))
                .isEqualTo(2L);
        String storedFilename = jdbc.queryForObject("SELECT stored_filename FROM data_export_log WHERE export_log_id=?",
                String.class, exportLogId);
        Path workbookPath = Path.of("target/test-exports-audit").resolve(storedFilename);
        try (var input = Files.newInputStream(workbookPath); Workbook workbook = new XSSFWorkbook(input)) {
            assertThat(workbook.getSheetAt(0).getLastRowNum()).isEqualTo(1);
            assertThat(workbook.getSheetAt(0).getRow(1).getCell(0).getStringCellValue()).isEqualTo("本人范围人口");
        }
    }

    @Test
    void invalidApplicantScopeSnapshotIsRejectedBeforeSensitiveExport() throws Exception {
        long applicationId = createSensitive("audit-export-self");
        addExportMaterial(applicationId);
        submit(applicationId, "audit-export-self");
        approve(applicationId);
        jdbc.update("UPDATE data_export_request SET export_scope='UNKNOWN' WHERE application_id=?", applicationId);

        mvc.perform(post("/api/exports/sensitive/applications/" + applicationId + "/execute")
                        .header("Authorization", bearer(token("admin")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"version\":2}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("申请人的数据权限范围快照无效，已拒绝执行"));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM data_export_log WHERE application_id=?", Integer.class, applicationId))
                .isZero();
    }

    private void createScopedUsers() {
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,role_level,data_scope,status) " +
                "VALUES(?, 'AUDIT_EXPORT_SELF', '审计导出本人', 'L2', 'SELF', 'ENABLED')," +
                "(?, 'AUDIT_EXPORT_DEPT', '审计导出部门', 'L2', 'DEPARTMENT', 'ENABLED')", SELF_ROLE, DEPARTMENT_ROLE);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) " +
                "SELECT ?, permission_id FROM sys_permission WHERE permission_code IN " +
                "('data:export:normal','data:export:sensitive:apply','application:submit')", SELF_ROLE);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) " +
                "SELECT ?, permission_id FROM sys_permission WHERE permission_code='data:export:normal'", DEPARTMENT_ROLE);
        String passwordHash = passwordEncoder.encode("123456");
        jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) " +
                "VALUES(?, 'audit-export-self', ?, ?, 2, '审计导出本人', 'ENABLED')," +
                "(?, 'audit-export-department', ?, ?, 2, '审计导出部门', 'ENABLED')",
                SELF_USER, passwordHash, SELF_ROLE, DEPARTMENT_USER, passwordHash, DEPARTMENT_ROLE);
    }

    private void createScopedRows() {
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,phone,current_address,status,current_status_code) " +
                "VALUES(?, '本人范围人口', '1', 'AUDIT-ID-SELF', '13800000001', '本人地址', '正常', 'REGISTERED')," +
                "(?, '同部门人口', '2', 'AUDIT-ID-DEPT', '13800000002', '部门地址', '正常', 'REGISTERED')," +
                "(?, '外区人口', '1', 'AUDIT-ID-REGION', '13800000003', '外区地址', '正常', 'REGISTERED')",
                PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION);
        jdbc.update("INSERT INTO household(household_id,household_no,address,region_code,establish_date,status,version) " +
                "VALUES(?, 'AUDIT-HH-SELF', '本人户地址', '110000', DATE '2020-01-01', 'ACTIVE', 0)," +
                "(?, 'AUDIT-HH-DEPT', '部门户地址', '110000', DATE '2020-01-01', 'ACTIVE', 0)," +
                "(?, 'AUDIT-HH-REGION', '外区户地址', '120000', DATE '2020-01-01', 'ACTIVE', 0)",
                PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION);
        insertApplication(BASE_APP_SELF, "AUDIT-BASE-SELF", "MIGRATION_IN", SELF_USER, 2, "110000", PERSON_SELF, PERSON_SELF);
        insertApplication(BASE_APP_DEPARTMENT, "AUDIT-BASE-DEPT", "MIGRATION_OUT", 2, 2, "110000", PERSON_DEPARTMENT, PERSON_DEPARTMENT);
        insertApplication(BASE_APP_REGION, "AUDIT-BASE-REGION", "MIGRATION_IN", 8, 6, "120000", PERSON_REGION, PERSON_REGION);
        insertResidence(PERSON_SELF, "110000");
        insertResidence(PERSON_DEPARTMENT, "110000");
        insertResidence(PERSON_REGION, "120000");
        insertArchives();
        insertCancellations();
        insertFloatingRows();
        insertPermitRows();
        insertLogs();
    }

    private void insertResidence(long personId, String regionCode) {
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code," +
                "register_date,start_date,status,created_by,version) VALUES(?,?,?,?,'OTHER',DATE '2020-01-01'," +
                "DATE '2020-01-01','ACTIVE',1,0)", personId, personId, "合成地址" + personId, regionCode);
    }

    private void insertArchives() {
        long[] apps = {BASE_APP_SELF, BASE_APP_DEPARTMENT, BASE_APP_REGION};
        long[] people = {PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION};
        String[] regions = {"110000", "110000", "120000"};
        for (int i = 0; i < apps.length; i++) {
            jdbc.update("INSERT INTO residence_archive(original_registration_id,person_id,household_id,application_id," +
                    "archive_type,person_name_snapshot,identity_no_snapshot,household_no_snapshot,registered_address_snapshot," +
                    "region_code_snapshot,end_date_snapshot,archived_by,archived_at) " +
                    "VALUES(?,?,?,?, 'MIGRATION_OUT', ?, ?, ?, ?, ?, DATE '2026-01-01', 1, CURRENT_TIMESTAMP)",
                    97000L + i, people[i], people[i], apps[i], "归档人口" + i, "SYNTHETIC-" + i,
                    "AUDIT-HH-" + i, "归档地址" + i, regions[i]);
        }
    }

    private void insertCancellations() {
        long[] apps = {BASE_APP_SELF, BASE_APP_DEPARTMENT, BASE_APP_REGION};
        long[] people = {PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION};
        String[] regions = {"110000", "110000", "120000"};
        for (int i = 0; i < apps.length; i++) {
            jdbc.update("INSERT INTO cancellation_record(cancellation_no,application_id,cancel_object_type,person_id," +
                    "cancel_reason_code,event_date,business_status,person_name_snapshot,identity_no_snapshot," +
                    "address_snapshot,region_code_snapshot) VALUES(?,?,'PERSON',?,'DEATH',DATE '2026-01-01'," +
                    "'COMPLETED',?,?,?,?)", "AUDIT-CANCEL-" + i, apps[i], people[i], "注销人口" + i,
                    "SYNTHETIC-CANCEL-" + i, "注销地址" + i, regions[i]);
        }
    }

    private void insertFloatingRows() {
        long[] apps = {FLOAT_APP_SELF, FLOAT_APP_DEPARTMENT, FLOAT_APP_REGION};
        long[] people = {PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION};
        long[] operators = {SELF_USER, 2, 8};
        long[] departments = {2, 2, 6};
        String[] regions = {"110000", "110000", "120000"};
        for (int i = 0; i < apps.length; i++) {
            insertApplication(apps[i], "AUDIT-FLOAT-" + i, "FLOATING_REGISTRATION", operators[i], departments[i],
                    regions[i], people[i], null);
            jdbc.update("INSERT INTO floating_population(floating_id,registration_no,source_application_id,person_id," +
                    "source_region_code,current_region_code,current_address,residence_reason_code,residence_proof_type," +
                    "arrival_date,registration_date,department_id,operator_id,status,current_flag,version) " +
                    "VALUES(?,?,?,?, '370100', ?, ?, 'EMPLOYMENT', 'RENTAL_CONTRACT', DATE '2026-01-01'," +
                    "DATE '2026-07-11', ?, ?, 'ACTIVE', 1, 0)",
                    apps[i], "AUDIT-FLOAT-ROW-" + i, apps[i], people[i], regions[i], "流动地址" + i,
                    departments[i], operators[i]);
        }
    }

    private void insertPermitRows() {
        long[] apps = {PERMIT_APP_SELF, PERMIT_APP_DEPARTMENT, PERMIT_APP_REGION};
        long[] floatings = {FLOAT_APP_SELF, FLOAT_APP_DEPARTMENT, FLOAT_APP_REGION};
        long[] people = {PERSON_SELF, PERSON_DEPARTMENT, PERSON_REGION};
        long[] operators = {SELF_USER, 2, 8};
        long[] departments = {2, 2, 6};
        String[] regions = {"110000", "110000", "120000"};
        for (int i = 0; i < apps.length; i++) {
            insertApplication(apps[i], "AUDIT-PERMIT-APP-" + i, "RESIDENCE_PERMIT_FIRST_ISSUE", operators[i],
                    departments[i], regions[i], people[i], null);
            jdbc.update("INSERT INTO residence_permit(permit_id,permit_no,person_id,floating_id,source_application_id," +
                    "issue_region_code,issuing_department_id,issuing_authority,issue_date,valid_from,valid_until,status,current_flag,version) " +
                    "VALUES(?,?,?,?,?,?,?,'测试机关',DATE '2026-07-11',DATE '2026-07-11',DATE '2027-07-11','ACTIVE',1,0)",
                    apps[i], "AUDIT-PERMIT-" + i, people[i], floatings[i], apps[i], regions[i], departments[i]);
            jdbc.update("INSERT INTO residence_permit_application(application_id,apply_type,floating_id,permit_id,person_id," +
                    "current_region_code,residence_basis_code,business_status,operator_id,version) " +
                    "VALUES(?, 'FIRST_ISSUE', ?, ?, ?, ?, 'EMPLOYMENT', 'COMPLETED', ?, 0)",
                    apps[i], floatings[i], apps[i], people[i], regions[i], operators[i]);
        }
    }

    private void insertLogs() {
        jdbc.update("INSERT INTO operation_log(user_id,operation_type,module_name,operation_result) " +
                "VALUES(?, 'AUDIT_SELF', 'EXPORT', 'SUCCESS'), (?, 'AUDIT_DEPT', 'EXPORT', 'SUCCESS'), " +
                "(8, 'AUDIT_REGION', 'EXPORT', 'SUCCESS')", SELF_USER, 2);
    }

    private void insertApplication(long id, String no, String type, long applicant, long department,
                                   String region, Long person, Long household) {
        jdbc.update("INSERT INTO business_application(application_id,application_no,business_type,title,applicant_user_id," +
                        "applicant_department_id,applicant_region_code,target_person_id,target_household_id,status,reason,version) " +
                        "VALUES(?,?,?,'审计范围合成申请',?,?,?,?,?,'COMPLETED','synthetic',0)",
                id, no, type, applicant, department, region, person, household);
    }

    private long normalExport(String username) throws Exception {
        String body = mvc.perform(post("/api/exports/normal")
                        .header("Authorization", bearer(token(username)))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"PERSON\",\"filters\":{},\"fields\":[\"name\"]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long exportLogId = json.readTree(body).path("data").path("exportLogId").asLong();
        return jdbc.queryForObject("SELECT row_count FROM data_export_log WHERE export_log_id=?", Integer.class, exportLogId);
    }

    private long createSensitive(String username) throws Exception {
        String body = mvc.perform(post("/api/exports/sensitive/applications")
                        .header("Authorization", bearer(token(username)))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"module\":\"PERSON\",\"filters\":{\"regionCode\":\"110000\"}," +
                                "\"fields\":[\"name\",\"identityNo\",\"phone\",\"registeredAddress\"]," +
                                "\"reason\":\"synthetic audit\",\"expectedRowLimit\":10,\"title\":\"审计敏感导出\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").asLong();
    }

    private void addExportMaterial(long applicationId) {
        String hash = java.util.UUID.randomUUID().toString().replace("-", "") +
                java.util.UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename," +
                "stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) " +
                "VALUES(?,'EXPORT_JUSTIFICATION','说明','audit.pdf',?,'test','application/pdf',1,?,1,'VERIFIED',7,2)",
                applicationId, java.util.UUID.randomUUID() + ".pdf", hash);
    }

    private void submit(long applicationId, String username) throws Exception {
        mvc.perform(post("/api/applications/" + applicationId + "/submit")
                        .header("Authorization", bearer(token(username))))
                .andExpect(status().isOk());
    }

    private void approve(long applicationId) throws Exception {
        long approvalId = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",
                Long.class, applicationId);
        mvc.perform(post("/api/approvals/" + approvalId + "/approve")
                        .header("Authorization", bearer(token("approver")))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"同意\",\"version\":0}"))
                .andExpect(status().isOk());
    }

    private String token(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private record ScopeExpectation(String name, DataScopeCriteria criteria, long rows) {}
}
