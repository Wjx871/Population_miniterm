package com.wjx871.population.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.security.DataScope;
import java.net.URI;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * #68/#71 的 H2 真实 HTTP/JWT 契约测试。
 *
 * <p>使用随机端口启动完整 Spring Boot 应用，经真实 JWT 登录后调用
 * {@code /api/query/persons} 与 {@code /api/queries/persons}，所有数据均为
 * 隔离 H2 数据库中的合成数据。MySQL 8.4 门禁 {@code Issue6871MySqlIT} 复用
 * 同一组测试方法，只替换数据源。</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.hikari.maximum-pool-size=8",
        "population.redis.enabled=false",
        "policy.ai.enabled=false"
})
class Issue6871ComprehensiveQueryIntegrationTest {

    protected static final String PREFIX = "Q6871";
    private static final String PASSWORD = "Issue6871-Test-Password";
    private static final String ADDRESS = "110101示例东区幸福路1号";

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper json;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    private long idCardSequence;

    @BeforeEach
    void resetFixture() {
        cleanupFixture();
        jdbc.update("UPDATE sys_user SET password_hash=? WHERE username='admin'",
                passwordEncoder.encode(PASSWORD));
    }

    // ---------------------------------------------------------------------
    // #68: current floating / permit projection and latest COMPLETED migration
    // ---------------------------------------------------------------------

