package com.wjx871.population.export;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.context.annotation.Configuration;import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling @EnableConfigurationProperties(ExportProperties.class) public class ExportConfiguration{}
