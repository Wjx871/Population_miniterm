package com.wjx871.population.person;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.SoftAssertions.assertSoftly;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.export.ExportMapper;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "export.dir=./target/round4-person-ownership-exports")
@AutoConfigureMockMvc
class PersonRegionOwnershipAuditIntegrationTest {

    private static final long OTHER_REGION_USER_ID = 9888L;
    private static final String OTHER_REGION_USERNAME = "round4-region-other";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper json;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private PersonMapper personMapper;

    @Autowired
    private ExportMapper exportMapper;

    private Long personId;
    private Long imageId;
    private Long exportLogId;
    private String householdNo;
    private LocalDateTime auditStart;
    private String personName;

    @BeforeEach
    void createOtherRegionAccount() {
        jdbc.update("DELETE FROM sys_user WHERE user_id = ?", OTHER_REGION_USER_ID);
        jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) " +
                        "VALUES(?,?,?,?,?,?, 'ENABLED')",
                OTHER_REGION_USER_ID, OTHER_REGION_USERNAME, passwordEncoder.encode("123456"),
                2L, 6L, "第四批外区合成账号");
    }

    @AfterEach
    void removeSyntheticRows() {
        if (exportLogId != null) {
            jdbc.update("DELETE FROM data_export_log WHERE export_log_id = ?", exportLogId);
        }
        if (imageId != null) {
            jdbc.update("DELETE FROM person_idcard_image WHERE image_id = ?", imageId);
        }
        if (personId != null) {
            jdbc.update("DELETE FROM residence WHERE person_id = ?", personId);
            jdbc.update("DELETE FROM person WHERE person_id = ?", personId);
        }
        if (householdNo != null) {
            jdbc.update("DELETE FROM household WHERE household_no = ?", householdNo);
        }
        if (auditStart != null) {
            jdbc.update("DELETE FROM operation_log WHERE user_id = 2 AND operation_type = 'PERSON_CREATE' " +
                    "AND operation_time >= ?", auditStart);
        }
        jdbc.update("DELETE FROM operation_log WHERE user_id = ?", OTHER_REGION_USER_ID);
        jdbc.update("DELETE FROM sys_user WHERE user_id = ?", OTHER_REGION_USER_ID);
    }

    @Test
    void regionCreatorCanReadNewUnregisteredPersonButOtherRegionCannotAndExportMatches() throws Exception {
        String sameRegionToken = token("population");
        String otherRegionToken = token(OTHER_REGION_USERNAME);
        String allScopeToken = token("viewer");
        String idCard = syntheticIdCard();
        personName = "第四批区域归属" + UUID.randomUUID();
        imageId = uploadImage(sameRegionToken);
        auditStart = LocalDateTime.now().minusSeconds(1);

        MvcResult createResult = mvc.perform(post("/api/persons")
                        .header("Authorization", bearer(sameRegionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "name", personName,
                                "gender", "男",
                                "idCard", idCard,
                                "idCardImageId", imageId,
                                "status", "正常"))))
                .andReturn();
        assertThat(createResult.getResponse().getStatus()).isEqualTo(201);
        String created = createResult.getResponse().getContentAsString();
        assertThat(created).doesNotContain("initialScope");
        personId = json.readTree(created).path("data").path("personId").asLong();
        assertThat(personId).isPositive();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id = ?", Integer.class, personId))
                .as("new direct record has no residence").isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application WHERE target_person_id = ?", Integer.class, personId))
                .as("new direct record has no business application").isZero();

        MvcResult creatorDetail = mvc.perform(get("/api/persons/{id}", personId)
                        .header("Authorization", bearer(sameRegionToken)))
                .andReturn();
        MvcResult creatorList = mvc.perform(get("/api/persons")
                        .param("name", personName)
                        .header("Authorization", bearer(sameRegionToken)))
                .andReturn();
        MvcResult otherRegionDetail = mvc.perform(get("/api/persons/{id}", personId)
                        .header("Authorization", bearer(otherRegionToken)))
                .andReturn();
        MvcResult otherRegionList = mvc.perform(get("/api/persons")
                        .param("name", personName)
                        .header("Authorization", bearer(otherRegionToken)))
                .andReturn();
        MvcResult allScopeDetail = mvc.perform(get("/api/persons/{id}", personId)
                        .header("Authorization", bearer(allScopeToken)))
                .andReturn();

        DataScopeCriteria ownerSelf = new DataScopeCriteria(DataScope.SELF, 2L, 2L, "110000", List.of());
        DataScopeCriteria otherSelf = new DataScopeCriteria(DataScope.SELF, OTHER_REGION_USER_ID, 6L, "120000", List.of());
        DataScopeCriteria ownerDepartment = new DataScopeCriteria(DataScope.DEPARTMENT, 9L, 2L, "110000", List.of());
        DataScopeCriteria otherDepartment = new DataScopeCriteria(DataScope.DEPARTMENT, 9L, 6L, "120000", List.of());
        DataScopeCriteria otherRegion = new DataScopeCriteria(DataScope.REGION, 9L, 6L, "120000", List.of("120000"));
        assertThat(personMapper.selectScopedById(personId, ownerSelf)).isPresent();
        assertThat(personMapper.selectScopedById(personId, otherSelf)).isEmpty();
        assertThat(personMapper.selectScopedById(personId, ownerDepartment)).isPresent();
        assertThat(personMapper.selectScopedById(personId, otherDepartment)).isEmpty();
        assertThat(exportMapper.countRows("PERSON", Map.of("name", personName), ownerSelf)).isEqualTo(1);
        assertThat(exportMapper.countRows("PERSON", Map.of("name", personName), otherSelf)).isZero();
        assertThat(exportMapper.countRows("PERSON", Map.of("name", personName), ownerDepartment)).isEqualTo(1);
        assertThat(exportMapper.countRows("PERSON", Map.of("name", personName), otherDepartment)).isZero();
        assertThat(exportMapper.countRows("PERSON", Map.of("name", personName), otherRegion)).isZero();

        MvcResult exportResult = mvc.perform(post("/api/exports/normal")
                        .header("Authorization", bearer(sameRegionToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "module", "PERSON",
                                "filters", Map.of("name", personName),
                                "fields", new String[]{"name"}))))
                .andReturn();
        assertThat(exportResult.getResponse().getStatus()).isEqualTo(201);
        String exportBody = exportResult.getResponse().getContentAsString();
        exportLogId = json.readTree(exportBody).path("data").path("exportLogId").asLong();
        int exportedRows = exportLogId > 0
                ? jdbc.queryForObject("SELECT row_count FROM data_export_log WHERE export_log_id = ?", Integer.class, exportLogId)
                : -1;

        householdNo = "R4-" + UUID.randomUUID().toString().substring(0, 20);
        jdbc.update("INSERT INTO household(household_no,address,region_code,establish_date,status,version) " +
                        "VALUES(?, '外区迁入合成地址', '120000', DATE '2020-01-01', 'ACTIVE', 0)", householdNo);
        Long householdId = jdbc.queryForObject("SELECT household_id FROM household WHERE household_no = ?", Long.class, householdNo);
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code," +
                        "register_date,start_date,status,version) VALUES(?,?,?,'120000','OTHER',DATE '2020-01-01'," +
                        "DATE '2020-01-01','ACTIVE',0)", personId, householdId, "外区迁入合成地址");
        MvcResult creatorAfterResidence = mvc.perform(get("/api/persons/{id}", personId)
                        .header("Authorization", bearer(sameRegionToken)))
                .andReturn();
        MvcResult otherRegionAfterResidence = mvc.perform(get("/api/persons/{id}", personId)
                        .header("Authorization", bearer(otherRegionToken)))
                .andReturn();

        long creatorListCount = json.readTree(creatorList.getResponse().getContentAsString())
                .path("data").path("totalElements").asLong();
        long otherRegionListCount = json.readTree(otherRegionList.getResponse().getContentAsString())
                .path("data").path("totalElements").asLong();
        assertSoftly(softly -> {
            softly.assertThat(creatorDetail.getResponse().getStatus()).as("creating REGION user detail").isEqualTo(200);
            softly.assertThat(creatorListCount).as("creating REGION user list").isEqualTo(1);
            softly.assertThat(otherRegionDetail.getResponse().getStatus()).as("other REGION user detail").isEqualTo(403);
            softly.assertThat(otherRegionListCount).as("other REGION user list").isZero();
            softly.assertThat(allScopeDetail.getResponse().getStatus()).as("ALL user detail").isEqualTo(200);
            softly.assertThat(exportedRows).as("PERSON export scope matches creator list").isEqualTo(1);
            softly.assertThat(creatorAfterResidence.getResponse().getStatus())
                    .as("initial creator loses origin scope after residence appears").isEqualTo(403);
            softly.assertThat(otherRegionAfterResidence.getResponse().getStatus())
                    .as("new residence region owns the person").isEqualTo(200);
        });
    }

    private String token(String username) throws Exception {
        String body = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("username", username, "password", "123456"))))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private Long uploadImage(String token) throws Exception {
        byte[] bytes = Arrays.copyOf(("round4-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8), 64);
        MockMultipartFile file = new MockMultipartFile("file", "synthetic-id-card.jpg", "image/jpeg", bytes);
        String body = mvc.perform(multipart("/api/persons/idcard-image").file(file)
                        .param("skipOcr", "true")
                        .header("Authorization", bearer(token)))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("imageId").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static String syntheticIdCard() {
        String base = "11010119991231" + String.format("%03d", ThreadLocalRandom.current().nextInt(50, 500) * 2 + 1);
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checks = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (base.charAt(i) - '0') * weights[i];
        }
        return base + checks[sum % 11];
    }
}
