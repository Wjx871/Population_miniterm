package com.example.population.util;

import com.example.population.exception.BizException;
import com.example.population.service.DataDictionaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 业务入参字典合法性校验工具。
 * <p>
 * 设计文档 §2.2 各业务流均要求入参编码与 data_dictionary 保持一致；本工具把校验集中为
 * fail-fast 调用，避免每个 Service 重复样板代码。
 * <p>
 * <b>用法</b>：在 Service 入口调用 {@code assertDictEnabled("HOUSEHOLD_TYPE", dto.getHouseholdTypeCode())}，
 * 不合法直接抛 BizException(400)，由 {@code GlobalExceptionHandler} 转 400。
 */
@Component
@RequiredArgsConstructor
public class DictionaryValidator {

    private final DataDictionaryService dataDictionaryService;

    /**
     * 指定 dictType + dictCode 必须已启用；空值直接放过（由 @NotNull/@NotBlank 控制必填）。
     */
    public void assertDictEnabled(String dictType, String dictCode) {
        if (!StringUtils.hasText(dictCode)) {
            return;
        }
        if (!dataDictionaryService.existsEnabled(dictType, dictCode)) {
            throw new BizException(400,
                    "字典值非法: [" + dictType + "] 不存在或未启用，code=" + dictCode);
        }
    }

    /**
     * 同上，但带自定义前缀以便业务定位。
     */
    public void assertDictEnabled(String dictType, String dictCode, String fieldLabel) {
        if (!StringUtils.hasText(dictCode)) {
            return;
        }
        if (!dataDictionaryService.existsEnabled(dictType, dictCode)) {
            throw new BizException(400,
                    fieldLabel + " 字典值非法: [" + dictType + "] 不存在或未启用，code=" + dictCode);
        }
    }
}
