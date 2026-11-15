package com.wjx871.population.system;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Covers authentication failures too; request bodies, tokens and query strings are not logged. */
@Log4j2
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestTraceFilter extends OncePerRequestFilter {
    private final String instanceId;
    private final RequestMetrics metrics;

    @org.springframework.beans.factory.annotation.Autowired
    public RequestTraceFilter(@Value("${population.instance-id:local}") String instanceId, RequestMetrics metrics) {
        this.instanceId = instanceId;
        this.metrics = metrics;
    }
    public RequestTraceFilter(String instanceId) { this(instanceId, new RequestMetrics()); }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String supplied = request.getHeader("X-Trace-Id");
        String traceId = supplied != null && supplied.matches("[A-Za-z0-9_-]{1,64}")
                ? supplied : UUID.randomUUID().toString();
        var previous = ThreadContext.getImmutableContext();
        long started = System.nanoTime();
        boolean failed = true;
        try {
            ThreadContext.put("traceId", traceId);
            ThreadContext.put("instanceId", instanceId);
            request.setAttribute("traceId", traceId);
            response.setHeader("X-Trace-Id", traceId);
            response.setHeader("X-Instance-Id", instanceId);
            chain.doFilter(request, response);
            failed = false;
        } finally {
            metrics.record(System.nanoTime() - started, failed ? 500 : response.getStatus());
            log.info("http_request method={} status={} elapsedMs={} exception={}",
                    request.getMethod(), failed ? 500 : response.getStatus(),
                    (System.nanoTime() - started) / 1_000_000, failed);
            ThreadContext.clearMap();
            ThreadContext.putAll(previous);
        }
    }
}
