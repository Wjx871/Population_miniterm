package com.wjx871.population.assistant;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assistant/policy")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('population:view')")
public class PolicyAssistantController {
    private static final int MAX_TURN_CONTENT_LENGTH = 500;
    private static final int MAX_TURN_ROLE_LENGTH = 32;

    private final PolicyAssistantService service;
    private final PolicyWorkflowService workflowService;
    private final PolicyTaskAgentService taskAgentService;

    @PostMapping("/query")
    public ApiResponse<PolicyTaskAgentService.AgentResponse> query(@Valid @RequestBody QueryRequest request,
                                                                   HttpServletRequest httpRequest,
                                                                   HttpServletResponse httpResponse) {
        validateHistoryBounds(request.history());
        String traceId = S2TraceContext.resolveTraceId(
                httpRequest == null ? null : httpRequest.getHeader(S2TraceContext.TRACE_HEADER)
        );
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            if (httpResponse != null) {
                httpResponse.setHeader(S2TraceContext.TRACE_HEADER, traceId);
            }
            return ApiResponse.ok(taskAgentService.execute(request.question(), request.history(), traceId));
        }
    }

    public ApiResponse<PolicyTaskAgentService.AgentResponse> query(QueryRequest request) {
        return query(request, null, null);
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<String>> suggestions(HttpServletRequest httpRequest,
                                                 HttpServletResponse httpResponse) {
        String traceId = S2TraceContext.resolveTraceId(
                httpRequest == null ? null : httpRequest.getHeader(S2TraceContext.TRACE_HEADER)
        );
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            if (httpResponse != null) {
                httpResponse.setHeader(S2TraceContext.TRACE_HEADER, traceId);
            }
            return ApiResponse.ok(service.suggestions());
        }
    }

    public ApiResponse<List<String>> suggestions() {
        return suggestions(null, null);
    }

    @PostMapping("/check-materials")
    public ApiResponse<PolicyWorkflowService.WorkflowResponse> checkMaterials(@Valid @RequestBody ChecklistRequest request,
                                                                              HttpServletRequest httpRequest,
                                                                              HttpServletResponse httpResponse) {
        String traceId = S2TraceContext.resolveTraceId(
                httpRequest == null ? null : httpRequest.getHeader(S2TraceContext.TRACE_HEADER)
        );
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            if (httpResponse != null) {
                httpResponse.setHeader(S2TraceContext.TRACE_HEADER, traceId);
            }
            return ApiResponse.ok(workflowService.generateChecklist(request.question(), request.idCardRecognized()));
        }
    }

    public ApiResponse<PolicyWorkflowService.WorkflowResponse> checkMaterials(ChecklistRequest request) {
        return checkMaterials(request, null, null);
    }

    private void validateHistoryBounds(List<PolicyTaskAgentService.ConversationTurn> history) {
        if (history == null) {
            return;
        }
        for (PolicyTaskAgentService.ConversationTurn turn : history) {
            if (turn == null) {
                continue;
            }
            if (turn.role() != null && turn.role().length() > MAX_TURN_ROLE_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "history.role 长度超出限制");
            }
            if (turn.content() != null && turn.content().length() > MAX_TURN_CONTENT_LENGTH) {
                throw new BusinessException(HttpStatus.BAD_REQUEST, "history.content 长度超出 500 字符限制");
            }
        }
    }

    public record QueryRequest(@NotBlank @Size(max = 500) String question,
                               @Size(max = 6) List<PolicyTaskAgentService.ConversationTurn> history) {
    }

    public record ChecklistRequest(@NotBlank @Size(max = 500) String question, boolean idCardRecognized) {
    }
}
