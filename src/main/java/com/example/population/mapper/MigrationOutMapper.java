package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.MigrationOut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MigrationOutMapper extends BaseMapper<MigrationOut> {

    MigrationOut findByApplicationIdForUpdate(@Param("applicationId") Long applicationId);

    List<MigrationOut> listByTransferBatch(@Param("transferBatchNo") String transferBatchNo);
}