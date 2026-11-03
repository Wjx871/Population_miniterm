package com.wjx871.population.s2;

import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsClient;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationMetricsResult;
import com.wjx871.population.stats.StatisticsService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Service
@ConditionalOnProperty(prefix = "population.s2.remote", name = "enabled", havingValue = "true")
public class S2AggregationFacade {

    private final StatisticsService statisticsService;
    private final PopulationMetricsClient remoteClient;

    public S2AggregationFacade(StatisticsService statisticsService, PopulationMetricsClient remoteClient) {
        this.statisticsService = statisticsService;
        this.remoteClient = remoteClient;
    }

    public PopulationMetricsResult aggregateAuthorizedSummary(String traceId)
            throws InvalidPopulationMetricsException {
        Map<String, Object> summary = statisticsService.getSummary();
        if (Boolean.TRUE.equals(summary.get("isMock"))) {
            throw new IllegalStateException("S2 remote aggregation will not label mock statistics as database data");
        }
        PopulationMetricsRequest request = new PopulationMetricsRequest(
                "s2-" + UUID.randomUUID().toString().substring(0, 12), traceId,
                count(summary, "residentCount"), count(summary, "floatingCount"),
                count(summary, "keyPopulationCount"), count(summary, "householdCount"));
        return remoteClient.aggregate(request);
    }

    private long count(Map<String, Object> summary, String key) {
        Object value = summary.get(key);
        if (!(value instanceof Number number)) {
            throw new IllegalStateException("Statistics summary did not contain a numeric " + key);
        }
        try {
            return new BigDecimal(number.toString()).longValueExact();
        } catch (NumberFormatException | ArithmeticException exception) {
            throw new IllegalStateException("Statistics summary contained an out-of-range " + key, exception);
        }
    }
}