    @Test
    void issue68Phase11SummaryIncludesCurrentFloatingPermitAndLatestCompletedMigration() throws Exception {
        long fullPerson = insertPerson("full");
        long household = insertHousehold("full", fullPerson, "980101", ADDRESS);
        insertMember(household, fullPerson);
        insertResidence(fullPerson, household, "980101", "ACTIVE", ADDRESS);

        long floatingApplication = insertApplication("FLOAT-FULL", 3L, 3L, "980101", fullPerson, "COMPLETED");
        long floatingId = insertFloating(fullPerson, "FULL", "980101", "ACTIVE", 1, floatingApplication,
                LocalDate.of(2025, 3, 1));
        long permitApplication = insertApplication("PERMIT-FULL", 3L, 3L, "980101", fullPerson, "COMPLETED");
        long permitId = insertPermit(fullPerson, floatingId, permitApplication, "980101", "ACTIVE", 1,
                "JZP-2026-0001", LocalDate.of(2027, 3, 1));

        // Two COMPLETED records share 2026-06-15: contract chooses OUT deterministically.
        long inApplication = insertApplication("MIG-IN-1", 3L, 3L, "980101", fullPerson, "COMPLETED");
        insertMigrationIn(fullPerson, household, inApplication, "980101", "COMPLETED", LocalDate.of(2026, 6, 15));
        long outApplication = insertApplication("MIG-OUT-1", 3L, 3L, "980101", fullPerson, "COMPLETED");
        insertMigrationOut(fullPerson, household, outApplication, "980101", "COMPLETED", LocalDate.of(2026, 6, 15));

        // Non-completed migration rows are dated later and must never win.
        for (String status : List.of("DRAFT", "REJECTED", "WITHDRAWN", "CANCELLED")) {
            long app = insertApplication("MIG-" + status, 3L, 3L, "980101", fullPerson, status);
            insertMigrationIn(fullPerson, household, app, "980101", status, LocalDate.of(2026, 12, 1));
        }

        // Historical rows must not replace the single current row or duplicate the result.
        long closedApp = insertApplication("FLOAT-HIST", 3L, 3L, "980101", fullPerson, "COMPLETED");
        insertFloating(fullPerson, "HIST", "980101", "CLOSED", null, closedApp, LocalDate.of(2024, 1, 1));
        long cancelledApp = insertApplication("PERMIT-HIST", 3L, 3L, "980101", fullPerson, "COMPLETED");
        insertPermit(fullPerson, floatingId, cancelledApp, "980101", "CANCELLED", null,
                "JZP-2024-9999", LocalDate.of(2024, 12, 31));

        long noAssocPerson = insertPerson("none");
        ensureUser("q6871_viewer", "Q6871_VIEWER", DataScope.ALL.name(), "980000", true);
        Map<String, Object> businessStateBefore = businessSnapshot(fullPerson);

        String token = login("q6871_viewer");
        JsonNode page = getData("/api/query/persons?name=" + PREFIX + "_full&page=0&size=10", token);
        System.out.println("EVIDENCE #68 HTTP=" + page);
        System.out.println("EVIDENCE #68 DB=" + jdbc.queryForList("""
                SELECT p.person_id, r.status residence_status, r.region_code residence_region,
                       f.floating_id, f.status floating_status, f.current_flag floating_current,
                       f.current_region_code floating_region, f.arrival_date,
                       rp.permit_id, rp.permit_no, rp.status permit_status, rp.current_flag permit_current,
                       rp.valid_until,
                       (SELECT MAX(in_date) FROM migration_in WHERE person_id=p.person_id AND business_status='COMPLETED') last_in,
                       (SELECT MAX(out_date) FROM migration_out WHERE person_id=p.person_id AND business_status='COMPLETED') last_out
                FROM person p
                LEFT JOIN residence r ON r.person_id=p.person_id AND r.status='ACTIVE'
                LEFT JOIN floating_population f ON f.person_id=p.person_id AND f.status='ACTIVE' AND f.current_flag=1
                LEFT JOIN residence_permit rp ON rp.person_id=p.person_id AND rp.status='ACTIVE' AND rp.current_flag=1
                WHERE p.person_id=?
                """, fullPerson));

        assertThat(page.path("totalElements").asLong()).isEqualTo(1);
        assertThat(page.path("content")).hasSize(1);
        JsonNode row = page.path("content").get(0);
        assertThat(row.path("personId").asLong()).isEqualTo(fullPerson);
        assertThat(row.path("floatingId").asLong()).isEqualTo(floatingId);
        assertThat(row.path("floatingStatus").asText()).isEqualTo("ACTIVE");
        assertThat(row.path("arrivalDate").asText()).isEqualTo("2025-03-01");
        assertThat(row.path("permitId").asLong()).isEqualTo(permitId);
        assertThat(row.path("maskedPermitNo").asText()).isEqualTo("JZP-****0001");
        assertThat(row.path("permitStatus").asText()).isEqualTo("ACTIVE");
        assertThat(row.path("permitValidUntil").asText()).isEqualTo("2027-03-01");
        assertThat(row.path("lastMigrationDirection").asText()).isEqualTo("OUT");
        assertThat(row.path("lastMigrationDate").asText()).isEqualTo("2026-06-15");
        String idCard = jdbc.queryForObject("SELECT id_card FROM person WHERE person_id=?", String.class, fullPerson);
        assertThat(row.path("maskedIdentityNo").asText())
                .isEqualTo(idCard.substring(0, 4) + "**********" + idCard.substring(14));
        assertThat(row.path("maskedPhone").asText()).isEqualTo("138****8000");
        assertThat(row.path("currentAddress").asText()).isEqualTo("110101示例东区（详细地址已隐藏）");

        JsonNode none = getData("/api/query/persons?name=" + PREFIX + "_none&page=0&size=10", token);
        assertThat(none.path("content")).hasSize(1);
        JsonNode noneRow = none.path("content").get(0);
        assertThat(noneRow.path("personId").asLong()).isEqualTo(noAssocPerson);
        for (String field : List.of("floatingId", "floatingStatus", "arrivalDate", "permitId",
                "maskedPermitNo", "permitStatus", "permitValidUntil", "lastMigrationDirection",
                "lastMigrationDate")) {
            assertThat(noneRow.path(field).isNull() || noneRow.path(field).isMissingNode())
                    .as("null association field %s", field).isTrue();
        }
        assertThat(businessSnapshot(fullPerson)).as("query must not mutate business rows")
                .isEqualTo(businessStateBefore);
    }

    // ---------------------------------------------------------------------
    // #71: REGION scope = ACTIVE residence OR current ACTIVE floating
    // ---------------------------------------------------------------------

