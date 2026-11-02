package com.wjx871.population.dashboard;

import static org.assertj.core.api.Assertions.assertThat;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 直接加载 DashboardMapper XML，验证统计过滤口径与数据范围。
 */
@SpringBootTest
@Transactional
class DashboardMapperTest {

    @Autowired
    private DashboardMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DashboardService dashboardService;

    private Long personActive;
    private Long personInactiveResidence;
    private Long personOtherRegion;

    @BeforeEach
    void setUp() {
        cleanBusinessData();
        seedData();
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void registeredPopulationCountsOnlyActiveResidence() {
        DataScopeCriteria all = allScope();
        assertThat(mapper.countRegisteredPopulation(all)).isEqualTo(2L);
    }

    @Test
    void floatingAndPermitRequireActiveAndCurrentFlag() {
        DataScopeCriteria all = allScope();
        assertThat(mapper.countActiveFloating(all)).isEqualTo(1L);
        assertThat(mapper.countActivePermits(all)).isEqualTo(1L);
    }

    @Test
    void migrationsAndPendingApprovalsUseBusinessStatusFilters() {
        DataScopeCriteria all = allScope();
        LocalDate from = LocalDate.of(2026, 6, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        assertThat(mapper.countMigrationsIn(from, to, all)).isEqualTo(1L);
        assertThat(mapper.countMigrationsOut(from, to, all)).isEqualTo(1L);
        assertThat(mapper.countPendingApprovals(all)).isEqualTo(1L);

        List<MigrationTrendPoint> inTrend = mapper.migrationInTrend(from, to, all);
        List<MigrationTrendPoint> outTrend = mapper.migrationOutTrend(from, to, all);
        assertThat(inTrend).hasSize(1);
        assertThat(inTrend.get(0).getInCount()).isEqualTo(1L);
        assertThat(outTrend).hasSize(1);
        assertThat(outTrend.get(0).getOutCount()).isEqualTo(1L);
    }

    @Test
    void regionRankingRespectsDataScope() {
        DataScopeCriteria region = new DataScopeCriteria(DataScope.REGION, 1L, 1L, "110000", regionCodes());
        List<RegionCountView> rows = mapper.registeredPopulationByRegion(region, 8);
        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> assertThat(row.getRegionCode()).startsWith("11"));
        assertThat(rows).noneMatch(row -> row.getRegionCode() != null && row.getRegionCode().startsWith("12"));
    }

    @Test
    void countyRegionScopeDoesNotIncludeSiblingDistricts() {
        Long ownCountyPerson = insertPerson("本区县", "AUDIT-REGION-OWN");
        Long ownCountyHousehold = insertHousehold("HH-COUNTY-OWN", ownCountyPerson, "110105");
        insertResidence(ownCountyPerson, ownCountyHousehold, "110105", "ACTIVE");

        DataScopeCriteria county = new DataScopeCriteria(DataScope.REGION, 1L, 1L, "110105",
                List.of("110105", "110105001", "110105001001"));
        List<RegionCountView> rows = mapper.registeredPopulationByRegion(county, 20);

        assertThat(rows).extracting(RegionCountView::getRegionCode).containsExactly("110105");
    }

    @Test
    void regionScopeIncludesBusinessApplicationsInChildRegions() {
        DataScopeCriteria region = new DataScopeCriteria(DataScope.REGION, 1L, 1L, "110000", regionCodes());
        Long childRegionApplication = insertApplication("D-APP-CHILD", 2L, 2L, "110105", personActive);
        jdbc.update("""
                INSERT INTO sys_approval_request (approval_no, application_id, status, submitted_by, submitted_at, version)
                VALUES ('APR-CHILD', ?, 'PENDING', 2, CURRENT_TIMESTAMP, 0)
                """, childRegionApplication);

        assertThat(mapper.countPendingApprovals(region)).isEqualTo(2L);
        assertThat(mapper.approvalStatusDistribution(region))
                .filteredOn(row -> "PENDING".equals(row.getCode()))
                .singleElement()
                .extracting(NamedCountView::getValue)
                .isEqualTo(2L);
    }

    @Test
    void supplementalDashboardPanelsReadTheirSourceTables() {
        DataScopeCriteria all = allScope();
        LocalDate from = LocalDate.of(2025, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);

        assertThat(mapper.registeredPopulationGender(all))
                .anyMatch(row -> "男".equals(row.getCode()));
        assertThat(mapper.registeredPopulationAgeGroups(LocalDate.of(2026, 7, 12), all)).isNotEmpty();
        assertThat(mapper.businessScale(from, to.plusDays(1), all))
                .anyMatch(row -> "DASH_SEED".equals(row.getCode()));
        assertThat(mapper.populationScaleTrend(from, to, all)).isNotEmpty();
        assertThat(mapper.approvalStatusDistribution(all))
                .extracting(NamedCountView::getCode)
                .contains("PENDING", "APPROVED");
        assertThat(mapper.countActiveKeyPopulation(all)).isZero();
        assertThat(mapper.countPendingCancellation(all)).isZero();
        assertThat(mapper.countPendingSensitiveExport(all)).isZero();
    }

    @Test
    void selfFloatingAndPermitStatisticsFollowApplicantAcrossExecutors() {
        seedSelfOwnershipScenario();
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);

        DataScopeCriteria a = selfScope(2L, 2L);
        DataScopeCriteria b = selfScope(99202L, 2L);
        DataScopeCriteria executor = selfScope(99203L, 2L);
        SoftAssertions softly = new SoftAssertions();

        softly.assertThat(mapper.countActiveFloating(a)).as("A sees own floating record executed by C").isEqualTo(1L);
        softly.assertThat(mapper.countActivePermits(a)).as("A cannot see B's permit").isEqualTo(1L);
        softly.assertThat(mapper.countExpiringPermits(today, today.plusDays(30), a))
                .as("A sees only own expiring permit").isEqualTo(1L);
        softly.assertThat(mapper.countActiveFloating(b)).as("B sees own floating record").isEqualTo(1L);
        softly.assertThat(mapper.countActivePermits(b)).as("B cannot see A's permit").isEqualTo(1L);
        softly.assertThat(mapper.countExpiringPermits(today, today.plusDays(30), b))
                .as("B sees only own expiring permit").isEqualTo(1L);
        softly.assertThat(mapper.countActiveFloating(executor)).as("C has no applicant-owned floating record").isZero();
        softly.assertThat(mapper.countActivePermits(executor)).as("C has no applicant-owned permit").isZero();
        softly.assertThat(mapper.countExpiringPermits(today, today.plusDays(30), executor))
                .as("C has no applicant-owned expiring permit").isZero();

        assertTrend(softly, a, from, today, 1L, 1L, "A");
        assertTrend(softly, b, from, today, 1L, 1L, "B");
        assertTrend(softly, executor, from, today, 0L, 0L, "C");
        softly.assertAll();
    }

