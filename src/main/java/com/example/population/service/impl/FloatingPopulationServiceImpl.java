package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.FloatingPopulation;
import com.example.population.mapper.FloatingPopulationMapper;
import com.example.population.service.FloatingPopulationService;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FloatingPopulationServiceImpl extends ServiceImpl<FloatingPopulationMapper, FloatingPopulation>
        implements FloatingPopulationService {

    @Override
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
        w.orderByDesc(FloatingPopulation::getRegisterDate);
        return this.page(page, w);
    }
}