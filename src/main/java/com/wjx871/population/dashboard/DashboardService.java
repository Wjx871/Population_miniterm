package com.wjx871.population.dashboard;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private static final Logger log = LoggerFactory.getLogger(DashboardService.class);
    private static final String QUERY_VIEWER_ROLE = "QUERY_VIEWER";

    private final DashboardMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardOverviewView overview(int periodDays, int expiryDays) {
        DataScopeCriteria scope = scopeForDashboard();
        LocalDate today = LocalDate.now(clock);
        DashboardOverviewView view = new DashboardOverviewView();
        view.setGeneratedAt(LocalDateTime.now(clock));
        view.setPeriodDays(periodDays);
        view.setExpiryDays(expiryDays);
        view.setRegisteredPopulation(mapper.countRegisteredPopulation(scope));
        view.setActiveFloatingPopulation(mapper.countActiveFloating(scope));
        view.setActiveResidencePermits(mapper.countActivePermits(scope));
        view.setPendingApprovals(mapper.countPendingApprovals(scope));
        view.setExpiringResidencePermits(mapper.countExpiringPermits(today, today.plusDays(expiryDays), scope));
        view.setMigrationInPeriod(mapper.countMigrationsIn(today.minusDays(periodDays - 1L), today, scope));
        view.setMigrationOutPeriod(mapper.countMigrationsOut(today.minusDays(periodDays - 1L), today, scope));
        view.setPopulationStructure(populationStructure(today, scope));
        view.setKeyBusiness(keyBusiness(today, expiryDays, scope));
        log.info("[DASHBOARD DEBUG] overview() returning: populationStructure={}, keyBusiness={}, gender male={}, ageGroups size={}",
                view.getPopulationStructure() != null ? "EXISTS" : "NULL",
                view.getKeyBusiness() != null ? "EXISTS" : "NULL",
                view.getPopulationStructure() != null ? view.getPopulationStructure().getGender() : "N/A",
                view.getPopulationStructure() != null ? view.getPopulationStructure().getAgeGroups().size() : -1);
        return view;
    }

    @Transactional(readOnly = true)
    public DashboardChartsView charts(int days, int regionLimit) {
        DataScopeCriteria scope = scopeForDashboard();
        LocalDate today = LocalDate.now(clock);
        LocalDate from = today.minusDays(days - 1L);
        Map<LocalDate, Long> incoming = asMap(mapper.migrationInTrend(from, today, scope), true);
        Map<LocalDate, Long> outgoing = asMap(mapper.migrationOutTrend(from, today, scope), false);
        List<MigrationTrendPoint> trend = java.util.stream.IntStream.range(0, days)
                .mapToObj(index -> {
                    LocalDate date = from.plusDays(index);
                    return new MigrationTrendPoint(date, incoming.getOrDefault(date, 0L), outgoing.getOrDefault(date, 0L));
                }).toList();
        DashboardChartsView view = new DashboardChartsView();
        view.setGeneratedAt(LocalDateTime.now(clock));
        view.setMigrationTrend(trend);
        view.setBusinessScale(mapper.businessScale(from, today, scope));
        view.setApprovalStatusDistribution(mapper.approvalStatusDistribution(scope));
        view.setRegisteredPopulationByRegion(mapper.registeredPopulationByRegion(scope, regionLimit));
        view.setPopulationScaleTrend(populationScaleTrend(from, today, scope));
        return view;
    }

    /**
     * Dashboard KPIs describe system-wide master data (person, residence, residence_permit,
     * floating_population) and business flow counts; they are not "things I produced" reports.
     * For the read-only QUERY_VIEWER role we therefore widen the data scope to ALL so the
     * dashboard is not zeroed out by an inherited DEPARTMENT scope, while write APIs still
     * rely on the role's permission set for authorization. SYSTEM_ADMIN keeps ALL; other
     * roles (POPULATION_MANAGER, HOUSEHOLD_MANAGER, APPROVER) keep their original scope.
     */
    private DataScopeCriteria scopeForDashboard() {
        DataScopeCriteria scope = DataScopeCriteria.current();
        if (scope.dataScope() == DataScope.ALL) {
            return scope;
        }
        AuthenticatedUser user = CurrentUserContext.requireUser();
        if (QUERY_VIEWER_ROLE.equals(user.roleCode())) {
            return new DataScopeCriteria(DataScope.ALL, scope.userId(), scope.departmentId(), scope.regionCode());
        }
        return scope;
    }

    private Map<LocalDate, Long> asMap(List<MigrationTrendPoint> points, boolean incoming) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (MigrationTrendPoint point : points) {
            result.put(point.getDate(), incoming ? point.getInCount() : point.getOutCount());
        }
        return result;
    }

    private PopulationStructureView populationStructure(LocalDate today, DataScopeCriteria scope) {
        Map<String, Long> genderCounts = namedCountMap(mapper.registeredPopulationGender(scope));
        long male = genderCounts.getOrDefault("M", 0L) + genderCounts.getOrDefault("男", 0L);
        long female = genderCounts.getOrDefault("F", 0L) + genderCounts.getOrDefault("女", 0L);
        long total = male + female;

        PopulationStructureView view = new PopulationStructureView();
        view.setGender(new GenderDistributionView(percent(male, total), percent(female, total)));
        Map<String, Long> ageCounts = namedCountMap(mapper.registeredPopulationAgeGroups(today, scope));
        view.setAgeGroups(List.of(
                new NamedCountView("AGE_0_17", "0-17岁", ageCounts.getOrDefault("AGE_0_17", 0L)),
                new NamedCountView("AGE_18_29", "18-29岁", ageCounts.getOrDefault("AGE_18_29", 0L)),
                new NamedCountView("AGE_30_44", "30-44岁", ageCounts.getOrDefault("AGE_30_44", 0L)),
                new NamedCountView("AGE_45_59", "45-59岁", ageCounts.getOrDefault("AGE_45_59", 0L)),
                new NamedCountView("AGE_60_PLUS", "60岁及以上", ageCounts.getOrDefault("AGE_60_PLUS", 0L)),
                new NamedCountView("UNKNOWN", "出生日期缺失", ageCounts.getOrDefault("UNKNOWN", 0L))));
        return view;
    }

    private KeyBusinessView keyBusiness(LocalDate today, int expiryDays, DataScopeCriteria scope) {
        KeyBusinessView view = new KeyBusinessView();
        view.setActiveKeyPopulation(mapper.countActiveKeyPopulation(scope));
        view.setPendingCancellation(mapper.countPendingCancellation(scope));
        view.setExpiringResidencePermits(mapper.countExpiringPermits(today, today.plusDays(expiryDays), scope));
        view.setPendingSensitiveExport(mapper.countPendingSensitiveExport(scope));
        return view;
    }

    private List<PopulationScaleTrendPoint> populationScaleTrend(LocalDate from, LocalDate today,
            DataScopeCriteria scope) {
        Map<LocalDate, PopulationScaleTrendPoint> source = new HashMap<>();
        List<PopulationScaleTrendPoint> rows = mapper.populationScaleTrend(from, today, scope);
        if (rows != null) {
            for (PopulationScaleTrendPoint row : rows) {
                if (row != null && row.getDate() != null) {
                    source.put(row.getDate(), row);
                }
            }
        }
        List<PopulationScaleTrendPoint> result = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            PopulationScaleTrendPoint row = source.get(date);
            result.add(row == null ? new PopulationScaleTrendPoint(date, 0L, 0L, 0L) : row);
        }
        return result;
    }

    private Map<String, Long> namedCountMap(List<NamedCountView> rows) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (rows == null) {
            return result;
        }
        for (NamedCountView row : rows) {
            if (row != null && row.getCode() != null) {
                result.put(row.getCode(), row.getValue());
            }
        }
        return result;
    }

    private double percent(long value, long total) {
        if (total == 0L) {
            return 0D;
        }
        return Math.round(value * 1000D / total) / 10D;
    }
}
