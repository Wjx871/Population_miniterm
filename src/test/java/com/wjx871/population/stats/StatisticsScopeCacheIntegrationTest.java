package com.wjx871.population.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wjx871.population.auth.AuthService;
import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.cache.RedisProperties;
import com.wjx871.population.dashboard.DashboardChartsView;
import com.wjx871.population.dashboard.DashboardOverviewView;
import com.wjx871.population.dashboard.DashboardService;
import com.wjx871.population.dashboard.RegionCountView;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.JwtService;
import com.wjx871.population.security.RoleLevel;
import com.wjx871.population.support.ControllableRedisTestConfiguration;
import com.wjx871.population.support.ControllableRedisTestConfiguration.Store;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.mybatis.spring.SqlSessionTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** Real scoped SQL and JSON caching; only the Redis transport is replaced. */
@SpringBootTest(properties = "population.redis.enabled=true")
@AutoConfigureMockMvc
@Import(ControllableRedisTestConfiguration.class)
@Transactional
class StatisticsScopeCacheIntegrationTest {
    private static final long HTTP_FIXTURE_ID = 99521L;
    private static final String HTTP_USERNAME = "s5a2-statistics-http";
    @Autowired JdbcTemplate jdbc;
    @Autowired UnifiedStatisticsService statistics;
    @Autowired DashboardService dashboard;
    @Autowired StatisticsMapper mapper;
    @Autowired OptionalRedisService cache;
    @Autowired RedisProperties redisProperties;
    @Autowired Store store;
    @Autowired AuthService auth;
    @Autowired JwtService jwt;
    @Autowired MockMvc mvc;
    @Autowired SqlSessionTemplate sqlSession;

    private boolean originalEnabled;

    @BeforeEach
    void seedDifferentScopeResults() {
        originalEnabled = redisProperties.isEnabled();
        redisProperties.setEnabled(true);
        store.reset();
        SecurityContextHolder.clearContext();
        seed(2L, 2L, "110105");
        seed(6L, 4L, "110106");
        seed(6L, 4L, "110106");
        seed(7L, 5L, "110105001");
        seed(7L, 5L, "110105001");
        seed(7L, 5L, "110105001");
    }

    @AfterEach
    void restoreMutableState() {
        SecurityContextHolder.clearContext();
        redisProperties.setEnabled(originalEnabled);
        store.reset();
    }

    @Test
    void selfUsersHaveDifferentRealResultsAndIndependentCachedPayloads() {
        authenticate(DataScope.SELF, 2L, 2L, "110105", List.of());
        assertResult(1L, 0L, Map.of("110105", 1L));
        authenticate(DataScope.SELF, 6L, 4L, "110106", List.of());
        assertResult(2L, 0L, Map.of("110106", 2L));
        assertThat(store.puts.get()).isEqualTo(4);
        authenticate(DataScope.SELF, 2L, 2L, "110105", List.of());
        assertResult(1L, 0L, Map.of("110105", 1L));
        assertThat(store.puts.get()).isEqualTo(4);
    }

    @Test
    void sameUserMovingBetweenRegionsCannotReadPreviousRegionPayload() {
        authenticate(DataScope.REGION, 2L, 2L, "110105", List.of("110105"));
        assertResult(1L, 1L, Map.of("110105", 1L));
        authenticate(DataScope.REGION, 2L, 2L, "110106", List.of("110106"));
        assertResult(2L, 2L, Map.of("110106", 2L));
        assertThat(store.puts.get()).isEqualTo(4);
        authenticate(DataScope.REGION, 2L, 2L, "110105", List.of("110105"));
        assertResult(1L, 1L, Map.of("110105", 1L));
        assertThat(store.puts.get()).isEqualTo(4);
    }

    @Test
    void enabledDescendantChangesAreReloadedEvenWhenUserAndRootStayTheSame() {
        jdbc.update("UPDATE sys_department SET region_code='110105' WHERE department_id=2");
        sqlSession.clearCache();
        authenticate(auth.loadUser("population"));
        assertResult(4L, 4L, Map.of("110105", 1L, "110105001", 3L));
        jdbc.update("UPDATE admin_region SET status='DISABLED' WHERE region_code='110105001'");
        sqlSession.clearCache();
        AuthenticatedUser reduced = auth.loadUser("population");
        assertThat(reduced.regionCode()).isEqualTo("110105");
        assertThat(reduced.regionCodes()).containsExactly("110105");
        authenticate(reduced);
        assertResult(1L, 1L, Map.of("110105", 1L));
        assertThat(store.puts.get()).isEqualTo(4);
        jdbc.update("UPDATE admin_region SET status='ENABLED' WHERE region_code='110105001'");
        sqlSession.clearCache();
        authenticate(auth.loadUser("population"));
        assertResult(4L, 4L, Map.of("110105", 1L, "110105001", 3L));
        assertThat(store.puts.get()).isEqualTo(4);
    }

