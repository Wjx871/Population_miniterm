package com.wjx871.population.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.data.redis.core.StringRedisTemplate;

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
}
