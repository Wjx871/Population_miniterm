package com.wjx871.population.migration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.*;

/** Real HTTP-dispatch/Spring/MyBatis/JDBC paths. Only the named archive SQL is fault-injected.
 * No test-managed transaction and no mapper spy. Synthetic non-1 return values are labelled separately.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(Issue22ArchiveAffectedRowsIntegrationTest.ProbeConfiguration.class)
class Issue22ArchiveAffectedRowsIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired ArchiveProbe probe;
    @MockBean MigrationTransactionHook hook;
    private String householdToken;
    private String approverToken;
    @BeforeEach
    void seedRealDatabase() throws Exception {
        probe.stop();
        reset(hook);
        // Default H2 fixtures; explicit MySQL IT uses only its guarded isolated schema.
        // Reset only database fixtures, never any project or uploaded files.
        for (String table : List.of("residence_archive", "migration_in", "migration_out", "residence",
                "household_member", "sys_approval_log", "sys_approval_request", "application_material",
                "cancellation_record", "business_application", "operation_log")) {
            jdbc.update("DELETE FROM " + table);
        }
        jdbc.update("DELETE FROM household WHERE household_id>=101");
        jdbc.update("DELETE FROM person WHERE person_id>=101");
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,current_address,status,current_status_code) "
                + "VALUES(101,'事务甲','1','110101199001010011','旧址','正常','REGISTERED'),"
                + "(102,'事务乙','2','110101199202020022',NULL,'正常','PENDING'),"
                + "(103,'事务丙','1','110101199303030033','旧址','正常','REGISTERED')");
        jdbc.update("INSERT INTO household(household_id,household_no,head_person_id,address,region_code,"
                + "establish_date,status,version) VALUES(101,'TX-H101',101,'事务旧址','110101',"
                + "DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) "
                + "VALUES(101,101,'HEAD',DATE '2020-01-01','ACTIVE',0),"
                + "(101,103,'CHILD',DATE '2020-01-01','ACTIVE',0)");
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,"
                + "register_date,start_date,status,created_by,version) VALUES"
                + "(101,101,'事务旧址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0),"
                + "(103,101,'事务旧址','110101','BIRTH',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',6,0)");
        householdToken = token("household");
        approverToken = token("approver");
        assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
    }


    @AfterEach void stopProbe() { probe.stop(); }

    @Test void zeroRowSqlRejectsBeforeHookAndPreservesAllBusinessRows() throws Exception {
        assertRejected("ZERO_SQL");
    }

    @ParameterizedTest @ValueSource(ints = {-1, 2})
    void syntheticUnexpectedMapperCountRollsBackTheActualInsert(int count) throws Exception {
        assertRejected("RETURN_" + count);
    }

    private void assertRejected(String mode) throws Exception {
        long id = approved("out");
        var before = snapshot();
        String beforeSummary = summary(id);
        Observation o = probe.start(mode);
        var response = mvc.perform(post("/api/migrations/out/applications/" + id + "/execute")
                .header("Authorization", "Bearer " + householdToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":2}")).andReturn().getResponse();
        probe.stop();
        System.out.println("ISSUE22 mode=" + mode + " physicalRows=" + o.physicalRows
                + " returnedRows=" + o.returnedRows + " transactionCompletion=" + o.completion
                + " HTTP=" + response.getStatus() + " body=" + response.getContentAsString()
                + " BEFORE=" + beforeSummary + " AFTER=" + summary(id)
                + " unchanged=" + before.equals(snapshot()) + " SQL=" + o.sql);
        assertThat(o.physicalRows).containsExactly(mode.equals("ZERO_SQL") ? 0 : 1);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(json.readTree(response.getContentAsString()).path("message").asText())
                .isEqualTo("ARCHIVE_INSERT_ROW_COUNT_MISMATCH");
        assertThat(o.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(hook);
    }

    @Test void normalArchiveInsertsExactlyOneThenCompletesAndRepeatIsRejected() throws Exception {
        long id = approved("out");
        Observation o = probe.start("NORMAL");
        execute("out", id, 2, 200);
        probe.stop();
        assertThat(o.physicalRows).containsExactly(1);
        assertThat(o.completion).isEqualTo(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(count("residence_archive", "application_id", id)).isOne();
        assertThat(count("residence", "person_id", 101)).isZero();
        assertThat(applicationStatus(id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT business_status FROM migration_out WHERE application_id=?", String.class, id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT head_person_id FROM household WHERE household_id=101", Long.class)).isEqualTo(103L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'", Integer.class, id)).isOne();
        verify(hook).afterArchiveInserted();
        var completed = snapshot();
        execute("out", id, 2, 409);
        assertThat(snapshot()).isEqualTo(completed);
        System.out.println("ISSUE22 NORMAL physicalRows=" + o.physicalRows + " completion=" + o.completion + " " + summary(id));
    }

    @Test void archiveUniqueConflictRollsBackWithoutAnyLaterHook() throws Exception {
        long id = approved("out");
        // Real application_id unique key conflict; do not alter production SQL/schema.
        jdbc.update("INSERT INTO residence_archive(original_registration_id,person_id,household_id,application_id,migration_out_id,archive_type,archive_reason,person_name_snapshot,identity_no_snapshot,household_no_snapshot,registered_address_snapshot,region_code_snapshot,register_type_code_snapshot,register_date_snapshot,start_date_snapshot,end_date_snapshot,original_status,archived_by,archived_at) "
                + "SELECT r.residence_id,101,101,?,o.out_id,'MIGRATION_OUT','fixture',p.name,p.id_card,h.household_no,r.registered_address,r.region_code,r.register_type_code,r.register_date,r.start_date,o.out_date,r.status,6,CURRENT_TIMESTAMP FROM residence r JOIN person p ON p.person_id=r.person_id JOIN household h ON h.household_id=r.household_id JOIN migration_out o ON o.application_id=? WHERE r.person_id=101", id, id);
        var before = snapshot();
        Observation o = probe.start("NORMAL");
        execute("out", id, 2, 409);
        probe.stop();
        assertThat(o.completion).isEqualTo(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(snapshot()).isEqualTo(before);
        verifyNoInteractions(hook);
        System.out.println("ISSUE22 UNIQUE_CONFLICT completion=" + o.completion + " unchanged=true " + summary(id));
    }

    @Test void concurrentOutExecutesAtMostOnce() throws Exception {
        long id = approved("out");
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        Callable<Integer> run = () -> {
            ready.countDown();
            if (!start.await(10, TimeUnit.SECONDS)) throw new AssertionError("start timeout");
            return mvc.perform(post("/api/migrations/out/applications/" + id + "/execute")
                    .header("Authorization", "Bearer " + householdToken).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"version\":2}")).andReturn().getResponse().getStatus();
        };
        try {
            var a = pool.submit(run); var b = pool.submit(run);
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            var statuses = List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
            System.out.println("ISSUE22 CONCURRENT HTTP=" + statuses + " " + summary(id));
            assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        } finally { start.countDown(); pool.shutdownNow(); }
        assertThat(count("residence_archive", "application_id", id)).isOne();
        assertThat(applicationStatus(id)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'", Integer.class, id)).isOne();
        verify(hook, times(1)).afterArchiveInserted();
    }

    private Map<String, List<Map<String,Object>>> snapshot() {
        Map<String, List<Map<String,Object>>> rows = new LinkedHashMap<>();
        for (String table : List.of("residence_archive", "residence", "household_member", "household", "person",
                "business_application", "migration_out", "sys_approval_request", "sys_approval_log", "operation_log"))
            rows.put(table, jdbc.queryForList("SELECT * FROM " + table + " ORDER BY 1"));
        return rows;
    }

    private String summary(long id) {
        return "archive=" + count("residence_archive", "application_id", id) + ",residence=" + count("residence", "person_id",101)
                + ",application=" + applicationStatus(id)
                + ",out=" + jdbc.queryForMap("SELECT business_status,version,transfer_batch_no FROM migration_out WHERE application_id=?", id)
                + ",person=" + jdbc.queryForMap("SELECT current_status_code,version FROM person WHERE person_id=101")
                + ",household=" + jdbc.queryForMap("SELECT head_person_id,status,version FROM household WHERE household_id=101")
                + ",members=" + jdbc.queryForList("SELECT person_id,relationship,status,version FROM household_member WHERE household_id=101 ORDER BY person_id")
                + ",executeLogs=" + jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_log WHERE application_id=? AND action='EXECUTE'", Integer.class,id)
                + ",successAudit=" + jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='MIGRATION_OUT_EXECUTE'",Integer.class);
    }
    private int count(String table, String key, long id) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + key + "=?", Integer.class,id); }
    private String applicationStatus(long id) { return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",String.class,id); }
    private long create(String direction) throws Exception {
        String body = direction.equals("in") ? """
                {"personId":102,"migrationType":"OUTSIDE_CITY","fromRegionCode":"370100","fromAddress":"济南",
                 "toRegionCode":"110101","toHouseholdId":101,"inDate":"2026-11-06","reason":"投靠","title":"事务迁入"}
                """ : """
                {"personId":101,"migrationType":"OUTSIDE_CITY","toRegionCode":"310000","toAddress":"上海",
                 "outDate":"2026-11-06","reason":"工作","newHeadPersonId":103,"title":"事务迁出"}
                """;
        return request(post("/api/migrations/" + direction + "/applications").content(body), householdToken, 201)
                .path("data").path("applicationId").asLong();
    }

    private long approved(String direction) throws Exception {
        long id = create(direction);
        material(id, "IDENTITY_PROOF", "VERIFIED");
        material(id, direction.equals("in") ? "ADDRESS_PROOF" : "HOUSEHOLD_BOOK", "VERIFIED");
        request(post("/api/applications/" + id + "/submit"), householdToken, 200);
        approve(id);
        assertThat(applicationStatus(id)).isEqualTo("APPROVED");
        return id;
    }

    private void approve(long id) throws Exception {
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, id);
        request(post("/api/approvals/" + approval + "/approve").content("{\"comment\":\"同意\",\"version\":0}"), approverToken, 200);
    }

    private long material(long id, String type, String verify) {
        String object = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,"
                + "storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) "
                + "VALUES(?,?,?,'fixture.pdf',?,'transaction-fixture','application/pdf',1,?,1,?,7,6)", id, type, type,
                object + ".pdf", object.replace("-", "").repeat(2), verify);
        return jdbc.queryForObject("SELECT material_id FROM application_material WHERE stored_filename=?", Long.class, object + ".pdf");
    }

    private void execute(String direction, long id, int version, int expected) throws Exception {
        request(post("/api/migrations/" + direction + "/applications/" + id + "/execute")
                .content("{\"version\":" + version + "}"), householdToken, expected);
    }

    private JsonNode request(MockHttpServletRequestBuilder builder, String token, int expected) throws Exception {
        var response = mvc.perform(builder.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
        assertThat(response.getStatus()).withFailMessage("HTTP %s: %s", response.getStatus(), response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }

    private String token(String user) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }


    @TestConfiguration(proxyBeanMethods=false)
    static class ProbeConfiguration {
        @Bean ArchiveProbe archiveProbe() { return new ArchiveProbe(); }
    }
    static final class Observation {
        final String mode;
        final List<Integer> physicalRows = new ArrayList<>(), returnedRows = new ArrayList<>();
        String sql;
        Integer completion;
        Observation(String mode) { this.mode = mode; }
    }
    @Intercepts({
        @Signature(type=Executor.class, method="update", args={MappedStatement.class,Object.class}),
        @Signature(type=StatementHandler.class, method="prepare", args={Connection.class,Integer.class})
    })
    static final class ArchiveProbe implements Interceptor {
        private static final String INSERT = "com.wjx871.population.migration.MigrationMapper.insertArchive";
        private final ThreadLocal<Observation> current = new ThreadLocal<>();
        private final ThreadLocal<Boolean> archiveSql = new ThreadLocal<>();
        Observation start(String mode) { Observation o = new Observation(mode); current.set(o); return o; }
        void stop() { current.remove(); archiveSql.remove(); }
        @Override public Object intercept(Invocation invocation) throws Throwable {
            Observation o = current.get();
            if (o == null) return invocation.proceed();
            if (invocation.getTarget() instanceof StatementHandler handler) {
                if (Boolean.TRUE.equals(archiveSql.get())) {
                    String sql = handler.getBoundSql().getSql();
                    if (o.mode.equals("ZERO_SQL")) {
                        sql += " AND 1=0 /* issue22 controlled zero-row SQL fault */";
                        SystemMetaObject.forObject(handler.getBoundSql()).setValue("sql",sql);
                    }
                    o.sql = sql;
                }
                return invocation.proceed();
            }
            if (!INSERT.equals(((MappedStatement)invocation.getArgs()[0]).getId())) return invocation.proceed();
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { o.completion = status; }
            });
            archiveSql.set(true);
            try {
                int actual = (Integer)invocation.proceed();
                o.physicalRows.add(actual);
                int returned = o.mode.startsWith("RETURN_") ? Integer.parseInt(o.mode.substring(7)) : actual;
                o.returnedRows.add(returned);
                return returned;
            } finally { archiveSql.remove(); }
        }
    }
}
