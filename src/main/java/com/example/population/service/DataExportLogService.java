package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.DataExportLog;

public interface DataExportLogService extends IService<DataExportLog> {

    IPage<DataExportLog> page(long current, long size, Long userId, String exportTypeCode, Integer sensitivityLevel);
}