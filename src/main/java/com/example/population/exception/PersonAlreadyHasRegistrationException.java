package com.example.population.exception;

/**
 * 一人一条有效当前户籍登记冲突。
 * 当给一个已经有 ACTIVE 登记的人口再次登记时抛出（code = 409）。
 */
public class PersonAlreadyHasRegistrationException extends BizException {

    public PersonAlreadyHasRegistrationException(Long personId) {
        super(409, "人口[" + personId + "]已存在有效的户籍登记，不可重复登记");
    }
}
