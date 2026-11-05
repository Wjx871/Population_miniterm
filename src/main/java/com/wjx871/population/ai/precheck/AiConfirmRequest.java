package com.wjx871.population.ai.precheck;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AiConfirmRequest(
        @NotBlank @Size(max = 20) String requestSchemaVersion,
        @NotNull Long taskVersion,
        @NotBlank @Size(max = 80) String resolutionToken,
        @Valid SelectedCandidates selectedCandidates,
        @NotBlank @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String idempotencyKey,
        @Size(max = 40) List<@Valid ConfirmedField> confirmedFields,
        @Size(max = 20) List<@NotBlank @Size(max = 80) String> acknowledgedAlerts,
        @Size(max = 10) List<@Valid MaterialBinding> materialBindings,
        @Pattern(regexp = "HOUSEHOLD_BOOK|ADDRESS_PROOF") String householdEvidenceType
) {
    public record SelectedCandidates(
            @Size(max = 80) String person,
            @Size(max = 80) String targetHousehold
    ) { }

    public record ConfirmedField(
            @NotBlank @Size(max = 80) String fieldCode,
            @Size(max = 500) String value
    ) { }

    public record MaterialBinding(
            @NotBlank @Pattern(regexp = "stg_[A-Fa-f0-9]{32}") String stageMaterialId,
            @Size(max = 50) String materialType
    ) { }
}
