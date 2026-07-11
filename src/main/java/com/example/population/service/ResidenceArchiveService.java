package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.ResidenceArchive;

public interface ResidenceArchiveService extends IService<ResidenceArchive> {

    IPage<ResidenceArchive> page(long current, long size, Long personId, Long householdId, String archiveTypeCode);
}