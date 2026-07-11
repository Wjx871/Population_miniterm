package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.ResidencePermit;

public interface ResidencePermitService extends IService<ResidencePermit> {

    IPage<ResidencePermit> page(long current, long size, Long personId, String permitTypeCode, String permitStatus);

    boolean cancel(Long permitId);
}