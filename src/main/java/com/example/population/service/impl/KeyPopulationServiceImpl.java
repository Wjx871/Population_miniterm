package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.KeyPopulationCreateDTO;
import com.example.population.entity.KeyPopulation;
import com.example.population.entity.Person;
import com.example.population.exception.BizException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.BusinessApplicationMapper;
import com.example.population.mapper.KeyPopulationMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.service.KeyPopulationService;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class KeyPopulationServiceImpl extends ServiceImpl<KeyPopulationMapper, KeyPopulation>
        implements KeyPopulationService {

    private final PersonMapper personMapper;
    private final BusinessApplicationMapper businessApplicationMapper;

    @Override
    public IPage<KeyPopulation> page(long current, long size, String keyword, String keyTypeCode,
                                     String managementLevelCode, String status, Long responsibleDepartmentId) {
        Page<KeyPopulation> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<KeyPopulation> w = new LambdaQueryWrapper<>();
        String safeKw = SafeLike.escape(keyword);
        if (safeKw != null && !safeKw.isEmpty()) {
            w.and(w2 -> w2.like(KeyPopulation::getSourceBasisSummary, safeKw)
                    .or().like(KeyPopulation::getRemark, safeKw));
        }
        if (StringUtils.hasText(keyTypeCode)) {
            w.eq(KeyPopulation::getKeyTypeCode, keyTypeCode);
        }
        if (StringUtils.hasText(managementLevelCode)) {
            w.eq(KeyPopulation::getManagementLevelCode, managementLevelCode);
        }
        if (StringUtils.hasText(status)) {
            w.eq(KeyPopulation::getStatus, status);
        }
        if (responsibleDepartmentId != null) {
            w.eq(KeyPopulation::getResponsibleDepartmentId, responsibleDepartmentId);
        }
        w.orderByDesc(KeyPopulation::getRegisterDate);
        return this.page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KeyPopulation register(KeyPopulationCreateDTO dto) {
        // 1. 重点类型必填
        if (!StringUtils.hasText(dto.getKeyTypeCode())) {
            throw new BizException(400, "重点类型（keyTypeCode）不能为空");
        }
        // 2. 人员存在
        Person person = personMapper.selectById(dto.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + dto.getPersonId() + "]不存在，请先维护人口基础信息");
        }
        // 3. 登记申请单存在（重大业务）
        if (dto.getRegisterApplicationId() != null
                && businessApplicationMapper.selectById(dto.getRegisterApplicationId()) == null) {
            throw new NotFoundException("登记申请单[" + dto.getRegisterApplicationId() + "]不存在");
        }
        // 4. 同人同类型重复防护（§3.20 C 实现要点、§2.2.7 第 5 步）
        if (existsActiveByPersonAndType(dto.getPersonId(), dto.getKeyTypeCode())) {
            throw new BizException(409,
                    "人口[" + dto.getPersonId() + "]已有类型["
                            + dto.getKeyTypeCode() + "]的有效重点登记，禁止重复");
        }
        // 5. 日期范围（manage_end_date >= manage_start_date）
        if (dto.getManageEndDate() != null && dto.getManageStartDate() != null
                && dto.getManageEndDate().isBefore(dto.getManageStartDate())) {
            throw new BizException(400, "管理结束日期不得早于管理起始日期");
        }
        // 6. 入表
        KeyPopulation entity = new KeyPopulation();
        BeanUtils.copyProperties(dto, entity);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("ACTIVE");
        }
        if (dto.getRegisterDate() == null) {
            entity.setRegisterDate(LocalDate.now());
        }
        if (dto.getManageStartDate() == null) {
            entity.setManageStartDate(LocalDate.now());
        }
        if (!StringUtils.hasText(entity.getManagementLevelCode())) {
            entity.setManagementLevelCode("NORMAL");
        }
        baseMapper.insert(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean release(Long keyId, Long releaseApplicationId) {
        KeyPopulation k = baseMapper.selectById(keyId);
        if (k == null) {
            throw new NotFoundException("重点记录[" + keyId + "]不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(k.getStatus())) {
            throw new BizException(409, "该重点记录已被解除，不可重复办理解除");
        }
        if (releaseApplicationId != null
                && businessApplicationMapper.selectById(releaseApplicationId) == null) {
            throw new NotFoundException("解除申请单[" + releaseApplicationId + "]不存在");
        }
        k.setStatus("RELEASED");
        k.setReleaseApplicationId(releaseApplicationId);
        k.setManageEndDate(LocalDate.now());
        return updateById(k);
    }

    @Override
    public List<KeyPopulation> listActiveByPerson(Long personId) {
        return this.list(new LambdaQueryWrapper<KeyPopulation>()
                .eq(KeyPopulation::getPersonId, personId)
                .eq(KeyPopulation::getStatus, "ACTIVE")
                .orderByDesc(KeyPopulation::getRegisterDate));
    }

    @Override
    public boolean existsActiveByPersonAndType(Long personId, String keyTypeCode) {
        if (personId == null || !StringUtils.hasText(keyTypeCode)) {
            return false;
        }
        return this.count(new LambdaQueryWrapper<KeyPopulation>()
                .eq(KeyPopulation::getPersonId, personId)
                .eq(KeyPopulation::getKeyTypeCode, keyTypeCode)
                .eq(KeyPopulation::getStatus, "ACTIVE")) > 0;
    }

    /**
     * 校验登记前查人助手，把"登记时锁定人员"暴露为统一入口：
     * 给前端 GET /api/persons/search 调用，本接口提供人员是否已有重点类型列表用于前端过滤。
     */
    public java.util.Set<String> activeKeyTypesOfPerson(Long personId) {
        java.util.Set<String> types = new java.util.HashSet<>();
        for (KeyPopulation k : listActiveByPerson(personId)) {
            if (k.getKeyTypeCode() != null) {
                types.add(k.getKeyTypeCode());
            }
        }
        return types;
    }
}