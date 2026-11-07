package com.wjx871.population.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.security.JwtService;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

/** Synthetic history inserted directly into an isolated database; never prints raw fixtures or HTTP bodies. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:issue70-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(Issue70StatisticsLogRedactionIntegrationTest.MySqlCastCompatibility.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Transactional
class Issue70StatisticsLogRedactionIntegrationTest {
    private static final long ROLE = 97001L;
    private static final long READER = 97001L;
    private static final String TYPE = "ISSUE70_SYNTHETIC";
    private static final String PLAIN = "合成历史记录：办理人口登记完成";
    private static final String MASKED = "id=1101**********0016 phone=138****8000 Bearer *** password=***";
    // Publicly invented test examples only, never obtained from real people or credentials.
    private static final List<String> RAW = List.of(
            "id=" + "110105" + "19800101" + "0016",
            "phone=" + "1380013" + "8000",
            "Bearer " + "issue70.synthetic-token",
            "password=" + "issue70-synthetic-secret",
            PLAIN, MASKED);
    private static final List<String> EXPECTED = List.of(
            "id=1101**********0016", "phone=138****8000", "Bearer ***", "password=***", PLAIN, MASKED);

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;

    @BeforeEach
    void seedOwnUsersAndPermissions() {
        // The shared H2 schema has no statistics:view seed; create it only in this isolated fixture.
        jdbc.update("INSERT INTO sys_permission(permission_id,permission_code,permission_name,module_name,permission_type,status) "
                + "SELECT 97001,'statistics:view','Synthetic statistics','STATISTICS','API','ENABLED' "
                + "WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='statistics:view')");
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,role_level,data_scope,status) "
                + "VALUES(?,'ISSUE70_READER','Synthetic reader','L1','ALL','ENABLED')", ROLE);
        for (String permission : List.of("log:view", "statistics:view")) {
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) "
                    + "SELECT ?,permission_id FROM sys_permission WHERE permission_code=?", ROLE, permission);
        }
        for (int i = 1; i <= 3; i++) {
            jdbc.update("INSERT INTO sys_department(department_id,department_code,department_name,region_code,status,sort_no) "
                    + "VALUES(?,?,?,?,'ENABLED',0)", 97000L + i, "ISSUE70_D" + i,
                    "Synthetic department " + i, i < 3 ? "110105" : "120000");
        }
        for (int i = 1; i <= 4; i++) {
            long department = i <= 2 ? 97001L : i == 3 ? 97002L : 97003L;
            jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) "
                    + "VALUES(?,?,?, ?,?,?,'ENABLED')", 97000L + i, "issue70-user" + i,
                    "{unused-synthetic-password}", ROLE, department, "Synthetic user " + i);
        }
    }

    @Test
    void historicalDetailsAreMaskedAtHttpBoundaryAndDatabaseIsUnchanged() throws Exception {
        verifyHistoricalDetails("issue70-user1");
    }

    @Test
    void fullSensitiveAdministratorStillGetsFixedAuditMasking() throws Exception {
        assertThat(auth.loadUser("admin").permissions()).contains("sensitive-data:view-full", "log:view");
        verifyHistoricalDetails("admin");
    }

    private void verifyHistoricalDetails(String username) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (String raw : RAW) ids.add(insertLog(READER, raw));
        long nullId = insertLog(READER, null);
        Map<Long, String> before = databaseDetails();
        JsonNode rows = data(request("/api/statistics/logs", username), 200);
        assertThat(rows.size()).isEqualTo(7);
        boolean rawReturned = true;
        boolean redacted = true;
        for (int i = 0; i < ids.size(); i++) {
            JsonNode row = row(rows, ids.get(i));
            rawReturned &= RAW.get(i).equals(row.path("detail").asText());
            redacted &= EXPECTED.get(i).equals(row.path("detail").asText());
            assertMetadata(row, ids.get(i));
        }
        boolean nullPreserved = row(rows, nullId).path("detail").isNull();
        boolean databaseUnchanged = before.equals(databaseDetails());
        // Sanitized RED/GREEN evidence: deliberately excludes raw values, tokens and entire HTTP responses.
        System.out.printf("ISSUE70 actor=%s HTTP=200 rows=%d rawReturned=%s redacted=%s nullPreserved=%s databaseUnchanged=%s%n",
                username, rows.size(), rawReturned, redacted, nullPreserved, databaseUnchanged);
        assertThat(databaseUnchanged).as("historical database details unchanged").isTrue();
        assertThat(redacted).as("all historical HTTP details match fixed audit redaction").isTrue();
        assertThat(nullPreserved).as("SQL NULL remains JSON null").isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELF", "DEPARTMENT", "REGION", "ALL"})
    void realHttpScopeKeepsExactlyTheOriginalOwners(String scope) throws Exception {
        jdbc.update("UPDATE sys_role SET data_scope=? WHERE role_id=?", scope, ROLE);
        List<Long> ids = new ArrayList<>();
        for (long owner = READER; owner <= READER + 3; owner++) ids.add(insertLog(owner, RAW.get(0)));
        List<Long> expected = ids.subList(0, switch (scope) {
            case "SELF" -> 1;
            case "DEPARTMENT" -> 2;
            case "REGION" -> 3;
            default -> 4;
        });
        JsonNode rows = data(request("/api/statistics/logs", "issue70-user1"), 200);
        List<Long> actual = new ArrayList<>();
        for (JsonNode item : rows) actual.add(item.path("id").asLong());
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
        boolean masked = true;
        for (JsonNode item : rows) masked &= EXPECTED.get(0).equals(item.path("detail").asText());
        System.out.printf("ISSUE70 scope=%s HTTP=200 expectedOwners=%d actualOwners=%d masked=%s%n",
                scope, expected.size(), actual.size(), masked);
        assertThat(masked).as("every in-scope historical row is masked").isTrue();
    }

    @Test
    void regionWithNoAuthorizedCodesFailsClosed() throws Exception {
        jdbc.update("UPDATE sys_role SET data_scope='REGION' WHERE role_id=?", ROLE);
        jdbc.update("UPDATE sys_department SET region_code=NULL WHERE department_id=97001");
        insertLog(READER, RAW.get(0));
        assertThat(request("/api/statistics/logs", "issue70-user1").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void missingLogPermissionIsForbiddenEvenWithFullSensitivePermission() throws Exception {
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id=? AND permission_id IN "
                + "(SELECT permission_id FROM sys_permission WHERE permission_code='log:view')", ROLE);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) "
                + "SELECT ?,permission_id FROM sys_permission WHERE permission_code='sensitive-data:view-full'", ROLE);
        insertLog(READER, RAW.get(0));
        assertThat(request("/api/statistics/logs", "issue70-user1").getResponse().getStatus()).isEqualTo(403);
        assertThat(data(request("/api/statistics/summary", "issue70-user1"), 200).isObject()).isTrue();
    }

    @Test
    void anonymousCannotReadStatisticsLogs() throws Exception {
        assertThat(mvc.perform(get("/api/statistics/logs")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void existingLogSearchAndGetKeepTheirFixedRedaction() throws Exception {
        List<Long> ids = new ArrayList<>();
        for (String detail : RAW) ids.add(insertLog(READER, detail));
        for (String username : List.of("issue70-user1", "admin")) {
            JsonNode rows = data(request("/api/logs/operations?operationType=" + TYPE, username), 200).path("content");
            assertThat(rows.size()).isEqualTo(RAW.size());
            for (int i = 0; i < ids.size(); i++) {
                long id = ids.get(i);
                JsonNode detail = data(request("/api/logs/operations/" + id, username), 200);
                JsonNode searchRow = null;
                for (JsonNode item : rows) if (item.path("logId").asLong() == id) searchRow = item;
                assertThat(searchRow != null).isTrue();
                assertThat(EXPECTED.get(i).equals(searchRow.path("detail").asText())).as("search fixed masking").isTrue();
                assertThat(EXPECTED.get(i).equals(detail.path("detail").asText())).as("get fixed masking").isTrue();
            }
        }
        assertThat(new ArrayList<>(databaseDetails().values()).equals(RAW)).as("log search/get leave history unchanged").isTrue();
    }

    @Test
    void summaryAndChartsKeepTheirHttpContractBeforeAndAfterLogRead() throws Exception {
        JsonNode summary = data(request("/api/statistics/summary", "issue70-user1"), 200);
        JsonNode charts = data(request("/api/statistics/charts", "issue70-user1"), 200);
        assertThat(summary.has("residentCount") && summary.has("householdCount")
                && summary.has("floatingCount") && summary.has("keyPopulationCount")).isTrue();
        assertThat(summary.path("isMock").asBoolean()).isFalse();
        assertThat(charts.has("keyPopulationDistribution") && charts.has("certificateStatus")).isTrue();
        assertThat(charts.path("migrationTrend").path("dates").size()).isEqualTo(7);
        insertLog(READER, RAW.get(0));
        data(request("/api/statistics/logs", "issue70-user1"), 200);
        assertThat(data(request("/api/statistics/summary", "issue70-user1"), 200)).isEqualTo(summary);
        assertThat(data(request("/api/statistics/charts", "issue70-user1"), 200)).isEqualTo(charts);
    }

    private long insertLog(long user, String detail) {
        // Direct SQL intentionally bypasses OperationLogService/write-side masking.
        jdbc.update("INSERT INTO operation_log(user_id,operation_type,target_table,operation_result,operation_time,detail) "
                + "VALUES(?,?,'synthetic','SUCCESS',?,?)", user, TYPE, LocalDateTime.of(2090, 1, 1, 12, 0), detail);
        return jdbc.queryForObject("SELECT MAX(log_id) FROM operation_log WHERE operation_type=?", Long.class, TYPE);
    }

    private Map<Long, String> databaseDetails() {
        Map<Long, String> result = new LinkedHashMap<>();
        jdbc.query("SELECT log_id,detail FROM operation_log WHERE operation_type=? ORDER BY log_id",
                rs -> { result.put(rs.getLong(1), rs.getString(2)); }, TYPE);
        return result;
    }

    private MvcResult request(String path, String username) throws Exception {
        return mvc.perform(get(path).header("Authorization", "Bearer " + jwt.createToken(auth.loadUser(username))))
                .andReturn();
    }

    private JsonNode data(MvcResult result, int expectedStatus) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(expectedStatus);
        JsonNode body = json.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        assertThat(body.path("code").asInt()).isEqualTo(expectedStatus);
        return body.path("data");
    }

    private JsonNode row(JsonNode rows, long id) {
        for (JsonNode row : rows) if (row.path("id").asLong() == id) return row;
        throw new AssertionError("Synthetic log ID missing from HTTP response");
    }

    private void assertMetadata(JsonNode row, long id) {
        assertThat(row.path("id").asLong()).isEqualTo(id);
        assertThat(row.path("operator").asText()).isEqualTo("issue70-user1");
        assertThat(row.path("type").asText()).isEqualTo(TYPE);
        assertThat(row.path("result").asText()).isEqualTo("SUCCESS");
        assertThat(row.path("time").asText()).startsWith("2090-01-01");
        // H2 lowercases unquoted map aliases; preserve the existing key rather than changing SQL.
        assertThat(row.path("targettable").asText()).isEqualTo("synthetic");
        List<String> fields = new ArrayList<>();
        row.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "operator", "type", "targettable", "result", "time", "detail");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MySqlCastCompatibility {
        @Bean
        Interceptor mysqlTextCastForH2() {
            return new FullTextCastInterceptor();
        }
    }

    /** H2 CHAR defaults to length 1; MySQL CAST AS CHAR returns the complete text. */
    @Intercepts(@Signature(type = StatementHandler.class, method = "prepare", args = {Connection.class, Integer.class}))
    static class FullTextCastInterceptor implements Interceptor {
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            StatementHandler handler = (StatementHandler) invocation.getTarget();
            String sql = handler.getBoundSql().getSql();
            String cast = "CAST(l.detail AS CHAR) AS detail";
            if (sql.contains(cast)) {
                // Adapt only the projection's text type; predicates, parameters, order and limit stay identical.
                SystemMetaObject.forObject(handler.getBoundSql()).setValue("sql",
                        sql.replace(cast, "CAST(l.detail AS VARCHAR) AS detail"));
            }
            return invocation.proceed();
        }
    }
}
