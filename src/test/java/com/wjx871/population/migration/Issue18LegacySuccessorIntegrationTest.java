package com.wjx871.population.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:issue18_legacy;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class Issue18LegacySuccessorIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;

    @Test
    void returnedHistoricalApprovalNeedsRestrictedSuccessorCorrection() throws Exception {
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) VALUES(91801,'合成甲','1','110101199001010011','测试地址','正常','REGISTERED'),(91802,'合成乙','2','110101199202020022','测试地址','正常','REGISTERED')");
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,establish_date,status,version) VALUES(91801,'ISSUE18-H',91801,'测试地址','110101',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES(91801,91801,'HEAD',DATE '2020-01-01','ACTIVE',0),(91801,91802,'CHILD',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,created_by,version) VALUES(91801,91801,'测试地址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0),(91802,91801,'测试地址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)");
        String owner = token("household");
        var created = mvc.perform(post("/api/migrations/out/applications").header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"personId\":91801,\"migrationType\":\"OUTSIDE_CITY\",\"toAddress\":\"合成地址\",\"outDate\":\"2026-07-12\",\"reason\":\"合成\",\"title\":\"历史单\"}"))
                .andExpect(status().isCreated()).andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).path("data").path("applicationId").asLong();
        jdbc.update("UPDATE business_application SET status='APPROVED' WHERE application_id=?", id);
        jdbc.update("UPDATE migration_out SET business_status='APPROVED' WHERE application_id=?", id);
        jdbc.update("INSERT INTO sys_approval_request(approval_no,application_id,status,submitted_by,submitted_at,decided_by,decided_at,decision_comment,version) VALUES('ISSUE18-APR',?,'APPROVED',6,CURRENT_TIMESTAMP,7,CURRENT_TIMESTAMP,'合成历史审批',1)", id);
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, id);
        jdbc.update("INSERT INTO sys_approval_log(approval_id,application_id,action,from_status,to_status,operator_user_id,comment) VALUES(?,?,'APPROVE','UNDER_REVIEW','APPROVED',7,'合成历史审批')", approval, id);
        for (String type : new String[]{"IDENTITY_PROOF", "HOUSEHOLD_BOOK"}) {
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) VALUES(?,?,?,'synthetic.pdf',?,'synthetic','application/pdf',1,?,1,'VERIFIED',7,6)", id, type, type, type + ".pdf", "a".repeat(64));
            jdbc.update("INSERT INTO sys_approval_log(application_id,action,from_status,to_status,operator_user_id,comment) VALUES(?,'MATERIAL_VERIFY','PENDING','VERIFIED',7,?)", id, "合成历史材料核验 " + type);
        }
        mvc.perform(post("/api/migrations/out/applications/{id}/execute", id).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}")).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE household_id=91801 AND status='ACTIVE'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=91801", Long.class)).isEqualTo(91801L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM residence_archive WHERE application_id=?", Integer.class, id)).isZero();
        mvc.perform(post("/api/applications/{id}/return", id).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"补正继任者\",\"version\":0}")).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='APPROVE'", Integer.class, id)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='MATERIAL_VERIFY'", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE application_id=? AND verify_status='VERIFIED'", Integer.class, id)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT status FROM sys_approval_request WHERE approval_id=?", String.class, approval)).isEqualTo("APPROVED");
        // Existing full-draft edit must stay unavailable after return; it cannot repair the old record.
        mvc.perform(put("/api/migrations/out/applications/{id}", id).header("Authorization", owner)
                .contentType(MediaType.APPLICATION_JSON).content("{\"personId\":91801,\"migrationType\":\"OUTSIDE_CITY\",\"toAddress\":\"合成地址\",\"outDate\":\"2026-07-12\",\"reason\":\"合成\",\"title\":\"历史单\",\"newHeadPersonId\":91802,\"version\":0}"))
                .andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT new_head_person_id FROM migration_out WHERE application_id=?", Long.class, id)).isNull();
    }

    private String token(String user) throws Exception {
        var response = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn();
        return "Bearer " + json.readTree(response.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
