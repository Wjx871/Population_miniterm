package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.MigrationOutDTO;
import com.example.population.entity.MigrationOut;

import java.time.LocalDate;
import java.util.List;

public interface MigrationOutService extends IService<MigrationOut> {

    IPage<MigrationOut> page(long current, long size, String keyword, String outTypeCode,
                             String fromRegionCode, LocalDate startDate, LocalDate endDate);

    /**
     * 登记迁出业务（不办结）。
     */
    MigrationOut createMigrationOut(MigrationOutDTO dto);

    /**
     * 办结迁出。<b>头号事务边界</b>：
     *   1) FOR UPDATE 查 out 行；
     *   2) FOR UPDATE 查 person 旧当前登记；
     *   3) INSERT residence_archive 快照；
     *   4) DELETE 当前 registration；
     *   5) UPDATE household_member 行 → LEFT；
     *   6) 回填 archive_id + completed_at。
     */
    boolean complete(Long outId, Long operatorId);

    /**
     * 按批次号查询。
     */
    List<MigrationOut> listByTransferBatch(String transferBatchNo);
}