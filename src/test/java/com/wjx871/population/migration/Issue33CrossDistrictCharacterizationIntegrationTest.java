package com.wjx871.population.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:issue33_city_boundary;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
class Issue33CrossDistrictCharacterizationIntegrationTest {
    private static final long PERSON_OUT = 9101L;
    private static final long PERSON_IN = 9102L;
    private static final long PERSON_HEAD_D2 = 9103L;
    private static final long PERSON_HEAD_D3 = 9104L;
    private static final long HOUSEHOLD_SOURCE = 9201L;
    private static final long HOUSEHOLD_D2 = 9202L;
    private static final long HOUSEHOLD_D3 = 9203L;

    private static final long P1 = 980001L;
    private static final long C1 = 980002L;
    private static final long D1 = 980003L;
    private static final long D2 = 980004L;
    private static final long S1 = 980005L;
    private static final long N1 = 980006L;
    private static final long C2 = 980007L;
    private static final long D3 = 980008L;
    private static final long P2 = 980009L;
    private static final long C3 = 980010L;
    private static final long D4 = 980011L;
    private static final long DISABLED_D = 980012L;
    private static final long DISABLED_C = 980013L;
    private static final long ENABLED_CHILD_OF_DISABLED_C = 980014L;
    private static final long SKIPPED_LEVEL_CHILD = 980015L;

    private static final String D1_CODE = "472901";
    private static final String D2_CODE = "183746";
    private static final String D3_CODE = "965208";
    private static final String D4_CODE = "350174";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seedIsolatedSyntheticData() {
        jdbc.update("DELETE FROM residence_archive");
        jdbc.update("DELETE FROM migration_in");
        jdbc.update("DELETE FROM migration_out");
        jdbc.update("DELETE FROM residence");
        jdbc.update("DELETE FROM household_member");
        jdbc.update("DELETE FROM application_material");
        jdbc.update("DELETE FROM sys_approval_log");
        jdbc.update("DELETE FROM sys_approval_request");
        jdbc.update("DELETE FROM cancellation_record");
        jdbc.update("DELETE FROM business_application");
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM household WHERE household_id BETWEEN 9201 AND 9203");
        jdbc.update("DELETE FROM person WHERE person_id BETWEEN 9101 AND 9104");
        jdbc.update("DELETE FROM admin_region WHERE region_id=980006");
        jdbc.update("DELETE FROM admin_region WHERE region_id=980005");
        jdbc.update("DELETE FROM admin_region WHERE region_id IN (980003,980004,980008,980011,980012,980014,980015)");
        jdbc.update("DELETE FROM admin_region WHERE region_id IN (980002,980007,980010,980013)");
        jdbc.update("DELETE FROM admin_region WHERE region_id IN (980001,980009)");
        seedRegions();
        seedPeopleAndHouseholds();
    }

