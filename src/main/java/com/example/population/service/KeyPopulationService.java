package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.KeyPopulation;

public interface KeyPopulationService extends IService<KeyPopulation> {

    IPage<KeyPopulation> page(long current, long size, String keyword, String keyTypeCode,
                              String managementLevelCode, String status, Long responsibleDepartmentId);

    boolean release(Long keyId, Long releaseApplicationId);
}