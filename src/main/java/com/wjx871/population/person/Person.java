package com.wjx871.population.person;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

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
