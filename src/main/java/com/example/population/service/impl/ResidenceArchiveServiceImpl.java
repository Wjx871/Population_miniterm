package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.ResidenceArchive;
import com.example.population.mapper.ResidenceArchiveMapper;
import com.example.population.service.ResidenceArchiveService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ResidenceArchiveServiceImpl extends ServiceImpl<ResidenceArchiveMapper, ResidenceArchive>
        implements ResidenceArchiveService {

    @Override
    public IPage<ResidenceArchive> page(long current, long size, Long personId, Long householdId, String archiveTypeCode) {
        Page<ResidenceArchive> page = new Page<>(current, size);
        LambdaQueryWrapper<ResidenceArchive> w = new LambdaQueryWrapper<>();
        if (personId != null) {
            w.eq(ResidenceArchive::getPersonId, personId);
        }
        if (householdId != null) {
            w.eq(ResidenceArchive::getHouseholdId, householdId);
        }
        if (StringUtils.hasText(archiveTypeCode)) {
            w.eq(ResidenceArchive::getArchiveTypeCode, archiveTypeCode);
        }
        w.orderByDesc(ResidenceArchive::getArchiveDate);
        return this.page(page, w);
    }
}