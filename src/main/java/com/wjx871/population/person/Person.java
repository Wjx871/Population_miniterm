package com.wjx871.population.person;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * 人口基础信息实体。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@Getter
@Setter
public class Person {

    private Long personId;

    private String name;

    private String gender;

    private String idCard;

    private LocalDate birthDate;

    private String ethnicity;

    private String phone;

    private String currentAddress;

    private String status = PersonStatus.NORMAL;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
