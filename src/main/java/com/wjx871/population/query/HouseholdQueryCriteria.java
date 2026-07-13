package com.wjx871.population.query;

public record HouseholdQueryCriteria(String householdNo, String headPersonName, String address, String regionCode,
        String householdType, String status, Integer memberCountMin, Integer memberCountMax,
        Boolean containsKeyPopulation) {
}
