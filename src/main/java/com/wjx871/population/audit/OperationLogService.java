package com.wjx871.population.audit;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import com.wjx871.population.security.SensitiveDataMaskingService;

@Log4j2
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;
    private final SensitiveDataMaskingService masking;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(Long userId, String operationType, String result, String errorMessage,
                       HttpServletRequest request) {
        try {
            operationLogMapper.insert(OperationLog.builder()
                    .userId(userId)
                    .operationType(operationType)
                    .moduleName("AUTH")
                    .requestPath(request.getRequestURI())
                    .requestMethod(request.getMethod())
                    .operationResult(result)
                    .errorMessage(masking.auditDetail(errorMessage))
                    .ipAddress(clientIp(request))
                    .userAgent(truncate(request.getHeader("User-Agent"), 500))
                    .detail(masking.auditDetail(operationType))
                    .build());
        } catch (RuntimeException exception) {
            log.error("Failed to persist operation log type={}", operationType, exception);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransactional(Long userId, String operationType, HttpServletRequest request) {
        recordTransactional(userId, operationType, "APPROVAL", operationType, request);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void recordTransactional(Long userId, String operationType, String moduleName, String detail,
                                    HttpServletRequest request) {
        operationLogMapper.insert(OperationLog.builder().userId(userId).operationType(operationType)
                .moduleName(moduleName).requestPath(request.getRequestURI()).requestMethod(request.getMethod())
                .operationResult("SUCCESS").ipAddress(clientIp(request))
                .userAgent(truncate(request.getHeader("User-Agent"), 500)).detail(masking.auditDetail(detail)).build());
    }

    public String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 50);
        }
        return truncate(request.getRemoteAddr(), 50);
    }

    private String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
