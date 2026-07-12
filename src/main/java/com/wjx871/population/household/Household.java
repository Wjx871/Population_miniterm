package com.wjx871.population.household;
import java.time.*; import lombok.Data;
@Data public class Household {private Long householdId;private String householdNo;private Long headPersonId;private String headPersonName;private String address;private String regionCode;private String householdType;private LocalDate establishDate;private String status;private Integer version;private LocalDateTime createdAt;private LocalDateTime updatedAt;private Long activeMemberCount;}
