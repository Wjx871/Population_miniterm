package com.wjx871.population.dashboard;

import lombok.Data;

@Data
public class KeyBusinessView {
    private long activeKeyPopulation;
    private long pendingCancellation;
    private long expiringResidencePermits;
    private long pendingSensitiveExport;
}
