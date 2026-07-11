package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}