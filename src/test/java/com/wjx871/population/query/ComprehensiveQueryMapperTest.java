package com.wjx871.population.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/**
 * 直接加载 ComprehensiveQueryMapper XML，验证 JOIN 聚合、数据范围与一人一行。
 */
@SpringBootTest
@Transactional
class ComprehensiveQueryMapperTest {

    @Autowired
    private ComprehensiveQueryMapper mapper;

    @Autowired
    private JdbcTemplate jdbc;

    private Long personMain;
    private Long personRegionOther;
    private Long personDept;
    private Long personSelf;
    private Long personHistoryOnly;

    @BeforeEach
    void setUp() {
        cleanBusinessData();
        seedPersonsAndHouseholds();
        seedScopeApplications();
        seedCurrentAssociations();
        seedMigrations();
        seedHistoryFlags();
    }

    @Test
    void allScopeReturnsOneRowPerPersonAndCountMatchesList() {
        DataScopeCriteria scope = allScope();
        long count = mapper.countSummaries(null, null, null, null, null, null, scope);
        List<ComprehensivePersonSummaryView> rows = mapper.selectSummaries(
                null, null, null, null, null, null, scope, "p.person_id", "ASC", 50, 0L);

        assertThat(count).isEqualTo(rows.size());
        assertThat(rows).extracting(ComprehensivePersonSummaryView::getPersonId)
                .contains(personMain, personRegionOther, personDept, personSelf, personHistoryOnly)
                .doesNotHaveDuplicates();

        ComprehensivePersonSummaryView main = rows.stream()
                .filter(r -> personMain.equals(r.getPersonId()))
                .findFirst()
                .orElseThrow();
        assertThat(main.getResidenceStatus()).isEqualTo("ACTIVE");
        assertThat(main.getFloatingStatus()).isEqualTo("ACTIVE");
        assertThat(main.getPermitStatus()).isEqualTo("ACTIVE");
        assertThat(main.getFloatingId()).isNotNull();
        assertThat(main.getPermitId()).isNotNull();
        assertThat(main.getLastMigrationDirection()).isEqualTo("OUT");
        assertThat(main.getLastMigrationDate()).isEqualTo(LocalDate.of(2026, 6, 15));
    }

    @Test
    void regionScopeLimitsByRegionPrefix() {
        DataScopeCriteria scope = new DataScopeCriteria(DataScope.REGION, 1L, 1L, "110000");
        List<ComprehensivePersonSummaryView> rows = mapper.selectSummaries(
                null, null, null, null, null, null, scope, "p.person_id", "ASC", 50, 0L);
        assertThat(rows).extracting(ComprehensivePersonSummaryView::getPersonId)
                .contains(personMain)
                .doesNotContain(personRegionOther);
    }

    @Test
    void departmentAndSelfScopesLimitByApplication() {
        // personMain 也有 department_id=2 的业务申请（流动/居住证/迁移），应纳入 DEPARTMENT 范围
        DataScopeCriteria department = new DataScopeCriteria(DataScope.DEPARTMENT, 99L, 2L, "110000");
        List<ComprehensivePersonSummaryView> deptRows = mapper.selectSummaries(
                null, null, null, null, null, null, department, "p.person_id", "ASC", 50, 0L);
        assertThat(deptRows).extracting(ComprehensivePersonSummaryView::getPersonId)
                .contains(personDept, personMain)
                .doesNotContain(personSelf);

        DataScopeCriteria self = new DataScopeCriteria(DataScope.SELF, 1L, 1L, "110000");
        List<ComprehensivePersonSummaryView> selfRows = mapper.selectSummaries(
                null, null, null, null, null, null, self, "p.person_id", "ASC", 50, 0L);
        assertThat(selfRows).extracting(ComprehensivePersonSummaryView::getPersonId)
                .contains(personSelf)
                .doesNotContain(personDept, personMain);
    }

    @Test
    void historyCurrentFlagNullDoesNotJoinAsCurrent() {
        DataScopeCriteria scope = allScope();
        ComprehensivePersonSummaryView history = mapper.selectSummaries(
                        null, null, null, null, null, null, scope, "p.person_id", "ASC", 50, 0L)
                .stream()
                .filter(r -> personHistoryOnly.equals(r.getPersonId()))
                .findFirst()
                .orElseThrow();
        assertThat(history.getFloatingId()).isNull();
        assertThat(history.getFloatingStatus()).isNull();
        assertThat(history.getPermitId()).isNull();
        assertThat(history.getPermitStatus()).isNull();
    }

    @Test
    void nonCompletedMigrationsDoNotAffectLatestMigration() {
        DataScopeCriteria scope = allScope();
        ComprehensivePersonSummaryView other = mapper.selectSummaries(
                        null, null, null, null, null, null, scope, "p.person_id", "ASC", 50, 0L)
                .stream()
                .filter(r -> personRegionOther.equals(r.getPersonId()))
                .findFirst()
                .orElseThrow();
        // personRegionOther only has DRAFT migration_in
        assertThat(other.getLastMigrationDate()).isNull();
        assertThat(other.getLastMigrationDirection()).isNull();
    }

