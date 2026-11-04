package com.wjx871.population.ai.precheck;

import com.wjx871.population.migration.MigrationType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiResolutionPreviewRequest(
        @NotBlank @Size(max = 20) String requestSchemaVersion,
        @NotNull Long taskVersion,
        @Size(max = 20) List<@Valid Correction> corrections,
        @NotNull MigrationType migrationType,
        @NotBlank @Size(max = 20) String toRegionCode,
        @Size(max = 40) String transferBatchNo
) {
    public record Correction(
            @NotBlank @Size(max = 100) String fieldCode,
            @Size(max = 500) String value
    ) {
    }
}
