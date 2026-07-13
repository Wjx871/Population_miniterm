package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.OperationLog;

public interface OperationLogService extends IService<OperationLog> {

    IPage<OperationLog> page(long current, long size, Long userId, String operationTypeCode, String moduleName);

    /**
     * 异步写一条业务操作日志。由 {@code OperationLogAspect} 在方法返回后调用。
     * <p>
     * 新事务（REQUIRES_NEW）执行，避免写入失败污染主业务事务；
     * 写库异常仅记录 warn 不抛出，避免监控系统把日志失败放大为业务故障。
     *
     * @param log  已填充好的日志实体，{@code operationTime} 由 MetaObjectHandler 自动填充
     */
    void record(com.example.population.entity.OperationLog log);
}