package com.example.population.exception;

/**
 * 手机号校验失败。
 */
public class PhoneInvalidException extends BizException {

    public PhoneInvalidException(String message) {
        super(400, "手机号校验失败: " + message);
    }
}
