package com.wjx871.population.application;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Executor / supervisor pushes an APPROVED application back so the applicant can resubmit or withdraw.
 * 区别于审批驳回：驳回由审批人执行，会改写审批结论；退回由执行/复核岗执行，
 * 仅切换业务申请状态，保留审批结论、且 {@link ApplicationStatus#APPROVED} 之前的审批日志仍然有效。
 */
public record ReturnApplicationRequest(
        @NotBlank @Size(max = 500) String comment,
        @NotNull Integer version
) {}
