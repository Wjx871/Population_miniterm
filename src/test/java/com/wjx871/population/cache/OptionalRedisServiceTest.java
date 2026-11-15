package com.wjx871.population.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@Tag("unit")
class OptionalRedisServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final RedisProperties properties = new RedisProperties();
    private final ObjectMapper json = new ObjectMapper();
    private final OptionalRedisService service = new OptionalRedisService(redis, json, properties);

    @Test void disabledReadsDoNotContactRedisOrCountAsMisses() {
        assertThat(service.get("x", new TypeReference<List<String>>() {})).isEmpty();
        assertThat(service.snapshot().misses()).isZero();
        verifyNoInteractions(redis);
    }

    @Test void distinguishesHitsMissesAndErrorsAcrossBothReadOverloads() {
        properties.setEnabled(true);
        @SuppressWarnings("unchecked") ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("hit")).thenReturn("[\"ok\"]");
        when(values.get("bad")).thenReturn("not-json");
        when(values.get("down")).thenThrow(new IllegalStateException("test offline"));
        var type = new TypeReference<List<String>>() {};
        assertThat(service.get("hit", type)).contains(List.of("ok"));
        assertThat(service.get("missing", json.getTypeFactory().constructType(String.class))).isEmpty();
        assertThat(service.get("bad", type)).isEmpty();
        assertThat(service.get("down", type)).isEmpty();
        assertThat(service.snapshot()).isEqualTo(new OptionalRedisService.CacheSnapshot(1, 1, 2, 0, 0));
    }

    @Test void healthProbeClosesItsConnectionEvenOnFailure() {
        properties.setEnabled(true);
        RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(redis.getConnectionFactory()).thenReturn(factory);
        when(factory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenThrow(new IllegalStateException("offline"));
        assertThat(service.ping()).isFalse();
        verify(connection).close();
    }

    @Test void invalidationScansInBoundedBatchesAndClosesCursor() {
        properties.setEnabled(true);
        @SuppressWarnings("unchecked") Cursor<String> cursor = mock(Cursor.class);
        when(redis.scan(any(ScanOptions.class))).thenReturn(cursor);
        var keys = java.util.stream.IntStream.range(0, 130).mapToObj(i -> "region:" + i).toList();
        var iterator = keys.iterator();
        when(cursor.hasNext()).thenAnswer(i -> iterator.hasNext());
        when(cursor.next()).thenAnswer(i -> iterator.next());
        var sizes = new java.util.ArrayList<Integer>();
        when(redis.delete(anyCollection())).thenAnswer(i -> {
            sizes.add(((java.util.Collection<?>) i.getArgument(0)).size());
            return 1L;
        });
        service.evictPattern("region:*");
        assertThat(sizes).containsExactly(128, 2);
        verify(redis, never()).keys(anyString());
        verify(cursor).close();
    }
}
