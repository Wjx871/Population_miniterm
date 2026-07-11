package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.FloatingPopulation;

public interface FloatingPopulationService extends IService<FloatingPopulation> {

    IPage<FloatingPopulation> page(long current, long size, String keyword, String currentRegionCode,
                                   String status, Long personId);
}