package com.example.population.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;

/**
 * LIKE 通配符安全包装。
 * <p>
 * MyBatis-Plus 的 {@code LambdaQueryWrapper.like} 不会转义 {@code %} {@code _} {@code \}
 * 三种 SQL 通配字符，恶意输入 {@code %} 会触发全表扫描 DoS，并可能越权枚举数据。
 * 本工具对入参做转义，并把原始字面值作为绑定参数传入，行为安全。
 */
public final class SafeLike {

    /** 入参关键字允许的最大长度，超过即丢弃（防止大对象 DoS）。 */
    public static final int MAX_KEYWORD_LENGTH = 64;

    private SafeLike() {}

    /**
     * 转义 LIKE 通配符。{@code \ % _} 全部转义为字面字符；返回 null 表示输入超长丢弃。
     */
    public static String escape(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        if (trimmed.isEmpty()) {
            return trimmed;
        }
        if (trimmed.length() > MAX_KEYWORD_LENGTH) {
            return null;
        }
        StringBuilder sb = new StringBuilder(trimmed.length() + 8);
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            switch (c) {
                case '\\', '%', '_' -> sb.append('\\').append(c);
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * 安全 LIKE：自动转义并写入 wrapper。
     * <p>
     * 当转义结果为 null（超长）或空字符串时不会写入条件，避免 SQL 异常。
     */
    public static <T> void apply(LambdaQueryWrapper<T> wrapper, SFunction<T, ?> column, String keyword) {
        String safe = escape(keyword);
        if (safe == null || safe.isEmpty()) {
            return;
        }
        wrapper.like(column, safe);
    }
}
