package com.wjx871.population.audit;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class OperationLog {
    private Long userId;
    private String operationType;
    private String moduleName;
    private String requestPath;
    private String requestMethod;
    private String operationResult;
    private String errorMessage;
    private String ipAddress;
    private String userAgent;
    private String detail;
}
