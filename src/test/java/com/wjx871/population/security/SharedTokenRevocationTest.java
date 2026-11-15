package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.common.BusinessException;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

@Tag("unit")
class SharedTokenRevocationTest {
    private final OptionalRedisService redis = mock(OptionalRedisService.class);
    private final JdbcTokenRevocationStore store = mock(JdbcTokenRevocationStore.class);
    private final TokenRevocationService service = new TokenRevocationService(redis, store);
    @BeforeEach void keys() { when(redis.key(anyString())).thenAnswer(i -> "population:" + i.getArgument(0)); }
    @Test void durableWritePrecedesRedisMarker() {
        Instant expires = Instant.now().plusSeconds(60);
        service.revoke("jti-1", expires);
        var order = inOrder(store, redis);
        order.verify(store).revoke("jti-1", expires);
        order.verify(redis).key("security:jwt:blacklist:jti-1");
        order.verify(redis).putMarker(eq("population:security:jwt:blacklist:jti-1"), any());
    }
    @Test void redisMissAlwaysChecksSharedStoreWithoutNegativeCaching() {
        when(store.isRevoked("x")).thenReturn(false, true);
        assertThat(service.isRevoked("x")).isFalse();
        assertThat(service.isRevoked("x")).isTrue();
        verify(store, times(2)).isRevoked("x");
    }
    @Test void failedDurableWriteDoesNotPublishSuccessMarker() {
        doThrow(new DataAccessResourceFailureException("offline")).when(store).revoke(anyString(), any());
        assertThatThrownBy(() -> service.revoke("x", Instant.now().plusSeconds(60)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verify(redis, never()).putMarker(anyString(), any());
    }
    @Test void unavailableStoreFailsClosed() {
        when(store.isRevoked("x")).thenThrow(new DataAccessResourceFailureException("offline"));
        assertThatThrownBy(() -> service.isRevoked("x"))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(service.isRevoked(null)).isTrue();
    }
}
