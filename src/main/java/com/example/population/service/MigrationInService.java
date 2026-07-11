package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.MigrationInDTO;
import com.example.population.entity.MigrationIn;

import java.time.LocalDate;
import java.util.List;

public interface MigrationInService extends IService<MigrationIn> {

    IPage<MigrationIn> page(long current, long size, String keyword, String inTypeCode,
                            String toRegionCode, LocalDate startDate, LocalDate endDate);

    /**
     * 登记迁入业务（不办结）。
     */
    MigrationIn createMigrationIn(MigrationInDTO dto);

    /**
     * 办结迁入。事务内：
     *   1) 旧当前登记归档（MIGRATION_IN 入库时通常是"无旧登记"，外来迁入；同市跨区时存在）；
     *   2) 插入新当前登记；
     *   3) person.record_status_code=ACTIVE；
     *   4) 同步 household_member（CURRENT）；
     *   5) 回填 new_registration_id + completed_at。
     */
    boolean complete(Long inId, Long operatorId);

    /**
     * 按批次号查询（联办详情）。
     */
    List<MigrationIn> listByTransferBatch(String transferBatchNo);
}