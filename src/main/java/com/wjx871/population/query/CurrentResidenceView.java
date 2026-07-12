package com.wjx871.population.query;

import java.time.LocalDate;
import lombok.Data;

@Data
public class CurrentResidenceView {
    private Long residenceId;
    private Long householdId;
    private String registeredAddress;
    private String regionCode;
    private String registerTypeCode;
    private LocalDate registerDate;
    private LocalDate startDate;
    private String status;
}
