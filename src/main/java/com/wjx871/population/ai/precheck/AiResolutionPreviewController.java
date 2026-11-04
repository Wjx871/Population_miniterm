package com.wjx871.population.ai.precheck;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiResolutionPreviewController {

    private final AiResolutionService service;

    @PostMapping("/api/ai/tasks/{taskId}/resolution-preview")
    @PreAuthorize("hasAuthority('ai:precheck:view') and hasAuthority('migration:in:create')")
    public ResponseEntity<ApiResponse<AiResolutionPreviewView>> preview(
            @PathVariable String taskId,
            @Valid @RequestBody AiResolutionPreviewRequest body,
            HttpServletRequest request) {
        String traceId = S2TraceContext.resolveTraceId(request.getHeader(S2TraceContext.TRACE_HEADER));
        AiResolutionPreviewView view = service.preview(CurrentUserContext.requireUser(), taskId, body, traceId);
        return ResponseEntity.ok().header(S2TraceContext.TRACE_HEADER, traceId).body(ApiResponse.ok(view));
    }
}
