package com.wjx871.population.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class OptionalRedisServiceTest {

    @Test
    void pingClosesConnectionAfterPong() {
        Fixture fixture = new Fixture(true);
        when(fixture.connection.ping()).thenReturn("PONG");

        assertThat(fixture.service.ping()).isTrue();

        verify(fixture.connection).close();
    }

    @Test
    void pingClosesConnectionWhenResponseIsNotPong() {
        Fixture fixture = new Fixture(true);
        when(fixture.connection.ping()).thenReturn("NOAUTH");

        assertThat(fixture.service.ping()).isFalse();

        verify(fixture.connection).close();
    }

    @Test
    void pingClosesConnectionWhenPingThrowsAndDegradesToFalse() {
        Fixture fixture = new Fixture(true);
        doThrow(new IllegalStateException("synthetic-sensitive-marker")).when(fixture.connection).ping();

        assertThat(fixture.service.ping()).isFalse();

        verify(fixture.connection).close();
    }

    @Test
    void repeatedPingsCloseEveryAcquiredConnection() {
        Fixture fixture = new Fixture(true);
        RedisConnection nextConnection = mock(RedisConnection.class);
        when(fixture.connection.ping()).thenReturn("PONG");
        when(nextConnection.ping()).thenReturn("PONG");
        when(fixture.factory.getConnection()).thenReturn(fixture.connection, nextConnection);

        assertThat(fixture.service.ping()).isTrue();
        assertThat(fixture.service.ping()).isTrue();

        verify(fixture.connection).close();
        verify(nextConnection).close();
    }

    @Test
    void disabledRedisDoesNotAccessConnectionFactory() {
        Fixture fixture = new Fixture(false);

        assertThat(fixture.service.ping()).isFalse();

        verify(fixture.redis, never()).getConnectionFactory();
    }

    @Test
    void unavailableFactoryDegradesToFalse() {
        Fixture fixture = new Fixture(true);
        when(fixture.factory.getConnection()).thenThrow(new IllegalStateException("connection unavailable"));

        assertThat(fixture.service.ping()).isFalse();

        verify(fixture.connection, never()).close();
    }

    @Test
    void missingConnectionFactoryDegradesToFalse() {
        Fixture fixture = new Fixture(true);
        when(fixture.redis.getConnectionFactory()).thenReturn(null);

        assertThat(fixture.service.ping()).isFalse();

        verify(fixture.factory, never()).getConnection();
        verify(fixture.connection, never()).close();
    }

    @Test
    void pingFailureLogDoesNotIncludeExceptionMessage() {
        Fixture fixture = new Fixture(true);
        doThrow(new IllegalStateException("synthetic-sensitive-marker")).when(fixture.connection).ping();
        Logger logger = (Logger) LogManager.getLogger(OptionalRedisService.class);
        Level previousLevel = logger.getLevel();
        List<String> messages = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("redis-ping-test", null, null, false) {
            @Override
            public void append(LogEvent event) {
                messages.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        try {
            assertThat(fixture.service.ping()).isFalse();
        } finally {
            logger.removeAppender(appender);
            logger.setLevel(previousLevel);
            appender.stop();
        }

        assertThat(messages).isNotEmpty()
                .allSatisfy(message -> assertThat(message).doesNotContain("synthetic-sensitive-marker"));
    }

    private static final class Fixture {
        private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
        private final RedisConnectionFactory factory = mock(RedisConnectionFactory.class);
        private final RedisConnection connection = mock(RedisConnection.class);
        private final RedisProperties properties = new RedisProperties();
        private final OptionalRedisService service;

        private Fixture(boolean enabled) {
            properties.setEnabled(enabled);
            when(redis.getConnectionFactory()).thenReturn(factory);
            when(factory.getConnection()).thenReturn(connection);
            service = new OptionalRedisService(redis, new ObjectMapper(), properties);
        }
    }

    private final RedisProperties properties = new RedisProperties();
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
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
