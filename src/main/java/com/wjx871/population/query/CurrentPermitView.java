package com.wjx871.population.query;

import java.time.LocalDate;
import lombok.Data;

@Data
public class CurrentPermitView {
    private Long permitId;
    private String maskedPermitNo;
    private String status;
    private String issueRegionCode;
    private LocalDate validFrom;
    private LocalDate validUntil;
}
