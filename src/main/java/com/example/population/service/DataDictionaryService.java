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
}