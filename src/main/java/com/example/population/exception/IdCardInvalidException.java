package com.example.population.exception;

/**
 * 身份证号校验失败（GB 11643-1999 格式 + 校验位 + 出生日期合法性）。
 */
public class IdCardInvalidException extends BizException {

    public IdCardInvalidException(String message) {
        super(400, "身份证号校验失败: " + message);
    }
}
