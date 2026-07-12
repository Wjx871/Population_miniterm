package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.MigrationIn;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MigrationInMapper extends BaseMapper<MigrationIn> {

    List<MigrationIn> listByTransferBatch(@Param("transferBatchNo") String transferBatchNo);
}