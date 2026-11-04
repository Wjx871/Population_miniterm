package com.wjx871.population.ai.precheck;

import java.time.LocalDateTime;

public record AiStageMaterialView(
        String stageMaterialId,
        String status,
        String businessType,
        String documentRoleHint,
        String originalFilename,
        String contentType,
        long fileSize,
        String sha256,
        LocalDateTime createdAt,
        LocalDateTime expiresAt
) {
}
