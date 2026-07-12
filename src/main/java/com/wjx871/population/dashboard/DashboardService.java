package com.wjx871.population.dashboard;

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
    private final DashboardMapper mapper;
    private final Clock clock;

    @Transactional(readOnly = true)
    public DashboardOverviewView overview(int periodDays, int expiryDays) {
        DataScopeCriteria scope = DataScopeCriteria.current();
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
        DataScopeCriteria scope = DataScopeCriteria.current();
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

    private Map<LocalDate, Long> asMap(List<MigrationTrendPoint> points, boolean incoming) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (MigrationTrendPoint point : points) {
            result.put(point.getDate(), incoming ? point.getInCount() : point.getOutCount());
        }
        return result;
    }
}
