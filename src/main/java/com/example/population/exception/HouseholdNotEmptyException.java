package com.example.population.exception;

/**
 * 家庭户销户前置校验失败：户内仍有 CURRENT 成员。
 */
public class HouseholdNotEmptyException extends BizException {

    public HouseholdNotEmptyException(Long householdId) {
        super(409, "家庭户[" + householdId + "]仍有当前成员，禁止销户");
    }
}