    @Test
    void equalMigrationDatesPreferOutDirection() {
        DataScopeCriteria scope = allScope();
        ComprehensivePersonSummaryView main = mapper.selectScopedSummary(personMain, scope).orElseThrow();
        assertThat(main.getLastMigrationDate()).isEqualTo(LocalDate.of(2026, 6, 15));
        assertThat(main.getLastMigrationDirection()).isEqualTo("OUT");
    }

    @Test
    void laterInDirectionWinsWhenInIsNewer() {
        jdbc.update("UPDATE migration_in SET in_date = ? WHERE person_id = ?",
                LocalDate.of(2026, 7, 1), personMain);
        ComprehensivePersonSummaryView main = mapper.selectScopedSummary(personMain, allScope()).orElseThrow();
        assertThat(main.getLastMigrationDate()).isEqualTo(LocalDate.of(2026, 7, 1));
        assertThat(main.getLastMigrationDirection()).isEqualTo("IN");
    }

    @Test
    void selectScopedSummaryEnforcesScopeGate() {
        Optional<ComprehensivePersonSummaryView> allowed = mapper.selectScopedSummary(personMain, allScope());
        assertThat(allowed).isPresent();

        DataScopeCriteria self = new DataScopeCriteria(DataScope.SELF, 1L, 1L, "110000");
        Optional<ComprehensivePersonSummaryView> denied = mapper.selectScopedSummary(personMain, self);
        assertThat(denied).isEmpty();
    }

