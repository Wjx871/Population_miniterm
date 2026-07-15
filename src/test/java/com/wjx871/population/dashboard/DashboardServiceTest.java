package com.wjx871.population.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class DashboardServiceTest {
    private final DashboardMapper mapper = mock(DashboardMapper.class);
    private final DashboardService service = new DashboardService(mapper,
            Clock.fixed(Instant.parse("2026-07-12T00:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void authenticate() {
        AuthenticatedUser user = new AuthenticatedUser(7L, "reviewer", "", "Reviewer", "ENABLED", 1L,
                "ADMIN", "Admin", null, DataScope.ALL, "ENABLED", 10L, "Test", "110000", List.of("population:view"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void chartsZeroFillTrendAndExcludeHouseholdMetric() {
        when(mapper.migrationInTrend(any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of(new MigrationTrendPoint(LocalDate.of(2026, 7, 11), 3L, 0L)));
        when(mapper.migrationOutTrend(any(LocalDate.class), any(LocalDate.class), any()))
                .thenReturn(List.of(new MigrationTrendPoint(LocalDate.of(2026, 7, 12), 0L, 2L)));
        when(mapper.countRegisteredPopulation(any())).thenReturn(10L);
        when(mapper.countActiveFloating(any())).thenReturn(4L);
        when(mapper.countActivePermits(any())).thenReturn(6L);
        when(mapper.permitStatusDistribution(any())).thenReturn(List.of());
        when(mapper.registeredPopulationByRegion(any(), any(Integer.class))).thenReturn(List.of());

        DashboardChartsView result = service.charts(3, 10);

        assertThat(result.getMigrationTrend()).extracting(MigrationTrendPoint::getDate)
                .containsExactly(LocalDate.of(2026, 7, 10), LocalDate.of(2026, 7, 11), LocalDate.of(2026, 7, 12));
        assertThat(result.getMigrationTrend()).extracting(MigrationTrendPoint::getInCount).containsExactly(0L, 3L, 0L);
        assertThat(result.getMigrationTrend()).extracting(MigrationTrendPoint::getOutCount).containsExactly(0L, 0L, 2L);
        assertThat(result.getBusinessScale()).extracting(NamedCountView::getCode)
                .containsExactly("REGISTERED", "FLOATING_ACTIVE", "PERMIT_ACTIVE");
    }

    @Test
    void overviewEscalatesViewerDepartmentScopeToAll() {
        SecurityContextHolder.clearContext();
        AuthenticatedUser viewer = new AuthenticatedUser(8L, "viewer", "", "Viewer", "ENABLED", 1L,
                "QUERY_VIEWER", "Query Viewer", null, DataScope.DEPARTMENT, "ENABLED",
                20L, "QUERY", "110000", List.of("statistics:view"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(viewer, null, viewer.getAuthorities()));
        when(mapper.countRegisteredPopulation(any())).thenReturn(0L);
        when(mapper.countActiveFloating(any())).thenReturn(0L);
        when(mapper.countActivePermits(any())).thenReturn(0L);
        when(mapper.countPendingApprovals(any())).thenReturn(0L);
        when(mapper.countExpiringPermits(any(LocalDate.class), any(LocalDate.class), any())).thenReturn(0L);
        when(mapper.countMigrationsIn(any(LocalDate.class), any(LocalDate.class), any())).thenReturn(0L);
        when(mapper.countMigrationsOut(any(LocalDate.class), any(LocalDate.class), any())).thenReturn(0L);

        service.overview(7, 30);

        ArgumentCaptor<com.wjx871.population.security.DataScopeCriteria> captor =
                ArgumentCaptor.forClass(com.wjx871.population.security.DataScopeCriteria.class);
        org.mockito.Mockito.verify(mapper, org.mockito.Mockito.atLeastOnce())
                .countRegisteredPopulation(captor.capture());
        assertThat(captor.getAllValues())
                .allSatisfy(c -> assertThat(c.dataScope()).isEqualTo(DataScope.ALL));
    }
}
