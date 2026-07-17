package com.wjx871.population.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AuthRbacIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void resetAuditState() {
        jdbcTemplate.update("DELETE FROM operation_log");
        jdbcTemplate.update("UPDATE sys_user SET last_login_time = NULL, last_login_ip = NULL");
    }

    @Test
    void correctCredentialsReturnRealJwtAndUser() throws Exception {
        mockMvc.perform(login("admin", "123456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.roleCode").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.user.roleLevel").value("L3"))
                .andExpect(jsonPath("$.data.user.dataScope").value("ALL"));
    }

    @Test
    void allFiveDemonstrationAccountsCanLogin() throws Exception {
        for (String username : new String[]{"viewer", "population", "household", "approver", "admin"}) {
            mockMvc.perform(login(username, "123456"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.token").isNotEmpty());
        }
    }

    @Test
    void wrongPasswordReturns401() throws Exception {
        mockMvc.perform(login("admin", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void unknownAccountReturns401() throws Exception {
        mockMvc.perform(login("missing", "123456"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void disabledAccountAndRoleReturn403() throws Exception {
        mockMvc.perform(login("disabled", "123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        mockMvc.perform(login("disabled-role", "123456"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/persons"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.timestamp").isNumber());
    }

    @Test
    void invalidTokenReturnsJson401() throws Exception {
        mockMvc.perform(get("/api/persons").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    void expiredTokenReturnsJson401() throws Exception {
        String secret = "test-jwt-secret-must-have-at-least-thirty-two-bytes";
        String token = Jwts.builder().subject("viewer")
                .issuedAt(Date.from(Instant.now().minusSeconds(120)))
                .expiration(Date.from(Instant.now().minusSeconds(60)))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
                .compact();
        mockMvc.perform(get("/api/persons").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已过期"));
    }

    @Test
    void l1CanReadButCannotEdit() throws Exception {
        String token = tokenFor("viewer");
        mockMvc.perform(get("/api/persons").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/persons")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personJson("110101199901019998")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'ACCESS_DENIED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void l2CanEdit() throws Exception {
        String token = tokenFor("population");
        long imageId = uploadIdCardImage(token);
        mockMvc.perform(post("/api/persons")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(personJsonWithImage("110101199901018899", imageId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(201));

        Long populationUserId = jdbcTemplate.queryForObject(
                "SELECT user_id FROM sys_user WHERE username = 'population'", Long.class);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by_user_id FROM person WHERE id_card = ?", Long.class,
                "110101199901018899")).isEqualTo(populationUserId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_region_code FROM person WHERE id_card = ?", String.class,
                "110101199901018899")).isEqualTo("110000");

        mockMvc.perform(get("/api/persons").param("idCard", "110101199901018899")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/persons").param("idCard", "110101199901018899")
                        .header("Authorization", bearer(tokenFor("household"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void householdCanViewRegionalMigrationApplicationsWithoutReceivingApprovalScope() throws Exception {
        String migrationNo = "APP-MIGRATION-SCOPE-001";
        String genericNo = "APP-GENERAL-SCOPE-001";
        jdbcTemplate.update("""
                INSERT INTO business_application(application_no,business_type,title,applicant_user_id,
                    applicant_department_id,applicant_region_code,status,reason,version)
                VALUES(?, 'MIGRATION_IN', 'Regional migration', 2, 2, '110105', 'DRAFT', 'test', 0)
                """, migrationNo);
        Long migrationId = jdbcTemplate.queryForObject(
                "SELECT application_id FROM business_application WHERE application_no=?", Long.class, migrationNo);
        jdbcTemplate.update("""
                INSERT INTO business_application(application_no,business_type,title,applicant_user_id,
                    applicant_department_id,applicant_region_code,status,reason,version)
                VALUES(?, 'GENERAL_SERVICE', 'Generic application', 2, 2, '110105', 'DRAFT', 'test', 0)
                """, genericNo);
        Long genericId = jdbcTemplate.queryForObject(
                "SELECT application_id FROM business_application WHERE application_no=?", Long.class, genericNo);
        String household = tokenFor("household");

        mockMvc.perform(get("/api/applications").param("businessType", "MIGRATION_IN")
                        .param("applicationNo", migrationNo).header("Authorization", bearer(household)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));
        mockMvc.perform(get("/api/applications/" + migrationId).header("Authorization", bearer(household)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/applications").param("businessType", "GENERAL_SERVICE")
                        .param("applicationNo", genericNo).header("Authorization", bearer(household)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
        mockMvc.perform(get("/api/applications/" + genericId).header("Authorization", bearer(household)))
                .andExpect(status().isForbidden());
    }

    @Test
    void meReturnsRoleScopeDepartmentAndPermissions() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenFor("viewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("QUERY_VIEWER"))
                .andExpect(jsonPath("$.data.roleLevel").value("L1"))
                .andExpect(jsonPath("$.data.dataScope").value("DEPARTMENT"))
                .andExpect(jsonPath("$.data.departmentId").value(1))
                .andExpect(jsonPath("$.data.permissions",
                        org.hamcrest.Matchers.hasItem("population:view")));
    }

    @Test
    void systemAdminReceivesAllEnabledPermissions() throws Exception {
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(tokenFor("admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roleCode").value("SYSTEM_ADMIN"))
                .andExpect(jsonPath("$.data.dataScope").value("ALL"))
                .andExpect(jsonPath("$.data.permissions", org.hamcrest.Matchers.hasItems(
                        "population:view", "population:edit", "household:view", "household:edit",
                        "approval:handle", "migration:execute", "cancellation:execute",
                        "region:manage", "dictionary:manage", "key-population:execute",
                        "sensitive-data:view-full", "system:user:manage", "log:view")));
    }

    @Test
    void loginUpdatesLastLoginAndWritesSuccessAndFailureLogs() throws Exception {
        mockMvc.perform(login("admin", "123456")).andExpect(status().isOk());
        mockMvc.perform(login("admin", "wrong")).andExpect(status().isUnauthorized());

        assertThat(jdbcTemplate.queryForObject(
                "SELECT last_login_time FROM sys_user WHERE username = 'admin'", Object.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGIN_SUCCESS'", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGIN_FAILED'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void seededBcryptHashMatchesDemonstrationPassword() {
        String hash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM sys_user WHERE username = 'admin'", String.class);
        assertThat(hash).startsWith("$2a$");
        assertThat(passwordEncoder.matches("123456", hash)).isTrue();
    }

    @Test
    void logoutWritesAuditLog() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", bearer(tokenFor("viewer"))))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGOUT'", Integer.class))
                .isEqualTo(1);
    }

    @Test
    void departmentScopedLogQueryDoesNotExposeOtherDepartmentLogs() throws Exception {
        jdbcTemplate.update("""
                INSERT INTO operation_log (user_id, operation_type, operation_result, detail)
                VALUES (1, 'VIEWER_ACTION', 'SUCCESS', 'viewer only'),
                       (2, 'POPULATION_ACTION', 'SUCCESS', 'other department')
                """);

        mockMvc.perform(get("/api/statistics/logs")
                        .header("Authorization", bearer(tokenFor("viewer"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].operator").value(
                        org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("viewer"))))
                .andExpect(jsonPath("$.data[*].detail",
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("other department"))));
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String username, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private String tokenFor(String username) throws Exception {
        String response = mockMvc.perform(login(username, "123456"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }

    private String personJson(String idCard) {
        return "{\"name\":\"测试人员\",\"gender\":\"男\",\"idCard\":\"" + idCard
                + "\",\"status\":\"正常\"}";
    }

    private String personJsonWithImage(String idCard, long imageId) {
        return "{\"name\":\"测试人员\",\"gender\":\"男\",\"idCard\":\"" + idCard
                + "\",\"idCardImageId\":" + imageId + ",\"status\":\"正常\"}";
    }

    private long uploadIdCardImage(String token) throws Exception {
        byte[] body = new byte[64];
        org.springframework.mock.web.MockMultipartFile file =
                new org.springframework.mock.web.MockMultipartFile("file", "rbac-l2.jpg", "image/jpeg", body);
        String resp = mockMvc.perform(multipart("/api/persons/idcard-image").file(file)
                        .param("skipOcr", "true")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(resp).path("data").path("imageId").asLong();
    }
}
