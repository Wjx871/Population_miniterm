package com.wjx871.population.s2;

import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class S2RemoteDisabledIntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void remoteIntegrationAndControllerAreAbsentByDefault() {
        assertThat(applicationContext.getBeansOfType(S2StatisticsController.class)).isEmpty();
        assertThat(applicationContext.getBeansOfType(PopulationMetricsClient.class)).isEmpty();
    }
}
