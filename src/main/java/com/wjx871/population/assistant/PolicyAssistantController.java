package com.wjx871.population.assistant;

import com.wjx871.population.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assistant/policy")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('population:view')")
public class PolicyAssistantController {
    private final PolicyAssistantService service;
    private final PolicyOcrService ocrService;
    private final PolicyWorkflowService workflowService;

    @PostMapping("/query")
    public ApiResponse<PolicyAssistantService.QueryResponse> query(@Valid @RequestBody QueryRequest request) {
        return ApiResponse.ok(service.query(request.question()));
    }

    @GetMapping("/suggestions")
    public ApiResponse<List<String>> suggestions() { return ApiResponse.ok(service.suggestions()); }

    @PostMapping(value = "/ocr/id-card", consumes = "multipart/form-data")
    public ApiResponse<PolicyOcrService.OcrResponse> recognizeIdCard(@RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(ocrService.recognizeIdCard(file));
    }

    @PostMapping("/check-materials")
    public ApiResponse<PolicyWorkflowService.WorkflowResponse> checkMaterials(@Valid @RequestBody ChecklistRequest request) {
        return ApiResponse.ok(workflowService.generateChecklist(request.question(), request.idCardRecognized()));
    }

    public record QueryRequest(@NotBlank @Size(max = 500) String question) { }
    public record ChecklistRequest(@NotBlank @Size(max = 500) String question, boolean idCardRecognized) { }
}
