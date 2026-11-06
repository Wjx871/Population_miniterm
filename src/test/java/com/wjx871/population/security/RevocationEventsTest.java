package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.cache.StrictRedisException;
import com.wjx871.population.s2.trace.S2TraceContext;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.ThreadContext;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@Tag("unit")
class RevocationEventsTest {
    @Test void failuresAreRateLimitedCountedAndNeverLogCredentialsKeysOrCause() {
        List<LogEvent> captured = new ArrayList<>();
        AbstractAppender appender = new AbstractAppender("revocation-events-test", null, null, false, null) {
            @Override public void append(LogEvent event) { captured.add(event.toImmutable()); }
        };
        Logger logger = (Logger) LogManager.getLogger(RevocationEvents.class);
        Level old = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
        RevocationEvents events = new RevocationEvents();
        OptionalRedisService redis = mock(OptionalRedisService.class);
        when(redis.key(anyString())).thenAnswer(call -> "population:" + call.getArgument(0));
        when(redis.strictContains(anyString())).thenThrow(new StrictRedisException("CONNECTION",
                new IllegalStateException("redis://secret-host password=secret-password JWT=eyJ.secret")));
        RevocationProperties properties = new RevocationProperties();
        properties.setMode(RevocationProperties.Mode.SHARED_REDIS_REQUIRED);
        TokenRevocationService service = new TokenRevocationService(redis, properties, Clock.systemUTC(), events, new MockEnvironment());
        try (S2TraceContext.Scope ignored = S2TraceContext.open("safe-trace-123")) {
            for (int i = 0; i < 3; i++) {
                assertThatThrownBy(() -> service.isRevoked("private-jti"))
                        .isInstanceOf(RevocationStoreUnavailableException.class)
                        .hasMessageNotContaining("secret-host").hasMessageNotContaining("secret-password")
                        .hasMessageNotContaining("private-jti").hasMessageNotContaining("eyJ.secret");
            }
            assertThat(events.count("REVOCATION_READ_UNAVAILABLE")).isEqualTo(3);
            assertThat(captured).hasSize(1);
            assertThat(captured.get(0).getThrown()).isNull();
            assertThat(captured.get(0).getMessage().getFormattedMessage())
                    .contains("event=REVOCATION_READ_UNAVAILABLE", "traceId=safe-trace-123",
                            "instanceId=", "operation=AUTHENTICATE", "mode=SHARED_REDIS_REQUIRED",
                            "errorCategory=CONNECTION", "elapsedMs=", "count=1")
                    .doesNotContain("private-jti", "blacklist", "secret-host", "secret-password", "eyJ.secret");
        } finally {
            logger.setLevel(old);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test void traceScopeRejectsUnsafeHeadersAndRestoresPreviousContext() {
        String previous = ThreadContext.get("traceId");
        try {
            ThreadContext.put("traceId", "outer-trace");
            try (S2TraceContext.Scope ignored = S2TraceContext.open("bad\r\nAuthorization: secret")) {
                assertThat(ThreadContext.get("traceId")).matches("[A-Za-z0-9._-]{1,64}");
                assertThat(ThreadContext.get("traceId")).doesNotContain("Authorization", "secret");
            }
            assertThat(ThreadContext.get("traceId")).isEqualTo("outer-trace");
        } finally {
            if (previous == null) ThreadContext.remove("traceId"); else ThreadContext.put("traceId", previous);
        }
    }
}
