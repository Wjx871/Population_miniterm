package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;

@SpringBootTest
@AutoConfigureMockMvc
class FloatingSelfScopeAuditIntegrationTest {
    private static final long ROLE_ID = 93000;
    private static final long USER_A = 93001;
    private static final long USER_B = 93002;
    private static final long FLOATING_A = 93001;
    private static final long FLOATING_B = 93002;
    private static final long FLOAT_APP_A = 93011;
    private static final long FLOAT_APP_B = 93012;
    private static final long PERMIT_A = 93021;
    private static final long PERMIT_B = 93022;

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwordEncoder;

    @BeforeEach
    void setup() {
        clearSyntheticRows();

        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,role_level,data_scope,status) " +
                "VALUES(?, 'AUDIT_SELF_SCOPE', '审计模拟本人', 'L2', 'SELF', 'ENABLED')", ROLE_ID);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) " +
                "SELECT ?, permission_id FROM sys_permission WHERE permission_code IN " +
                "('floating:view','floating:close','residence-permit:view','residence-permit:apply')", ROLE_ID);
        String passwordHash = passwordEncoder.encode("123456");
        jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) " +
                "VALUES(?, 'audit-self-a', ?, ?, 2, '模拟本人甲', 'ENABLED')," +
                "(?, 'audit-self-b', ?, ?, 2, '模拟本人乙', 'ENABLED')",
                USER_A, passwordHash, ROLE_ID, USER_B, passwordHash, ROLE_ID);

        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,status,current_status_code) " +
                "VALUES(?, '本人甲', '1', 'AUDIT-IDENTITY-A', '正常', 'REGISTERED')," +
                "(?, '本人乙', '2', 'AUDIT-IDENTITY-B', '正常', 'REGISTERED')", USER_A, USER_B);
        insertApplication(FLOAT_APP_A, "AUDIT-FLOAT-A", "FLOATING_REGISTRATION", USER_A, USER_A);
        insertApplication(FLOAT_APP_B, "AUDIT-FLOAT-B", "FLOATING_REGISTRATION", USER_B, USER_B);
        jdbc.update("INSERT INTO floating_population(floating_id,registration_no,source_application_id,person_id," +
                "source_region_code,current_region_code,current_address,residence_reason_code,residence_proof_type," +
                "arrival_date,registration_date,eligible_from_date,department_id,operator_id,status,current_flag,version) " +
                "VALUES(?, 'FR-SELF-A', ?, ?, '370100', '110000', '测试地址甲', 'EMPLOYMENT', 'RENTAL_CONTRACT'," +
                "DATE '2026-01-01', DATE '2026-07-11', DATE '2026-06-30', 2, ?, 'ACTIVE', 1, 0)," +
                "(?, 'FR-SELF-B', ?, ?, '370100', '110000', '测试地址乙', 'EMPLOYMENT', 'RENTAL_CONTRACT'," +
                "DATE '2026-01-01', DATE '2026-07-11', DATE '2026-06-30', 2, ?, 'ACTIVE', 1, 0)",
                FLOATING_A, FLOAT_APP_A, USER_A, USER_A,
                FLOATING_B, FLOAT_APP_B, USER_B, USER_B);

        insertApplication(PERMIT_A, "AUDIT-PERMIT-A", "RESIDENCE_PERMIT_FIRST_ISSUE", USER_A, USER_A);
        insertApplication(PERMIT_B, "AUDIT-PERMIT-B", "RESIDENCE_PERMIT_FIRST_ISSUE", USER_B, USER_B);
        jdbc.update("INSERT INTO residence_permit(permit_id,permit_no,person_id,floating_id,source_application_id," +
                "issue_region_code,issuing_department_id,issuing_authority,issue_date,valid_from,valid_until,status,current_flag,version) " +
                "VALUES(?, 'RP-SELF-A', ?, ?, ?, '110000', 2, '测试机关', DATE '2026-07-11', DATE '2026-07-11'," +
                "DATE '2027-07-11', 'ACTIVE', 1, 0)," +
                "(?, 'RP-SELF-B', ?, ?, ?, '110000', 2, '测试机关', DATE '2026-07-11', DATE '2026-07-11'," +
                "DATE '2027-07-11', 'ACTIVE', 1, 0)",
                PERMIT_A, USER_A, FLOATING_A, PERMIT_A,
                PERMIT_B, USER_B, FLOATING_B, PERMIT_B);
        jdbc.update("INSERT INTO residence_permit_application(application_id,apply_type,floating_id,permit_id,person_id," +
                "current_region_code,residence_basis_code,business_status,operator_id,version) " +
                "VALUES(?, 'FIRST_ISSUE', ?, ?, ?, '110000', 'EMPLOYMENT', 'COMPLETED', ?, 0)," +
                "(?, 'FIRST_ISSUE', ?, ?, ?, '110000', 'EMPLOYMENT', 'COMPLETED', ?, 0)",
                PERMIT_A, FLOATING_A, PERMIT_A, USER_A, USER_A,
                PERMIT_B, FLOATING_B, PERMIT_B, USER_B, USER_B);
    }

    @AfterEach
    void cleanup() {
        clearSyntheticRows();
    }

    private void clearSyntheticRows() {
        jdbc.update("DELETE FROM residence_permit_log WHERE permit_id IN (?,?)", PERMIT_A, PERMIT_B);
        jdbc.update("DELETE FROM residence_permit_application WHERE application_id IN (?,?)", PERMIT_A, PERMIT_B);
        jdbc.update("DELETE FROM residence_permit_application WHERE application_id IN " +
                "(SELECT application_id FROM business_application WHERE applicant_user_id IN (?,?))", USER_A, USER_B);
        jdbc.update("DELETE FROM residence_permit WHERE permit_id IN (?,?)", PERMIT_A, PERMIT_B);
        jdbc.update("DELETE FROM residence_permit WHERE source_application_id IN " +
                "(SELECT application_id FROM business_application WHERE applicant_user_id IN (?,?))", USER_A, USER_B);
        jdbc.update("DELETE FROM floating_population WHERE floating_id IN (?,?)", FLOATING_A, FLOATING_B);
        jdbc.update("DELETE FROM floating_population WHERE source_application_id IN " +
                "(SELECT application_id FROM business_application WHERE applicant_user_id IN (?,?))", USER_A, USER_B);
        jdbc.update("DELETE FROM floating_registration_application WHERE application_id IN (?,?)", FLOAT_APP_A, FLOAT_APP_B);
        jdbc.update("DELETE FROM floating_registration_application WHERE application_id IN " +
                "(SELECT application_id FROM business_application WHERE applicant_user_id IN (?,?))", USER_A, USER_B);
        jdbc.update("DELETE FROM application_material WHERE application_id IN " +
                "(SELECT application_id FROM business_application WHERE applicant_user_id IN (?,?))", USER_A, USER_B);
        jdbc.update("DELETE FROM sys_approval_log WHERE application_id IN " +
                "(SELECT application_id FROM business_application WHERE applicant_user_id IN (?,?))", USER_A, USER_B);
        jdbc.update("DELETE FROM sys_approval_request WHERE application_id IN " +
                "(SELECT application_id FROM business_application WHERE applicant_user_id IN (?,?))", USER_A, USER_B);
        jdbc.update("DELETE FROM operation_log WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbc.update("DELETE FROM business_application WHERE applicant_user_id IN (?,?) OR application_id IN (?,?,?,?)",
                USER_A, USER_B, FLOAT_APP_A, FLOAT_APP_B, PERMIT_A, PERMIT_B);
        jdbc.update("DELETE FROM sys_user WHERE user_id IN (?,?)", USER_A, USER_B);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=?", ROLE_ID);
        jdbc.update("DELETE FROM sys_role WHERE role_id=?", ROLE_ID);
        jdbc.update("DELETE FROM person WHERE person_id IN (?,?)", USER_A, USER_B);
    }

    @Test
    void selfScopeFloatingListAndDetailsOnlyAllowTheRecordOwner() throws Exception {
        String tokenA = bearer(token("audit-self-a"));
        String tokenB = bearer(token("audit-self-b"));

        mvc.perform(get("/api/floating-populations?includeHistory=true").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].registrationNo").value("FR-SELF-A"));
        mvc.perform(get("/api/floating-populations/" + FLOATING_A).header("Authorization", tokenA))
                .andExpect(status().isOk());
        mvc.perform(get("/api/floating-populations/" + FLOATING_B).header("Authorization", tokenA))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/floating-populations/" + FLOATING_B).header("Authorization", tokenB))
                .andExpect(status().isOk());
        mvc.perform(get("/api/floating-populations/" + FLOATING_A).header("Authorization", tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void selfScopePermitListAndDetailsOnlyAllowTheRecordOwner() throws Exception {
        String tokenA = bearer(token("audit-self-a"));
        String tokenB = bearer(token("audit-self-b"));

        mvc.perform(get("/api/residence-permits").header("Authorization", tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].permitNo").value("RP-S****LF-A"));
        mvc.perform(get("/api/residence-permits/" + PERMIT_A).header("Authorization", tokenA))
                .andExpect(status().isOk());
        mvc.perform(get("/api/residence-permits/" + PERMIT_B).header("Authorization", tokenA))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/residence-permits/" + PERMIT_B).header("Authorization", tokenB))
                .andExpect(status().isOk());
        mvc.perform(get("/api/residence-permits/" + PERMIT_A).header("Authorization", tokenB))
                .andExpect(status().isForbidden());
    }

    @Test
    void selfUserCannotCloseAnotherUsersFloatingRegistration() throws Exception {
        mvc.perform(post("/api/floating-populations/" + FLOATING_B + "/close")
                        .header("Authorization", bearer(token("audit-self-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reasonCode\":\"LEFT_REGION\",\"comment\":\"synthetic\",\"version\":0}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT status FROM floating_population WHERE floating_id=?", String.class, FLOATING_B))
                .isEqualTo("ACTIVE");
    }

    @Test
    void selfUserCannotStartPermitActionForAnotherUsersPermit() throws Exception {
        mvc.perform(post("/api/residence-permits/applications/" + PERMIT_B + "/endorsement")
                        .header("Authorization", bearer(token("audit-self-a")))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"测试\",\"reason\":\"synthetic\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE applicant_user_id=? " +
                "AND business_type='RESIDENCE_PERMIT_ENDORSEMENT'", Integer.class, USER_A)).isZero();
    }

    private void insertApplication(long id, String no, String type, long applicant, long person) {
        jdbc.update("INSERT INTO business_application(application_id,application_no,business_type,title,applicant_user_id," +
                        "applicant_department_id,applicant_region_code,target_person_id,status,reason,version) " +
                        "VALUES(?,?,?,'审计模拟',?,2,'110000',?,'COMPLETED','synthetic',0)",
                id, no, type, applicant, person);
    }

    private String token(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
