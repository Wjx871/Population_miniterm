package com.wjx871.population.migration;
import lombok.Data;
@Data public class HouseholdSnapshot { private Long householdId; private String householdNo; private Long headPersonId; private String address; private String regionCode; private String status; private Long activeMemberCount; }
