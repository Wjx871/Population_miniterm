package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.KeyPopulation;
import com.example.population.mapper.KeyPopulationMapper;
import com.example.population.service.KeyPopulationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
public class KeyPopulationServiceImpl extends ServiceImpl<KeyPopulationMapper, KeyPopulation>
        implements KeyPopulationService {

    @Override
    public IPage<KeyPopulation> page(long current, long size, String keyword, String keyTypeCode,
                                     String managementLevelCode, String status, Long responsibleDepartmentId) {
        Page<KeyPopulation> page = new Page<>(current, size);
        LambdaQueryWrapper<KeyPopulation> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(KeyPopulation::getSourceBasisSummary, keyword)
                    .or().like(KeyPopulation::getRemark, keyword);
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
    public boolean release(Long keyId, Long releaseApplicationId) {
        KeyPopulation k = this.getById(keyId);
        if (k == null) {
            return false;
        }
        k.setStatus("RELEASED");
        k.setReleaseApplicationId(releaseApplicationId);
        k.setManageEndDate(LocalDate.now());
        return this.updateById(k);
    }
}