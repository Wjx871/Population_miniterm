package com.wjx871.population.query;

import java.time.LocalDate;

public record PersonQueryCriteria(String name, String identityNo, String gender, Integer ageMin, Integer ageMax,
        String regionCode, Long departmentId, Long householdId, String householdType, String residenceStatus,
        String floatingStatus, String certificateType, String keyPopulationType, String currentStatus,
        LocalDate birthDateFrom, LocalDate birthDateTo) {
}
