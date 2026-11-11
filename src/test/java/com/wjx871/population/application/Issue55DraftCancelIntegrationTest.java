package com.wjx871.population.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:issue55_cancel;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class Issue55DraftCancelIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void versionAndAuthorizationGateTheCancellation() throws Exception {
        String owner = token("population");
        String other = token("household");
        String viewer = token("viewer");
        var created = mvc.perform(post("/api/applications").header("Authorization", owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"businessType\":\"GENERAL_SERVICE\",\"title\":\"版本测试\",\"reason\":\"合成测试\"}"))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).path("data").path("applicationId").asLong();
        mvc.perform(delete("/api/applications/{id}", id).header("Authorization", owner)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/applications/{id}", id).param("version", "bad").header("Authorization", owner)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/applications/{id}", id).param("version", "-1").header("Authorization", owner)).andExpect(status().isBadRequest());
        mvc.perform(delete("/api/applications/{id}", id).param("version", "0").header("Authorization", other)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/applications/{id}", id).param("version", "0").header("Authorization", viewer)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/applications/{id}", id).param("version", "1").header("Authorization", owner)).andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(cancelLogs(id)).isZero();
        mvc.perform(delete("/api/applications/{id}", id).param("version", "0").header("Authorization", owner)).andExpect(status().isOk());
        mvc.perform(delete("/api/applications/{id}", id).param("version", "0").header("Authorization", owner)).andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(cancelLogs(id)).isEqualTo(1);
    }

    @Test
    void migrationOutCancellationUsesTheClientApplicationVersion() throws Exception {
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) VALUES(95501,'合成甲','1','110101199001010011','测试地址','正常','REGISTERED')");
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,establish_date,status,version) VALUES(95501,'ISSUE55-H',95501,'测试地址','110101',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES(95501,95501,'HEAD',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES(95501,95501,'测试地址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)");
        String owner = token("household");
        var response = mvc.perform(post("/api/migrations/out/applications").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"personId\":95501,\"migrationType\":\"OUTSIDE_CITY\",\"toAddress\":\"合成地址\",\"outDate\":\"2026-07-12\",\"reason\":\"合成\",\"title\":\"取消版本测试\"}"))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(response.getResponse().getContentAsString()).path("data").path("applicationId").asLong();
        jdbc.update("UPDATE business_application SET version=version+1 WHERE application_id=?", id);
        mvc.perform(delete("/api/applications/{id}", id).param("version", "0").header("Authorization", owner)).andExpect(status().isConflict());
        assertThat(statusOf(id)).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, id)).isEqualTo("DRAFT");
        assertThat(cancelLogs(id)).isZero();
        mvc.perform(delete("/api/applications/{id}", id).param("version", "1").header("Authorization", owner)).andExpect(status().isOk());
        assertThat(statusOf(id)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, id)).isEqualTo("CANCELLED");
        assertThat(cancelLogs(id)).isOne();
    }

    private String token(String user) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(response.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, id);
    }

    private int cancelLogs(long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='CANCEL'", Integer.class, id);
    }
}
