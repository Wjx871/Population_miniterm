package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.DataDictionary;
import com.example.population.mapper.DataDictionaryMapper;
import com.example.population.service.DataDictionaryService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class DataDictionaryServiceImpl extends ServiceImpl<DataDictionaryMapper, DataDictionary>
        implements DataDictionaryService {

    @Override
    public List<DataDictionary> listByType(String dictType) {
        return this.list(new LambdaQueryWrapper<DataDictionary>()
                .eq(DataDictionary::getDictType, dictType)
                .orderByAsc(DataDictionary::getSortNo));
    }

    @Override
    public List<DataDictionary> listEnabledByType(String dictType) {
        return this.list(new LambdaQueryWrapper<DataDictionary>()
                .eq(DataDictionary::getDictType, dictType)
                .eq(DataDictionary::getStatus, "ENABLED")
                .orderByAsc(DataDictionary::getSortNo));
    }

    @Override
    public String getLabel(String dictType, String dictCode) {
        if (!StringUtils.hasText(dictType) || !StringUtils.hasText(dictCode)) {
            return null;
        }
        DataDictionary d = baseMapper.selectOne(new LambdaQueryWrapper<DataDictionary>()
                .eq(DataDictionary::getDictType, dictType)
                .eq(DataDictionary::getDictCode, dictCode)
                .last("LIMIT 1"));
        return d == null ? null : d.getDictLabel();
    }
}
