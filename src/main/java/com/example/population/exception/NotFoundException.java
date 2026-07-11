package com.example.population.exception;

/**
 * 资源不存在异常（HTTP 语义 404，统一 code = 404）。
 */
public class NotFoundException extends BizException {

    public NotFoundException(String message) {
        super(404, message);
    }
}
