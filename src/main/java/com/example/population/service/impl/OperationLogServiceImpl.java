package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.OperationLog;
import com.example.population.mapper.OperationLogMapper;
import com.example.population.service.OperationLogService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    @Override
    public IPage<OperationLog> page(long current, long size, Long userId,
                                    String operationTypeCode, String moduleName) {
        Page<OperationLog> page = new Page<>(current, size);
        LambdaQueryWrapper<OperationLog> w = new LambdaQueryWrapper<>();
        if (userId != null) {
            w.eq(OperationLog::getUserId, userId);
        }
        if (StringUtils.hasText(operationTypeCode)) {
            w.eq(OperationLog::getOperationTypeCode, operationTypeCode);
        }
        if (StringUtils.hasText(moduleName)) {
            w.eq(OperationLog::getModuleName, moduleName);
        }
        w.orderByDesc(OperationLog::getOperationTime);
        return this.page(page, w);
    }
}