    @Test
    void crossCityDraftAndMissingSourceRegionCodeAreAcceptedAtCreation() throws Exception {
        String response = mvc.perform(post("/api/migrations/in/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(crossInBody(PERSON_IN, D1_CODE, D3_CODE, HOUSEHOLD_D3, "TR-NOT-COMPLETED")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        long draft = json.readTree(response).path("data").path("applicationId").asLong();
        assertThat(draft).isPositive();
        assertThat(parentId(D1)).isEqualTo(C1);
        assertThat(parentId(D3)).isEqualTo(C2);
        assertThat(parentId(C1)).isEqualTo(P1);
        assertThat(parentId(C2)).isEqualTo(P1);
        assertThat(jdbc.queryForObject("SELECT from_region_code FROM migration_in WHERE application_id=?", String.class, draft)).isEqualTo(D1_CODE);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_out WHERE transfer_batch_no='TR-NOT-COMPLETED' AND business_status='COMPLETED'", Integer.class)).isZero();
        addVerifiedMaterials(draft, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submitAndApprove(draft);
        execute("in", draft, 409);
        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, draft)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?", Integer.class, PERSON_IN)).isZero();

        String sameDistrictResponse = mvc.perform(post("/api/migrations/in/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(outsideInBody(PERSON_IN, D1_CODE, HOUSEHOLD_SOURCE)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long sameDistrictDraft = json.readTree(sameDistrictResponse).path("data").path("applicationId").asLong();
        assertThat(jdbc.queryForObject("SELECT to_region_code FROM migration_in WHERE application_id=?", String.class, sameDistrictDraft)).isEqualTo(D1_CODE);

        String missingRegionResponse = mvc.perform(post("/api/migrations/in/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(crossInBody(PERSON_IN, "991234", D3_CODE, HOUSEHOLD_D3, "TR-ALSO-NOT-COMPLETED")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long missingCodeDraft = json.readTree(missingRegionResponse).path("data").path("applicationId").asLong();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_region WHERE region_code='991234'", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT from_region_code FROM migration_in WHERE application_id=?", String.class, missingCodeDraft)).isEqualTo("991234");
    }

    @Test
    void globalResidenceLookupAndUniqueConstraintRejectSecondCurrentRegistration() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);
        mvc.perform(post("/api/migrations/in/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(outsideInBody(PERSON_OUT, D2_CODE, HOUSEHOLD_D2)))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class)).isEqualTo(before);

        assertThatThrownBy(() -> jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES(?,?,? ,?,'BIRTH',DATE '2026-11-06',DATE '2026-11-06','ACTIVE',6,0)",
            PERSON_OUT, HOUSEHOLD_D2, "Synthetic duplicate", D2_CODE))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?", Integer.class, PERSON_OUT)).isOne();
    }

    @Test
    void outsideCityTypeCanCompleteSameCityDifferentDistrictMigrationOut() throws Exception {
        long out = createOut("OUTSIDE_CITY", D2_CODE);
        assertThat(jdbc.queryForObject("SELECT from_region_code FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo(D1_CODE);
        assertThat(jdbc.queryForObject("SELECT to_region_code FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo(D2_CODE);
        assertThat(parentId(D1)).isEqualTo(C1);
        assertThat(parentId(D2)).isEqualTo(C1);

        mvc.perform(put("/api/migrations/out/applications/{id}", out)
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personId\":" + PERSON_OUT + ",\"migrationType\":\"OUTSIDE_CITY\",\"toRegionCode\":\"" + D2_CODE
                    + "\",\"toAddress\":\"Synthetic destination update\",\"outDate\":\"2026-11-06\",\"reason\":\"Synthetic test\",\"title\":\"Issue 33 out update\",\"version\":0}"))
            .andExpect(status().isOk());

        addVerifiedMaterials(out, "IDENTITY_PROOF", "HOUSEHOLD_BOOK");
        submitAndApprove(out);
        execute("out", out, 200);

        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT transfer_batch_no FROM migration_out WHERE application_id=?", String.class, out)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, out)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?", Integer.class, PERSON_OUT)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM household_member WHERE household_id=? AND person_id=?", String.class, HOUSEHOLD_SOURCE, PERSON_OUT)).isEqualTo("LEFT");
    }

    @Test
    void completedCrossCityBatchExecutesAfterDraftRegionChangeAndRecordsConsumption() throws Exception {
        long out = createOut("IN_CITY_CROSS_DISTRICT", D3_CODE);
        addVerifiedMaterials(out, "IDENTITY_PROOF", "HOUSEHOLD_BOOK");
        submitAndApprove(out);
        execute("out", out, 200);
        String batch = jdbc.queryForObject("SELECT transfer_batch_no FROM migration_out WHERE application_id=?", String.class, out);
        assertThat(batch).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT from_region_code FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo(D1_CODE);
        assertThat(jdbc.queryForObject("SELECT to_region_code FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo(D3_CODE);
        assertThat(parentId(D1)).isEqualTo(C1);
        assertThat(parentId(D3)).isEqualTo(C2);

        long in = createCrossIn(PERSON_OUT, D4_CODE, D3_CODE, HOUSEHOLD_D3, batch);
        assertThat(jdbc.queryForObject("SELECT from_region_code FROM migration_in WHERE application_id=?", String.class, in)).isEqualTo(D4_CODE);
        assertThat(jdbc.queryForObject("SELECT from_region_code FROM migration_out WHERE application_id=?", String.class, out)).isEqualTo(D1_CODE);

        jdbc.update("UPDATE admin_region SET parent_id=?,status='DISABLED' WHERE region_id=?", C3, D3);
        assertThat(parentId(D3)).isEqualTo(C3);
        assertThat(jdbc.queryForObject("SELECT status FROM admin_region WHERE region_id=?", String.class, D3)).isEqualTo("DISABLED");

        int inVersion = jdbc.queryForObject("SELECT version FROM migration_in WHERE application_id=?", Integer.class, in);
        mvc.perform(put("/api/migrations/in/applications/{id}", in)
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(crossInBody(PERSON_OUT, D4_CODE, D3_CODE, HOUSEHOLD_D3, batch, inVersion)))
            .andExpect(status().isOk());

        addVerifiedMaterials(in, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submitAndApprove(in);
        execute("in", in, 200);

        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, in)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND region_code=?", Integer.class, PERSON_OUT, D3_CODE)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=? AND business_status='COMPLETED'", Integer.class, batch)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM admin_region WHERE region_id=? AND status='ENABLED'", Integer.class, D3)).isZero();

        int applicationsBeforeDuplicate = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);
        mvc.perform(post("/api/migrations/in/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(crossInBody(PERSON_IN, D1_CODE, D3_CODE, HOUSEHOLD_D3, batch)))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class)).isEqualTo(applicationsBeforeDuplicate);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=?", Integer.class, batch)).isOne();

        int before = jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class);
        mvc.perform(post("/api/migrations/in/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(crossInBody(PERSON_OUT, D1_CODE, D3_CODE, HOUSEHOLD_D3, batch)))
            .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM business_application", Integer.class)).isEqualTo(before);
    }

    @Test
    void completedBatchRejectsMismatchedTargetAtExecutionWithoutPartialWrites() throws Exception {
        String batch = completedCrossCityBatch();
        long in = createCrossIn(PERSON_OUT, D1_CODE, D2_CODE, HOUSEHOLD_D2, batch);
        addVerifiedMaterials(in, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submitAndApprove(in);

        execute("in", in, 409);

        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, in)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, in)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?", Integer.class, PERSON_OUT)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=? AND business_status='COMPLETED'", Integer.class, batch)).isZero();
    }

    @Test
    void completedBatchRejectsMismatchedPersonAtExecutionWithoutPartialWrites() throws Exception {
        String batch = completedCrossCityBatch();
        long in = createCrossIn(PERSON_IN, D1_CODE, D3_CODE, HOUSEHOLD_D3, batch);
        addVerifiedMaterials(in, "IDENTITY_PROOF", "ADDRESS_PROOF", "MIGRATION_PROOF");
        submitAndApprove(in);

        execute("in", in, 409);

        assertThat(jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, in)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_in WHERE application_id=?", String.class, in)).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=?", Integer.class, PERSON_IN)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=? AND business_status='COMPLETED'", Integer.class, batch)).isZero();
    }

    private void seedRegions() {
        region(P1, "821604", "Synthetic province one", null, 1, "ENABLED");
        region(C1, "314807", "Synthetic city one", P1, 2, "ENABLED");
        region(D1, D1_CODE, "Synthetic district one", C1, 3, "ENABLED");
        region(D2, D2_CODE, "Synthetic district two", C1, 3, "ENABLED");
        region(S1, "507319", "Synthetic street", D2, 4, "ENABLED");
        region(N1, "692450", "Synthetic community", S1, 5, "ENABLED");
        region(C2, "748215", "Synthetic city two", P1, 2, "ENABLED");
        region(D3, D3_CODE, "Synthetic district three", C2, 3, "ENABLED");
        region(P2, "105938", "Synthetic province two", null, 1, "ENABLED");
        region(C3, "684023", "Synthetic city three", P2, 2, "ENABLED");
        region(D4, D4_CODE, "Synthetic district four", C3, 3, "ENABLED");
        region(DISABLED_D, "239680", "Disabled synthetic district", C1, 3, "DISABLED");
        region(DISABLED_C, "871356", "Disabled synthetic city", P2, 2, "DISABLED");
        region(ENABLED_CHILD_OF_DISABLED_C, "428901", "Enabled child under disabled city", DISABLED_C, 3, "ENABLED");
        region(SKIPPED_LEVEL_CHILD, "950173", "Synthetic skipped-level child", C1, 5, "ENABLED");
    }

    private void region(long id, String code, String name, Long parentId, int level, String status) {
        jdbc.update("INSERT INTO admin_region(region_id,region_code,region_name,parent_id,region_level,full_name,sort_no,status,version) VALUES(?,?,?,?,?,?,0,?,0)",
            id, code, name, parentId, level, name, status);
    }

    private void seedPeopleAndHouseholds() {
        for (long id : List.of(PERSON_OUT, PERSON_IN, PERSON_HEAD_D2, PERSON_HEAD_D3)) {
            jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) VALUES(?,?,'1',?,NULL,'正常',?)",
                id, "Synthetic " + id, String.format("990000000000%06d", id), id == PERSON_OUT ? "REGISTERED" : "PENDING");
        }
        household(HOUSEHOLD_SOURCE, PERSON_OUT, D1_CODE);
        household(HOUSEHOLD_D2, PERSON_HEAD_D2, D2_CODE);
        household(HOUSEHOLD_D3, PERSON_HEAD_D3, D3_CODE);
        member(HOUSEHOLD_SOURCE, PERSON_OUT, "HEAD");
        member(HOUSEHOLD_D2, PERSON_HEAD_D2, "HEAD");
        member(HOUSEHOLD_D3, PERSON_HEAD_D3, "HEAD");
        residence(PERSON_OUT, HOUSEHOLD_SOURCE, D1_CODE);
        residence(PERSON_HEAD_D2, HOUSEHOLD_D2, D2_CODE);
        residence(PERSON_HEAD_D3, HOUSEHOLD_D3, D3_CODE);
    }

    private void household(long id, long head, String regionCode) {
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,establish_date,status,version) VALUES(?,?,?,? ,?,DATE '2020-01-01','ACTIVE',0)",
            id, "SYN-" + id, head, "Synthetic address " + regionCode, regionCode);
    }

