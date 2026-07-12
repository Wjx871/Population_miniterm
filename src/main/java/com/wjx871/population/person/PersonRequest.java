package com.wjx871.population.person;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 人口基础信息新增和修改请求。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
public record PersonRequest(
        @NotBlank @Size(max = 50) String name,
        @NotBlank @Size(max = 1) String gender,
        @NotBlank @Size(min = 18, max = 18) String idCard,
        LocalDate birthDate,
        @Size(max = 30) String ethnicity,
        @Pattern(regexp = "^$|^1[3-9]\\d{9}$|^[0-9\\-]{7,20}$") String phone,
        @Size(max = 255) String currentAddress,
        @Size(max = 20) String status
) {
}
