package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.KeyPopulationCreateDTO;
import com.example.population.entity.KeyPopulation;

import java.util.List;

public interface KeyPopulationService extends IService<KeyPopulation> {

    IPage<KeyPopulation> page(long current, long size, String keyword, String keyTypeCode,
                              String managementLevelCode, String status, Long responsibleDepartmentId);

    /**
     * 登记重点人口：事务内
     * <ul>
     *   <li>校验人员存在</li>
     *   <li>校验同人员同类型不存在 ACTIVE 记录（§3.20 / §2.2.7 重复防护）</li>
     *   <li>校验申请主单存在（重大业务）</li>
     *   <li>写入登记记录</li>
     * </ul>
     */
    KeyPopulation register(KeyPopulationCreateDTO dto);

    /**
     * 解除重点管理：仅状态 ACTIVE 可被解除，状态机迁移到 RELEASED。
     */
    boolean release(Long keyId, Long releaseApplicationId);

    /**
     * 重点人口查人助手：前端在登记新重点人口时先按姓名/证件号找人。
     * 返回人员 ID + 已有重点类型列表，让前端去重防误选。
     */
    List<KeyPopulation> listActiveByPerson(Long personId);

    /**
     * 同人员同类型是否已有有效记录。
     */
    boolean existsActiveByPersonAndType(Long personId, String keyTypeCode);
}