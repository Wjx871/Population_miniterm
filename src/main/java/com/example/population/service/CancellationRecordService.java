package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.CancellationDTO;
import com.example.population.entity.CancellationRecord;

public interface CancellationRecordService extends IService<CancellationRecord> {

    IPage<CancellationRecord> page(long current, long size, String cancelObjectType, String cancelReasonCode);

    /**
     * 新建注销/销户记录（不办结）。由 Controller 或其他业务方调用。
     */
    CancellationRecord createCancellation(CancellationDTO dto);

    /**
     * 办结人口注销。事务内走 archiveAndRemove。
     */
    boolean completePersonCancellation(Long cancelId, Long operatorId);

    /**
     * 办结家庭户销户。事务内前置校验无 CURRENT 成员，逐人归档后置 household.status='CANCELLED'。
     */
    boolean completeHouseholdCancellation(Long cancelId, Long operatorId);

    /**
     * 人口注销前置校验：未办结申请单数。返回结构给 Controller。
     */
    PrecheckResult precheckPerson(Long personId);

    /**
     * 家庭户销户前置校验：当前成员数、未办结申请单数。
     */
    PrecheckResult precheckHousehold(Long householdId);

    /**
     * 内部使用：用于前置校验的返回结构。
     */
    record PrecheckResult(boolean passable, String message, Long outstandingApplications, Long currentMembers) {}
}