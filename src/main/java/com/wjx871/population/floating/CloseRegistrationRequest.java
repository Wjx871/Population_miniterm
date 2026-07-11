package com.wjx871.population.floating;import jakarta.validation.constraints.*;
public record CloseRegistrationRequest(@NotBlank String reasonCode,@NotBlank @Size(max=500)String comment,@NotNull Integer version){}
