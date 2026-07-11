package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.FloatingPopulation;
import com.example.population.mapper.FloatingPopulationMapper;
import com.example.population.service.FloatingPopulationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class FloatingPopulationServiceImpl extends ServiceImpl<FloatingPopulationMapper, FloatingPopulation>
        implements FloatingPopulationService {

    @Override
    public IPage<FloatingPopulation> page(long current, long size, String keyword, String currentRegionCode,
                                          String status, Long personId) {
        Page<FloatingPopulation> page = new Page<>(current, size);
        LambdaQueryWrapper<FloatingPopulation> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(FloatingPopulation::getCurrentAddress, keyword)
                    .or().like(FloatingPopulation::getSourceAddress, keyword);
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