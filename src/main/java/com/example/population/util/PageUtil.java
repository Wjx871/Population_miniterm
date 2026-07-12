package com.example.population.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.PageVO;

/**
 * 分页辅助工具。
 * <p>
 * 提供 size 上限钳制，避免客户端传入 {@code size=10_000_000} 触发数据库全量扫描 DoS。
 */
public final class PageUtil {

    /** 默认单页最大记录数。 */
    public static final long MAX_PAGE_SIZE = 200;

    /** 默认单页最小记录数。 */
    public static final long MIN_PAGE_SIZE = 1;

    private PageUtil() {}

    /**
     * 钳制单页大小：小于 {@link #MIN_PAGE_SIZE} 视为 1；大于 {@link #MAX_PAGE_SIZE} 截断。
     * 同时保证 current 至少为 1。
     */
    @SuppressWarnings("unchecked")
    public static <T> Page<T> clamp(long current, long size) {
        long safeSize = size < MIN_PAGE_SIZE ? MIN_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
        long safeCurrent = current < 1 ? 1 : current;
        return (Page<T>) new Page<>(safeCurrent, safeSize);
    }

    public static <T> PageVO<T> toPageVO(Page<?> page, java.util.List<T> records) {
        return PageVO.of(page.getTotal(), page.getPages(), records, page.getCurrent(), page.getSize());
    }
}