    private void member(long householdId, long personId, String relationship) {
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES(?,?,?,DATE '2020-01-01','ACTIVE',0)",
            householdId, personId, relationship);
    }

    private void residence(long personId, long householdId, String regionCode) {
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES(?,?,?,?,'BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)",
            personId, householdId, "Synthetic residence " + regionCode, regionCode);
    }

    private String crossInBody(long personId, String fromRegion, String toRegion, long householdId, String batch) {
        return crossInBody(personId, fromRegion, toRegion, householdId, batch, null);
    }

    private String crossInBody(long personId, String fromRegion, String toRegion, long householdId, String batch, Integer version) {
        return "{\"personId\":" + personId + ",\"migrationType\":\"IN_CITY_CROSS_DISTRICT\",\"fromRegionCode\":\"" + fromRegion
            + "\",\"fromAddress\":\"Synthetic source\",\"toRegionCode\":\"" + toRegion + "\",\"toHouseholdId\":" + householdId
            + ",\"inDate\":\"2026-11-06\",\"reason\":\"Synthetic test\",\"title\":\"Issue 33 characterization\",\"transferBatchNo\":\"" + batch + "\""
            + (version == null ? "" : ",\"version\":" + version) + "}";
    }

    private String outsideInBody(long personId, String toRegion, long householdId) {
        return "{\"personId\":" + personId + ",\"migrationType\":\"OUTSIDE_CITY\",\"fromRegionCode\":\"999999\",\"fromAddress\":\"Synthetic source\",\"toRegionCode\":\"" + toRegion
            + "\",\"toHouseholdId\":" + householdId + ",\"inDate\":\"2026-11-06\",\"reason\":\"Synthetic test\",\"title\":\"Issue 33 characterization\"}";
    }

    private long createOut(String type, String toRegion) throws Exception {
        String response = mvc.perform(post("/api/migrations/out/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personId\":" + PERSON_OUT + ",\"migrationType\":\"" + type + "\",\"fromRegionCode\":\"999999\",\"toRegionCode\":\"" + toRegion
                    + "\",\"toAddress\":\"Synthetic destination\",\"outDate\":\"2026-11-06\",\"reason\":\"Synthetic test\",\"title\":\"Issue 33 out\"}"))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("applicationId").asLong();
    }

    private long createCrossIn(long personId, String fromRegion, String toRegion, long householdId, String batch) throws Exception {
        String response = mvc.perform(post("/api/migrations/in/applications")
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content(crossInBody(personId, fromRegion, toRegion, householdId, batch)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("applicationId").asLong();
    }

    private String completedCrossCityBatch() throws Exception {
        long out = createOut("IN_CITY_CROSS_DISTRICT", D3_CODE);
        addVerifiedMaterials(out, "IDENTITY_PROOF", "HOUSEHOLD_BOOK");
        submitAndApprove(out);
        execute("out", out, 200);
        return jdbc.queryForObject("SELECT transfer_batch_no FROM migration_out WHERE application_id=?", String.class, out);
    }

    private void addVerifiedMaterials(long applicationId, String... types) {
        for (String type : types) {
            String sha = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) VALUES(?,?,?,'synthetic.pdf',?,'h2-synthetic','application/pdf',1,?,1,'VERIFIED',7,6)",
                applicationId, type, type, UUID.randomUUID() + ".pdf", sha);
        }
    }

    private void submitAndApprove(long applicationId) throws Exception {
        mvc.perform(post("/api/applications/{id}/submit", applicationId)
                .header("Authorization", bearer(token("household"))))
            .andExpect(status().isOk());
        long approvalId = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, applicationId);
        mvc.perform(post("/api/approvals/{id}/approve", approvalId)
                .header("Authorization", bearer(token("approver")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"comment\":\"Synthetic characterization\",\"version\":0}"))
            .andExpect(status().isOk());
    }

    private void execute(String direction, long applicationId, int expectedStatus) throws Exception {
        String table = "in".equals(direction) ? "migration_in" : "migration_out";
        int version = jdbc.queryForObject("SELECT version FROM " + table + " WHERE application_id=?", Integer.class, applicationId);
        mvc.perform(post("/api/migrations/{direction}/applications/{id}/execute", direction, applicationId)
                .header("Authorization", bearer(token("household")))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}"))
            .andExpect(status().is(expectedStatus));
    }

    private Long parentId(long regionId) {
        return jdbc.queryForObject("SELECT parent_id FROM admin_region WHERE region_id=?", Long.class, regionId);
    }

    private String token(String username) throws Exception {
        String response = mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("data").path("token").asText();
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
