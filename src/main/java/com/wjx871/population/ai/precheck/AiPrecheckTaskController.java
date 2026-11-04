package com.wjx871.population.ai.precheck;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiPrecheckTaskController {
    private final AiPrecheckTaskService service;

    @PostMapping("/api/ai/tasks")
    @PreAuthorize("hasAuthority('ai:precheck:create') and hasAuthority('migration:in:create')")
    public ResponseEntity<ApiResponse<AiTaskAcceptedView>> create(@Valid @RequestBody AiTaskCreateRequest body,
                                                                  HttpServletRequest request) {
        String traceId = S2TraceContext.resolveTraceId(request.getHeader(S2TraceContext.TRACE_HEADER));
        AiTaskAcceptedView task = service.create(CurrentUserContext.requireUser(), body, traceId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).header(S2TraceContext.TRACE_HEADER, traceId)
                .body(ApiResponse.ok(task));
    }

    @GetMapping("/api/ai/tasks/{taskId}")
    @PreAuthorize("hasAuthority('ai:precheck:view')")
    public ResponseEntity<ApiResponse<AiTaskView>> get(@PathVariable String taskId, HttpServletRequest request) {
        String traceId = S2TraceContext.resolveTraceId(request.getHeader(S2TraceContext.TRACE_HEADER));
        return ResponseEntity.ok().header(S2TraceContext.TRACE_HEADER, traceId)
                .body(ApiResponse.ok(service.get(CurrentUserContext.requireUser(), taskId)));
    }
}
