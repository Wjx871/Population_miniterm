package com.wjx871.population.application;

import com.wjx871.population.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Central state transition rules for reusable business applications. */
@Component
public class ApplicationStateMachine {
    public void requireDraft(ApplicationStatus status) {
        if (status != ApplicationStatus.DRAFT) {
            throw new BusinessException(HttpStatus.CONFLICT, "只有草稿申请可以执行该操作");
        }
    }

    public void requireReview(ApplicationStatus status) {
        if (status != ApplicationStatus.UNDER_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "申请当前不处于待审核状态");
        }
    }

    public void requireWithdrawable(ApplicationStatus status) {
        // SUBMITTED / UNDER_REVIEW：审批阶段的撤回，由 ApprovalService 走审批单撤回流程；
        // RETURNED：审批已通过、但被退回的状态，由 ApplicationService 直接转 WITHDRAWN（无需审审批单）。
        if (status != ApplicationStatus.SUBMITTED
                && status != ApplicationStatus.UNDER_REVIEW
                && status != ApplicationStatus.RETURNED) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前申请状态不允许撤回");
        }
    }

    /**
     * 只有审批通过、尚未执行的申请可以被执行人/复核岗退回；
     * 不得将已撤回/已驳回/已办结等终态申请再退回，避免篡改审批结论。
     */
    public void requireReturnable(ApplicationStatus status) {
        if (status != ApplicationStatus.APPROVED) {
            throw new BusinessException(HttpStatus.CONFLICT, "仅已批准且待执行的申请可被退回");
        }
    }

    /**
     * 草稿提交或被退回后的再次提交都视为「提交流程入口」；
     * 区别于 {@link #requireDraft}，这里允许 RETURNED 状态。
     */
    public void requireResubmittable(ApplicationStatus status) {
        if (status != ApplicationStatus.DRAFT && status != ApplicationStatus.RETURNED) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前申请状态不允许提交");
        }
    }
}
