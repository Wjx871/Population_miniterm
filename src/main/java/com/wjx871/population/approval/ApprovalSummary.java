package com.wjx871.population.approval;
import com.wjx871.population.application.*; import java.time.LocalDateTime; import lombok.Data;
@Data public class ApprovalSummary {private Long approvalId;private String approvalNo;private Long applicationId;private String applicationNo;private String title;private BusinessType businessType;private ApprovalStatus status;private Long submittedBy;private String applicantName;private LocalDateTime submittedAt;private Integer version;}
