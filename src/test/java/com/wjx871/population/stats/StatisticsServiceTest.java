package com.wjx871.population.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@org.junit.jupiter.api.Tag("unit")
class StatisticsServiceTest {
    private final StatisticsMapper mapper = mock(StatisticsMapper.class);
    private final StatisticsService service = new StatisticsService(mapper);

    @BeforeEach
    void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(3L, "statistics-test", "", "Statistics test", "ENABLED",
                3L, "SYSTEM_ADMIN", "Admin", null, DataScope.ALL, "ENABLED", 3L, "System", "110000",
                List.of("statistics:view"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        when(mapper.countActivePersons(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(mapper.countFloatingPopulation(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(mapper.countKeyPopulation(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(mapper.countHouseholds(org.mockito.ArgumentMatchers.any())).thenReturn(0L);
        when(mapper.countKeyPopulationByType(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(mapper.countCertificateByStatus(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(mapper.countRecentMigrationIn(org.mockito.ArgumentMatchers.any(LocalDate.class), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(mapper.countRecentMigrationOut(org.mockito.ArgumentMatchers.any(LocalDate.class), org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
        when(mapper.selectRecentLogs(org.mockito.ArgumentMatchers.any())).thenReturn(List.of());
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void emptySummaryReportsRealZeroCounts() {
        Map<String, Object> summary = service.getSummary();

        assertThat(summary).containsEntry("residentCount", 0L)
                .containsEntry("floatingCount", 0L)
                .containsEntry("keyPopulationCount", 0L)
                .containsEntry("householdCount", 0L)
                .containsEntry("isMock", false);
        assertThat(summary.get("residentGrowth")).isNull();
        assertThat(summary.get("floatingGrowth")).isNull();
        assertThat(summary.get("keyPopulationGrowth")).isNull();
        assertThat(summary.get("householdGrowth")).isNull();
    }

    @Test
    void emptyChartsAndLogsDoNotInventRows() {
        Map<String, Object> charts = service.getChartData();
        @SuppressWarnings("unchecked")
        Map<String, Object> trend = (Map<String, Object>) charts.get("migrationTrend");
        @SuppressWarnings("unchecked")
        List<Long> inFlow = (List<Long>) trend.get("inFlow");
        @SuppressWarnings("unchecked")
        List<Long> outFlow = (List<Long>) trend.get("outFlow");

        assertThat(charts.get("keyPopulationDistribution")).isEqualTo(List.of());
        assertThat(charts.get("certificateStatus")).isEqualTo(List.of());
        assertThat(inFlow).containsOnly(0L).hasSize(7);
        assertThat(outFlow).containsOnly(0L).hasSize(7);
        assertThat(service.getRecentLogs()).isEmpty();
    }

    @Test
    void everyStatisticsQueryUsesTheAuthenticatedScope() {
        DataScopeCriteria scope = DataScopeCriteria.current();
        service.getSummary();
        service.getChartData();
        service.getRecentLogs();

        org.mockito.Mockito.verify(mapper).countActivePersons(scope);
        org.mockito.Mockito.verify(mapper).countFloatingPopulation(scope);
        org.mockito.Mockito.verify(mapper).countKeyPopulation(scope);
        org.mockito.Mockito.verify(mapper).countHouseholds(scope);
        org.mockito.Mockito.verify(mapper).countKeyPopulationByType(scope);
        org.mockito.Mockito.verify(mapper).countCertificateByStatus(scope);
        org.mockito.Mockito.verify(mapper).selectRecentLogs(scope);
    }
}
