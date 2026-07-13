package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.MigrationIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MigrationInMapper extends BaseMapper<MigrationIn> {

    List<MigrationIn> listByTransferBatch(@Param("transferBatchNo") String transferBatchNo);

    /**
     * 同 personId + transferBatchNo + inTypeCode 严格去重（用于创建前置校验）。
     */
    MigrationIn findDuplicateByBatch(@Param("personId") Long personId,
                                     @Param("transferBatchNo") String transferBatchNo,
                                     @Param("inTypeCode") String inTypeCode);

    /**
     * 查找该 personId 尚未办结（completed_at IS NULL）的迁入记录。
     */
    java.util.List<MigrationIn> listPendingByPerson(@Param("personId") Long personId);
}