    @Test
    void sameUserChangingDepartmentDoesNotReusePreviousDepartmentsPayload() {
        authenticate(DataScope.DEPARTMENT, 2L, 2L, "110105", List.of());
        assertResult(1L, 1L, Map.of("110105", 1L));
        authenticate(DataScope.DEPARTMENT, 2L, 4L, "110105", List.of());
        assertResult(2L, 2L, Map.of("110106", 2L));
        assertThat(store.puts.get()).isEqualTo(4);
    }

    @Test
    void allAndLimitedScopesForSameUserHaveIndependentRealPayloads() {
        authenticate(DataScope.ALL, 2L, 2L, "110105", List.of("110105"));
        assertResult(6L, 6L, Map.of("110105", 1L, "110106", 2L, "110105001", 3L));
        authenticate(DataScope.REGION, 2L, 2L, "110105", List.of("110105"));
        assertResult(1L, 1L, Map.of("110105", 1L));
        authenticate(DataScope.SELF, 2L, 2L, "110105", List.of("110105"));
        assertResult(1L, 0L, Map.of("110105", 1L));
        assertThat(store.puts.get()).isEqualTo(6);
    }

    @Test
    void identicalScopeActuallyHitsSerializedOverviewAndCharts() {
        authenticate(DataScope.SELF, 2L, 2L, "110105", List.of());
        DashboardOverviewView first = statistics.overview(30, 30);
        DashboardChartsView firstCharts = statistics.charts(30, 8);
        assertThat(first.getRegisteredPopulation()).isEqualTo(1L);
        assertThat(regionCounts(firstCharts)).containsExactlyInAnyOrderEntriesOf(Map.of("110105", 1L));
        assertThat(store.values).hasSize(2);
        assertThat(store.values.values()).allSatisfy(json -> assertThat(json).startsWith("{"));
        int reads = store.gets.get();
        int writes = store.puts.get();
        seed(2L, 2L, "110105");

        // The uncached production service and mapper now see 2, while the same scope hits old JSON.
        assertThat(dashboard.overview(30, 30).getRegisteredPopulation()).isEqualTo(2L);
        assertThat(mapper.countActivePersons(DataScopeCriteria.current())).isEqualTo(2L);
        DashboardOverviewView hit = statistics.overview(30, 30);
        DashboardChartsView chartsHit = statistics.charts(30, 8);
        assertThat(hit).isNotSameAs(first);
        assertThat(hit).usingRecursiveComparison().isEqualTo(first);
        assertThat(chartsHit).isNotSameAs(firstCharts);
        assertThat(chartsHit).usingRecursiveComparison().isEqualTo(firstCharts);
        assertThat(store.gets.get()).isEqualTo(reads + 2);
        assertThat(store.puts.get()).isEqualTo(writes);
    }

    @Test
    void redisReadAndWriteFailuresRequerySqlUsingEachCurrentScope() {
        authenticate(DataScope.SELF, 2L, 2L, "110105", List.of());
        assertResult(1L, 0L, Map.of("110105", 1L));
        authenticate(DataScope.SELF, 6L, 4L, "110106", List.of());
        assertResult(2L, 0L, Map.of("110106", 2L));
        Map<String, String> oldPayloads = Map.copyOf(store.values);
        seed(2L, 2L, "110105");
        seed(6L, 4L, "110106");
        store.failGet = true;
        store.failSet = true;
        int reads = store.gets.get();
        int writes = store.puts.get();

        authenticate(DataScope.SELF, 2L, 2L, "110105", List.of());
        assertResult(2L, 0L, Map.of("110105", 2L));
        authenticate(DataScope.SELF, 6L, 4L, "110106", List.of());
        assertResult(3L, 0L, Map.of("110106", 3L));
        assertThat(store.gets.get()).isEqualTo(reads + 4);
        assertThat(store.puts.get()).isEqualTo(writes + 4);
        assertThat(store.values).containsExactlyInAnyOrderEntriesOf(oldPayloads);
    }

