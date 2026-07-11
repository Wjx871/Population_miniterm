package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {

    IPage<OperationLog> page(long current, long size, Long userId, String operationTypeCode, String moduleName);
}