package com.wjx871.population.approval;
import java.time.LocalDateTime; import lombok.Data;
@Data public class ApprovalLog { private Long logId;private Long approvalId;private Long applicationId;private ApprovalAction action;private String fromStatus;private String toStatus;private Long operatorUserId;private String comment;private LocalDateTime operationTime;private String ipAddress;private LocalDateTime createdAt;
 public static ApprovalLog of(Long approvalId,Long applicationId,ApprovalAction action,String from,String to,Long user,String comment,String ip){ApprovalLog l=new ApprovalLog();l.setApprovalId(approvalId);l.setApplicationId(applicationId);l.setAction(action);l.setFromStatus(from);l.setToStatus(to);l.setOperatorUserId(user);l.setComment(comment);l.setIpAddress(ip);return l;}}
