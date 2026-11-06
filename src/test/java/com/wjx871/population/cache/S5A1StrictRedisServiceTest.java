package com.wjx871.population.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.PermissionDeniedDataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Tag("unit")
class S5A1StrictRedisServiceTest {
    @Test void confirmedPresenceAndAbsenceRemainDistinct() {
        Fixture f = new Fixture();
        when(f.template.hasKey("key")).thenReturn(true, false);
        assertThat(f.service.strictContains("key")).isTrue();
        assertThat(f.service.strictContains("key")).isFalse();
    }

    @Test void nullReadIsUnavailableNotAbsent() {
        Fixture f = new Fixture();
        when(f.template.hasKey("key")).thenReturn(null);
        assertThatThrownBy(() -> f.service.strictContains("key"))
                .isInstanceOf(StrictRedisException.class).extracting("category").isEqualTo("UNCONFIRMED");
    }

    @Test void connectionFailureIsNotSwallowed() {
        assertReadFailure(new RedisConnectionFailureException("redis://user:synthetic-password@internal"), "CONNECTION");
    }

    @Test void timeoutIsNotSwallowed() {
        assertReadFailure(new java.util.concurrent.CompletionException(new java.util.concurrent.TimeoutException("synthetic-timeout")), "TIMEOUT");
    }

    @Test void authenticationFailureIsNotSwallowed() {
        assertReadFailure(new PermissionDeniedDataAccessException("synthetic-password", null), "AUTHENTICATION");
    }

    @Test void disabledStrictReadAndWriteFailWithoutIo() {
        Fixture f = new Fixture();
        f.properties.setEnabled(false);
        assertThatThrownBy(() -> f.service.strictContains("key")).isInstanceOf(StrictRedisException.class);
        assertThatThrownBy(() -> f.service.strictPutMarker("key", Instant.now().plusSeconds(10)))
                .isInstanceOf(StrictRedisException.class);
        verify(f.template, never()).hasKey(any());
        verify(f.template, never()).execute(any(RedisCallback.class));
    }

    @Test void writeUsesOneAtomicAbsoluteExpiryAndConstantMarker() {
        Fixture f = new Fixture();
        Instant expiry = Instant.parse("2030-01-01T00:00:00.123Z");
        f.service.strictPutMarker("population:security:jwt:blacklist:jti", expiry);
        ArgumentCaptor<Expiration> expiration = ArgumentCaptor.forClass(Expiration.class);
        verify(f.commands).set(eq("population:security:jwt:blacklist:jti".getBytes(StandardCharsets.UTF_8)),
                eq("1".getBytes(StandardCharsets.UTF_8)), expiration.capture(), eq(SetOption.UPSERT));
        assertThat(expiration.getValue().isUnixTimestamp()).isTrue();
        assertThat(expiration.getValue().getExpirationTimeInMilliseconds()).isEqualTo(expiry.toEpochMilli());
        assertThat(expiration.getValue().getTimeUnit()).isEqualTo(TimeUnit.MILLISECONDS);
        verify(f.template, times(1)).execute(any(RedisCallback.class));
    }

    @Test void negativeWriteAcknowledgementFails() {
        Fixture f = new Fixture();
        when(f.commands.set(any(), any(), any(), any())).thenReturn(false);
        assertThatThrownBy(() -> f.service.strictPutMarker("key", Instant.now().plusSeconds(10)))
                .isInstanceOf(StrictRedisException.class).extracting("category").isEqualTo("UNCONFIRMED");
    }

    @Test void nullWriteAcknowledgementFails() {
        Fixture f = new Fixture();
        when(f.commands.set(any(), any(), any(), any())).thenReturn(null);
        assertThatThrownBy(() -> f.service.strictPutMarker("key", Instant.now().plusSeconds(10)))
                .isInstanceOf(StrictRedisException.class);
    }

    @Test void writeConnectionFailurePreservesCauseButSafeMessage() {
        Fixture f = new Fixture();
        RuntimeException sensitive = new RedisConnectionFailureException("synthetic-password");
        when(f.commands.set(any(), any(), any(), any())).thenThrow(sensitive);
        assertThatThrownBy(() -> f.service.strictPutMarker("key", Instant.now().plusSeconds(10)))
                .isInstanceOf(StrictRedisException.class).hasCause(sensitive)
                .hasMessageNotContaining("synthetic-password");
    }

    @Test void writeTimeoutFails() {
        Fixture f = new Fixture();
        when(f.commands.set(any(), any(), any(), any())).thenThrow(new java.util.concurrent.CompletionException(new java.util.concurrent.TimeoutException()));
        assertThatThrownBy(() -> f.service.strictPutMarker("key", Instant.now().plusSeconds(10)))
                .isInstanceOf(StrictRedisException.class).extracting("category").isEqualTo("TIMEOUT");
    }

    @Test void writeAuthenticationFailureFails() {
        Fixture f = new Fixture();
        when(f.commands.set(any(), any(), any(), any())).thenThrow(new PermissionDeniedDataAccessException("synthetic-password", null));
        assertThatThrownBy(() -> f.service.strictPutMarker("key", Instant.now().plusSeconds(10)))
                .isInstanceOf(StrictRedisException.class).extracting("category").isEqualTo("AUTHENTICATION");
    }

    @Test void ordinaryCachesStillDegradeOnRedisFailure() {
        Fixture f = new Fixture();
        when(f.template.hasKey("key")).thenThrow(new RedisConnectionFailureException("sensitive"));
        assertThat(f.service.contains("key")).isFalse();
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(f.template.opsForValue()).thenReturn(values);
        doThrow(new RedisConnectionFailureException("sensitive")).when(values).set(eq("key"), eq("1"), any(Duration.class));
        f.service.putMarker("key", Duration.ofSeconds(10));
    }

    private void assertReadFailure(RuntimeException cause, String category) {
        Fixture f = new Fixture();
        when(f.template.hasKey("key")).thenThrow(cause);
        assertThatThrownBy(() -> f.service.strictContains("key"))
                .isInstanceOf(StrictRedisException.class).hasCause(cause)
                .hasMessageNotContaining("synthetic").extracting("category").isEqualTo(category);
    }

    private static class Fixture {
        final StringRedisTemplate template = mock(StringRedisTemplate.class);
        final RedisConnection connection = mock(RedisConnection.class);
        final RedisStringCommands commands = mock(RedisStringCommands.class);
        final RedisProperties properties = new RedisProperties();
        final OptionalRedisService service = new OptionalRedisService(template, new ObjectMapper(), properties);
        Fixture() {
            properties.setEnabled(true);
            when(template.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
            when(connection.stringCommands()).thenReturn(commands);
            when(commands.set(any(), any(), any(), any())).thenReturn(true);
            when(template.execute(any(RedisCallback.class))).thenAnswer(invocation ->
                    ((RedisCallback<?>) invocation.getArgument(0)).doInRedis(connection));
        }
    }
}