    @Test
    void issue71Phase11RegionScopeUsesOnlyActiveResidenceOrCurrentFloating() throws Exception {
        seedRegionScopeFixture();
        String tokenA = login("q6871_region_a");
        String tokenB = login("q6871_region_b");

        JsonNode pageA = getData(phase11Path(""), tokenA);
        System.out.println("EVIDENCE #71 PHASE11 REGION-A HTTP=" + pageA);
        System.out.println("EVIDENCE #71 DB=" + regionSnapshot());
        Set<String> namesA = names(pageA);
        assertThat(namesA)
                .contains("Q6871_resA", "Q6871_floatA", "Q6871_crossAB")
                .doesNotContain("Q6871_closedA", "Q6871_nullFlagA", "Q6871_historyAppA",
                        "Q6871_inactiveResA", "Q6871_permitOnlyA", "Q6871_resB");

        Set<String> namesB = names(getData(phase11Path(""), tokenB));
        assertThat(namesB)
                .contains("Q6871_crossAB", "Q6871_resB")
                .doesNotContain("Q6871_resA", "Q6871_floatA", "Q6871_closedA", "Q6871_nullFlagA",
                        "Q6871_historyAppA", "Q6871_inactiveResA", "Q6871_permitOnlyA");

        // regionCode is an explicit business filter over any current valid jurisdiction,
        // not the authorization rule itself.
        Set<String> regionFiltered = names(getData(phase11Path("&regionCode=980101"), tokenA));
        assertThat(regionFiltered)
                .contains("Q6871_resA", "Q6871_floatA", "Q6871_crossAB")
                .doesNotContain("Q6871_resB");

        // Scope may be granted by floating registration, but residenceStatus=ACTIVE
        // still means "must have an ACTIVE residence"; floating-only people stay out.
        Set<String> activeResidence = names(getData(phase11Path("&residenceStatus=ACTIVE"), tokenA));
        assertThat(activeResidence)
                .contains("Q6871_resA", "Q6871_crossAB")
                .doesNotContain("Q6871_floatA");

        Set<String> activeFloating = names(getData(phase11Path("&floatingStatus=ACTIVE"), tokenA));
        assertThat(activeFloating)
                .contains("Q6871_floatA", "Q6871_crossAB")
                .doesNotContain("Q6871_closedA", "Q6871_nullFlagA");
    }

    @Test
    void issue71SecondInterfaceRegionScopeHasNoHistoricalFallback() throws Exception {
        seedRegionScopeFixture();
        String tokenA = login("q6871_region_a");

        JsonNode second = getData("/api/queries/persons?keyword=Q6871&page=0&size=100", tokenA);
        System.out.println("EVIDENCE #71 SECOND HTTP=" + second);
        Set<String> secondNames = names(second);
        assertThat(secondNames)
                .contains("Q6871_resA", "Q6871_floatA", "Q6871_crossAB")
                .doesNotContain("Q6871_closedA", "Q6871_nullFlagA", "Q6871_historyAppA",
                        "Q6871_inactiveResA", "Q6871_permitOnlyA", "Q6871_resB");

        Set<String> secondRegionFiltered = names(getData(
                "/api/queries/persons?keyword=Q6871&regionCode=980101&page=0&size=100", tokenA));
        assertThat(secondRegionFiltered)
                .contains("Q6871_resA", "Q6871_floatA", "Q6871_crossAB")
                .doesNotContain("Q6871_resB");

        long historyAppPerson = jdbc.queryForObject(
                "SELECT person_id FROM person WHERE name='Q6871_historyAppA'", Long.class);
        ResponseEntity<String> profile = exchange("/api/queries/persons/" + historyAppPerson, tokenA);
        assertThat(profile.getStatusCode().value()).as("historical application must not open profile")
                .isEqualTo(403);
        System.out.println("EVIDENCE #71 SECOND PROFILE HTTP=" + profile.getStatusCode().value()
                + " body=" + profile.getBody());
    }

    // ---------------------------------------------------------------------
    // count / pagination / one row per person
    // ---------------------------------------------------------------------

