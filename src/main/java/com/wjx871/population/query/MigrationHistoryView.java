package com.wjx871.population.query;

import java.time.LocalDate;
import lombok.Data;

@Data
public class MigrationHistoryView {
    private String direction;
    private Long migrationId;
    private String migrationType;
    private LocalDate businessDate;
    private String fromRegionCode;
    private String toRegionCode;
    private String address;
    private String businessStatus;
}
