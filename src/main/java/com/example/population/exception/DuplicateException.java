package com.example.population.exception;

/**
 * 唯一约束冲突异常（DB DuplicateKeyException 的业务翻译）。
 * 统一 code = 409。
 */
public class DuplicateException extends BizException {

    public DuplicateException(String message) {
        super(409, message);
    }

    public DuplicateException(String message, Throwable cause) {
        super(409, message, cause);
    }
}