    private DataScopeCriteria allScope() {
        return new DataScopeCriteria(DataScope.ALL, 3L, 3L, "110000");
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

    private void seedPersonsAndHouseholds() {
        personMain = insertPerson("张三", "110101199001010011", "男");
        personRegionOther = insertPerson("李四", "120101199002020022", "女");
        personDept = insertPerson("王五", "110101199003030033", "男");
        personSelf = insertPerson("赵六", "110101199004040044", "女");
        personHistoryOnly = insertPerson("钱七", "110101199005050055", "男");

        Long householdMain = insertHousehold("HH-MAIN", personMain, "110101");
        Long householdOther = insertHousehold("HH-OTHER", personRegionOther, "120101");
        Long householdDept = insertHousehold("HH-DEPT", personDept, "110102");
        Long householdSelf = insertHousehold("HH-SELF", personSelf, "110103");
        Long householdHist = insertHousehold("HH-HIST", personHistoryOnly, "110104");

        insertResidence(personMain, householdMain, "110101", "ACTIVE");
        insertResidence(personRegionOther, householdOther, "120101", "ACTIVE");
        insertResidence(personDept, householdDept, "110102", "ACTIVE");
        insertResidence(personSelf, householdSelf, "110103", "ACTIVE");
        insertResidence(personHistoryOnly, householdHist, "110104", "ACTIVE");

        insertMember(householdMain, personMain);
        insertMember(householdOther, personRegionOther);
        insertMember(householdDept, personDept);
        insertMember(householdSelf, personSelf);
        insertMember(householdHist, personHistoryOnly);
    }

    private void seedScopeApplications() {
        // DEPARTMENT scope for personDept via department_id=2
        insertApplication("APP-DEPT-1", 2L, 2L, "110000", personDept);
        // SELF scope for personSelf via applicant_user_id=1
        insertApplication("APP-SELF-1", 1L, 1L, "110000", personSelf);
        // REGION helper application for personMain
        insertApplication("APP-MAIN-1", 2L, 2L, "110000", personMain);
    }

    private void seedCurrentAssociations() {
        Long appFloating = insertApplication("APP-FLOAT-1", 2L, 2L, "110000", personMain);
        Long floatingId = insertFloating(personMain, appFloating, "ACTIVE", 1, 2L, 2L);
        Long appPermit = insertApplication("APP-PERMIT-1", 2L, 2L, "110000", personMain);
        insertPermit(personMain, floatingId, appPermit, "ACTIVE", 1, 2L, "110101");
    }

    private void seedMigrations() {
        Long appIn = insertApplication("APP-IN-1", 2L, 2L, "110000", personMain);
        Long appOut = insertApplication("APP-OUT-1", 2L, 2L, "110000", personMain);
        Long householdMain = jdbc.queryForObject(
                "SELECT household_id FROM residence WHERE person_id = ?", Long.class, personMain);

        jdbc.update("""
                INSERT INTO migration_in (
                    application_id, person_id, migration_type, from_region_code, from_address,
                    to_region_code, to_household_id, to_address_snapshot, in_date, reason, business_status
                ) VALUES (?, ?, 'TRANSFER', '120000', 'from', '110101', ?, 'to', ?, 'reason', 'COMPLETED')
                """, appIn, personMain, householdMain, LocalDate.of(2026, 6, 15));
        jdbc.update("""
                INSERT INTO migration_out (
                    application_id, person_id, migration_type, from_region_code, from_household_id,
                    from_address_snapshot, to_region_code, to_address, out_date, reason, business_status
                ) VALUES (?, ?, 'TRANSFER', '110101', ?, 'from', '120000', 'to', ?, 'reason', 'COMPLETED')
                """, appOut, personMain, householdMain, LocalDate.of(2026, 6, 15));

        Long appDraft = insertApplication("APP-IN-DRAFT", 2L, 2L, "120000", personRegionOther);
        Long householdOther = jdbc.queryForObject(
                "SELECT household_id FROM residence WHERE person_id = ?", Long.class, personRegionOther);
        jdbc.update("""
                INSERT INTO migration_in (
                    application_id, person_id, migration_type, from_region_code, from_address,
                    to_region_code, to_household_id, to_address_snapshot, in_date, reason, business_status
                ) VALUES (?, ?, 'TRANSFER', '110000', 'from', '120101', ?, 'to', ?, 'reason', 'DRAFT')
                """, appDraft, personRegionOther, householdOther, LocalDate.of(2026, 6, 20));
    }

    private void seedHistoryFlags() {
        Long appFloatHist = insertApplication("APP-FLOAT-HIST", 2L, 2L, "110000", personHistoryOnly);
        insertFloating(personHistoryOnly, appFloatHist, "CLOSED", null, 2L, 2L);
        Long appPermitHist = insertApplication("APP-PERMIT-HIST", 2L, 2L, "110000", personHistoryOnly);
        Long floatingHist = jdbc.queryForObject(
                "SELECT floating_id FROM floating_population WHERE person_id = ?", Long.class, personHistoryOnly);
        insertPermit(personHistoryOnly, floatingHist, appPermitHist, "CANCELLED", null, 2L, "110104");
    }

    private Long insertPerson(String name, String idCard, String gender) {
        jdbc.update("""
                INSERT INTO person (name, gender, id_card, birth_date, ethnicity, phone, current_address, status, current_status_code)
                VALUES (?, ?, ?, '1990-01-01', 'Han', '13800138000', 'addr', '正常', 'REGISTERED')
                """, name, gender, idCard);
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

    private void insertMember(Long householdId, Long personId) {
        jdbc.update("""
                INSERT INTO household_member (household_id, person_id, relationship, join_date, status, version)
                VALUES (?, ?, '户主', '2020-01-01', 'ACTIVE', 0)
                """, householdId, personId);
    }

    private Long insertApplication(String no, Long userId, Long departmentId, String regionCode, Long personId) {
        jdbc.update("""
                INSERT INTO business_application (
                    application_no, business_type, title, applicant_user_id, applicant_department_id,
                    applicant_region_code, target_person_id, status, reason, version
                ) VALUES (?, 'QUERY_SEED', 'seed', ?, ?, ?, ?, 'COMPLETED', 'seed', 0)
                """, no, userId, departmentId, regionCode, personId);
        return jdbc.queryForObject(
                "SELECT application_id FROM business_application WHERE application_no = ?", Long.class, no);
    }

    private Long insertFloating(Long personId, Long applicationId, String status, Integer currentFlag,
            Long departmentId, Long operatorId) {
        String regNo = "FP-" + personId + "-" + (currentFlag == null ? "H" : "C");
        jdbc.update("""
                INSERT INTO floating_population (
                    registration_no, source_application_id, person_id, source_region_code, source_address,
                    current_region_code, current_address, residence_reason_code, residence_proof_type,
                    arrival_date, registration_date, department_id, operator_id, status, current_flag, version
                ) VALUES (?, ?, ?, '120000', 'src', '110101', 'cur', 'WORK', 'LEASE',
                          '2025-01-01', '2025-01-02', ?, ?, ?, ?, 0)
                """, regNo, applicationId, personId, departmentId, operatorId, status, currentFlag);
        return jdbc.queryForObject(
                "SELECT floating_id FROM floating_population WHERE registration_no = ?", Long.class, regNo);
    }

    private void insertPermit(Long personId, Long floatingId, Long applicationId, String status,
            Integer currentFlag, Long departmentId, String regionCode) {
        String permitNo = "JZP-" + personId + "-" + (currentFlag == null ? "H" : "C");
        jdbc.update("""
                INSERT INTO residence_permit (
                    permit_no, person_id, floating_id, source_application_id, issue_region_code,
                    issuing_department_id, issuing_authority, issue_date, valid_from, valid_until,
                    status, current_flag, version
                ) VALUES (?, ?, ?, ?, ?, ?, '公安局', '2025-06-01', '2025-06-01', '2026-06-01', ?, ?, 0)
                """, permitNo, personId, floatingId, applicationId, regionCode, departmentId, status, currentFlag);
    }
}
