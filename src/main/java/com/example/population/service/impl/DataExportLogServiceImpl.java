package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.DataExportLog;
import com.example.population.mapper.DataExportLogMapper;
import com.example.population.service.DataExportLogService;
import com.example.population.util.PageUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class DataExportLogServiceImpl extends ServiceImpl<DataExportLogMapper, DataExportLog>
        implements DataExportLogService {

    @Override
    public IPage<DataExportLog> page(long current, long size, Long userId,
                                     String exportTypeCode, Integer sensitivityLevel) {
        Page<DataExportLog> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<DataExportLog> w = new LambdaQueryWrapper<>();
        if (userId != null) {
            w.eq(DataExportLog::getUserId, userId);
        }
        if (StringUtils.hasText(exportTypeCode)) {
            w.eq(DataExportLog::getExportTypeCode, exportTypeCode);
        }
        if (sensitivityLevel != null) {
            w.eq(DataExportLog::getSensitivityLevel, sensitivityLevel);
        }
        w.orderByDesc(DataExportLog::getExportedAt);
        return this.page(page, w);
    }
}