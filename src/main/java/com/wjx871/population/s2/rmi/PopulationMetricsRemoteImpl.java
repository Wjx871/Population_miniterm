package com.wjx871.population.s2.rmi;

import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsCalculator;
import com.wjx871.population.s2.aggregation.PopulationMetricsRemote;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationMetricsResult;
import com.wjx871.population.s2.trace.S2TraceContext;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class PopulationMetricsRemoteImpl implements PopulationMetricsRemote {

    private final PopulationMetricsCalculator calculator;
    private final long demoDelayMillis;

    public PopulationMetricsRemoteImpl(PopulationMetricsCalculator calculator, long demoDelayMillis) {
        this.calculator = calculator;
        this.demoDelayMillis = Math.max(0, Math.min(demoDelayMillis, 30_000));
    }

    @Override
    public PopulationMetricsResult aggregate(PopulationMetricsRequest request)
            throws InvalidPopulationMetricsException {
        String traceId = request == null ? null : request.getTraceId();
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            if (demoDelayMillis > 0) {
                try {
                    Thread.sleep(demoDelayMillis);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("RMI demo operation interrupted");
                }
            }
            PopulationMetricsResult result = calculator.calculate(request);
            log.info("S2 remote aggregate completed requestId={} outcome=SUCCESS", result.getRequestId());
            return result;
        }
    }
}
