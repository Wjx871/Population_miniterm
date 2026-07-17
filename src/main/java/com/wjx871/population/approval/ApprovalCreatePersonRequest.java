package com.wjx871.population.approval;

import com.wjx871.population.person.PersonRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Final approval payload for document-based person-registration applications. */
public record ApprovalCreatePersonRequest(
        @Size(max = 500) String comment,
        @NotNull Integer version,
        @NotNull @Valid PersonRequest person
) {
}
