package com.wjx871.population.household;
import java.time.*; import lombok.Data;
@Data public class HouseholdMember {private Long memberId;private Long householdId;private Long personId;private String personName;private String idCard;private String phone;private String relationship;private LocalDate joinDate;private LocalDate leaveDate;private String status;private Integer version;private LocalDateTime createdAt;private LocalDateTime updatedAt;}
