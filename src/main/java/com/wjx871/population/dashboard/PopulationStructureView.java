package com.wjx871.population.dashboard;

import java.util.List;
import lombok.Data;

@Data
public class PopulationStructureView {
    private GenderDistributionView gender;
    private List<NamedCountView> ageGroups;
}