    @Test
    void phase11CountMatchesListAcrossPagesWithoutDuplicates() throws Exception {
        seedRegionScopeFixture();
        String token = login("admin");
        long total = -1;
        List<Long> seen = new ArrayList<>();
        for (int pageNo = 0; pageNo < 10; pageNo++) {
            JsonNode page = getData("/api/query/persons?name=Q6871&page=" + pageNo
                    + "&size=2&sort=personId,ASC", token);
            if (total < 0) total = page.path("totalElements").asLong();
            assertThat(page.path("totalElements").asLong()).isEqualTo(total);
            assertThat(page.path("number").asInt()).isEqualTo(pageNo);
            for (JsonNode row : page.path("content")) {
                seen.add(row.path("personId").asLong());
            }
            if (page.path("last").asBoolean()) break;
        }
        assertThat(total).isEqualTo(9);
        assertThat(seen).hasSize(9).doesNotHaveDuplicates();
        assertThat(seen).isSorted();
        System.out.println("COUNT/PAGE total=" + total + " seen=" + seen);
    }

    // ---------------------------------------------------------------------
    // permission and fail-closed
    // ---------------------------------------------------------------------

    @Test
    void phase11SelfAndDepartmentScopesKeepExistingApplicationSemantics() throws Exception {
        ensureUser("q6871_self", "Q6871_SELF", DataScope.SELF.name(), "980000", true);
        ensureUser("q6871_dept", "Q6871_DEPT", DataScope.DEPARTMENT.name(), "980000", true);
        long selfUserId = jdbc.queryForObject(
                "SELECT user_id FROM sys_user WHERE username='q6871_self'", Long.class);
        long departmentId = jdbc.queryForObject("""
                SELECT d.department_id FROM sys_department d
                JOIN sys_user u ON u.department_id=d.department_id
                WHERE u.username='q6871_dept'
                """, Long.class);

        long selfPerson = insertPerson("selfPerson");
        insertApplication("SELF-APP", selfUserId, 3L, "980000", selfPerson, "COMPLETED");
        long departmentPerson = insertPerson("deptPerson");
        insertApplication("DEPT-APP", 3L, departmentId, "980000", departmentPerson, "COMPLETED");
        long otherPerson = insertPerson("otherPerson");

        Set<String> selfNames = names(getData(phase11Path(""), login("q6871_self")));
        assertThat(selfNames).contains("Q6871_selfPerson")
                .doesNotContain("Q6871_deptPerson", "Q6871_otherPerson");
        Set<String> departmentNames = names(getData(phase11Path(""), login("q6871_dept")));
        assertThat(departmentNames).contains("Q6871_deptPerson")
                .doesNotContain("Q6871_selfPerson", "Q6871_otherPerson");
        Set<String> allNames = names(getData(phase11Path(""), login("admin")));
        assertThat(allNames).contains("Q6871_selfPerson", "Q6871_deptPerson", "Q6871_otherPerson");
    }

    @Test
    void permissionAndEmptyRegionScopeFailClosedOnBothInterfaces() throws Exception {
        ensureUser("q6871_noview", "Q6871_NO_VIEW", DataScope.ALL.name(), "980000", false);
        ensureUser("q6871_empty_region", "Q6871_EMPTY_REGION", DataScope.REGION.name(), "989999", true);

        String noView = login("q6871_noview");
        assertThat(exchange("/api/query/persons", noView).getStatusCode().value()).isEqualTo(403);
        assertThat(exchange("/api/queries/persons", noView).getStatusCode().value()).isEqualTo(403);

        String emptyRegion = login("q6871_empty_region");
        ResponseEntity<String> denied = exchange("/api/query/persons", emptyRegion);
        assertThat(denied.getStatusCode().value()).isEqualTo(403);
        System.out.println("FAIL-CLOSED empty regionCodes HTTP=" + denied.getStatusCode().value()
                + " body=" + denied.getBody());
    }

    // ---------------------------------------------------------------------
    // fixture helpers
    // ---------------------------------------------------------------------

