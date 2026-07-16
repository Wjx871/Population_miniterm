package com.wjx871.population.dashboard;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class DashboardChartsView {
    private LocalDateTime generatedAt;
    private List<MigrationTrendPoint> migrationTrend;
    private List<NamedCountView> businessScale;
    private List<NamedCountView> permitStatusDistribution;
    private List<RegionCountView> registeredPopulationByRegion;
    private List<PopulationScaleTrendPoint> populationScaleTrend;
}
