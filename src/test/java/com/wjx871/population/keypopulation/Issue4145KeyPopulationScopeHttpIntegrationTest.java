package com.wjx871.population.keypopulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.mybatis.spring.boot.autoconfigure.MybatisProperties;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.util.ReflectionTestUtils;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.mapping.BoundSql;
import static org.assertj.core.api.Assertions.assertThat;

/** Actual TCP HTTP, Security/JWT/AuthService and isolated SQL database; no mocked RBAC or mapper. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "issue4145.http-isolation=true")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class Issue4145KeyPopulationScopeHttpIntegrationTest {
    static final AtomicLong IDS = new AtomicLong(414500);
    @Autowired TestRestTemplate http;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired SqlSessionFactory sessions;
    long id;
    String a, b, approver;
    final Map<String, String> tokens = new HashMap<>();

    @BeforeAll static void warmMapperResourcesBeforeTomcat() {
        // MybatisProperties 3.0.5 has a static Spring resolver whose directory cache retains its loader.
        MybatisProperties properties = new MybatisProperties();
        properties.setMapperLocations(new String[] {"classpath:mapper/*.xml"});
        properties.resolveMapperLocations();
    }
    @AfterAll static void restoreTestClassLoader() {
        // Do not leave cached resources backed by a stopped embedded-webapp loader in other tests.
        ((PathMatchingResourcePatternResolver) ReflectionTestUtils.getField(MybatisProperties.class, "resourceResolver")).clearCache();
        Thread.currentThread().setContextClassLoader(Issue4145KeyPopulationScopeHttpIntegrationTest.class.getClassLoader());
    }

    @BeforeEach void fixture() throws Exception {
        try (var connection = jdbc.getDataSource().getConnection()) {
            String url = connection.getMetaData().getURL();
            if (connection.getMetaData().getDatabaseProductName().equals("MySQL")) {
                assertThat(url).matches("jdbc:mysql://127\\.0\\.0\\.1:(?!3306/)[0-9]+/population_miniterm_mysql_gate_[A-Za-z0-9_]+\\?.*");
                assertThat(connection.getMetaData().getUserName()).startsWith("i4145_app@");
                assertThat(jdbc.queryForObject("SELECT @@version", String.class)).startsWith("8.4.");
            } else { assertThat(url).startsWith("jdbc:h2:mem:"); }
        }
        id = IDS.addAndGet(10);
        tokens.clear();
        for (int n = 0; n < 2; n++) {
            long x = id + n;
            String region = n == 0 ? "110101" : "110105";
            jdbc.update("INSERT INTO sys_department(department_id,department_code,department_name,region_code,status,sort_no) VALUES(?,?,?,?,'ENABLED',0)",
                    x, "I4145D" + x, "Synthetic department", region);
            jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,role_level,data_scope,status) VALUES(?,?,?,'L2','REGION','ENABLED')",
                    x, "I4145R" + x, "Synthetic role " + x);
            jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) SELECT ?,permission_id FROM sys_permission WHERE permission_code IN "
                    + "('key-population:view','key-population:apply','key-population:execute','application:create','application:submit','application:view','cancellation:person:create','cancellation:execute')", x);
            jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) SELECT ?,?,password_hash,?,?,?,'ENABLED' FROM sys_user WHERE user_id=2",
                    x, "i4145u" + x, x, x, "Synthetic user");
        }
        a = token("i4145u" + id);
        b = token("i4145u" + (id + 1));
        approver = token("approver");
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,phone,status,current_status_code) VALUES(?,'Synthetic person','M',?,'13812345678','正常','REGISTERED')",
                id, String.format("1101051980%08d", id));
        jdbc.update("INSERT INTO household(household_id,household_no,address,region_code,household_type,establish_date,status,version) VALUES(?,?,'Synthetic address','110101','FAMILY',DATE '2020-01-01','ACTIVE',0)", id, "I4145H" + id);
        jdbc.update("INSERT INTO household_member(household_id,person_id,relationship,join_date,status,version) VALUES(?,?,'MEMBER',DATE '2020-01-01','ACTIVE',0)", id, id);
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,register_date,start_date,status,version) VALUES(?,?,'Synthetic address','110101','OTHER',DATE '2020-01-01',DATE '2020-01-01','ACTIVE',0)", id, id);
        assertThat(data(get("/api/auth/me", a)).path("regionCodes").toString()).doesNotContain("110105");
        assertThat(data(get("/api/auth/me", b)).path("regionCodes").toString()).doesNotContain("110101");
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void cancelledOriginalManagerCanCreateSubmitAndExplicitlyExecuteRelease(boolean generic) throws Exception {
        long record = register(a);
        snapshotPerson("before-cancellation", record);
        cancel(a, "SETTLED_ABROAD");
        assertThat(jdbc.queryForObject("SELECT current_status_code FROM person WHERE person_id=?", String.class, id)).isEqualTo("CANCELLED");
        assertThat(count("residence", "person_id", id)).isZero();
        assertThat(count("residence_archive", "person_id", id)).isOne();
        snapshotPerson("after-cancellation", record);
        expect(get("/api/key-populations/" + record, a), 200);
        long release = release(record, a);
        materials(release, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        String path = generic ? "/api/applications/" + release + "/submit" : "/api/key-populations/release-applications/" + release + "/submit";
        ResponseEntity<String> submitted = post(path, a, null);
        System.out.printf("ISSUE41_STAGES query=200 draft=201 materials=%d generic=%s submit=%d status=%s approvals=%d%n",
                count("application_material", "application_id", release), generic, submitted.getStatusCode().value(),
                jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, release), count("sys_approval_request", "application_id", release));
        expect(submitted, 200); // Old baseline RED is at submit, after query/draft/material succeed.
        approve(release);
        assertThat(jdbc.queryForObject("SELECT status FROM key_population WHERE key_id=?", String.class, record)).isEqualTo("ACTIVE");
        expect(post("/api/key-populations/release-applications/" + release + "/execute", a, Map.of("version", 2)), 200);
        assertThat(jdbc.queryForObject("SELECT status FROM key_population WHERE key_id=?", String.class, record)).isEqualTo("RELEASED");
        assertThat(count("key_population_history", "record_id", record)).isEqualTo(2);
        expect(post("/api/key-populations/register-applications", token("admin"), registerBody()), 409);
        assertVisible(record, a, true);
        assertVisible(record, b, false);
    }

    @Test void inactiveResidenceDoesNotGrantAnyCurrentScope() throws Exception {
        long record = register(bAfterMove());
        // A real schema has UNIQUE(person_id). Keep it: one isolated inactive historical row.
        jdbc.update("UPDATE residence SET region_code='110101',status='INACTIVE' WHERE person_id=?", id);
        Map<String, Integer> observed = new LinkedHashMap<>();
        observed.put("list-total", data(get("/api/key-populations?personId=" + id, a)).path("totalElements").asInt());
        observed.put("person-total", data(get("/api/persons/" + id + "/key-populations", a)).path("totalElements").asInt());
        observed.put("detail", get("/api/key-populations/" + record, a).getStatusCode().value());
        observed.put("history", get("/api/key-populations/" + record + "/history", a).getStatusCode().value());
        var release = post("/api/key-populations/" + record + "/release-applications", a, releaseBody());
        observed.put("release-create", release.getStatusCode().value());
        observed.put("register-create", post("/api/key-populations/register-applications", a, registerBody()).getStatusCode().value());
        // Isolated pre-existing draft: submit must recheck current personnel scope on both routes.
        long app = syntheticDraft("REGISTER", null, id);
        materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        observed.put("generic-submit", post("/api/applications/" + app + "/submit", a, null).getStatusCode().value());
        if (jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?", String.class, app).equals("UNDER_REVIEW")) {
            // Separate draft for the second old-code path; no resetting statuses to fake a pass.
            app = syntheticDraft("REGISTER", null, id);
            materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        }
        observed.put("specialized-submit", post("/api/key-populations/register-applications/" + app + "/submit", a, null).getStatusCode().value());
        JsonNode all = data(get("/api/key-populations?personId=" + id, token("admin")));
        observed.put("explicit-history-region-total", data(get("/api/key-populations?personId=" + id + "&regionCode=110101", token("admin"))).path("totalElements").asInt());
        System.out.println("ISSUE45_OLD_OR_NEW_MATRIX " + observed + " projection=" + all.path("content").path(0).path("regionCode"));
        assertThat(observed).containsEntry("list-total", 0).containsEntry("person-total", 0)
                .containsEntry("detail", 404).containsEntry("history", 404).containsEntry("release-create", 404)
                .containsEntry("register-create", 404).containsEntry("generic-submit", 403).containsEntry("specialized-submit", 403)
                .containsEntry("explicit-history-region-total", 0);
        assertThat(all.path("content").path(0).path("regionCode").isNull()).isTrue();
    }

    @Test void movedPersonHasOnlyCurrentRegionAndUnrelatedApplicationsCannotManage() throws Exception {
        long record = register(a);
        jdbc.update("UPDATE residence SET region_code='110105' WHERE person_id=?", id);
        assertVisible(record, a, false);
        assertVisible(record, b, true);
        denyUnchanged("/api/key-populations/" + record + "/release-applications", a, releaseBody(), 404);
        long release = release(record, b);
        materials(release, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        expect(post("/api/applications/" + release + "/submit", b, null), 200);
        // Revoking current scope after submit must also block approval and execution.
        jdbc.update("UPDATE sys_department SET region_code='110101' WHERE department_id=?", id + 1);
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, release);
        denyUnchanged("/api/approvals/" + approval + "/approve", approver, Map.of("comment", "Synthetic", "version", 0), 403);
        jdbc.update("UPDATE sys_department SET region_code='110105' WHERE department_id=?", id + 1);
        approve(release);
        jdbc.update("UPDATE residence SET region_code='110101' WHERE person_id=?", id);
        denyUnchanged("/api/key-populations/release-applications/" + release + "/execute", b, Map.of("version", 2), 403);
    }

    @Test void migrationThenCancellationDoesNotResurrectOriginalRegion() throws Exception {
        long record = register(a);
        jdbc.update("UPDATE residence SET region_code='110105' WHERE person_id=?", id);
        cancel(b, "SETTLED_ABROAD");
        assertVisible(record, a, false);
        assertVisible(record, b, false); // No proven matching B source; do not invent a transfer of responsibility.
        denyUnchanged("/api/key-populations/" + record + "/release-applications", a, releaseBody(), 404);
        assertVisible(record, token("admin"), true);
    }

    @Test void allApplicantRegionDoesNotBecomeHistoricalJurisdiction() throws Exception {
        jdbc.update("UPDATE residence SET region_code='110105' WHERE person_id=?", id);
        jdbc.update("UPDATE sys_role SET data_scope='ALL' WHERE role_id=?", id);
        long record = register(a); // Applicant department is A, actual residence is B.
        jdbc.update("UPDATE sys_role SET data_scope='REGION' WHERE role_id=?", id);
        cancel(b, "SETTLED_ABROAD");
        assertVisible(record, a, false);
        denyUnchanged("/api/key-populations/" + record + "/release-applications", a, releaseBody(), 404);
    }

    @Test void deathCancellationStillAllowsProvenRelease() throws Exception {
        long record = register(a);
        cancel(a, "DEATH");
        long app = release(record, a);
        materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        expect(post("/api/applications/" + app + "/submit", a, null), 200);
        approve(app);
        expect(post("/api/key-populations/release-applications/" + app + "/execute", a, Map.of("version", 2)), 200);
        assertThat(jdbc.queryForObject("SELECT status FROM key_population WHERE key_id=?", String.class, record)).isEqualTo("RELEASED");
    }

    @Test void parentRegionUsesVerifiedDescendantCollectionAfterCancellation() throws Exception {
        jdbc.update("UPDATE sys_department SET region_code='110000' WHERE department_id=?", id);
        long record = register(a); // Source region is parent, actual cancellation residence is child 110101.
        cancel(a, "SETTLED_ABROAD");
        long app = release(record, a);
        materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        expect(post("/api/applications/" + app + "/submit", a, null), 200);
        approve(app);
        expect(post("/api/key-populations/release-applications/" + app + "/execute", a, Map.of("version", 2)), 200);
    }

    @ParameterizedTest @ValueSource(strings = {"SELF", "DEPARTMENT"})
    void unrelatedBusinessCanRemainVisibleButCannotGrantReleaseManagement(String scope) throws Exception {
        long record = register(bAfterMove());
        syntheticDraft("REGISTER", null, id); // unrelated draft owned by A is NOT record's source.
        jdbc.update("UPDATE sys_role SET data_scope=? WHERE role_id=?", scope, id);
        assertVisible(record, a, true); // Existing SELF read contract is intentionally separate.
        denyUnchanged("/api/key-populations/" + record + "/release-applications", a, releaseBody(), 404);
        long app = syntheticDraft("RELEASE", record, id);
        materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        denyUnchanged("/api/applications/" + app + "/submit", a, null, 403);
        denyUnchanged("/api/key-populations/release-applications/" + app + "/submit", a, null, 403);
    }

    @ParameterizedTest @ValueSource(strings = {"CANCELLED", "DECEASED"})
    void registerDraftCannotSubmitAfterPersonCancellation(String status) throws Exception {
        long app = create("/api/key-populations/register-applications", a, registerBody(), true);
        materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        jdbc.update("UPDATE sys_role SET data_scope='ALL' WHERE role_id=?", id);
        jdbc.update("UPDATE person SET current_status_code=? WHERE person_id=?", status, id);
        denyUnchanged("/api/applications/" + app + "/submit", a, null, 409);
        denyUnchanged("/api/key-populations/register-applications/" + app + "/submit", a, null, 409);
    }

    @Test void actualMapperSqlAndMysqlPlans() throws Exception {
        long record = register(a);
        try (var connection = jdbc.getDataSource().getConnection()) {
            if (!connection.getMetaData().getDatabaseProductName().equals("MySQL")) return;
        }
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("personId", id); parameters.put("id", record);
        parameters.put("scope", new com.wjx871.population.security.DataScopeCriteria(com.wjx871.population.security.DataScope.REGION, id, id, "110101", List.of("110101")));
        parameters.put("limit", 1); parameters.put("offset", 0);
        for (String scenario : List.of("ACTIVE", "CANCELLED")) {
          if (scenario.equals("CANCELLED")) cancel(a, "SETTLED_ABROAD");
          for (String statement : List.of("search", "count", "findScopedRecord", "lockScopedRecord", "findManagedRecord", "lockManagedRecord", "personScopedStatus", "lockPersonScopedStatus")) {
            if (statement.contains("Person") || statement.equals("personScopedStatus")) parameters.put("id", id);
            else parameters.put("id", record);
            BoundSql sql = sessions.getConfiguration().getMappedStatement("com.wjx871.population.keypopulation.KeyPopulationMapper." + statement).getBoundSql(parameters);
            List<Object> values = new ArrayList<>();
            for (var mapping : sql.getParameterMappings()) {
                String property = mapping.getProperty();
                values.add(sql.hasAdditionalParameter(property) ? sql.getAdditionalParameter(property) : sessions.getConfiguration().newMetaObject(parameters).getValue(property));
            }
            System.out.printf("ISSUE4145_MYSQL_SQL scenario=%s statement=%s sql=%s values=%s plan=%s%n", scenario, statement, sql.getSql().replaceAll("\\s+", " "), values,
                    jdbc.queryForList("EXPLAIN " + sql.getSql(), values.toArray()));
          }
        }
    }

    @ParameterizedTest @ValueSource(strings = {"SELF", "DEPARTMENT", "ALL"})
    void nonRegionScopesHavePositiveAndNegativeManagementExamples(String scope) throws Exception {
        long record = register(a);
        jdbc.update("UPDATE sys_role SET data_scope=? WHERE role_id IN (?,?)", scope, id, id + 1);
        assertVisible(record, a, true);
        if (!scope.equals("ALL")) {
            assertVisible(record, b, false);
            denyUnchanged("/api/key-populations/" + record + "/release-applications", b, releaseBody(), 404);
        } else {
            assertVisible(record, b, true);
            denyUnchanged("/api/key-populations/999999999/release-applications", b, releaseBody(), 404);
        }
        long app = release(record, a);
        materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        expect(post("/api/applications/" + app + "/submit", a, null), 200);
    }

    @Test void wrongPersonRecordAndOperationAreRejectedWithoutEffects() throws Exception {
        long record = register(a);
        long release = release(record, a);
        materials(release, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        long other = id + 2;
        jdbc.update("INSERT INTO person(person_id,name,gender,id_card,status,current_status_code) VALUES(?,'Synthetic other','M',?,'正常','REGISTERED')", other, String.format("1101051980%08d", other));
        jdbc.update("UPDATE key_population_application SET person_id=? WHERE application_id=?", other, release);
        denyUnchanged("/api/applications/" + release + "/submit", a, null, 403);
        jdbc.update("UPDATE key_population_application SET person_id=?,operation_type='REGISTER' WHERE application_id=?", id, release);
        denyUnchanged("/api/applications/" + release + "/submit", a, null, 403);
        jdbc.update("UPDATE key_population_application SET operation_type='RELEASE',record_id=NULL WHERE application_id=?", release);
        denyUnchanged("/api/applications/" + release + "/submit", a, null, 403);
    }

    @Test void missingRegionFailsClosedAndQueriesRemainMasked() throws Exception {
        long record = register(a);
        JsonNode row = data(get("/api/key-populations/" + record, a));
        assertThat(row.path("idCard").asText()).contains("**********");
        assertThat(row.path("phone").asText()).isEqualTo("138****5678");
        jdbc.update("UPDATE sys_department SET region_code='unknown' WHERE department_id=?", id);
        expect(get("/api/key-populations/" + record, a), 403);
        expect(get("/api/key-populations?personId=" + id, a), 403);
        denyUnchanged("/api/key-populations/" + record + "/release-applications", a, releaseBody(), 403);
    }

    @ParameterizedTest @ValueSource(strings = {"missing", "unrelated", "wrong-person", "wrong-type", "incomplete", "missing-history"})
    void corruptSourceCannotGrantCancelledRegionManagement(String corruption) throws Exception {
        long record = register(a);
        cancel(a, "SETTLED_ABROAD"); // Establish a valid archived responsibility chain before corrupting one link.
        long source = jdbc.queryForObject("SELECT source_application_id FROM key_population WHERE key_id=?", Long.class, record);
        switch (corruption) {
            case "missing" -> jdbc.update("UPDATE key_population SET source_application_id=NULL WHERE key_id=?", record);
            case "unrelated" -> jdbc.update("UPDATE key_population SET source_application_id=? WHERE key_id=?", syntheticDraft("REGISTER", null, id), record);
            case "wrong-person" -> {
                long other = id + 2;
                jdbc.update("INSERT INTO person(person_id,name,gender,id_card,status,current_status_code) VALUES(?,'Synthetic other','M',?,'正常','REGISTERED')", other, String.format("1101051980%08d", other));
                jdbc.update("UPDATE business_application SET target_person_id=? WHERE application_id=?", other, source);
            }
            case "wrong-type" -> jdbc.update("UPDATE business_application SET business_type='GENERAL_SERVICE' WHERE application_id=?", source);
            case "incomplete" -> jdbc.update("UPDATE key_population_application SET business_status='APPROVED' WHERE application_id=?", source);
            case "missing-history" -> jdbc.update("UPDATE key_population_history SET event_type='CORRUPT' WHERE record_id=?", record);
        }
        assertVisible(record, a, false);
        denyUnchanged("/api/key-populations/" + record + "/release-applications", a, releaseBody(), 404);
    }

    String bAfterMove() {
        jdbc.update("UPDATE residence SET region_code='110105' WHERE person_id=?", id);
        return b;
    }
    void cancel(String who, String reason) throws Exception {
        long app = create("/api/cancellations/person/applications", who,
                Map.of("personId", id, "cancelReasonCode", reason, "eventDate", "2026-07-13", "title", "Synthetic cancellation", "reason", "Synthetic reason"), false);
        materials(app, "APPLICANT_IDENTITY_PROOF", "HOUSEHOLD_BOOK", reason.equals("DEATH") ? "DEATH_CERTIFICATE" : "SETTLEMENT_ABROAD_PROOF");
        expect(post("/api/applications/" + app + "/submit", who, null), 200);
        approve(app);
        expect(post("/api/cancellations/person/applications/" + app + "/execute", who, Map.of("version", 2)), 200);
    }
    long register(String who) throws Exception {
        long app = create("/api/key-populations/register-applications", who, registerBody(), true);
        materials(app, "KEY_POPULATION_BASIS", "SITUATION_DESCRIPTION");
        expect(post("/api/key-populations/register-applications/" + app + "/submit", who, null), 200);
        approve(app);
        expect(post("/api/key-populations/register-applications/" + app + "/execute", who, Map.of("version", 2)), 200);
        return jdbc.queryForObject("SELECT key_id FROM key_population WHERE source_application_id=?", Long.class, app);
    }
    long release(long record, String who) throws Exception { return create("/api/key-populations/" + record + "/release-applications", who, releaseBody(), true); }
    Map<String, Object> registerBody() { return Map.of("personId", id, "populationType", "OTHER", "attentionLevel", "MEDIUM", "registerReason", "Synthetic register", "registerDate", "2026-07-13", "title", "Synthetic register"); }
    Map<String, Object> releaseBody() { return Map.of("releaseReason", "Synthetic release", "releaseDate", "2026-07-14", "title", "Synthetic release"); }
    long create(String path, String who, Object body, boolean nested) throws Exception {
        ResponseEntity<String> r = post(path, who, body); expect(r, 201);
        JsonNode d = data(r); return (nested ? d.path("application") : d).path("applicationId").asLong();
    }
    void approve(long app) throws Exception {
        long approval = jdbc.queryForObject("SELECT approval_id FROM sys_approval_request WHERE application_id=?", Long.class, app);
        expect(post("/api/approvals/" + approval + "/approve", approver, Map.of("comment", "Synthetic approval", "version", 0)), 200);
    }
    void materials(long app, String... types) {
        for (String type : types) {
            String hash = UUID.randomUUID().toString().replace("-", "");
            long verifier = jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username='approver'", Long.class);
            jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,stored_filename,storage_path,content_type,file_size,file_sha256,required_flag,verify_status,verify_user_id,uploaded_by) VALUES(?,?,?,'synthetic.pdf',?,'synthetic-no-file','application/pdf',1,?,1,'VERIFIED',?,?)", app, type, type, hash + ".pdf", hash + hash, verifier, id);
        }
        System.out.printf("ISSUE4145_MATERIAL_FIXTURE app=%d types=%s DB-verified=true no-physical-upload=true%n", app, Arrays.toString(types));
    }
    long syntheticDraft(String op, Long record, long person) {
        String no = "I4145-" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,applicant_department_id,applicant_region_code,target_person_id,status,reason,version) VALUES(?,?,'Synthetic preexisting draft',?,?,'110101',?,'DRAFT','Synthetic reason',0)", no, "KEY_POPULATION_" + op, id, id, person);
        long app = jdbc.queryForObject("SELECT application_id FROM business_application WHERE application_no=?", Long.class, no);
        jdbc.update("INSERT INTO key_population_application(application_id,operation_type,record_id,person_id,population_type,attention_level,reason,event_date,business_status,version) VALUES(?,?,?,?,'OTHER','MEDIUM','Synthetic reason',DATE '2026-07-14','DRAFT',0)", app, op, record, person);
        return app;
    }
    void assertVisible(long record, String who, boolean visible) throws Exception {
        JsonNode list = data(get("/api/key-populations?personId=" + id + "&size=1", who));
        assertThat(list.path("totalElements").asInt()).isEqualTo(visible ? 1 : 0);
        assertThat(list.path("content").size()).isEqualTo(visible ? 1 : 0);
        expect(get("/api/key-populations/" + record, who), visible ? 200 : 404);
        expect(get("/api/key-populations/" + record + "/history", who), visible ? 200 : 404);
        assertThat(data(get("/api/persons/" + id + "/key-populations", who)).path("totalElements").asInt()).isEqualTo(visible ? 1 : 0);
        System.out.printf("ISSUE4145_QUERY_CONSISTENCY person=%d record=%d visible=%s list=count=detail=history=personList%n", id, record, visible);
    }
    void denyUnchanged(String path, String who, Object body, int status) throws Exception {
        var before = businessSnapshot();
        expect(post(path, who, body), status);
        assertThat(businessSnapshot()).as("denial must not alter business, approval, materials or successful logs").isEqualTo(before);
        System.out.printf("ISSUE4145_DENIED_NO_EFFECTS path=%s status=%d observedTables=%d%n", path, status, before.size());
    }
    Map<String, List<List<String>>> businessSnapshot() {
        Map<String, List<List<String>>> result = new LinkedHashMap<>();
        for (String table : List.of("business_application", "key_population_application", "key_population", "key_population_history", "application_material", "sys_approval_request", "sys_approval_log", "person", "residence", "household", "operation_log")) {
            result.put(table, jdbc.query("SELECT * FROM " + table + (table.equals("operation_log") ? " WHERE operation_result='SUCCESS'" : "") + " ORDER BY 1", (rs, row) -> {
                List<String> values = new ArrayList<>();
                for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) values.add(rs.getString(i));
                return values;
            }));
        }
        return result;
    }
    void snapshotPerson(String stage, long record) {
        System.out.printf("ISSUE41_DB stage=%s person=%s residence=%s key=%s archive=%s source=%s%n", stage,
                jdbc.queryForList("SELECT person_id,current_status_code,version FROM person WHERE person_id=?", id),
                jdbc.queryForList("SELECT residence_id,person_id,region_code,status,version FROM residence WHERE person_id=?", id),
                jdbc.queryForList("SELECT key_id,person_id,status,source_application_id FROM key_population WHERE key_id=?", record),
                jdbc.queryForList("SELECT original_registration_id,person_id,region_code_snapshot,archive_type,application_id FROM residence_archive WHERE person_id=?", id),
                jdbc.queryForList("SELECT business_type,target_person_id,applicant_region_code,status FROM business_application WHERE application_id=(SELECT source_application_id FROM key_population WHERE key_id=?)", record));
    }
    int count(String table, String column, long value) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE " + column + "=?", Integer.class, value); }
    String token(String username) throws Exception {
        if (tokens.containsKey(username)) return tokens.get(username);
        ResponseEntity<String> r = http.postForEntity("/api/auth/login", Map.of("username", username, "password", "123456"), String.class);
        expect(r, 200); String token = "Bearer " + data(r).path("token").asText(); tokens.put(username, token); return token;
    }
    ResponseEntity<String> get(String path, String who) { return request(HttpMethod.GET, path, who, null); }
    ResponseEntity<String> post(String path, String who, Object body) { return request(HttpMethod.POST, path, who, body); }
    ResponseEntity<String> request(HttpMethod method, String path, String who, Object body) {
        HttpHeaders headers = new HttpHeaders(); headers.set("Authorization", who); headers.setContentType(MediaType.APPLICATION_JSON);
        ResponseEntity<String> response = http.exchange(path, method, new HttpEntity<>(body, headers), String.class);
        System.out.printf("ISSUE4145_HTTP method=%s path=%s status=%d body=%s%n", method, path, response.getStatusCode().value(), response.getBody());
        return response;
    }
    JsonNode data(ResponseEntity<String> response) throws Exception { expect(response, 200, 201); return json.readTree(response.getBody()).path("data"); }
    void expect(ResponseEntity<String> response, int... statuses) { assertThat(response.getStatusCode().value()).withFailMessage("HTTP %s %s", response.getStatusCode(), response.getBody()).isIn(Arrays.stream(statuses).boxed().toArray(Integer[]::new)); }
}
