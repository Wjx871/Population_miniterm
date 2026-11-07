package com.wjx871.population.floating;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.reset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.application.ApplicationService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.*;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

/** Real TCP HTTP tests, each context owns a random, in-memory H2 database. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "residence-permit.min-residence-days=180", "residence-permit.validity-days=365",
    "spring.datasource.hikari.maximum-pool-size=12"})
@Import(Issue34HttpSupport.ClockConfig.class)
abstract class Issue34HttpSupport {
    @TestConfiguration static class ClockConfig {
        @Bean @Primary Clock issue34Clock() {
            return Clock.fixed(Instant.parse("2026-07-11T00:00:00Z"), ZoneOffset.UTC);
        }
    }
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @SpyBean ApplicationService applications;
    final Map<String,String> tokens = new HashMap<>();

    @BeforeEach void resetDatabase() throws Exception {
        reset(applications);
        try (java.sql.Connection connection = jdbc.getDataSource().getConnection()) {
            verifyIsolation(connection);
        }
        for (String table : List.of("residence_permit_log", "residence_permit_application", "residence_permit",
                "floating_population", "floating_registration_application", "residence_archive", "cancellation_record",
                "migration_in", "migration_out", "residence", "household_member", "sys_approval_log",
                "sys_approval_request", "application_material", "business_application", "operation_log")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM person WHERE person_id IN (9340001,9340002)");
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) "
            + "VALUES(9340001,'Issue34 A','1','370101199001010011','old','正常','MIGRATED_OUT'),"
            + "(9340002,'Issue34 B','1','370101199001010022','old','正常','MIGRATED_OUT')");
        tokens.clear();
        for (String user : List.of("population", "household", "approver", "admin", "viewer")) {
            ResponseEntity<String> response = http.postForEntity("/api/auth/login",
                Map.of("username", user, "password", "123456"), String.class);
            check(response, 200);
            tokens.put(user, tree(response).path("data").path("token").asText());
        }
    }
    void verifyIsolation(java.sql.Connection connection) throws Exception {
        assertThat(connection.getMetaData().getURL()).startsWith("jdbc:h2:mem:");
    }
    ResponseEntity<String> request(String method, String path, String user, Object body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.get(user));
        headers.setContentType(MediaType.APPLICATION_JSON);
        return http.exchange(path, HttpMethod.valueOf(method), new HttpEntity<>(body, headers), String.class);
    }
    ResponseEntity<String> post(String path, String user, Object body) { return request("POST", path, user, body); }
    void check(ResponseEntity<String> response, int expected) {
        assertThat(response.getStatusCode().value()).as(response.getBody()).isEqualTo(expected);
    }
    JsonNode tree(ResponseEntity<String> response) throws Exception { return json.readTree(response.getBody()); }
    long applicationId(ResponseEntity<String> response) throws Exception {
        check(response, 201); return tree(response).path("data").path("applicationId").asLong();
    }
    ResponseEntity<String> first(long floating, String user) {
        return post("/api/residence-permits/applications/first-issue", user,
            Map.of("floatingId", floating, "title", "Issue34 first issue", "reason", "eligibility",
                "residenceBasisCode", "EMPLOYMENT"));
    }
    long floating(long person) throws Exception {
        long app = applicationId(post("/api/floating-registrations/applications", "population",
            Map.of("personId", person, "sourceRegionCode", "370100", "sourceAddress", "source",
                "currentRegionCode", "110000", "currentAddress", "current", "residenceReasonCode", "EMPLOYMENT",
                "residenceProofType", "RENTAL_CONTRACT", "arrivalDate", "2026-01-01", "title", "Issue34 registration",
                "reason", "employment")));
        materials(app, false);
        submit(app); approve(app);
        check(post("/api/floating-registrations/applications/" + app + "/execute", "household", Map.of("version", 2)), 200);
        return jdbc.queryForObject("SELECT floating_id FROM floating_population WHERE source_application_id=?", Long.class, app);
    }
    void materials(long app, boolean permit) {
        List<String> types = permit ? List.of("APPLICANT_IDENTITY_PROOF", "PERSON_PHOTO", "RESIDENCE_ADDRESS_PROOF", "EMPLOYMENT_PROOF")
            : List.of("APPLICANT_IDENTITY_PROOF", "RESIDENCE_ADDRESS_PROOF", "EMPLOYMENT_PROOF");
        for (String type : types) {
            String hash = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,"
                + "storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) "
                + "VALUES(?,?,?,'x.pdf',?,'test','application/pdf',1,?,1,'VERIFIED',?,?)", app, type, type, UUID.randomUUID()+".pdf", hash,
                jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username='approver'",Long.class),
                jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username='population'",Long.class));
        }
    }
    void submit(long app) { check(post("/api/applications/"+app+"/submit", "population", null), 200); }
    void approve(long app) {
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, app);
        check(post("/api/approvals/"+approval+"/approve", "approver", Map.of("comment", "agree", "version", 0)), 200);
    }
    void returned(long app) {
        int version = jdbc.queryForObject("SELECT version FROM business_application WHERE application_id=?", Integer.class, app);
        check(post("/api/applications/"+app+"/return", "household", Map.of("comment", "correct materials", "version", version)), 200);
    }
    String state(long app) { return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, app); }
    int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM "+table, Integer.class); }
    List<Integer> snapshot() {
        return List.of(count("business_application"), count("residence_permit_application"), count("sys_approval_request"),
            count("sys_approval_log"), count("operation_log"), count("application_material"), count("residence_permit_log"));
    }
    void evidence(String scenario) {
        System.out.println("ISSUE34 " + scenario + " generic=" + jdbc.queryForList("SELECT application_id,target_person_id,status,applicant_user_id FROM business_application WHERE business_type='RESIDENCE_PERMIT_FIRST_ISSUE'")
            + " professional=" + jdbc.queryForList("SELECT application_id,person_id,floating_id,apply_type,business_status FROM residence_permit_application"));
    }
}