    @Test
    void dashboardServiceReturnsSelfScopedOverviewAndTrendCounts() {
        seedSelfOwnershipScenario();
        authenticateSelf(2L, 2L);

        DashboardOverviewView overview = dashboardService.overview(7, 30);
        DashboardChartsView charts = dashboardService.charts(7, 8);
        LocalDate today = LocalDate.now();
        PopulationScaleTrendPoint todayTrend = charts.getPopulationScaleTrend().stream()
                .filter(point -> today.equals(point.getDate()))
                .findFirst()
                .orElseThrow();

        assertThat(overview.getActiveFloatingPopulation()).isEqualTo(1L);
        assertThat(overview.getActiveResidencePermits()).isEqualTo(1L);
        assertThat(overview.getExpiringResidencePermits()).isEqualTo(1L);
        assertThat(todayTrend.getFloatingPopulation()).isEqualTo(1L);
        assertThat(todayTrend.getResidencePermits()).isEqualTo(1L);
    }

    @Test
    void departmentRegionAndAllScopesRemainUnchangedAndIgnoreHistoricalCurrentFlags() {
        seedSelfOwnershipScenario();
        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        DataScopeCriteria department = new DataScopeCriteria(DataScope.DEPARTMENT, 2L, 2L, "110000");
        DataScopeCriteria region = new DataScopeCriteria(DataScope.REGION, 2L, 2L, "110101", List.of("110101"));
        DataScopeCriteria all = new DataScopeCriteria(DataScope.ALL, 3L, 3L, "110000");

        assertThat(mapper.countActiveFloating(department)).isEqualTo(2L);
        assertThat(mapper.countActivePermits(department)).isEqualTo(2L);
        assertThat(mapper.countExpiringPermits(today, today.plusDays(30), department)).isEqualTo(2L);
        assertThat(mapper.countActiveFloating(region)).isEqualTo(2L);
        assertThat(mapper.countActivePermits(region)).isEqualTo(2L);
        assertThat(mapper.countExpiringPermits(today, today.plusDays(30), region)).isEqualTo(2L);
        assertThat(mapper.countActiveFloating(all)).isEqualTo(3L);
        assertThat(mapper.countActivePermits(all)).isEqualTo(3L);
        assertThat(mapper.countExpiringPermits(today, today.plusDays(30), all)).isEqualTo(3L);

        SoftAssertions trendAssertions = new SoftAssertions();
        assertTrend(trendAssertions, department, from, today, 2L, 2L, "department");
        assertTrend(trendAssertions, region, from, today, 2L, 2L, "region");
        assertTrend(trendAssertions, all, from, today, 3L, 3L, "all");
        trendAssertions.assertAll();
    }

