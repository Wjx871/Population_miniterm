package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.MigrationIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MigrationInMapper extends BaseMapper<MigrationIn> {

    MigrationIn findByApplicationIdForUpdate(@Param("applicationId") Long applicationId);

    MigrationIn findByApplicationId(@Param("applicationId") Long applicationId);

    int existsByTransferBatch(@Param("transferBatchNo") String transferBatchNo);

    List<MigrationIn> listByTransferBatch(@Param("transferBatchNo") String transferBatchNo);
}