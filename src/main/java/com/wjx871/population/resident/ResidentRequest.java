package com.wjx871.population.resident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ResidentRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull Gender gender,
        @NotNull LocalDate birthDate,
        @NotBlank @Pattern(regexp = "^[0-9Xx]{18}$") String idCardNumber,
        @Size(max = 30) String phoneNumber,
        @Size(max = 100) String province,
        @Size(max = 100) String city,
        @Size(max = 100) String district,
        @Size(max = 255) String address,
        Boolean active
) {
}
