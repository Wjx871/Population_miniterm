package com.wjx871.population.cache;

import java.time.Duration;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties("population.redis")
public class RedisProperties {
    private boolean enabled;
    private String keyPrefix = "population";
    private Duration regionTtl = Duration.ofMinutes(30);
    private Duration dictionaryTtl = Duration.ofMinutes(30);
    private Duration overviewTtl = Duration.ofMinutes(5);
    private Duration trendTtl = Duration.ofMinutes(5);
}
