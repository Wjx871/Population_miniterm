package com.wjx871.population.query;

import lombok.Data;

@Data
public class HouseholdQueryView {
    private Long householdId;
    private String householdNo;
    private String headPersonName;
    private String address;
    private String regionCode;
    private String householdType;
    private String status;
    private Long memberCount;
    private Boolean containsKeyPopulation;
}
