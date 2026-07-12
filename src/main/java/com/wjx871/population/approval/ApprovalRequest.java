package com.wjx871.population.approval;
import java.time.LocalDateTime; import lombok.Data;
@Data public class ApprovalRequest { private Long approvalId;private String approvalNo;private Long applicationId;private ApprovalStatus status;private Long currentApproverId;private Long currentDepartmentId;private String currentRegionCode;private Long submittedBy;private LocalDateTime submittedAt;private Long decidedBy;private LocalDateTime decidedAt;private String decisionComment;private Integer version;private LocalDateTime createdAt;private LocalDateTime updatedAt; }
