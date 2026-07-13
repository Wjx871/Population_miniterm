package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.OperationLog;
import com.example.population.mapper.OperationLogMapper;
import com.example.population.service.OperationLogService;
import com.example.population.util.PageUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
public class OperationLogServiceImpl extends ServiceImpl<OperationLogMapper, OperationLog>
        implements OperationLogService {

    @Override
    public IPage<OperationLog> page(long current, long size, Long userId,
                                    String operationTypeCode, String moduleName) {
        Page<OperationLog> page = PageUtil.clamp(current, size);
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

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(OperationLog log) {
        if (log == null) {
            return;
        }
        try {
            this.save(log);
        } catch (Exception ex) {
            // 日志写入失败仅记录 warn，避免放大为业务异常
            OperationLogServiceImpl.log.warn("operation_log 写入失败: {}", ex.getMessage());
        }
    }
}