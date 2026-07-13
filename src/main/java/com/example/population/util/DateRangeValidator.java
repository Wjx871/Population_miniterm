package com.example.population.util;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 时间范围通用校验工具。
 * <p>
 * 用于 Controller 入参层 fail-fast：start > end 时直接抛 {@link IllegalArgumentException}，
 * 由 {@code GlobalExceptionHandler} 统一翻译为 400 响应。
 *
 * <p>P1-4 要求：所有 controller 的 startDate/endDate（以及 startTime/endTime）
 * 入参在交给 Service 前必须经过「start &lt;= end」校验，避免脏查询压库。
 */
public final class DateRangeValidator {

    private DateRangeValidator() {}

    public static void assertStartBeforeEnd(LocalDate start, LocalDate end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "时间范围非法：起始日期[" + start + "] 晚于结束日期[" + end + "]");
        }
    }

    public static void assertStartBeforeEnd(LocalDateTime start, LocalDateTime end) {
        if (start != null && end != null && start.isAfter(end)) {
            throw new IllegalArgumentException(
                    "时间范围非法：起始时间[" + start + "] 晚于结束时间[" + end + "]");
        }
    }
}
