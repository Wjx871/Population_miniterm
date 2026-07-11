package com.example.population.util;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.PageVO;

import java.util.List;

public final class PageUtil {

    private PageUtil() {}

    public static <T> PageVO<T> toPageVO(Page<?> page, List<T> records) {
        return PageVO.of(page.getTotal(), page.getPages(), records, page.getCurrent(), page.getSize());
    }
}