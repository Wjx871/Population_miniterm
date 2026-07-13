package com.wjx871.population.keypopulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class Phase10KeyPopulationGateIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @MockBean KeyPopulationTransactionHook hook;

    @BeforeEach
    void setup() {
        reset(hook);
        jdbc.update("DELETE FROM key_population_history");
        jdbc.update("DELETE FROM key_population_application");
        jdbc.update("DELETE FROM key_population");
        jdbc.update("DELETE FROM sys_approval_log");
        jdbc.update("DELETE FROM sys_approval_request");
        jdbc.update("DELETE FROM application_material");
        jdbc.update("DELETE FROM business_application");
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("DELETE FROM residence WHERE person_id>=11001");
        jdbc.update("DELETE FROM household WHERE household_id>=11001");
        jdbc.update("DELETE FROM person WHERE person_id>=11001");
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,phone,current_address,status,current_status_code) VALUES(11001,'门禁人员','M','110105198001010016','13800138000','示例省测试地址','正常','REGISTERED')");
        jdbc.update("INSERT INTO household(household_id,household_no,address,region_code,household_type,establish_date,status,version) VALUES(11001,'GATE-H1','示例地址','110105','FAMILY',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,version) VALUES(11001,11001,'示例地址','110105','OTHER',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',0)");
    }

    @AfterEach void restoreHook() { reset(hook); }

    @Test
    void viewerAuthenticatesAndHasReadOnlyBoundary() throws Exception {
        String viewer = token("viewer");
        mvc.perform(get("/api/auth/me").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(get("/api/key-populations").header("Authorization", viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/key-populations/register-applications").header("Authorization", viewer)
                .contentType(MediaType.APPLICATION_JSON).content(registerBody())).andExpect(status().isForbidden());
        mvc.perform(post("/api/key-populations/register-applications/999/execute").header("Authorization", viewer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"version\":0}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void concurrentRegisterExecuteHasOneWinner() throws Exception {
        long app = approvedRegister();
        List<Integer> statuses = concurrentExecute("/api/key-populations/register-applications/" + app + "/execute", 2);
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(count("key_population", "source_application_id", app)).isOne();
        assertThat(count("key_population_history", "source_application_id", app)).isOne();
        assertThat(countOperation("KEY_POPULATION_REGISTER_EXECUTE")).isOne();
        assertThat(appStatus(app)).isEqualTo("COMPLETED");
        assertThat(detailStatus(app)).isEqualTo("COMPLETED");
    }

    @Test
    void concurrentReleaseExecuteHasOneWinner() throws Exception {
        long record = executeRegisterNormally();
        long app = approvedRelease(record);
        List<Integer> statuses = concurrentExecute("/api/key-populations/release-applications/" + app + "/execute", 2);
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(jdbc.queryForObject("SELECT status FROM key_population WHERE key_id=?", String.class, record)).isEqualTo("RELEASED");
        assertThat(count("key_population_history", "source_application_id", app)).isOne();
        assertThat(countOperation("KEY_POPULATION_RELEASE_EXECUTE")).isOne();
        assertThat(appStatus(app)).isEqualTo("COMPLETED");
    }

    @Test
    void registerFailureAfterHistoryRollsBackAndCanRetry() throws Exception {
        long app = approvedRegister();
        doThrow(new IllegalStateException("forced register rollback")).when(hook).afterHistoryInsert();
        execute("/api/key-populations/register-applications/" + app + "/execute", 2, 500);
        assertThat(count("key_population", "source_application_id", app)).isZero();
        assertThat(count("key_population_history", "source_application_id", app)).isZero();
        assertThat(appStatus(app)).isEqualTo("APPROVED");
        assertThat(detailStatus(app)).isEqualTo("APPROVED");
        assertThat(countOperation("KEY_POPULATION_REGISTER_EXECUTE")).isZero();
        assertThat(count("application_material", "application_id", app)).isEqualTo(2);
        assertThat(count("sys_approval_request", "application_id", app)).isOne();
        reset(hook);
        execute("/api/key-populations/register-applications/" + app + "/execute", 2, 200);
        assertThat(count("key_population", "source_application_id", app)).isOne();
        assertThat(count("key_population_history", "source_application_id", app)).isOne();
    }

    @Test
    void releaseFailureAfterHistoryRollsBackAndCanRetry() throws Exception {
        long record = executeRegisterNormally();
        int historyBefore = jdbc.queryForObject("SELECT COUNT(*) FROM key_population_history WHERE record_id=?", Integer.class, record);
        long app = approvedRelease(record);
        doThrow(new IllegalStateException("forced release rollback")).when(hook).afterHistoryInsert();
        execute("/api/key-populations/release-applications/" + app + "/execute", 2, 500);
        var row = jdbc.queryForMap("SELECT status,release_reason,release_date FROM key_population WHERE key_id=?", record);
        assertThat(row.get("STATUS")).isEqualTo("ACTIVE");
        assertThat(row.get("RELEASE_REASON")).isNull();
        assertThat(row.get("RELEASE_DATE")).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_population_history WHERE record_id=?", Integer.class, record)).isEqualTo(historyBefore);
        assertThat(appStatus(app)).isEqualTo("APPROVED");
        assertThat(detailStatus(app)).isEqualTo("APPROVED");
        assertThat(countOperation("KEY_POPULATION_RELEASE_EXECUTE")).isZero();
        reset(hook);
        execute("/api/key-populations/release-applications/" + app + "/execute", 2, 200);
        assertThat(jdbc.queryForObject("SELECT status FROM key_population WHERE key_id=?", String.class, record)).isEqualTo("RELEASED");
        assertThat(count("key_population_history", "source_application_id", app)).isOne();
    }

    private List<Integer> concurrentExecute(String path, int version) throws Exception {
        String auth = token("population");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < 2; i++) futures.add(pool.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("start timeout");
                return mvc.perform(post(path).header("Authorization", auth).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\":" + version + "}"))
                        .andReturn().getResponse().getStatus();
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            List<Integer> result = new ArrayList<>();
            for (Future<Integer> future : futures) result.add(future.get(15, TimeUnit.SECONDS));
            return result;
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long executeRegisterNormally() throws Exception {
        long app = approvedRegister();
        execute("/api/key-populations/register-applications/" + app + "/execute", 2, 200);
        return jdbc.queryForObject("SELECT key_id FROM key_population WHERE source_application_id=?", Long.class, app);
    }
    private long approvedRegister() throws Exception {
        JsonNode created = json.readTree(mvc.perform(post("/api/key-populations/register-applications")
                .header("Authorization", token("population")).contentType(MediaType.APPLICATION_JSON).content(registerBody()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long app = created.path("data").path("application").path("applicationId").asLong();
        prepareMaterials(app);
        mvc.perform(post("/api/key-populations/register-applications/" + app + "/submit")
                .header("Authorization", token("population"))).andExpect(status().isOk());
        approve(app);
        assertThat(count("key_population", "source_application_id", app)).isZero();
        return app;
    }
    private long approvedRelease(long record) throws Exception {
        String body = "{\"releaseReason\":\"门禁解除\",\"releaseDate\":\"2026-07-13\",\"title\":\"解除申请\"}";
        JsonNode created = json.readTree(mvc.perform(post("/api/key-populations/" + record + "/release-applications")
                .header("Authorization", token("population")).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long app = created.path("data").path("application").path("applicationId").asLong();
        prepareMaterials(app);
        mvc.perform(post("/api/key-populations/release-applications/" + app + "/submit")
                .header("Authorization", token("population"))).andExpect(status().isOk());
        approve(app);
        assertThat(jdbc.queryForObject("SELECT status FROM key_population WHERE key_id=?", String.class, record)).isEqualTo("ACTIVE");
        return app;
    }
    private void approve(long app) throws Exception {
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, app);
        mvc.perform(post("/api/approvals/" + approval + "/approve").header("Authorization", token("approver"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"同意\",\"version\":0}"))
                .andExpect(status().isOk());
    }
    private void prepareMaterials(long app) {
        material(app, "KEY_POPULATION_BASIS"); material(app, "SITUATION_DESCRIPTION");
    }
    private void material(long app, String type) {
        String id = UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,uploaded_by) VALUES(?,?,?,'test.pdf',?,'test','application/pdf',1,?,1,'VERIFIED',2)", app, type, type, id + ".pdf", id + id);
    }
    private void execute(String path, int version, int expected) throws Exception {
        mvc.perform(post(path).header("Authorization", token("population")).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":" + version + "}")) .andExpect(status().is(expected));
    }
    private String registerBody() { return "{\"personId\":11001,\"populationType\":\"OTHER\",\"attentionLevel\":\"MEDIUM\",\"registerReason\":\"门禁建档\",\"registerDate\":\"2026-07-13\",\"title\":\"建档申请\"}"; }
    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + json.readTree(body).path("data").path("token").asText();
    }
    private int count(String table, String key, long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + key + "=?", Integer.class, id); }
    private int countOperation(String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type=?", Integer.class, type); }
    private String appStatus(long app) { return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, app); }
    private String detailStatus(long app) { return jdbc.queryForObject("SELECT business_status FROM key_population_application WHERE application_id=?", String.class, app); }
}