    protected void seedRegionScopeFixture() {
        ensureRegionTree("980000", "980100", "980101");
        ensureRegionTree("981000", "981100", "981101");
        ensureUser("q6871_region_a", "Q6871_REGION_A", DataScope.REGION.name(), "980000", true);
        ensureUser("q6871_region_b", "Q6871_REGION_B", DataScope.REGION.name(), "981000", true);

        long resA = insertPerson("resA");
        long householdA = insertHousehold("resA", resA, "980101", ADDRESS);
        insertMember(householdA, resA);
        insertResidence(resA, householdA, "980101", "ACTIVE", ADDRESS);

        long floatA = insertPerson("floatA");
        long floatAppA = insertApplication("FLOAT-A", 3L, 3L, "980101", floatA, "COMPLETED");
        insertFloating(floatA, "A", "980101", "ACTIVE", 1, floatAppA, LocalDate.of(2025, 4, 1));

        long closedA = insertPerson("closedA");
        long closedAppA = insertApplication("FLOAT-CLOSED-A", 3L, 3L, "980101", closedA, "COMPLETED");
        insertFloating(closedA, "CLOSED-A", "980101", "CLOSED", null, closedAppA, LocalDate.of(2024, 1, 1));

        long nullFlagA = insertPerson("nullFlagA");
        long nullAppA = insertApplication("FLOAT-NULL-A", 3L, 3L, "980101", nullFlagA, "COMPLETED");
        insertFloating(nullFlagA, "NULL-A", "980101", "ACTIVE", null, nullAppA, LocalDate.of(2024, 2, 1));

        long historyAppA = insertPerson("historyAppA");
        insertApplication("HISTORY-APP-A", 3L, 3L, "980101", historyAppA, "COMPLETED");

        long crossAB = insertPerson("crossAB");
        long householdCross = insertHousehold("crossAB", crossAB, "980101", ADDRESS);
        insertMember(householdCross, crossAB);
        insertResidence(crossAB, householdCross, "980101", "ACTIVE", ADDRESS);
        long crossApp = insertApplication("FLOAT-CROSS-B", 3L, 3L, "981101", crossAB, "COMPLETED");
        insertFloating(crossAB, "CROSS-B", "981101", "ACTIVE", 1, crossApp, LocalDate.of(2025, 5, 1));

        long inactiveResA = insertPerson("inactiveResA");
        long householdInactive = insertHousehold("inactiveResA", inactiveResA, "980101", ADDRESS);
        insertMember(householdInactive, inactiveResA);
        insertResidence(inactiveResA, householdInactive, "980101", "INACTIVE", ADDRESS);

        long permitOnlyA = insertPerson("permitOnlyA");
        long closedFloatApp = insertApplication("FLOAT-PERMIT-ONLY", 3L, 3L, "980101", permitOnlyA, "COMPLETED");
        long closedFloat = insertFloating(permitOnlyA, "PERMIT-ONLY", "981101", "CLOSED", null,
                closedFloatApp, LocalDate.of(2024, 3, 1));
        long permitOnlyApp = insertApplication("PERMIT-ONLY-A", 3L, 3L, "980101", permitOnlyA, "COMPLETED");
        insertPermit(permitOnlyA, closedFloat, permitOnlyApp, "980101", "ACTIVE", 1,
                "JZP-2026-0002", LocalDate.of(2027, 1, 1));

        long resB = insertPerson("resB");
        long householdB = insertHousehold("resB", resB, "981101", "120101示例西区幸福路2号");
        insertMember(householdB, resB);
        insertResidence(resB, householdB, "981101", "ACTIVE", "120101示例西区幸福路2号");
    }

    private String phase11Path(String extra) {
        return "/api/query/persons?name=Q6871&page=0&size=100&sort=personId,ASC" + extra;
    }

    protected String login(String username) throws Exception {
        ResponseEntity<String> response = rest.postForEntity("/api/auth/login",
                Map.of("username", username, "password", PASSWORD), String.class);
        assertThat(response.getStatusCode().value()).as("login %s: %s", username, response.getBody())
                .isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        String token = body.path("data").path("token").asText();
        assertThat(token).isNotBlank();
        return token;
    }

    private JsonNode getData(String path, String token) throws Exception {
        ResponseEntity<String> response = exchange(path, token);
        assertThat(response.getStatusCode().value()).as("GET %s body=%s", path, response.getBody())
                .isEqualTo(200);
        JsonNode body = json.readTree(response.getBody());
        assertThat(body.path("code").asInt()).isEqualTo(200);
        return body.path("data");
    }

