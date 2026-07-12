package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.ResidencePermit;
import com.example.population.mapper.ResidencePermitMapper;
import com.example.population.service.ResidencePermitService;
import com.example.population.util.PageUtil;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
public class ResidencePermitServiceImpl extends ServiceImpl<ResidencePermitMapper, ResidencePermit>
        implements ResidencePermitService {

    @Override
    public IPage<ResidencePermit> page(long current, long size, Long personId,
                                       String permitTypeCode, String permitStatus) {
        Page<ResidencePermit> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<ResidencePermit> w = new LambdaQueryWrapper<>();
        if (personId != null) {
            w.eq(ResidencePermit::getPersonId, personId);
        }
        if (StringUtils.hasText(permitTypeCode)) {
            w.eq(ResidencePermit::getPermitTypeCode, permitTypeCode);
        }
        if (StringUtils.hasText(permitStatus)) {
            w.eq(ResidencePermit::getPermitStatus, permitStatus);
        }
        w.orderByDesc(ResidencePermit::getCreatedAt);
        return this.page(page, w);
    }

    @Override
    public boolean cancel(Long permitId) {
        ResidencePermit p = this.getById(permitId);
        if (p == null) {
            return false;
        }
        p.setPermitStatus("CANCELLED");
        p.setCancelDate(LocalDate.now());
        return this.updateById(p);
    }
}