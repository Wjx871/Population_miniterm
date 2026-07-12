package com.wjx871.population.dashboard;

import java.time.LocalDate;
import lombok.Data;

@Data
public class MigrationTrendPoint {
    private LocalDate date;
    private long inCount;
    private long outCount;

    public MigrationTrendPoint() {}
    public MigrationTrendPoint(LocalDate date, long inCount, long outCount) {
        this.date = date;
        this.inCount = inCount;
        this.outCount = outCount;
    }
}
