package com.wjx871.population.query;

import java.util.List;
import lombok.Data;

@Data
public class ComprehensivePersonProfileView {
    private ComprehensivePersonSummaryView person;
    private CurrentHouseholdView currentHousehold;
    private CurrentResidenceView currentResidence;
    private CurrentFloatingView activeFloating;
    private CurrentPermitView currentPermit;
    private List<MigrationHistoryView> migrationHistory;
}
