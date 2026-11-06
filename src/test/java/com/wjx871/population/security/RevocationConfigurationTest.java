package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wjx871.population.cache.OptionalRedisService;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Tag("unit")
class RevocationConfigurationTest {
    private static final String PREFIX = "security.jwt.revocation.";
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(ConfigurationUnderTest.class)
            .withBean(OptionalRedisService.class, () -> client(true));

    @Test
    void defaultsBindToLocalCompatibilityWithBoundedCleanup() {
        runner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(TokenRevocationService.class);
            RevocationProperties properties = context.getBean(RevocationProperties.class);
            assertThat(properties.getMode()).isEqualTo(RevocationProperties.Mode.SINGLE_INSTANCE_LOCAL);
            assertThat(properties.getCleanupInterval()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getCleanupBatchSize()).isEqualTo(256);
            assertThat(properties.isAllowProductionSingleInstance()).isFalse();
        });
    }

    @Test
    void strictModeStartsWithRedisEnabledAndBindsCleanupConfiguration() {
        runner.withPropertyValues(PREFIX + "mode=SHARED_REDIS_REQUIRED",
                PREFIX + "cleanup-interval=2s", PREFIX + "cleanup-batch-size=17")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RevocationProperties properties = context.getBean(RevocationProperties.class);
                    assertThat(properties.getMode()).isEqualTo(RevocationProperties.Mode.SHARED_REDIS_REQUIRED);
                    assertThat(properties.getCleanupInterval()).isEqualTo(Duration.ofSeconds(2));
                    assertThat(properties.getCleanupBatchSize()).isEqualTo(17);
                });
    }

    @Test
    void strictModeRefusesStartupWhenRedisDisabled() {
        new ApplicationContextRunner().withUserConfiguration(ConfigurationUnderTest.class)
                .withBean(OptionalRedisService.class, () -> client(false))
                .withPropertyValues(PREFIX + "mode=SHARED_REDIS_REQUIRED")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("population.redis.enabled=true"));
    }

    @Test
    void localDevelopmentStartsWhenRedisDisabled() {
        new ApplicationContextRunner().withUserConfiguration(ConfigurationUnderTest.class)
                .withBean(OptionalRedisService.class, () -> client(false))
                .run(context -> assertThat(context).hasNotFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void productionProfilesRequireSharedModeByDefault(String profile) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class)
                        .hasStackTraceContaining("explicit audited single-instance exception"));
    }

    @Test
    void explicitProductionFlagAlsoRejectsLocalMode() {
        runner.withPropertyValues(PREFIX + "production=true").run(context -> assertThat(context).hasFailed()
                .getFailure().hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void falseProductionFlagCannotBypassProductionProfile() {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles("prod"))
                .withPropertyValues(PREFIX + "production=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production"})
    void sharedModeStartsUnderProductionProfilesWithoutLocalException(String profile) {
        runner.withInitializer(context -> context.getEnvironment().setActiveProfiles(profile))
                .withPropertyValues(PREFIX + "mode=SHARED_REDIS_REQUIRED")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void auditedProductionLocalExceptionStartsAndLogsApprovalIdentifier() {
        List<String> messages = new CopyOnWriteArrayList<>();
        AbstractAppender appender = new AbstractAppender("revocation-approval-test", null, null, false, null) {
            @Override public void append(LogEvent event) { messages.add(event.getMessage().getFormattedMessage()); }
        };
        org.apache.logging.log4j.core.Logger logger =
                (org.apache.logging.log4j.core.Logger) LogManager.getLogger(TokenRevocationService.class);
        org.apache.logging.log4j.Level previousLevel = logger.getLevel();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(org.apache.logging.log4j.Level.WARN);
        try {
            runner.withPropertyValues(PREFIX + "production=true", PREFIX + "allow-production-single-instance=true",
                    PREFIX + "production-single-instance-approval=SEC-2026/APPROVAL:17")
                    .run(context -> assertThat(context).hasNotFailed());
            assertThat(messages).anySatisfy(message -> assertThat(message)
                    .contains("event=REVOCATION_PRODUCTION_LOCAL_EXCEPTION", "mode=SINGLE_INSTANCE_LOCAL",
                            "approval=SEC-2026/APPROVAL:17"));
        } finally {
            logger.setLevel(previousLevel);
            logger.removeAppender(appender);
            appender.stop();
        }
    }

    @Test
    void approvalIdentifierAloneDoesNotAuthorizeProductionLocalMode() {
        runner.withPropertyValues(PREFIX + "production=true", PREFIX + "production-single-instance-approval=SEC-17")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "has space", "中文审批", "bad\nInjected=event", "bad?value"})
    void explicitExceptionRequiresSafeAuditIdentifier(String approval) {
        runner.withPropertyValues(PREFIX + "production=true", PREFIX + "allow-production-single-instance=true",
                PREFIX + "production-single-instance-approval=" + approval)
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasRootCauseInstanceOf(IllegalStateException.class));
    }

    @Test
    void missingAndOverlongApprovalAreRejected() {
        runner.withPropertyValues(PREFIX + "production=true", PREFIX + "allow-production-single-instance=true")
                .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues(PREFIX + "production=true", PREFIX + "allow-production-single-instance=true",
                PREFIX + "production-single-instance-approval=" + "a".repeat(129))
                .run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"mode=UNKNOWN", "cleanup-interval=not-a-duration", "cleanup-batch-size=not-a-number"})
    void invalidPropertyBindingFailsStartup(String property) {
        runner.withPropertyValues(PREFIX + property).run(context -> assertThat(context).hasFailed());
    }

    @ParameterizedTest
    @ValueSource(strings = {"cleanup-interval=0s", "cleanup-interval=-1s", "cleanup-interval=PT0.000000001S",
            "cleanup-batch-size=0", "cleanup-batch-size=-1"})
    void nonPositiveOrSubMillisecondCleanupConfigurationFailsStartup(String property) {
        runner.withPropertyValues(PREFIX + property).run(context -> assertThat(context).hasFailed().getFailure()
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("Invalid JWT revocation mode or cleanup configuration"));
    }

    private static OptionalRedisService client(boolean enabled) {
        OptionalRedisService redis = mock(OptionalRedisService.class);
        when(redis.enabled()).thenReturn(enabled);
        return redis;
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(RevocationProperties.class)
    static class ConfigurationUnderTest {
        @Bean
        TokenRevocationService tokenRevocationService(OptionalRedisService redis, RevocationProperties properties,
                Environment environment) {
            return new TokenRevocationService(redis, properties, Clock.systemUTC(), mock(RevocationEvents.class), environment);
        }
    }
}
