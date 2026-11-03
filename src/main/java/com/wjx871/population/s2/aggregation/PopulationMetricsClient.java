package com.wjx871.population.s2.aggregation;

/** Local client port; production adapters and demo clients can be proxied independently. */
public interface PopulationMetricsClient {
    PopulationMetricsResult aggregate(PopulationMetricsRequest request)
            throws InvalidPopulationMetricsException;
}
