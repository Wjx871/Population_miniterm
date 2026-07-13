package com.wjx871.population.query;

import java.time.LocalDate;

public record MigrationQueryCriteria(Long personId, String personName, String migrationType,
        String sourceRegionCode, String targetRegionCode, String status, LocalDate executeDateFrom,
        LocalDate executeDateTo, String applicationNo) {
}
