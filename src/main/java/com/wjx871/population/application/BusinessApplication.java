package com.wjx871.population.application;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BusinessApplication {
    private Long applicationId;
    private String applicationNo;
    private BusinessType businessType;
    private String title;
    private Long applicantUserId;
    private String applicantName;
    private Long applicantDepartmentId;
    private String applicantRegionCode;
    private Long targetPersonId;
    private Long targetHouseholdId;
    private ApplicationStatus status;
    private String reason;
    private String remark;
    private LocalDateTime submittedAt;
    private LocalDateTime completedAt;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
