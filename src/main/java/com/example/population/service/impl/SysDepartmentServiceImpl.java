package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.SysDepartment;
import com.example.population.mapper.SysDepartmentMapper;
import com.example.population.service.SysDepartmentService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SysDepartmentServiceImpl extends ServiceImpl<SysDepartmentMapper, SysDepartment> implements SysDepartmentService {

    @Override
    public IPage<SysDepartment> page(long current, long size, String keyword, String regionCode, Long parentId) {
        Page<SysDepartment> page = new Page<>(current, size);
        LambdaQueryWrapper<SysDepartment> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(SysDepartment::getDepartmentName, keyword)
                    .or().like(SysDepartment::getDepartmentCode, keyword);
        }
        if (StringUtils.hasText(regionCode)) {
            w.eq(SysDepartment::getRegionCode, regionCode);
        }
        if (parentId != null) {
            w.eq(SysDepartment::getParentId, parentId);
        }
        w.orderByAsc(SysDepartment::getDepartmentCode);
        return this.page(page, w);
    }
}