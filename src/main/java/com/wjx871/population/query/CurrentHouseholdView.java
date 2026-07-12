package com.wjx871.population.query;

import lombok.Data;

@Data
public class CurrentHouseholdView {
    private Long householdId;
    private String householdNo;
    private String headPersonName;
    private String relationship;
    private Boolean householdHead;
    private String status;
    private String address;
    private String regionCode;
}
