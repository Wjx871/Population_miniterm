package com.wjx871.population.s2.soap;

import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsCalculator;
import com.wjx871.population.s2.aggregation.PopulationMetricsRequest;
import com.wjx871.population.s2.aggregation.PopulationMetricsResult;
import com.wjx871.population.s2.aggregation.PopulationMetricsSoapContract;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.jws.WebService;
import lombok.extern.log4j.Log4j2;

@Log4j2
@WebService(
        endpointInterface = "com.wjx871.population.s2.aggregation.PopulationMetricsSoapContract",
        serviceName = "PopulationMetricsService",
        portName = "PopulationMetricsPort",
        targetNamespace = "urn:com:wjx871:population:s2")
public class PopulationMetricsSoapServiceImpl implements PopulationMetricsSoapContract {

    private final PopulationMetricsCalculator calculator = new PopulationMetricsCalculator();

    @Override
    public PopulationMetricsResult aggregate(PopulationMetricsRequest request)
            throws InvalidPopulationMetricsException {
        String traceId = request == null ? null : request.getTraceId();
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            PopulationMetricsResult result = calculator.calculate(request);
            log.info("S2 SOAP aggregate completed requestId={} outcome=SUCCESS", result.getRequestId());
            return result;
        } catch (InvalidPopulationMetricsException exception) {
            log.warn("S2 SOAP aggregate rejected outcome=INVALID_REQUEST errorCode={}",
                    exception.getFaultInfo().getCode());
            throw exception;
        }
    }
}
