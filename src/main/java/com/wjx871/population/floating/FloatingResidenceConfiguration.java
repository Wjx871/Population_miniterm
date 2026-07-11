package com.wjx871.population.floating;
import java.time.Clock;import org.springframework.boot.context.properties.EnableConfigurationProperties;import org.springframework.context.annotation.*;import org.springframework.scheduling.annotation.EnableScheduling;
@Configuration @EnableScheduling @EnableConfigurationProperties(ResidencePermitProperties.class) public class FloatingResidenceConfiguration {@Bean public Clock businessClock(){return Clock.systemDefaultZone();}}
