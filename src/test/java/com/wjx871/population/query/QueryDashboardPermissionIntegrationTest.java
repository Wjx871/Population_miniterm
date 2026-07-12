package com.wjx871.population.query;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 验证 population:view 与 statistics:view 在查询/工作台接口上的 200/403 语义。
 * 权限种子仅在本测试事务内通过 JdbcTemplate 写入，不修改全局 schema.sql。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class QueryDashboardPermissionIntegrationTest {

    private static final long STATISTICS_PERMISSION_ID = 9101L;
    private static final long POP_ONLY_ROLE_ID = 9102L;
    private static final long STAT_ONLY_ROLE_ID = 9103L;
    private static final long BOTH_ROLE_ID = 9104L;
    private static final long NONE_ROLE_ID = 9105L;
    private static final long POP_ONLY_USER_ID = 9201L;
    private static final long STAT_ONLY_USER_ID = 9202L;
    private static final long BOTH_USER_ID = 9203L;
    private static final long NONE_USER_ID = 9204L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedPermissionMatrix() {
        // password: 123456 (same bcrypt seed as schema.sql demo accounts)
        String hash = "$2a$10$hqLjVyldvMDp7tlJcpkDZOaTT1dCAuSA5I7FgRfD/B7QXluT8ArB.";

        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE permission_id = ?", STATISTICS_PERMISSION_ID);
        jdbcTemplate.update("DELETE FROM sys_permission WHERE permission_id = ? OR permission_code = 'statistics:view'",
                STATISTICS_PERMISSION_ID);
        jdbcTemplate.update("""
                INSERT INTO sys_permission (permission_id, permission_code, permission_name, module_name, permission_type, status)
                VALUES (?, 'statistics:view', '查看统计', 'STATISTICS', 'API', 'ENABLED')
                """, STATISTICS_PERMISSION_ID);

        insertRole(POP_ONLY_ROLE_ID, "POP_ONLY_M5", "仅人口查看");
        insertRole(STAT_ONLY_ROLE_ID, "STAT_ONLY_M5", "仅统计查看");
        insertRole(BOTH_ROLE_ID, "BOTH_M5", "人口与统计");
        insertRole(NONE_ROLE_ID, "NONE_M5", "无查询统计权限");

        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id IN (?,?,?,?)",
                POP_ONLY_ROLE_ID, STAT_ONLY_ROLE_ID, BOTH_ROLE_ID, NONE_ROLE_ID);
        // population:view is permission_id=1 in schema.sql
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id, permission_id) VALUES (?, 1)", POP_ONLY_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id, permission_id) VALUES (?, ?)",
                STAT_ONLY_ROLE_ID, STATISTICS_PERMISSION_ID);
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id, permission_id) VALUES (?, 1)", BOTH_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id, permission_id) VALUES (?, ?)",
                BOTH_ROLE_ID, STATISTICS_PERMISSION_ID);

        insertUser(POP_ONLY_USER_ID, "m5_pop_only", POP_ONLY_ROLE_ID, hash);
        insertUser(STAT_ONLY_USER_ID, "m5_stat_only", STAT_ONLY_ROLE_ID, hash);
        insertUser(BOTH_USER_ID, "m5_both", BOTH_ROLE_ID, hash);
        insertUser(NONE_USER_ID, "m5_none", NONE_ROLE_ID, hash);
    }

    @Test
    void populationOnlyCanQueryButNotDashboard() throws Exception {
        String token = tokenFor("m5_pop_only");
        mockMvc.perform(get("/api/queries/persons").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/dashboard/overview").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/charts").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void statisticsOnlyCanDashboardButNotQuery() throws Exception {
        String token = tokenFor("m5_stat_only");
        mockMvc.perform(get("/api/dashboard/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/dashboard/charts").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/api/queries/persons").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void bothPermissionsAllowQueryAndDashboard() throws Exception {
        String token = tokenFor("m5_both");
        mockMvc.perform(get("/api/queries/persons").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/dashboard/overview").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void neitherPermissionDeniesBoth() throws Exception {
        String token = tokenFor("m5_none");
        mockMvc.perform(get("/api/queries/persons").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/overview").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    private void insertRole(long roleId, String code, String name) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM sys_role_permission WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE role_id = ?", roleId);
        jdbcTemplate.update("""
                INSERT INTO sys_role (role_id, role_code, role_name, role_level, data_scope, status)
                VALUES (?, ?, ?, 'L1', 'ALL', 'ENABLED')
                """, roleId, code, name);
    }

    private void insertUser(long userId, String username, long roleId, String hash) {
        jdbcTemplate.update("DELETE FROM sys_user WHERE user_id = ? OR username = ?", userId, username);
        jdbcTemplate.update("""
                INSERT INTO sys_user (user_id, username, password_hash, role_id, department_id, real_name, status)
                VALUES (?, ?, ?, ?, 1, ?, 'ENABLED')
                """, userId, username, hash, roleId, username);
    }

    private String tokenFor(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode body = objectMapper.readTree(response);
        return body.path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
