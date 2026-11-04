package com.wjx871.population.ai.precheck;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiTaskCreateRequest(
        @NotBlank @Size(max = 20) String requestSchemaVersion,
        @NotBlank @Size(max = 40) String taskType,
        @NotBlank @Size(max = 40) String businessType,
        @NotBlank @Size(max = 50) String migrationType,
        @NotEmpty @Size(min = 1, max = 10) List<@NotBlank @Size(max = 80) String> stageMaterialIds,
        @NotBlank @Size(max = 128) String idempotencyKey
) {
}
