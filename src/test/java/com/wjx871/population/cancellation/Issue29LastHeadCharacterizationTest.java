package com.wjx871.population.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/** Characterizes an existing loss, rather than claiming that the history is recoverable. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:issue29-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc
class Issue29LastHeadCharacterizationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test void personCancellationThenHouseholdArchiveLosesHeadSnapshot() throws Exception {
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,status,current_status_code) VALUES(929001,'Synthetic Head','1','110101199001010011','正常','REGISTERED')");
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,household_type,establish_date,status,version) VALUES(929001,'I29-SYNTHETIC',929001,'Synthetic address','110101','FAMILY',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES(929001,929001,'HEAD',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES(929001,929001,'Synthetic address','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)");
        String population = token("population"), household = token("household"), approver = token("approver");
        long personApp = create("/api/cancellations/person/applications", population,
            "{\"personId\":929001,\"cancelReasonCode\":\"DEATH\",\"eventDate\":\"2026-07-13\",\"title\":\"synthetic\",\"reason\":\"synthetic\"}");
        for (String type : new String[]{"APPLICANT_IDENTITY_PROOF", "HOUSEHOLD_BOOK", "DEATH_CERTIFICATE"}) material(personApp, type);
        approve(personApp, population, approver);
        execute("/api/cancellations/person/applications/", personApp, household);
        assertThat(jdbc.queryForObject("SELECT status FROM household WHERE household_id=929001", String.class)).isEqualTo("PENDING_CANCELLATION");
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=929001", Long.class)).isNull();
        assertThat(jdbc.queryForObject("SELECT person_name_snapshot FROM residence_archive WHERE application_id=?", String.class, personApp)).isEqualTo("Synthetic Head");
        assertThat(jdbc.queryForObject("SELECT status FROM household_member WHERE household_id=929001 AND person_id=929001", String.class)).isEqualTo("CANCELLED");
        long houseApp = create("/api/cancellations/household/applications", household,
            "{\"householdId\":929001,\"cancelReasonCode\":\"NO_ACTIVE_MEMBERS\",\"eventDate\":\"2026-07-13\",\"title\":\"synthetic\",\"reason\":\"synthetic\"}");
        for (String type : new String[]{"CANCELLATION_APPLICATION", "HOUSEHOLD_BOOK", "HOUSEHOLD_CANCELLATION_PROOF"}) material(houseApp, type);
        approve(houseApp, household, approver);
        execute("/api/cancellations/household/applications/", houseApp, household);
        assertThat(jdbc.queryForObject("SELECT head_person_id_snapshot FROM household_archive WHERE application_id=?", Long.class, houseApp)).isNull();
        assertThat(jdbc.queryForObject("SELECT head_person_name_snapshot FROM household_archive WHERE application_id=?", String.class, houseApp)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'", Integer.class, personApp)).isOne();
    }
    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\""+user+"\",\"password\":\"123456\"}")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("token").asText();
    }
    private long create(String url, String token, String payload) throws Exception {
        String body = mvc.perform(post(url).header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content(payload)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("applicationId").asLong();
    }
    private void material(long app, String type) {
        String hash=UUID.randomUUID().toString().replace("-", "")+UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) VALUES(?,?,?,'x.pdf',?,?,'application/pdf',1,?,1,'VERIFIED',7,6)",app,type,type,UUID.randomUUID()+".pdf","test",hash);
    }
    private void approve(long app, String token, String approver) throws Exception {
        mvc.perform(post("/api/applications/"+app+"/submit").header("Authorization",token)).andExpect(status().isOk());
        Long id=jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?",Long.class,app);
        mvc.perform(post("/api/approvals/"+id+"/approve").header("Authorization",approver).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"synthetic\",\"version\":0}")).andExpect(status().isOk());
    }
    private void execute(String url, long app, String token) throws Exception {
        mvc.perform(post(url+app+"/execute").header("Authorization",token).contentType(MediaType.APPLICATION_JSON).content("{\"version\":2}")).andExpect(status().isOk());
    }
}
