package com.wjx871.population.material;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
public record MaterialVerifyRequest(@NotNull MaterialVerifyStatus result, @Size(max=500) String comment) {}
