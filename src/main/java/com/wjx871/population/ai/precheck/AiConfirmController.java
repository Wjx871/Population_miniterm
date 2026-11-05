package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiConfirmController {
    private final AiConfirmService service;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @PostMapping(value = "/api/ai/tasks/{taskId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAuthority('ai:precheck:confirm') and hasAuthority('migration:in:create')")
    public ResponseEntity<ApiResponse<AiConfirmView>> confirm(@PathVariable String taskId,
                                                               @RequestBody String body,
                                                               HttpServletRequest request) {
        AiConfirmRequest parsed = parseStrict(body);
        String traceId = S2TraceContext.resolveTraceId(request.getHeader(S2TraceContext.TRACE_HEADER));
        AiConfirmService.ConfirmResult result = service.confirm(CurrentUserContext.requireUser(), taskId, parsed,
                request, traceId);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header(S2TraceContext.TRACE_HEADER, traceId)
                .body(result.replayed() ? ApiResponse.ok(result.view()) : ApiResponse.created(result.view()));
    }

    private AiConfirmRequest parseStrict(String body) {
        if (body == null || body.isBlank()) throw invalidRequest();
        try (JsonParser parser = objectMapper.getFactory().createParser(body)) {
            parser.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
            AiConfirmRequest request = objectMapper.readerFor(AiConfirmRequest.class)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).readValue(parser);
            if (request == null) throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CONFIRM_REQUEST");
            Set<ConstraintViolation<AiConfirmRequest>> violations = validator.validate(request);
            if (!violations.isEmpty()) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CONFIRM_REQUEST");
            }
            return request;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw invalidRequest();
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_CONFIRM_REQUEST");
    }
}
