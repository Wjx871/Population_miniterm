package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.MigrationOut;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MigrationOutMapper extends BaseMapper<MigrationOut> {

    /**
     * 按主键 out_id 加行锁。用于 complete 阶段防重入。
     * 必须在事务内调用，否则锁立即释放。
     */
    MigrationOut findByOutIdForUpdate(@Param("outId") Long outId);

    List<MigrationOut> listByTransferBatch(@Param("transferBatchNo") String transferBatchNo);
}