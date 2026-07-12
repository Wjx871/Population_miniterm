package com.wjx871.population.query;

import java.time.LocalDate;
import lombok.Data;

@Data
public class ComprehensivePersonSummaryView {
    private Long personId;
    private String name;
    private String gender;
    private LocalDate birthDate;
    private String maskedIdentityNo;
    private String maskedPhone;
    private String personStatus;
    private Long householdId;
    private String householdNo;
    private String headPersonName;
    private String relationship;
    private Boolean householdHead;
    private String currentRegionCode;
    private String currentRegionName;
    private String currentAddress;
    private String residenceStatus;
    private Long floatingId;
    private String floatingStatus;
    private LocalDate arrivalDate;
    private Long permitId;
    private String maskedPermitNo;
    private String permitStatus;
    private LocalDate permitValidUntil;
    private String lastMigrationDirection;
    private LocalDate lastMigrationDate;
}
