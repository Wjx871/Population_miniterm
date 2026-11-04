package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

public record AiTaskView(
        String taskId,
        String taskType,
        String businessType,
        String migrationType,
        String status,
        Integer progress,
        JsonNode result,
        Failure failure,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        String traceId,
        String taskSchemaVersion,
        String resultSchemaVersion,
        long version
) {
    public record Failure(String code, boolean retryable, String message) {
    }
}
