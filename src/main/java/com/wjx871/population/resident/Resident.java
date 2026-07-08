package com.wjx871.population.resident;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 居民演示模块实体。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@Getter
@Setter
public class Resident {

    private Long id;

    private String name;

    private Gender gender;

    private LocalDate birthDate;

    private String idCardNumber;

    private String phoneNumber;

    private String province;

    private String city;

    private String district;

    private String address;

    private boolean active = true;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
