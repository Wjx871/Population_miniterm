package com.wjx871.population.person;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record PersonRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 1) String gender,
        @NotBlank @Pattern(regexp = "^[0-9Xx]{18}$") String idCard,
        LocalDate birthDate,
        @Size(max = 30) String ethnicity,
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^[0-9\\-]{7,20}$") String phone,
        @Size(max = 255) String currentAddress,
        @Size(max = 20) String status
) {
}
