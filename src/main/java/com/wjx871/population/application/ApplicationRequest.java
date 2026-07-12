package com.wjx871.population.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ApplicationRequest(
        @NotNull BusinessType businessType,
        @NotBlank @Size(max = 200) String title,
        Long targetPersonId,
        Long targetHouseholdId,
        @NotBlank String reason,
        @Size(max = 500) String remark,
        Integer version
) {
}
