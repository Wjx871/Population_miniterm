package com.wjx871.population.common;

/**
 * 统一接口响应对象。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
public record ApiResponse<T>(int code, String message, T data, long timestamp) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "ok", data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "created", data, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(500, message, null, System.currentTimeMillis());
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return new ApiResponse<>(401, message, null, System.currentTimeMillis());
    }
}
