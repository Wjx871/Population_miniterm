package com.wjx871.population.ai.precheck;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "population.ai.precheck")
public class AiPrecheckProperties {
    private boolean enabled;
    private Path storageDir = Path.of("./data/private-ai-stage");
    private long maxMaterialSizeMb = 10;
    private Duration readyRetention = Duration.ofHours(24);
    private Duration completedRetention = Duration.ofHours(24);
    private Duration resolutionTokenTtl = Duration.ofMinutes(5);
    private boolean rabbitEnabled;
    private String exchange = "population.ai.precheck.exchange";
    private String queue = "population.ai.precheck.queue";
    private String routingKey = "population.ai.precheck.request";
    private String deadLetterExchange = "population.ai.precheck.dlx";
    private String deadLetterQueue = "population.ai.precheck.dlq";
    private String deadLetterRoutingKey = "population.ai.precheck.dead";
    private int consumerConcurrency = 2;
    private int prefetch = 1;
    private int queueCapacity = 1000;
    private int maxProcessingAttempts = 3;
    private List<Duration> backoff = List.of(Duration.ofSeconds(2), Duration.ofSeconds(10));
    private Duration processingTimeout = Duration.ofSeconds(125);
    private Duration workerLeaseDuration = Duration.ofSeconds(180);
    private Duration analysisConnectTimeout = Duration.ofSeconds(2);
    private Duration analysisReadTimeout = Duration.ofSeconds(120);
    private String analysisClient = "MOCK";
    private MockMode mockMode = MockMode.SUCCESS;
    private Duration mockDelay = Duration.ZERO;
    private int mockFailuresBeforeSuccess;

    public long maxMaterialSizeBytes() {
        return Math.multiplyExact(maxMaterialSizeMb, 1024L * 1024L);
    }

    public void validate() {
        if (maxMaterialSizeMb <= 0 || maxProcessingAttempts <= 0
                || consumerConcurrency <= 0 || prefetch <= 0 || queueCapacity <= 0
                || workerLeaseDuration.compareTo(processingTimeout) <= 0
                || analysisReadTimeout.compareTo(processingTimeout) >= 0
                || resolutionTokenTtl == null || resolutionTokenTtl.isZero() || resolutionTokenTtl.isNegative()
                || backoff == null || backoff.isEmpty() || backoff.stream().anyMatch(d -> d == null || d.isNegative())) {
            throw new IllegalStateException("Invalid AI precheck resource or timeout configuration");
        }
    }

    public enum MockMode {
        SUCCESS,
        TIMEOUT,
        PERMANENT_FAILURE,
        INVALID_RESPONSE
    }
}
