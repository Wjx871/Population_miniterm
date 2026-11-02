package com.wjx871.population.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.cache.RedisProperties;
import com.wjx871.population.dashboard.DashboardOverviewView;
import com.wjx871.population.dashboard.DashboardService;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class UnifiedStatisticsScopeCacheTest {

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void selfUsersUseDifferentDashboardCacheKeys() {
        DashboardService dashboard = mock(DashboardService.class);
        StatisticsMapper mapper = mock(StatisticsMapper.class);
        OptionalRedisService cache = mock(OptionalRedisService.class);
        when(cache.get(anyString(), any(TypeReference.class))).thenReturn(Optional.empty());
        when(cache.key(anyString())).thenAnswer(invocation -> "population:" + invocation.getArgument(0));
        when(dashboard.overview(30, 30)).thenReturn(new DashboardOverviewView());
        UnifiedStatisticsService service = new UnifiedStatisticsService(dashboard, mapper, cache,
                new RedisProperties());

        authenticate(21L);
        service.overview(30, 30);
        authenticate(22L);
        service.overview(30, 30);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cache, times(2)).key(keys.capture());
        assertThat(keys.getAllValues()).doesNotHaveDuplicates();
        assertThat(keys.getAllValues().get(0)).contains(":21:");
        assertThat(keys.getAllValues().get(1)).contains(":22:");
    }

    private void authenticate(long userId) {
        AuthenticatedUser user = new AuthenticatedUser(userId, "statistics-self-" + userId, "", "Self user",
                "ENABLED", 1L, "SELF_TEST", "Self test", null, DataScope.SELF, "ENABLED", 2L,
                "Synthetic department", "110105", List.of("statistics:view"));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
