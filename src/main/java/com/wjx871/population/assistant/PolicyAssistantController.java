package com.wjx871.population.assistant;

import com.wjx871.population.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant/policy")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('population:view')")
public class PolicyAssistantController {
    private final PolicyAssistantService service;
    private final PolicyWorkflowService workflowService;
    private final PolicyTaskAgentService taskAgentService;

    @PostMapping("/query")
    public ApiResponse<PolicyTaskAgentService.AgentResponse> query(@Valid @RequestBody QueryRequest request) {
        return ApiResponse.ok(taskAgentService.execute(request.question(), request.history()));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<String>> suggestions() { return ApiResponse.ok(service.suggestions()); }

    @PostMapping("/check-materials")
    public ApiResponse<PolicyWorkflowService.WorkflowResponse> checkMaterials(@Valid @RequestBody ChecklistRequest request) {
        return ApiResponse.ok(workflowService.generateChecklist(request.question(), request.idCardRecognized()));
    }

    public record QueryRequest(@NotBlank @Size(max = 500) String question,
                               @Size(max = 6) List<PolicyTaskAgentService.ConversationTurn> history) { }
    public record ChecklistRequest(@NotBlank @Size(max = 500) String question, boolean idCardRecognized) { }
}
