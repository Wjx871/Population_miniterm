package com.wjx871.population.common;

/**
 * 统一接口响应对象。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "ok", data);
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, "created", data);
    }

    public static <T> ApiResponse<T> fail(String message) {
        return new ApiResponse<>(false, message, null);
    }
}
