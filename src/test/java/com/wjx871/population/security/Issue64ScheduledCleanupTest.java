package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.export.ExportConfiguration;
import java.time.*;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

class Issue64ScheduledCleanupTest {
    @Test void actualProjectSchedulingActivelyRotatesBoundedBatchesAndStops() throws Exception {
        MutableClock clock = new MutableClock();
        OptionalRedisService redis = mock(OptionalRedisService.class);
        when(redis.key(anyString())).thenAnswer(invocation -> invocation.getArgument(0));
        RevocationProperties properties = new RevocationProperties();
        properties.setCleanupBatchSize(2);
        TokenRevocationService service = spy(new TokenRevocationService(redis, properties, clock,
                mock(RevocationEvents.class), new MockEnvironment()));
        service.revoke("live-a", clock.instant().plusSeconds(60));
        service.revoke("live-b", clock.instant().plusSeconds(60));
        for (int i = 0; i < 3; i++) service.revoke("expired-" + i, clock.instant().plusSeconds(1));
        clock.now = clock.now.plusSeconds(1);
        List<Integer> counts = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> { Object result = invocation.callRealMethod(); counts.add(service.localEntryCount()); return result; })
                .when(service).cleanupExpired();
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setThreadNamePrefix("issue64-isolated-");
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", java.util.Map.of(
                    "security.jwt.revocation.cleanup-interval", "PT0.2S")));
            context.register(ExportConfiguration.class);
            context.registerBean("taskScheduler", ThreadPoolTaskScheduler.class, () -> scheduler);
            context.registerBean(TokenRevocationService.class, () -> service);
            context.refresh();
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8);
            while (counts.size() < 3 && System.nanoTime() < deadline) Thread.sleep(20);
            assertThat(counts).hasSizeGreaterThanOrEqualTo(3);
            assertThat(counts.subList(0, 3)).containsExactly(5, 3, 2);
            assertThat(service.isRevoked("live-a")).isTrue();
            assertThat(service.isRevoked("live-b")).isTrue();
        }
        assertThat(scheduler.getScheduledThreadPoolExecutor().isShutdown()).isTrue();
        assertThat(scheduler.getScheduledThreadPoolExecutor().awaitTermination(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
    }
    static class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-01-01T00:00:00Z");
        public Instant instant() { return now; }
        public ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
    }
}
