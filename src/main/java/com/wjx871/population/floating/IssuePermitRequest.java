package com.wjx871.population.floating;import jakarta.validation.constraints.*;
public record IssuePermitRequest(@NotBlank @Size(max=100)String issuingAuthority,@NotNull Integer version){}
