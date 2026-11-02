package com.wjx871.population.stats;

import static org.assertj.core.api.Assertions.assertThat;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.RoleLevel;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class StatisticsScopeIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired StatisticsService service;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void summaryUsesCurrentRegionAndCountsOnlyActiveBusinessRows() {
        seed("本区县合成人口", "110105", "ACTIVE", "ACTIVE");
        seed("同市其他区县合成人口", "110101", "ACTIVE", "ACTIVE");
        seed("外市合成人口", "120000", "ACTIVE", "ACTIVE");
        seed("本区县失效合成人口", "110105", "CANCELLED", "RELEASED");
        authenticateRegion("110105", List.of("110105", "110105001", "110105001001"));

        Map<String, Object> summary = service.getSummary();
        Map<String, Object> charts = service.getChartData();

        assertThat(summary).containsEntry("residentCount", 1L)
                .containsEntry("householdCount", 1L)
                .containsEntry("keyPopulationCount", 1L)
                .containsEntry("isMock", false);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_population WHERE status='有效'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household WHERE status='正常'", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM key_population WHERE status='ACTIVE'", Integer.class))
                .isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM household WHERE status='ACTIVE'", Integer.class))
                .isEqualTo(3);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> keyTypes = (List<Map<String, Object>>) charts.get("keyPopulationDistribution");
        assertThat(keyTypes).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("name", "AUDIT_KEY_POPULATION"));
    }

    @Test
    void departmentAndSelfStatisticsExcludeOtherOwners() {
        long departmentPerson = seed("本部门合成人口", "110105", "ACTIVE", "ACTIVE");
        long otherDepartmentPerson = seed("其他部门合成人口", "110101", "ACTIVE", "ACTIVE");
        long departmentHousehold = householdOf(departmentPerson);
        long otherDepartmentHousehold = householdOf(otherDepartmentPerson);
        long departmentApp = createApplication("AUDIT-D-" + UUID.randomUUID().toString().substring(0, 8),
                departmentPerson, departmentHousehold, 2L, 2L, "110105");
        long otherDepartmentApp = createApplication("AUDIT-O-" + UUID.randomUUID().toString().substring(0, 8),
                otherDepartmentPerson, otherDepartmentHousehold, 6L, 4L, "110101");
        insertFloating(departmentApp, departmentPerson, 2L, "110105");
        insertFloating(otherDepartmentApp, otherDepartmentPerson, 4L, "110101");
        insertMigrationIn(departmentApp, departmentPerson, departmentHousehold, "110105");
        insertMigrationIn(otherDepartmentApp, otherDepartmentPerson, otherDepartmentHousehold, "110101");
        insertLog(2L, "department log");
        insertLog(6L, "other department log");

        authenticate(DataScope.DEPARTMENT, 2L, 2L, "110105", List.of());
        Map<String, Object> departmentSummary = service.getSummary();
        assertThat(departmentSummary).containsEntry("residentCount", 1L)
                .containsEntry("floatingCount", 1L).containsEntry("householdCount", 1L)
                .containsEntry("keyPopulationCount", 1L);
        Map<String, Object> departmentCharts = service.getChartData();
        @SuppressWarnings("unchecked")
        Map<String, Object> departmentTrend = (Map<String, Object>) departmentCharts.get("migrationTrend");
        @SuppressWarnings("unchecked")
        List<Long> departmentInFlow = (List<Long>) departmentTrend.get("inFlow");
        assertThat(departmentInFlow).hasSize(7).endsWith(1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> departmentCertificates = (List<Map<String, Object>>) departmentCharts.get("certificateStatus");
        assertThat(departmentCertificates).singleElement()
                .satisfies(row -> assertThat(row).containsEntry("name", "ACTIVE").containsEntry("value", 1L));
        assertThat(service.getRecentLogs()).singleElement()
                .satisfies(log -> assertThat(log).containsEntry("operator", "population"));

        authenticate(DataScope.SELF, 2L, 2L, "110105", List.of());
        Map<String, Object> selfSummary = service.getSummary();
        assertThat(selfSummary).containsEntry("residentCount", 1L)
                .containsEntry("floatingCount", 1L).containsEntry("householdCount", 0L)
                .containsEntry("keyPopulationCount", 1L);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> selfLogs = service.getRecentLogs();
        assertThat(selfLogs).singleElement().satisfies(log -> assertThat(log).containsEntry("operator", "population"));
    }

    @Test
    void allScopeCanReadRealActiveTotalsWithoutMockFallback() {
        seed("本区县合成人口", "110105", "ACTIVE", "ACTIVE");
        seed("同市其他区县合成人口", "110101", "ACTIVE", "ACTIVE");
        seed("已注销合成人口", "120000", "CANCELLED", "RELEASED");
        authenticate(DataScope.ALL, 3L, 3L, "110000", List.of());

        assertThat(service.getSummary()).containsEntry("residentCount", 2L)
                .containsEntry("householdCount", 2L).containsEntry("keyPopulationCount", 2L)
                .containsEntry("isMock", false);
    }

    private void authenticateRegion(String regionCode, List<String> regionCodes) {
        authenticate(DataScope.REGION, 99101L, 2L, regionCode, regionCodes);
    }

    private void authenticate(DataScope scope, long userId, long departmentId, String regionCode,
            List<String> regionCodes) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "statistics-" + userId, "", "Synthetic statistics user",
                "ENABLED", userId, "STATISTICS_TEST", "Statistics test", RoleLevel.L1, scope, "ENABLED",
                departmentId, "Synthetic department", regionCode, List.of("statistics:view"), regionCodes);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private long seed(String name, String region, String householdStatus, String keyStatus) {
        String identity = "AUDIT-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbc.update("INSERT INTO person(name,gender,id_card,current_address,status,current_status_code) "
                + "VALUES(?,'男',?,'synthetic address',?,?)",
                name, identity, "CANCELLED".equals(householdStatus) ? "已注销" : "正常",
                "CANCELLED".equals(householdStatus) ? "CANCELLED" : "REGISTERED");
        Long personId = jdbc.queryForObject("SELECT person_id FROM person WHERE id_card=?", Long.class, identity);
        String householdNo = "AUDIT-HH-" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbc.update("INSERT INTO household(household_no,head_person_id,address,region_code,household_type,"
                + "establish_date,status,version) VALUES(?,?,?,?,'FAMILY',? ,?,0)",
                householdNo, personId, "synthetic household address", region, LocalDate.of(2020, 1, 1), householdStatus);
        Long householdId = jdbc.queryForObject("SELECT household_id FROM household WHERE household_no=?",
                Long.class, householdNo);
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,"
                + "register_date,start_date,status,created_by,version) VALUES(?,?,?,?,'OTHER',?,?,?,2,0)",
                personId, householdId, "synthetic address", region, LocalDate.of(2020, 1, 1),
                LocalDate.of(2020, 1, 1), "CANCELLED".equals(householdStatus) ? "INACTIVE" : "ACTIVE");
        jdbc.update("INSERT INTO key_population(person_id,key_type,register_date,status,version) "
                + "VALUES(?,'AUDIT_KEY_POPULATION',?,?,0)", personId, LocalDate.now(), keyStatus);
        jdbc.update("INSERT INTO certificate(person_id,certificate_type,certificate_no,issue_date,expire_date,status) "
                + "VALUES(?,'OTHER',?,?,?,'ACTIVE')", personId,
                "AUDIT-CERT-" + UUID.randomUUID().toString().substring(0, 8), LocalDate.now(),
                LocalDate.now().plusYears(1));
        return personId;
    }

    private long householdOf(long personId) {
        return jdbc.queryForObject("SELECT household_id FROM residence WHERE person_id=?", Long.class, personId);
    }

    private long createApplication(String applicationNo, long personId, long householdId, long applicant,
            long department, String region) {
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,"
                + "applicant_department_id,applicant_region_code,target_person_id,target_household_id,status,reason,version) "
                + "VALUES(?,'STATISTICS_AUDIT','Statistics scope audit',?,?,?,?,?,'COMPLETED','synthetic test',0)",
                applicationNo, applicant, department, region, personId, householdId);
        return jdbc.queryForObject("SELECT application_id FROM business_application WHERE application_no=?",
                Long.class, applicationNo);
    }

    private void insertFloating(long applicationId, long personId, long department, String region) {
        jdbc.update("INSERT INTO floating_population(registration_no,source_application_id,person_id,source_region_code,"
                + "current_region_code,current_address,residence_reason_code,residence_proof_type,arrival_date,"
                + "registration_date,department_id,status,current_flag,version) "
                + "VALUES(?,?,?,'110000',?,'synthetic address','WORK','LEASE',?,?,?, 'ACTIVE',1,0)",
                "AUDIT-FLOAT-" + UUID.randomUUID().toString().substring(0, 8), applicationId, personId, region,
                LocalDate.now(), LocalDate.now(), department);
    }

    private void insertMigrationIn(long applicationId, long personId, long householdId, String region) {
        jdbc.update("INSERT INTO migration_in(application_id,person_id,migration_type,from_address,to_region_code,"
                + "to_household_id,in_date,reason,business_status,version) "
                + "VALUES(?,?,'DOMESTIC','synthetic origin',?,?,?,'statistics migration','COMPLETED',0)",
                applicationId, personId, region, householdId, LocalDate.now());
    }

    private void insertLog(long userId, String detail) {
        jdbc.update("INSERT INTO operation_log(user_id,operation_type,target_table,operation_result,detail) "
                + "VALUES(?,'STATISTICS_AUDIT','synthetic','SUCCESS',?)", userId, detail);
    }
}
