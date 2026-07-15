package com.wjx871.population.dashboard;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DashboardService {
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
        view.setBusinessScale(List.of(
                new NamedCountView("REGISTERED", "当前户籍人口", mapper.countRegisteredPopulation(scope)),
                new NamedCountView("FLOATING_ACTIVE", "在册流动人口", mapper.countActiveFloating(scope)),
                new NamedCountView("PERMIT_ACTIVE", "有效居住证", mapper.countActivePermits(scope))));
        view.setPermitStatusDistribution(mapper.permitStatusDistribution(scope));
        view.setRegisteredPopulationByRegion(mapper.registeredPopulationByRegion(scope, regionLimit));
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
}
