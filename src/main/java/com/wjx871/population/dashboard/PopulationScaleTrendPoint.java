package com.wjx871.population.dashboard;

import java.time.LocalDate;
import lombok.Data;

@Data
public class PopulationScaleTrendPoint {
    private LocalDate date;
    private long registeredPopulation;
    private long floatingPopulation;
    private long residencePermits;

    public PopulationScaleTrendPoint() {}

    public PopulationScaleTrendPoint(LocalDate date, long registeredPopulation, long floatingPopulation,
            long residencePermits) {
        this.date = date;
        this.registeredPopulation = registeredPopulation;
        this.floatingPopulation = floatingPopulation;
        this.residencePermits = residencePermits;
    }
}