    private DataScopeCriteria allScope() {
        return new DataScopeCriteria(DataScope.ALL, 3L, 3L, "110000");
    }

    private List<String> regionCodes() {
        return List.of("110000", "110100", "110101", "110105", "110105001", "110105001001");
    }

    private DataScopeCriteria selfScope(long userId, long departmentId) {
        return new DataScopeCriteria(DataScope.SELF, userId, departmentId, "110101");
    }

    private void assertTrend(SoftAssertions softly, DataScopeCriteria scope, LocalDate from, LocalDate to,
            long expectedFloating, long expectedPermits, String label) {
        List<PopulationScaleTrendPoint> rows = mapper.populationScaleTrend(from, to, scope);
        PopulationScaleTrendPoint today = rows.stream()
                .filter(point -> to.equals(point.getDate()))
                .findFirst()
                .orElse(new PopulationScaleTrendPoint(to, 0L, 0L, 0L));
        softly.assertThat(today.getFloatingPopulation()).as(label + " floating trend").isEqualTo(expectedFloating);
        softly.assertThat(today.getResidencePermits()).as(label + " permit trend").isEqualTo(expectedPermits);
    }

    private void seedSelfOwnershipScenario() {
        long ownerB = 99202L;
        long executor = 99203L;
        jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) "
                + "VALUES(?,?,?,2,2,?,'ENABLED')", ownerB, "audit-self-b", "synthetic-test-hash", "SELF owner B");
        jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) "
                + "VALUES(?,?,?,2,2,?,'ENABLED')", executor, "audit-self-c", "synthetic-test-hash", "SELF executor C");

        LocalDate today = LocalDate.now();
        LocalDate historicalDate = today.minusDays(90);
        jdbc.update("UPDATE floating_population SET current_region_code='110101',operator_id=?,registration_date=? "
                + "WHERE person_id=? AND status='ACTIVE' AND current_flag=1", executor, today, personActive);
        Long floatingA = jdbc.queryForObject(
                "SELECT floating_id FROM floating_population WHERE person_id=? AND status='ACTIVE' AND current_flag=1",
                Long.class, personActive);
        jdbc.update("UPDATE residence_permit SET issue_region_code='110101',issue_date=?,valid_from=?,valid_until=? "
                + "WHERE person_id=? AND status='ACTIVE' AND current_flag=1", today, today, today.plusDays(7), personActive);

        Long personB = insertPerson("SELF owner B", auditId("B"));
        Long appFloatB = insertApplication("DASH-SELF-B-F-" + auditSuffix(), ownerB, 2L, "110101", personB);
        Long floatingB = insertSyntheticFloating(personB, appFloatB, "ACTIVE", 1, "110101", 2L, executor, today);
        Long appPermitB = insertApplication("DASH-SELF-B-P-" + auditSuffix(), ownerB, 2L, "110101", personB);
        insertSyntheticPermit(personB, floatingB, appPermitB, "ACTIVE", 1, "110101", 2L, today,
                today, today.plusDays(7));

        Long outsiderAppFloat = insertApplication("DASH-SELF-O-F-" + auditSuffix(), 6L, 4L, "120000", personOtherRegion);
        Long outsiderFloating = insertSyntheticFloating(personOtherRegion, outsiderAppFloat, "ACTIVE", 1,
                "120000", 4L, executor, today);
        Long outsiderAppPermit = insertApplication("DASH-SELF-O-P-" + auditSuffix(), 6L, 4L, "120000", personOtherRegion);
        insertSyntheticPermit(personOtherRegion, outsiderFloating, outsiderAppPermit, "ACTIVE", 1,
                "120000", 4L, today, today, today.plusDays(7));

        Long closedApp = insertApplication("DASH-SELF-A-FH-" + auditSuffix(), 2L, 2L, "110101", personActive);
        insertSyntheticFloating(personActive, closedApp, "CLOSED", null, "110101", 2L, executor, historicalDate);
        Long nonCurrentApp = insertApplication("DASH-SELF-A-P0-" + auditSuffix(), 2L, 2L, "110101", personActive);
        insertSyntheticPermit(personActive, floatingA, nonCurrentApp, "ACTIVE", 0, "110101", 2L,
                historicalDate, today, today.plusDays(7));
        Long cancelledApp = insertApplication("DASH-SELF-A-PC-" + auditSuffix(), 2L, 2L, "110101", personActive);
        insertSyntheticPermit(personActive, floatingA, cancelledApp, "CANCELLED", null, "110101", 2L,
                historicalDate, historicalDate, today.plusDays(7));
        Long expiredApp = insertApplication("DASH-SELF-A-PE-" + auditSuffix(), 2L, 2L, "110101", personActive);
        insertSyntheticPermit(personActive, floatingA, expiredApp, "EXPIRED", null, "110101", 2L,
                historicalDate, historicalDate, today.minusDays(1));
    }

    private Long insertSyntheticFloating(Long personId, Long applicationId, String status, Integer currentFlag,
            String region, Long departmentId, Long operatorId, LocalDate registrationDate) {
        String registrationNo = "DSF-" + auditSuffix();
        jdbc.update("INSERT INTO floating_population(registration_no,source_application_id,person_id,"
                + "source_region_code,current_region_code,current_address,residence_reason_code,residence_proof_type,"
                + "arrival_date,registration_date,department_id,operator_id,status,current_flag,version) "
                + "VALUES(?, ?, ?, '110000', ?, 'synthetic address', 'WORK', 'LEASE', ?, ?, ?, ?, ?, ?, 0)",
                registrationNo, applicationId, personId, region, registrationDate, registrationDate, departmentId,
                operatorId, status, currentFlag);
        return jdbc.queryForObject("SELECT floating_id FROM floating_population WHERE registration_no=?",
                Long.class, registrationNo);
    }

    private void insertSyntheticPermit(Long personId, Long floatingId, Long applicationId, String status,
            Integer currentFlag, String region, Long departmentId, LocalDate issueDate, LocalDate validFrom,
            LocalDate validUntil) {
        String permitNo = "DSP-" + auditSuffix();
        jdbc.update("INSERT INTO residence_permit(permit_no,person_id,floating_id,source_application_id,"
                + "issue_region_code,issuing_department_id,issuing_authority,issue_date,valid_from,valid_until,"
                + "status,current_flag,version) VALUES(?,?,?,?,?,?, 'Synthetic authority',?,?,?,?,?,0)",
                permitNo, personId, floatingId, applicationId, region, departmentId, issueDate, validFrom,
                validUntil, status, currentFlag);
    }

    private String auditId(String owner) {
        return "AUD" + owner + auditSuffix();
    }

    private String auditSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private void authenticateSelf(long userId, long departmentId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "dashboard-self-" + userId, "", "Synthetic user",
                "ENABLED", 2L, "SELF_TEST", "SELF test", null, DataScope.SELF, "ENABLED", departmentId,
                "Population", "110101", List.of("statistics:view"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void cleanBusinessData() {
        jdbc.update("DELETE FROM residence_permit_log");
        jdbc.update("DELETE FROM residence_permit");
        jdbc.update("DELETE FROM residence_permit_application");
        jdbc.update("DELETE FROM floating_population");
        jdbc.update("DELETE FROM floating_registration_application");
        jdbc.update("DELETE FROM residence_archive");
        jdbc.update("DELETE FROM migration_out");
        jdbc.update("DELETE FROM migration_in");
        jdbc.update("DELETE FROM sys_approval_log");
        jdbc.update("DELETE FROM sys_approval_request");
        jdbc.update("DELETE FROM application_material");
        jdbc.update("DELETE FROM business_application");
        jdbc.update("DELETE FROM household_member");
        jdbc.update("DELETE FROM residence");
        jdbc.update("DELETE FROM household");
        jdbc.update("DELETE FROM person");
    }

    private void seedData() {
        personActive = insertPerson("活跃", "110101199101010011");
        personInactiveResidence = insertPerson("历史户籍", "110101199102020022");
        personOtherRegion = insertPerson("外区", "120101199103030033");

        Long hhActive = insertHousehold("HH-D-A", personActive, "110101");
        Long hhInactive = insertHousehold("HH-D-I", personInactiveResidence, "110102");
        Long hhOther = insertHousehold("HH-D-O", personOtherRegion, "120101");

        insertResidence(personActive, hhActive, "110101", "ACTIVE");
        insertResidence(personInactiveResidence, hhInactive, "110102", "INACTIVE");
        insertResidence(personOtherRegion, hhOther, "120101", "ACTIVE");

        Long appFloat = insertApplication("D-APP-F", 2L, 2L, "110000", personActive);
        Long floatingId = insertFloating(personActive, appFloat, "ACTIVE", 1);
        Long appFloatHist = insertApplication("D-APP-FH", 2L, 2L, "110000", personInactiveResidence);
        insertFloating(personInactiveResidence, appFloatHist, "CLOSED", null);

        Long appPermit = insertApplication("D-APP-P", 2L, 2L, "110000", personActive);
        insertPermit(personActive, floatingId, appPermit, "ACTIVE", 1);
        Long appPermitHist = insertApplication("D-APP-PH", 2L, 2L, "110000", personInactiveResidence);
        Long floatingHist = jdbc.queryForObject(
                "SELECT floating_id FROM floating_population WHERE person_id = ?", Long.class, personInactiveResidence);
        insertPermit(personInactiveResidence, floatingHist, appPermitHist, "CANCELLED", null);

        Long appIn = insertApplication("D-APP-IN", 2L, 2L, "110000", personActive);
        Long appOut = insertApplication("D-APP-OUT", 2L, 2L, "110000", personActive);
        Long appDraft = insertApplication("D-APP-DRAFT", 2L, 2L, "110000", personActive);
        jdbc.update("""
                INSERT INTO migration_in (
                    application_id, person_id, migration_type, from_region_code, from_address,
                    to_region_code, to_household_id, to_address_snapshot, in_date, reason, business_status
                ) VALUES (?, ?, 'TRANSFER', '120000', 'from', '110101', ?, 'to', ?, 'reason', 'COMPLETED')
                """, appIn, personActive, hhActive, LocalDate.of(2026, 6, 10));
        jdbc.update("""
                INSERT INTO migration_out (
                    application_id, person_id, migration_type, from_region_code, from_household_id,
                    from_address_snapshot, to_region_code, to_address, out_date, reason, business_status
                ) VALUES (?, ?, 'TRANSFER', '110101', ?, 'from', '120000', 'to', ?, 'reason', 'COMPLETED')
                """, appOut, personActive, hhActive, LocalDate.of(2026, 6, 12));
        jdbc.update("""
                INSERT INTO migration_in (
                    application_id, person_id, migration_type, from_region_code, from_address,
                    to_region_code, to_household_id, to_address_snapshot, in_date, reason, business_status
                ) VALUES (?, ?, 'TRANSFER', '120000', 'from', '110101', ?, 'to', ?, 'reason', 'DRAFT')
                """, appDraft, personActive, hhActive, LocalDate.of(2026, 6, 15));

        Long appPending = insertApplication("D-APP-PEND", 2L, 2L, "110000", personActive);
        jdbc.update("""
                INSERT INTO sys_approval_request (
                    approval_no, application_id, status, submitted_by, submitted_at, version
                ) VALUES ('APR-1', ?, 'PENDING', 2, CURRENT_TIMESTAMP, 0)
                """, appPending);
        Long appApproved = insertApplication("D-APP-OK", 2L, 2L, "110000", personActive);
        jdbc.update("""
                INSERT INTO sys_approval_request (
                    approval_no, application_id, status, submitted_by, submitted_at, version
                ) VALUES ('APR-2', ?, 'APPROVED', 2, CURRENT_TIMESTAMP, 0)
                """, appApproved);
    }

    private Long insertPerson(String name, String idCard) {
        jdbc.update("""
                INSERT INTO person (name, gender, id_card, birth_date, ethnicity, phone, current_address, status, current_status_code)
                VALUES (?, '男', ?, '1991-01-01', 'Han', '13800138000', 'addr', '正常', 'REGISTERED')
                """, name, idCard);
        return jdbc.queryForObject("SELECT person_id FROM person WHERE id_card = ?", Long.class, idCard);
    }

    private Long insertHousehold(String no, Long headPersonId, String regionCode) {
        jdbc.update("""
                INSERT INTO household (household_no, head_person_id, address, establish_date, status, region_code, version)
                VALUES (?, ?, 'addr', '2020-01-01', 'ACTIVE', ?, 0)
                """, no, headPersonId, regionCode);
        return jdbc.queryForObject("SELECT household_id FROM household WHERE household_no = ?", Long.class, no);
    }

    private void insertResidence(Long personId, Long householdId, String regionCode, String status) {
        jdbc.update("""
                INSERT INTO residence (
                    person_id, household_id, registered_address, region_code, register_type_code,
                    register_date, start_date, status, version
                ) VALUES (?, ?, 'reg-addr', ?, 'NORMAL', '2020-01-01', '2020-01-01', ?, 0)
                """, personId, householdId, regionCode, status);
    }

    private Long insertApplication(String no, Long userId, Long departmentId, String regionCode, Long personId) {
        jdbc.update("""
                INSERT INTO business_application (
                    application_no, business_type, title, applicant_user_id, applicant_department_id,
                    applicant_region_code, target_person_id, status, reason, version
                ) VALUES (?, 'DASH_SEED', 'seed', ?, ?, ?, ?, 'COMPLETED', 'seed', 0)
                """, no, userId, departmentId, regionCode, personId);
        return jdbc.queryForObject(
                "SELECT application_id FROM business_application WHERE application_no = ?", Long.class, no);
    }

    private Long insertFloating(Long personId, Long applicationId, String status, Integer currentFlag) {
        String regNo = "DFP-" + personId + "-" + (currentFlag == null ? "H" : "C");
        jdbc.update("""
                INSERT INTO floating_population (
                    registration_no, source_application_id, person_id, source_region_code, source_address,
                    current_region_code, current_address, residence_reason_code, residence_proof_type,
                    arrival_date, registration_date, department_id, operator_id, status, current_flag, version
                ) VALUES (?, ?, ?, '120000', 'src', '110101', 'cur', 'WORK', 'LEASE',
                          '2025-01-01', '2025-01-02', 2, 2, ?, ?, 0)
                """, regNo, applicationId, personId, status, currentFlag);
        return jdbc.queryForObject(
                "SELECT floating_id FROM floating_population WHERE registration_no = ?", Long.class, regNo);
    }

    private void insertPermit(Long personId, Long floatingId, Long applicationId, String status, Integer currentFlag) {
        String permitNo = "DJZP-" + personId + "-" + (currentFlag == null ? "H" : "C");
        jdbc.update("""
                INSERT INTO residence_permit (
                    permit_no, person_id, floating_id, source_application_id, issue_region_code,
                    issuing_department_id, issuing_authority, issue_date, valid_from, valid_until,
                    status, current_flag, version
                ) VALUES (?, ?, ?, ?, '110101', 2, '公安局', '2025-06-01', '2025-06-01', '2026-12-31', ?, ?, 0)
                """, permitNo, personId, floatingId, applicationId, status, currentFlag);
    }
}
