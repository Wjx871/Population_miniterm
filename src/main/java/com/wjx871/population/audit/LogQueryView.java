package com.wjx871.population.audit;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class LogQueryView {
    private Long logId;
    private Long userId;
    private String username;
    private String operationType;
    private String moduleName;
    private String requestPath;
    private String requestMethod;
    private LocalDateTime operationTime;
    private String ipAddress;
    private String operationResult;
    private String errorMessage;
    private String detail;
}