    @Test
    void enabledAndDisabledCacheReturnSameScopedSqlResults() {
        for (DataScope scope : DataScope.values()) {
            authenticate(scope, 2L, 2L, "110105", List.of("110105"));
            long expected = scope == DataScope.ALL ? 6L : 1L;
            long households = scope == DataScope.SELF ? 0L : expected;
            Map<String, Long> regions = scope == DataScope.ALL
                    ? Map.of("110105", 1L, "110106", 2L, "110105001", 3L) : Map.of("110105", 1L);
            redisProperties.setEnabled(true);
            assertThat(cache.enabled()).isTrue();
            assertResult(expected, households, regions);
            int reads = store.gets.get();
            int writes = store.puts.get();
            redisProperties.setEnabled(false);
            assertThat(cache.enabled()).isFalse();
            assertResult(expected, households, regions);
            assertThat(store.gets.get()).isEqualTo(reads);
            assertThat(store.puts.get()).isEqualTo(writes);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "999999"})
    void sameJwtWithEmptyOrInvalidCurrentRegionIsForbiddenBeforeAnyCachedRead(String invalidRegion)
            throws Exception {
        String token = warmScopedStatisticsJwt();
        jdbc.update("UPDATE sys_department SET region_code=? WHERE department_id=?", invalidRegion, HTTP_FIXTURE_ID);
        sqlSession.clearCache();
        assertThat(auth.loadUser(HTTP_USERNAME).regionCodes()).isEmpty();
        assertForbiddenWithoutCacheRead(token);
    }

    @Test
    void sameJwtWithDisabledCurrentRegionIsForbiddenBeforeAnyCachedRead() throws Exception {
        String token = warmScopedStatisticsJwt();
        jdbc.update("UPDATE admin_region SET status='DISABLED' WHERE region_code='110105'");
        sqlSession.clearCache();
        assertThat(auth.loadUser(HTTP_USERNAME).regionCodes()).isEmpty();
        assertForbiddenWithoutCacheRead(token);
    }

    private String warmScopedStatisticsJwt() throws Exception {
        // The shared population demo account has no statistics:view permission. Give this
        // transaction-local REGION account an explicit real grant without changing demo RBAC.
        jdbc.update("INSERT INTO sys_department(department_id,department_code,department_name,region_code,status,sort_no) "
                + "VALUES(?,'S5A2_STATS_HTTP','Statistics HTTP fixture','110105','ENABLED',1)", HTTP_FIXTURE_ID);
        jdbc.update("INSERT INTO sys_role(role_id,role_code,role_name,role_level,data_scope,status) "
                + "VALUES(?,'S5A2_STATS_HTTP','Statistics HTTP fixture','L1','REGION','ENABLED')", HTTP_FIXTURE_ID);
        jdbc.update("INSERT INTO sys_permission(permission_id,permission_code,permission_name,module_name,permission_type,status) "
                + "SELECT ?,'statistics:view','Statistics fixture permission','STATISTICS','API','ENABLED' "
                + "WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code='statistics:view')", HTTP_FIXTURE_ID);
        jdbc.update("INSERT INTO sys_role_permission(role_id,permission_id) "
                + "SELECT ?,permission_id FROM sys_permission WHERE permission_code='statistics:view'", HTTP_FIXTURE_ID);
        jdbc.update("INSERT INTO sys_user(user_id,username,password_hash,role_id,department_id,real_name,status) "
                + "VALUES(?,?,'unused-test-password',?,?,'Statistics HTTP fixture','ENABLED')",
                HTTP_FIXTURE_ID, HTTP_USERNAME, HTTP_FIXTURE_ID, HTTP_FIXTURE_ID);
        sqlSession.clearCache();
        AuthenticatedUser realUser = auth.loadUser(HTTP_USERNAME);
        assertThat(realUser.dataScope()).isEqualTo(DataScope.REGION);
        assertThat(realUser.permissions()).contains("statistics:view");
        assertThat(realUser.regionCodes()).contains("110105", "110105001");
        String token = jwt.createToken(realUser);
        mvc.perform(get("/api/statistics/overview").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.registeredPopulation").value(4));
        mvc.perform(get("/api/statistics/region-distribution").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2));
        assertThat(store.values).hasSize(2);
        return token;
    }

