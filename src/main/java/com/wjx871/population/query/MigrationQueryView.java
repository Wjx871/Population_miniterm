package com.wjx871.population.query;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class MigrationQueryView {
    private String direction;
    private Long migrationId;
    private Long personId;
    private String personName;
    private String migrationType;
    private String sourceRegionCode;
    private String targetRegionCode;
    private String status;
    private LocalDate executeDate;
    private LocalDateTime executedAt;
    private String applicationNo;
}
