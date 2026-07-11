package com.example.population.service;

import com.example.population.dto.ApprovalDraftDTO;

/**
 * 审批联动服务。
 * <p>
 * 用于"approval_required=1"权限码对应的接口：
 * L2 经办人提交 → 写入 sys_approval_request 草稿 → 等待 L3 审批；
 * L3 审批通过 → 反序列化 payload 并调用对应 Service 真正落地。
 */
public interface ApprovalGateService {

    /**
     * 提交草稿（用于 L2 经办人）。
     *
     * @return 新建审批单的 approvalId
     */
    Long submit(ApprovalDraftDTO draft);

    /**
     * 审批通过（用于 L3）。
     * <p>
     * 该方法会在事务内执行：①写 sys_approval_log ②更新 sys_approval_request.status = APPROVED
     * ③根据 businessType 反射调用对应 Service 进行实际数据落地。
     *
     * @return 受影响记录的主键 ID（如 personId、householdId 等）
     */
    Long approve(Long approvalId, String comment);

    /**
     * 审批驳回。
     */
    void reject(Long approvalId, String comment);
}
