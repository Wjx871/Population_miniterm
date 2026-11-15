package com.wjx871.population.cache;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.wjx871.population.dashboard.*;
import com.wjx871.population.security.*;
import com.wjx871.population.stats.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("unit")
class StatisticsCacheScopeTest {
    private final DashboardService dashboard = mock(DashboardService.class);
    private final OptionalRedisService cache = mock(OptionalRedisService.class);
    private final Map<String, Object> stored = new HashMap<>();
    private final UnifiedStatisticsService service = new UnifiedStatisticsService(dashboard,
            mock(StatisticsMapper.class), cache, new RedisProperties());

    @BeforeEach @SuppressWarnings({"unchecked", "rawtypes"}) void setup() {
        when(cache.key(anyString())).thenAnswer(i -> i.getArgument(0));
        when(cache.get(anyString(), any(TypeReference.class))).thenAnswer(i -> Optional.ofNullable(stored.get(i.getArgument(0))));
        doAnswer(i -> { stored.put(i.getArgument(0), i.getArgument(1)); return null; }).when(cache).put(anyString(), any(), any());
        when(dashboard.overview(30, 30)).thenAnswer(i -> {
            var view = new DashboardOverviewView();
            view.setRegisteredPopulation(CurrentUserContext.requireUser().userId());
            return view;
        });
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void selfScopedUsersCannotReuseEachOthersCachedStatistics() {
        login(101L, "ROLE", DataScope.SELF);
        assertThat(service.overview(30, 30).getRegisteredPopulation()).isEqualTo(101);
        login(202L, "ROLE", DataScope.SELF);
        assertThat(service.overview(30, 30).getRegisteredPopulation()).isEqualTo(202);
        login(101L, "ROLE", DataScope.SELF);
        assertThat(service.overview(30, 30).getRegisteredPopulation()).isEqualTo(101);
        verify(dashboard, times(2)).overview(30, 30);
    }
    @Test void rolesWithDifferentDashboardScopeRulesCannotShareDepartmentCache() {
        login(101L, "QUERY_VIEWER", DataScope.DEPARTMENT);
        assertThat(service.overview(30, 30).getRegisteredPopulation()).isEqualTo(101);
        login(202L, "OTHER_ROLE", DataScope.DEPARTMENT);
        assertThat(service.overview(30, 30).getRegisteredPopulation()).isEqualTo(202);
    }
    private void login(Long id, String role, DataScope scope) {
        var user = new AuthenticatedUser(id, "cache-user", "", "Fixture", "ENABLED", 1L, role, role,
                RoleLevel.L1, scope, "ENABLED", 1L, "Dept", "110000", List.of("statistics:view"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
