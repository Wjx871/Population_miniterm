package com.wjx871.population.query;

import java.time.LocalDate;
import lombok.Data;

@Data
public class CurrentFloatingView {
    private Long floatingId;
    private String registrationNo;
    private String status;
    private String currentRegionCode;
    private String currentAddress;
    private LocalDate arrivalDate;
    private LocalDate plannedLeaveDate;
}
