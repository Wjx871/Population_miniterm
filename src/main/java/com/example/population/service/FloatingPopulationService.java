package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.FloatingPopulationCreateDTO;
import com.example.population.dto.FloatingPopulationUpdateDTO;
import com.example.population.dto.FloatingLeaveDTO;
import com.example.population.entity.FloatingPopulation;

public interface FloatingPopulationService extends IService<FloatingPopulation> {

    IPage<FloatingPopulation> page(long current, long size, String keyword, String currentRegionCode,
                                   String status, Long personId);

    /**
     * 新增流动人口登记。
     * <ul>
     *   <li>校验日期范围（arrival_date ≤ planned_leave_date 等）</li>
     *   <li>校验同人员不存在多条 ACTIVE 流动记录（C-设计 §3.18）</li>
     *   <li>校验人员存在（人口基础信息已建立）</li>
     *   <li>写表后 <b>联动</b>：将 person.record_status_code 设置为 {@code FLOATING}</li>
     * </ul>
     */
    FloatingPopulation createFloating(FloatingPopulationCreateDTO dto);

    /**
     * 更新流动人口（白名单字段）。
     */
    FloatingPopulation updateFloating(Long floatingId, FloatingPopulationUpdateDTO dto);

    /**
     * 离开登记：把 ACTIVE 记录置为 LEFT，并写入实际离开日期。
     * 若该人员没有其他 ACTIVE 流动记录，将 person.record_status_code 回滚到 ACTIVE。
     */
    FloatingPopulation leave(Long floatingId, FloatingLeaveDTO dto);

    /**
     * 到期扫描：把"预计离开日期 < 当前日期"的 ACTIVE 记录批量置为 EXPIRED。
     * 同时联动人口状态。返回扫描/更新数量。
     */
    int scanExpiring();
}