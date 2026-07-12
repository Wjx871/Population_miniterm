package com.wjx871.population.audit;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper {
    int insert(OperationLog operationLog);
}
