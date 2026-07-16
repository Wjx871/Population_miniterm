package com.wjx871.population.dashboard;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class DashboardOverviewView {
    private LocalDateTime generatedAt;
    private int periodDays;
    private int expiryDays;
    private long registeredPopulation;
    private long activeFloatingPopulation;
    private long activeResidencePermits;
    private long pendingApprovals;
    private long expiringResidencePermits;
    private long migrationInPeriod;
    private long migrationOutPeriod;
    private PopulationStructureView populationStructure;
    private KeyBusinessView keyBusiness;
}
