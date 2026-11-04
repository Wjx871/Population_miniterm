package com.wjx871.population.ai.precheck;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AiPrecheckProperties.class)
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiPrecheckFeatureConfiguration {
}