    protected ResponseEntity<String> exchange(String path, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        return rest.exchange(URI.create(rest.getRootUri() + path), HttpMethod.GET,
                new HttpEntity<>(headers), String.class);
    }

    private Set<String> names(JsonNode page) {
        Set<String> names = new LinkedHashSet<>();
        for (JsonNode row : page.path("content")) {
            names.add(row.path("name").asText());
        }
        return names;
    }

    private Map<String, Object> businessSnapshot(long personId) {
        return jdbc.queryForMap("""
                SELECT
                  (SELECT COUNT(*) FROM floating_population WHERE person_id=?) AS floating_rows,
                  (SELECT COUNT(*) FROM residence_permit WHERE person_id=?) AS permit_rows,
                  (SELECT COUNT(*) FROM migration_in WHERE person_id=?) AS migration_in_rows,
                  (SELECT COUNT(*) FROM migration_out WHERE person_id=?) AS migration_out_rows,
                  (SELECT status FROM residence WHERE person_id=?) AS residence_status
                """, personId, personId, personId, personId, personId);
    }

    private String regionSnapshot() {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT p.name, r.status residence_status, r.region_code residence_region,
                       f.status floating_status, f.current_flag floating_current,
                       f.current_region_code floating_region,
                       rp.status permit_status, rp.current_flag permit_current, rp.issue_region_code permit_region
                FROM person p
                LEFT JOIN residence r ON r.person_id=p.person_id AND r.status='ACTIVE'
                LEFT JOIN floating_population f ON f.person_id=p.person_id AND f.status='ACTIVE' AND f.current_flag=1
                LEFT JOIN residence_permit rp ON rp.person_id=p.person_id AND rp.status='ACTIVE' AND rp.current_flag=1
                WHERE p.name LIKE 'Q6871%'
                ORDER BY p.person_id
                """);
        return rows.toString();
    }

    protected long insertPerson(String label) {
        String name = PREFIX + "_" + label;
        String idCard = String.format("1101011990%08d", ++idCardSequence);
        jdbc.update("""
                INSERT INTO person (name, gender, id_card, birth_date, ethnicity, phone,
                                    current_address, status, current_status_code)
                VALUES (?, '男', ?, '1990-01-01', '汉族', '13800138000', ?, '正常', 'REGISTERED')
                """, name, idCard, ADDRESS);
        return jdbc.queryForObject("SELECT person_id FROM person WHERE name=?", Long.class, name);
    }

    protected long insertHousehold(String label, long headPersonId, String regionCode, String address) {
        String no = PREFIX + "_HH_" + label;
        jdbc.update("""
                INSERT INTO household (household_no, head_person_id, address, establish_date, status,
                                       region_code, household_type, version)
                VALUES (?, ?, ?, '2020-01-01', 'ACTIVE', ?, 'FAMILY', 0)
                """, no, headPersonId, address, regionCode);
        return jdbc.queryForObject("SELECT household_id FROM household WHERE household_no=?", Long.class, no);
    }

    protected void insertMember(long householdId, long personId) {
        jdbc.update("""
                INSERT INTO household_member (household_id, person_id, relationship, join_date, status, version)
                VALUES (?, ?, 'HEAD', '2020-01-01', 'ACTIVE', 0)
                """, householdId, personId);
    }

    protected void insertResidence(long personId, long householdId, String regionCode, String status,
            String address) {
        jdbc.update("""
                INSERT INTO residence (person_id, household_id, registered_address, region_code,
                                       register_type_code, register_date, start_date, status, created_by, version)
                VALUES (?, ?, ?, ?, 'NORMAL', '2020-01-01', '2020-01-01', ?, 3, 0)
                """, personId, householdId, address, regionCode, status);
    }

    protected long insertApplication(String label, long userId, long departmentId, String regionCode,
            long personId, String status) {
        String no = PREFIX + "_APP_" + label;
        jdbc.update("""
                INSERT INTO business_application (application_no, business_type, title, applicant_user_id,
                        applicant_department_id, applicant_region_code, target_person_id, status, reason, version)
                VALUES (?, 'QUERY_SEED', 'seed', ?, ?, ?, ?, ?, 'seed', 0)
                """, no, userId, departmentId, regionCode, personId, status);
        return jdbc.queryForObject(
                "SELECT application_id FROM business_application WHERE application_no=?", Long.class, no);
    }

    protected long insertFloating(long personId, String label, String currentRegion, String status,
            Integer currentFlag, long applicationId, LocalDate arrivalDate) {
        String registrationNo = PREFIX + "_FP_" + label;
        jdbc.update("""
                INSERT INTO floating_population (registration_no, source_application_id, person_id,
                        source_region_code, source_address, current_region_code, current_address,
                        residence_reason_code, residence_proof_type, arrival_date, registration_date,
                        eligible_from_date, department_id, operator_id, status, current_flag, version)
                VALUES (?, ?, ?, '120000', 'source', ?, ?, 'WORK', 'LEASE', ?, '2025-01-02',
                        '2025-01-02', 2, 2, ?, ?, 0)
                """, registrationNo, applicationId, personId, currentRegion, ADDRESS, arrivalDate, status,
                currentFlag);
        return jdbc.queryForObject(
                "SELECT floating_id FROM floating_population WHERE registration_no=?", Long.class, registrationNo);
    }

    protected long insertPermit(long personId, long floatingId, long applicationId, String issueRegion,
            String status, Integer currentFlag, String permitNo, LocalDate validUntil) {
        jdbc.update("""
                INSERT INTO residence_permit (permit_no, person_id, floating_id, source_application_id,
                        issue_region_code, issuing_department_id, issuing_authority, issue_date, valid_from,
                        valid_until, status, current_flag, version)
                VALUES (?, ?, ?, ?, ?, 2, '公安局', '2025-06-01', '2025-06-01', ?, ?, ?, 0)
                """, permitNo, personId, floatingId, applicationId, issueRegion, validUntil, status, currentFlag);
        return jdbc.queryForObject(
                "SELECT permit_id FROM residence_permit WHERE permit_no=?", Long.class, permitNo);
    }

    protected void insertMigrationIn(long personId, long householdId, long applicationId, String regionCode,
            String status, LocalDate date) {
        jdbc.update("""
                INSERT INTO migration_in (application_id, person_id, migration_type, from_region_code,
                        from_address, to_region_code, to_household_id, to_address_snapshot, in_date, reason,
                        business_status, version)
                VALUES (?, ?, 'TRANSFER', '120000', 'from', ?, ?, 'to', ?, 'reason', ?, 0)
                """, applicationId, personId, regionCode, householdId, date, status);
    }

    protected void insertMigrationOut(long personId, long householdId, long applicationId, String regionCode,
            String status, LocalDate date) {
        jdbc.update("""
                INSERT INTO migration_out (application_id, person_id, migration_type, from_region_code,
                        from_household_id, from_address_snapshot, to_region_code, to_address, out_date, reason,
                        business_status, version)
                VALUES (?, ?, 'TRANSFER', ?, ?, 'from', ?, 'to', ?, 'reason', ?, 0)
                """, applicationId, personId, regionCode, householdId, regionCode, date, status);
    }

    private void ensureRegionTree(String root, String city, String district) {
        long rootId = ensureRegion(root, "Q6871 Region Root", null, 1);
        long cityId = ensureRegion(city, "Q6871 Region City", rootId, 2);
        ensureRegion(district, "Q6871 Region District", cityId, 3);
    }

    private long ensureRegion(String code, String name, Long parentId, int level) {
        List<Long> ids = jdbc.queryForList(
                "SELECT region_id FROM admin_region WHERE region_code=?", Long.class, code);
        if (!ids.isEmpty()) return ids.get(0);
        jdbc.update("""
                INSERT INTO admin_region (region_code, region_name, parent_id, region_level, full_name,
                                          sort_no, status, version)
                VALUES (?, ?, ?, ?, ?, 91, 'ENABLED', 0)
                """, code, name, parentId, level, name);
        return jdbc.queryForObject("SELECT region_id FROM admin_region WHERE region_code=?", Long.class, code);
    }

    private void ensureUser(String username, String roleCode, String dataScope, String regionCode,
            boolean populationView) {
        jdbc.update("DELETE FROM operation_log WHERE user_id IN (SELECT user_id FROM sys_user WHERE username=?)",
                username);
        jdbc.update("DELETE FROM sys_user WHERE username=?", username);
        jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN (SELECT role_id FROM sys_role WHERE role_code=?)",
                roleCode);
        jdbc.update("DELETE FROM sys_role WHERE role_code=?", roleCode);
        jdbc.update("""
                INSERT INTO sys_role (role_code, role_name, role_level, data_scope, status)
                VALUES (?, ?, 'L1', ?, 'ENABLED')
                """, roleCode, roleCode, dataScope);
        Long roleId = jdbc.queryForObject("SELECT role_id FROM sys_role WHERE role_code=?", Long.class, roleCode);
        if (populationView) {
            Long permissionId = jdbc.queryForObject(
                    "SELECT permission_id FROM sys_permission WHERE permission_code='population:view' AND status='ENABLED'",
                    Long.class);
            jdbc.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                    roleId, permissionId);
        }
        String departmentCode = "Q6871_DEPT_" + username.toUpperCase();
        jdbc.update("DELETE FROM sys_department WHERE department_code=?", departmentCode);
        jdbc.update("""
                INSERT INTO sys_department (department_code, department_name, region_code, status, sort_no)
                VALUES (?, ?, ?, 'ENABLED', 97)
                """, departmentCode, username, regionCode);
        Long departmentId = jdbc.queryForObject(
                "SELECT department_id FROM sys_department WHERE department_code=?", Long.class, departmentCode);
        jdbc.update("""
                INSERT INTO sys_user (username, password_hash, role_id, department_id, real_name, status)
                VALUES (?, ?, ?, ?, ?, 'ENABLED')
                """, username, passwordEncoder.encode(PASSWORD), roleId, departmentId, username);
    }

    protected void cleanupFixture() {
        List<Long> personIds = jdbc.queryForList(
                "SELECT person_id FROM person WHERE name LIKE ?", Long.class, PREFIX + "%");
        if (!personIds.isEmpty()) {
            String in = String.join(",", personIds.stream().map(String::valueOf).toList());
            jdbc.update("DELETE FROM residence_permit_log WHERE permit_id IN "
                    + "(SELECT permit_id FROM residence_permit WHERE person_id IN (" + in + "))");
            jdbc.update("DELETE FROM residence_permit_application WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM residence_archive WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM residence_permit WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM floating_registration_application WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM floating_population WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM migration_out WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM migration_in WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM business_application WHERE target_person_id IN (" + in + ")");
            jdbc.update("DELETE FROM household_member WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM residence WHERE person_id IN (" + in + ")");
            jdbc.update("DELETE FROM household WHERE household_no LIKE ?", PREFIX + "%");
            jdbc.update("DELETE FROM person WHERE person_id IN (" + in + ")");
        }
        jdbc.update("DELETE FROM household WHERE household_no LIKE ?", PREFIX + "%");
        for (String username : List.of("q6871_region_a", "q6871_region_b", "q6871_noview",
                "q6871_empty_region", "q6871_viewer", "q6871_self", "q6871_dept")) {
            jdbc.update("DELETE FROM operation_log WHERE user_id IN "
                    + "(SELECT user_id FROM sys_user WHERE username=?)", username);
            jdbc.update("DELETE FROM sys_user WHERE username=?", username);
        }
        for (String roleCode : List.of("Q6871_REGION_A", "Q6871_REGION_B", "Q6871_NO_VIEW",
                "Q6871_EMPTY_REGION", "Q6871_VIEWER", "Q6871_SELF", "Q6871_DEPT")) {
            jdbc.update("DELETE FROM sys_role_permission WHERE role_id IN "
                    + "(SELECT role_id FROM sys_role WHERE role_code=?)", roleCode);
            jdbc.update("DELETE FROM sys_role WHERE role_code=?", roleCode);
        }
        jdbc.update("DELETE FROM sys_department WHERE department_code LIKE 'Q6871_DEPT_%'");
        for (String code : List.of("980101", "980100", "980000", "981101", "981100", "981000")) {
            jdbc.update("DELETE FROM admin_region WHERE region_code=?", code);
        }
    }
}
