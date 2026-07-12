package com.wjx871.population.application;

import java.time.LocalDateTime;

public record ApplicationView(
        Long applicationId, String applicationNo, BusinessType businessType, String title,
        Long applicantUserId, String applicantName, Long applicantDepartmentId, String applicantRegionCode,
        Long targetPersonId, Long targetHouseholdId, ApplicationStatus status, String reason, String remark,
        LocalDateTime submittedAt, LocalDateTime completedAt, Integer version,
        LocalDateTime createdAt, LocalDateTime updatedAt
) {
    public static ApplicationView from(BusinessApplication value) {
        return new ApplicationView(value.getApplicationId(), value.getApplicationNo(), value.getBusinessType(),
                value.getTitle(), value.getApplicantUserId(), value.getApplicantName(),
                value.getApplicantDepartmentId(), value.getApplicantRegionCode(), value.getTargetPersonId(),
                value.getTargetHouseholdId(), value.getStatus(), value.getReason(), value.getRemark(),
                value.getSubmittedAt(), value.getCompletedAt(), value.getVersion(), value.getCreatedAt(),
                value.getUpdatedAt());
    }
}
