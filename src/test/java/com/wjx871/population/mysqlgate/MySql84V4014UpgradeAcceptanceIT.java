package com.wjx871.population.mysqlgate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** Explicit MySQL gate for the V4_013 -> V4_014 upgrade path and its API ownership contract. */
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "population.redis.enabled=false",
        "policy.ai.enabled=false",
        "export.dir=./target/mysql-v4014-upgrade-exports"
})
@AutoConfigureMockMvc
class MySql84V4014UpgradeAcceptanceIT {
    private static final Pattern URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/(?<database>[A-Za-z0-9_]+)\\?(?<options>.*)$",
            Pattern.CASE_INSENSITIVE);
    private static final String[] LEGACY_ID_CARDS = {
            "R4SYNTH000000001", "R4SYNTH000000002", "R4SYNTH000000003", "R4SYNTH000000004", "R4SYNTH000000005"
    };

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    private int expectedPort;
    private String expectedDatabase;
    private String expectedUser;

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry properties) {
        String url = required("MYSQL84_GATE_DB_URL");
        String user = required("MYSQL84_GATE_DB_USERNAME");
        String password = required("MYSQL84_GATE_DB_PASSWORD");
        Matcher match = URL.matcher(url);
        if (!match.matches()) throw new IllegalStateException("MySQL gate URL must use loopback and an explicit port/schema.");
        int port = Integer.parseInt(match.group("port"));
        String database = match.group("database");
        if (port == 3306 || !database.matches("(?i)population_miniterm_mysql_gate_[a-z0-9_]+")
                || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)", Pattern.CASE_INSENSITIVE)
                        .matcher(match.group("options")).find()
                || user.equalsIgnoreCase("root")) {
            throw new IllegalStateException("MySQL gate requires a named loopback test schema and a non-root account.");
        }
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("Set " + name + " for the explicit MySQL gate.");
        return value;
    }

    @BeforeEach
    void verifyMySqlGateIdentity() throws Exception {
        String url = required("MYSQL84_GATE_DB_URL");
        Matcher match = URL.matcher(url);
        assertThat(match.matches()).isTrue();
        expectedPort = Integer.parseInt(match.group("port"));
        expectedDatabase = match.group("database");
        expectedUser = required("MYSQL84_GATE_DB_USERNAME");
        var row = jdbc.queryForMap("SELECT @@version version,@@port port,@@bind_address bind_address,"
                + "@@transaction_isolation isolation_level,DATABASE() database_name,CURRENT_USER() connected_user");
        assertThat(String.valueOf(row.get("version"))).startsWith("8.4.");
        assertThat(((Number) row.get("port")).intValue()).isEqualTo(expectedPort);
        assertThat(String.valueOf(row.get("bind_address"))).isEqualTo("127.0.0.1");
        assertThat(row.get("database_name")).isEqualTo(expectedDatabase);
        assertThat(String.valueOf(row.get("connected_user"))).startsWith(expectedUser + "@");
        assertThat(String.valueOf(row.get("isolation_level"))).isEqualTo("REPEATABLE-READ");
    }

    @Test
    void c6LegacyRecordsRemainScopedAfterV4014Upgrade() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE person_id BETWEEN 940001 AND 940005",
                Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person WHERE person_id BETWEEN 940001 AND 940005 "
                + "AND initial_scope_user_id IS NULL AND initial_scope_department_id IS NULL "
                + "AND initial_scope_region_code IS NULL", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id IN(940001,940003)", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE application_no LIKE 'R4-FINAL-%'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM cancellation_record WHERE cancellation_no='R4-FINAL-CANCEL-1'",
                Integer.class)).isOne();

        String owner = bearer(token("population"));
        assertThat(mvc.perform(get("/api/persons/940001").header("Authorization", owner)).andReturn().getResponse().getStatus())
                .as("legacy residence scope remains available").isEqualTo(200);
        assertThat(mvc.perform(get("/api/persons/940004").header("Authorization", owner)).andReturn().getResponse().getStatus())
                .as("legacy application applicant-region scope remains available").isEqualTo(200);
        assertThat(mvc.perform(get("/api/persons/940005").header("Authorization", owner)).andReturn().getResponse().getStatus())
                .as("completed cancellation history remains in applicant scope").isEqualTo(200);
        assertThat(mvc.perform(get("/api/persons/940002").header("Authorization", owner)).andReturn().getResponse().getStatus())
                .as("legacy unregistered person without trusted ownership stays excluded").isEqualTo(403);
        assertThat(mvc.perform(get("/api/persons/940003").header("Authorization", owner)).andReturn().getResponse().getStatus())
                .as("other-region legacy person stays excluded").isEqualTo(403);

        for (String idCard : LEGACY_ID_CARDS) {
            long expected = idCard.endsWith("001") || idCard.endsWith("004") || idCard.endsWith("005") ? 1 : 0;
            MvcResult list = mvc.perform(get("/api/persons").param("idCard", idCard).header("Authorization", owner)).andReturn();
            assertThat(list.getResponse().getStatus()).isEqualTo(200);
            assertThat(json.readTree(list.getResponse().getContentAsString()).path("data").path("totalElements").asLong())
                    .as("person API list follows the same legacy scope for " + idCard).isEqualTo(expected);
        }
        MvcResult scopedList = mvc.perform(get("/api/persons").header("Authorization", owner)).andReturn();
        long apiRows = json.readTree(scopedList.getResponse().getContentAsString()).path("data").path("totalElements").asLong();
        MvcResult export = mvc.perform(post("/api/exports/normal").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("module", "PERSON", "filters", Map.of(), "fields", List.of("name")))))
                .andReturn();
        assertThat(export.getResponse().getStatus()).isEqualTo(201);
        long exportLogId = json.readTree(export.getResponse().getContentAsString()).path("data").path("exportLogId").asLong();
        assertThat(jdbc.queryForObject("SELECT row_count FROM data_export_log WHERE export_log_id=?", Integer.class, exportLogId))
                .as("legacy Person API list count equals actual normal export rows").isEqualTo((int) apiRows);
    }

    @Test
    void approvalMediatedCreationPersistsApplicantSnapshot() throws Exception {
        String applicant = bearer(token("population"));
        String approver = bearer(token("approver"));
        MvcResult created = mvc.perform(post("/api/applications").header("Authorization", applicant)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("businessType", "GENERAL_SERVICE",
                                "title", "V4_014 合成审批建档", "reason", "V4_014 MySQL 升级验收"))))
                .andReturn();
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long applicationId = json.readTree(created.getResponse().getContentAsString()).path("data").path("applicationId").asLong();
        long applicantId = jdbc.queryForObject("SELECT applicant_user_id FROM business_application WHERE application_id=?",
                Long.class, applicationId);
        long applicantDepartment = jdbc.queryForObject("SELECT applicant_department_id FROM business_application WHERE application_id=?",
                Long.class, applicationId);
        jdbc.update("UPDATE business_application SET remark='登记类型=RELEASED_RESTORE',applicant_region_code='110105' "
                + "WHERE application_id=?", applicationId);

        byte[] proof = "%PDF-r4-synthetic-proof".getBytes(StandardCharsets.UTF_8);
        MvcResult uploaded = mvc.perform(multipart("/api/applications/" + applicationId + "/materials")
                        .file(new MockMultipartFile("file", "r4-synthetic-proof.pdf", "application/pdf", proof))
                        .param("materialType", "PROOF").param("materialName", "Synthetic proof").param("requiredFlag", "true")
                        .header("Authorization", applicant))
                .andReturn();
        assertThat(uploaded.getResponse().getStatus()).isEqualTo(201);
        long materialId = json.readTree(uploaded.getResponse().getContentAsString()).path("data").path("materialId").asLong();
        assertThat(mvc.perform(post("/api/applications/" + applicationId + "/submit")
                        .header("Authorization", applicant)).andReturn().getResponse().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(post("/api/materials/" + materialId + "/verify").header("Authorization", approver)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"result\":\"VERIFIED\",\"comment\":\"synthetic MySQL gate\"}"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);

        byte[] imageBytes = Arrays.copyOf(("r4-v4014-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), 64);
        MvcResult image = mvc.perform(multipart("/api/persons/idcard-image")
                        .file(new MockMultipartFile("file", "r4-synthetic-idcard.jpg", "image/jpeg", imageBytes))
                        .param("skipOcr", "true").header("Authorization", applicant)).andReturn();
        assertThat(image.getResponse().getStatus()).isEqualTo(200);
        long imageId = json.readTree(image.getResponse().getContentAsString()).path("data").path("imageId").asLong();

        String idCard = syntheticIdCard();
        MvcResult approval = mvc.perform(post("/api/approvals/" + jdbc.queryForObject(
                                "SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, applicationId)
                        + "/approve-and-create-person")
                        .header("Authorization", approver).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("comment", "V4_014 synthetic approval",
                                "version", 0,
                                "person", Map.of("name", "R4 approval-created synthetic person", "gender", "M",
                                        "idCard", idCard, "birthDate", "1999-12-31", "ethnicity", "HAN",
                                        "currentAddress", "R4 synthetic approval address", "status", "正常",
                                        "idCardImageId", imageId)))))
                .andReturn();
        assertThat(approval.getResponse().getStatus()).isEqualTo(200);
        Long personId = jdbc.queryForObject("SELECT target_person_id FROM business_application WHERE application_id=?",
                Long.class, applicationId);
        assertThat(personId).isNotNull();
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, applicationId))
                .isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT initial_scope_user_id FROM person WHERE person_id=?", Long.class, personId))
                .isEqualTo(applicantId);
        assertThat(jdbc.queryForObject("SELECT initial_scope_department_id FROM person WHERE person_id=?", Long.class, personId))
                .isEqualTo(applicantDepartment);
        assertThat(jdbc.queryForObject("SELECT initial_scope_region_code FROM person WHERE person_id=?", String.class, personId))
                .isEqualTo("110105");
        assertThat(mvc.perform(get("/api/persons/" + personId).header("Authorization", applicant))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    private String token(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "123456"))))
                .andReturn().getResponse().getContentAsString();
        JsonNode root = json.readTree(body);
        assertThat(root.path("data").path("token").asText()).isNotBlank();
        return root.path("data").path("token").asText();
    }

    private static String bearer(String token) { return "Bearer " + token; }

    private static String syntheticIdCard() {
        String base = "11010119991231" + String.format("%03d", ThreadLocalRandom.current().nextInt(1, 500) * 2 - 1);
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) sum += (base.charAt(i) - '0') * weights[i];
        return base + checks[sum % 11];
    }
}