    private void assertForbiddenWithoutCacheRead(String token) throws Exception {
        assertThat(auth.loadUser(HTTP_USERNAME).permissions()).contains("statistics:view");
        int reads = store.gets.get();
        int writes = store.puts.get();
        Map<String, String> payloads = Map.copyOf(store.values);
        for (String endpoint : List.of("overview", "region-distribution")) {
            mvc.perform(get("/api/statistics/" + endpoint).header("Authorization", "Bearer " + token))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(403))
                    .andExpect(jsonPath("$.message").value("用户区划数据范围无法验证"));
        }
        assertThat(store.gets.get()).isEqualTo(reads);
        assertThat(store.puts.get()).isEqualTo(writes);
        assertThat(store.values).containsExactlyInAnyOrderEntriesOf(payloads);
    }

    private void assertResult(long population, long households, Map<String, Long> expectedRegions) {
        DashboardOverviewView overview = statistics.overview(30, 30);
        assertThat(overview.getRegisteredPopulation()).isEqualTo(population);
        assertThat(overview.getKeyBusiness().getActiveKeyPopulation()).isEqualTo(population);
        assertThat(regionCounts(statistics.charts(30, 8)))
                .containsExactlyInAnyOrderEntriesOf(expectedRegions);
        assertThat(statistics.keyPopulation()).containsEntry("active", population);
        assertThat(statistics.householdDistribution()).singleElement()
                .satisfies(row -> assertThat(row.getValue()).isEqualTo(households));
        assertThat(mapper.countActivePersons(DataScopeCriteria.current())).isEqualTo(population);
    }

    private Map<String, Long> regionCounts(DashboardChartsView charts) {
        return charts.getRegisteredPopulationByRegion().stream()
                .collect(java.util.stream.Collectors.toMap(RegionCountView::getRegionCode, RegionCountView::getValue));
    }

    private void authenticate(DataScope scope, long userId, long departmentId, String regionCode,
            List<String> regionCodes) {
        authenticate(new AuthenticatedUser(userId, "scope-cache-" + userId, "", "Scope cache fixture",
                "ENABLED", 1L, "STATISTICS_SCOPE_CACHE", "Statistics scope cache", RoleLevel.L1, scope,
                "ENABLED", departmentId, "Scope department", regionCode, List.of("statistics:view"), regionCodes));
    }

    private void authenticate(AuthenticatedUser user) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    private void seed(long applicant, long department, String region) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String identity = "S5-" + suffix;
        String householdNo = "S5-H-" + suffix;
        String applicationNo = "S5-A-" + suffix;
        LocalDate today = LocalDate.now();
        jdbc.update("INSERT INTO person(name,gender,id_card,birth_date,current_address,status,current_status_code) "
                + "VALUES(?,'男',?,?,'scope cache address','正常','REGISTERED')",
                "scope cache " + suffix, identity, LocalDate.of(2000, 1, 1));
        long person = jdbc.queryForObject("SELECT person_id FROM person WHERE id_card=?", Long.class, identity);
        jdbc.update("INSERT INTO household(household_no,head_person_id,address,region_code,household_type,"
                + "establish_date,status,version) VALUES(?,?,'scope cache household',?,'FAMILY',?,'ACTIVE',0)",
                householdNo, person, region, today);
        long household = jdbc.queryForObject("SELECT household_id FROM household WHERE household_no=?",
                Long.class, householdNo);
        jdbc.update("INSERT INTO residence(person_id,household_id,registered_address,region_code,register_type_code,"
                + "register_date,start_date,status,created_by,version) "
                + "VALUES(?,?,'scope cache residence',?,'OTHER',?,?,'ACTIVE',?,0)",
                person, household, region, today, today, applicant);
        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,"
                + "applicant_department_id,applicant_region_code,target_person_id,target_household_id,status,reason,version) "
                + "VALUES(?,'STATISTICS_AUDIT','Scope cache fixture',?,?,?,?,?,'COMPLETED','scope cache fixture',0)",
                applicationNo, applicant, department, region, person, household);
        jdbc.update("INSERT INTO key_population(person_id,key_type,register_date,status,version) "
                + "VALUES(?,'S5_SCOPE_CACHE',?,'ACTIVE',0)", person, today);
        // JdbcTemplate fixture mutations must not leave MyBatis's transaction-local SQL cache stale.
        sqlSession.clearCache();
    }
}
