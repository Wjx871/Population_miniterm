package com.wjx871.population.s2;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.s2.aggregation.InvalidPopulationMetricsException;
import com.wjx871.population.s2.aggregation.PopulationMetricsResult;
import com.wjx871.population.s2.aggregation.PopulationRemoteCallException;
import com.wjx871.population.s2.trace.S2TraceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/s2/statistics")
@ConditionalOnProperty(prefix = "population.s2.remote", name = "enabled", havingValue = "true")
public class S2StatisticsController {

    private final S2AggregationFacade facade;

    public S2StatisticsController(S2AggregationFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/aggregate")
    @PreAuthorize("hasAuthority('statistics:view')")
    public ResponseEntity<ApiResponse<PopulationMetricsResult>> aggregate(
            @RequestHeader(value = S2TraceContext.TRACE_HEADER, required = false) String incomingTraceId) {
        String traceId = S2TraceContext.resolveTraceId(incomingTraceId);
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            try {
                return ResponseEntity.ok()
                        .header(S2TraceContext.TRACE_HEADER, traceId)
                        .body(ApiResponse.ok(facade.aggregateAuthorizedSummary(traceId)));
            } catch (PopulationRemoteCallException exception) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .header(S2TraceContext.TRACE_HEADER, traceId)
                        .body(ApiResponse.fail(HttpStatus.SERVICE_UNAVAILABLE.value(), exception.getErrorCode()));
            } catch (InvalidPopulationMetricsException exception) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .header(S2TraceContext.TRACE_HEADER, traceId)
                        .body(ApiResponse.fail(HttpStatus.BAD_REQUEST.value(), exception.getFaultInfo().getCode()));
            }
        }
    }
}
