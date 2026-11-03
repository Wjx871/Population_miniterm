package com.wjx871.population.s2;

import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.rmi.PopulationMetricsClientFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "population.s2.remote", name = "enabled", havingValue = "true")
public class S2RemoteConfiguration {

    @Bean
    PopulationMetricsClient populationMetricsClient(
            org.springframework.core.env.Environment environment) {
        int registryPort = environment.getProperty("population.s2.rmi.registry-port", Integer.class, 1199);
        int objectPort = environment.getProperty("population.s2.rmi.object-port", Integer.class, 1200);
        return PopulationMetricsClientFactory.create(registryPort, objectPort);
    }
}
