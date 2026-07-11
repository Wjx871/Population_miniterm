package com.example.population.exception;

/**
 * 注销/迁出前置校验失败：目标人或户仍有关联未办结业务申请单。
 */
public class HouseholdHasOutstandingApplicationException extends BizException {

    public HouseholdHasOutstandingApplicationException(String target, Long id) {
        super(409, target + "[" + id + "]仍有关联的未办结申请，请先办结或撤销");
    }
}
