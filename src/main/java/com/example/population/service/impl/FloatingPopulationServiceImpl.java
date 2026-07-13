package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.annotation.DataScope;
import com.example.population.dto.DataScopeQuery;
import com.example.population.entity.FloatingPopulation;
import com.example.population.mapper.FloatingPopulationMapper;
import com.example.population.service.FloatingPopulationService;
import com.example.population.util.DataScopeHelper;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FloatingPopulationServiceImpl extends ServiceImpl<FloatingPopulationMapper, FloatingPopulation>
        implements FloatingPopulationService {

    @Override
    @DataScope(DataScope.Type.MIGRATION)
    public IPage<FloatingPopulation> page(long current, long size, String keyword, String currentRegionCode,
                                          String status, Long personId) {
        Page<FloatingPopulation> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<FloatingPopulation> w = new LambdaQueryWrapper<>();
        String safeKw = SafeLike.escape(keyword);
        if (safeKw != null && !safeKw.isEmpty()) {
            w.and(w2 -> w2.like(FloatingPopulation::getCurrentAddress, safeKw)
                    .or().like(FloatingPopulation::getSourceAddress, safeKw));
        }
        if (StringUtils.hasText(currentRegionCode)) {
            w.eq(FloatingPopulation::getCurrentRegionCode, currentRegionCode);
        }
        if (StringUtils.hasText(status)) {
            w.eq(FloatingPopulation::getStatus, status);
        }
        if (personId != null) {
            w.eq(FloatingPopulation::getPersonId, personId);
        }
        // P0: 应用数据范围过滤（设计文档 §6）
        DataScopeHelper.applyBusinessScope(w, DataScopeQuery.fromCurrentContext(),
                w2 -> ((LambdaQueryWrapper<FloatingPopulation>) w2).eq(FloatingPopulation::getHandlingDepartmentId, DataScopeQuery.fromCurrentContext().getDepartmentId()),
                w2 -> applyRegionFilter((LambdaQueryWrapper<FloatingPopulation>) w2, DataScopeQuery.fromCurrentContext()),
                null);
        w.orderByDesc(FloatingPopulation::getRegisterDate);
        return this.page(page, w);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<FloatingPopulation> applyRegionFilter(LambdaQueryWrapper<FloatingPopulation> w, DataScopeQuery ds) {
        if (ds.getVisibleRegionCodes() != null && !ds.getVisibleRegionCodes().isEmpty()) {
            return (LambdaQueryWrapper<FloatingPopulation>) w.in(FloatingPopulation::getCurrentRegionCode, ds.getVisibleRegionCodes());
        }
        if (ds.getRegionCode() != null) {
            return (LambdaQueryWrapper<FloatingPopulation>) w.eq(FloatingPopulation::getCurrentRegionCode, ds.getRegionCode());
        }
        return w;
    }
}