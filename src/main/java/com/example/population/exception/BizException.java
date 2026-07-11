package com.example.population.exception;

/**
 * 业务异常基类。
 * 业务代码通过抛出此类（及其子类）表达业务规则违反；
 * 由 GlobalExceptionHandler 统一翻译为 Result&lt;Void&gt;。
 *
 * 业务约定 code：
 *   - 400 参数错误 / 业务规则不满足
 *   - 409 资源冲突（唯一键、状态机冲突）
 *   - 404 资源不存在
 *   - 403 业务授权失败
 */
public class BizException extends RuntimeException {

    private final int code;

    public BizException(String message) {
        super(message);
        this.code = 400;
    }

    public BizException(int code, String message) {
        super(message);
        this.code = code;
    }

    public BizException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
