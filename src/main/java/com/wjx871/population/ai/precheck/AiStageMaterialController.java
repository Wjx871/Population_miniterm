package com.wjx871.population.ai.precheck;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Validated
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiStageMaterialController {
    private final AiStageMaterialService service;

    @PostMapping(value = "/api/ai/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('ai:precheck:create')")
    public ResponseEntity<ApiResponse<AiStageMaterialView>> upload(
            @RequestHeader("Idempotency-Key") @NotBlank String idempotencyKey,
            @RequestParam(defaultValue = "MIGRATION_IN") String businessType,
            @RequestParam(defaultValue = "UNKNOWN") DocumentRoleHint documentRoleHint,
            @RequestPart("file") MultipartFile file,
            jakarta.servlet.http.HttpServletRequest request) {
        String traceId = S2TraceContext.resolveTraceId(request.getHeader(S2TraceContext.TRACE_HEADER));
        AiStageMaterialService.UploadResult result;
        try {
            result = service.upload(CurrentUserContext.requireUser(), idempotencyKey, businessType,
                    documentRoleHint, file, traceId);
        } catch (BusinessException exception) {
            if (exception.getStatus() == HttpStatus.CONFLICT
                    && exception.getMessage() != null
                    && exception.getMessage().startsWith("MATERIAL_UPLOAD_IN_PROGRESS")) {
                return ResponseEntity.status(HttpStatus.CONFLICT).header(HttpHeaders.RETRY_AFTER, "1")
                        .body(ApiResponse.fail(HttpStatus.CONFLICT.value(), exception.getMessage()));
            }
            throw exception;
        }
        ApiResponse<AiStageMaterialView> body = result.replayed()
                ? ApiResponse.ok(result.material()) : ApiResponse.created(result.material());
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED).body(body);
    }

    @GetMapping("/api/ai/materials/{stageMaterialId}")
    @PreAuthorize("hasAuthority('ai:precheck:view')")
    public ApiResponse<AiStageMaterialView> get(@PathVariable String stageMaterialId) {
        return ApiResponse.ok(service.get(CurrentUserContext.requireUser(), stageMaterialId));
    }

    @DeleteMapping("/api/ai/materials/{stageMaterialId}")
    @PreAuthorize("hasAuthority('ai:precheck:create')")
    public ApiResponse<AiStageMaterialView> delete(@PathVariable String stageMaterialId,
                                                  jakarta.servlet.http.HttpServletRequest request) {
        String traceId = S2TraceContext.resolveTraceId(request.getHeader(S2TraceContext.TRACE_HEADER));
        return ApiResponse.ok(service.delete(CurrentUserContext.requireUser(), stageMaterialId, traceId));
    }
}
