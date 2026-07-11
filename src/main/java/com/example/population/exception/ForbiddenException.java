package com.example.population.exception;

/**
 * 403 业务无权限异常。
 * 由 {@code PermissionAspect} / {@code LevelGateAspect} 在权限校验失败时抛出，
 * 由 {@code GlobalExceptionHandler} 统一翻译为 403 Result。
 */
public class ForbiddenException extends BizException {

    public ForbiddenException(String message) {
        super(403, message);
    }
}
