package com.example.population.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.DataDictionary;

import java.util.List;

public interface DataDictionaryService extends IService<DataDictionary> {

    List<DataDictionary> listByType(String dictType);

    List<DataDictionary> listEnabledByType(String dictType);

    /**
     * 取单个字典项的 label；找不到返回 null。
     */
    String getLabel(String dictType, String dictCode);

    /**
     * 判断指定 dictType + dictCode 是否是已启用的合法字典项。
     * 用于 Service / Controller 入参层 fail-fast，避免脏数据落到主表。
     */
    boolean existsEnabled(String dictType, String dictCode);
}