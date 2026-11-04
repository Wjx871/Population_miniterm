package com.wjx871.population.ai.precheck;

import java.time.LocalDateTime;

public record AiTaskAcceptedView(
        String taskId,
        String taskType,
        String status,
        LocalDateTime acceptedAt,
        String taskSchemaVersion,
        String resultSchemaVersion,
        String traceId,
        long pollAfterMs
) {
}
