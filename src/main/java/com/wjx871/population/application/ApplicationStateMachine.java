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
        if (status != ApplicationStatus.SUBMITTED && status != ApplicationStatus.UNDER_REVIEW) {
            throw new BusinessException(HttpStatus.CONFLICT, "当前申请状态不允许撤回");
        }
    }
}
