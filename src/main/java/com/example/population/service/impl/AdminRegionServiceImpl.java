package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.AdminRegion;
import com.example.population.mapper.AdminRegionMapper;
import com.example.population.service.AdminRegionService;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AdminRegionServiceImpl extends ServiceImpl<AdminRegionMapper, AdminRegion> implements AdminRegionService {

    @Override
    public IPage<AdminRegion> page(long current, long size, String keyword, String levelCode) {
        Page<AdminRegion> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<AdminRegion> w = new LambdaQueryWrapper<>();
        String safeKw = SafeLike.escape(keyword);
        if (safeKw != null && !safeKw.isEmpty()) {
            w.and(w2 -> w2.like(AdminRegion::getRegionName, safeKw)
                    .or().like(AdminRegion::getRegionCode, safeKw));
        }
        if (StringUtils.hasText(levelCode)) {
            w.eq(AdminRegion::getRegionLevelCode, levelCode);
        }
        w.orderByAsc(AdminRegion::getSortNo).orderByAsc(AdminRegion::getRegionCode);
        return this.page(page, w);
    }

    @Override
    public List<AdminRegion> listChildren(String parentCode) {
        return this.list(new LambdaQueryWrapper<AdminRegion>()
                .eq(StringUtils.hasText(parentCode), AdminRegion::getParentCode, parentCode)
                .eq(AdminRegion::getEnabledFlag, 1)
                .orderByAsc(AdminRegion::getSortNo));
    }

    @Override
    public boolean updateEnabled(String regionCode, Integer enabledFlag) {
        return this.update(new LambdaUpdateWrapper<AdminRegion>()
                .eq(AdminRegion::getRegionCode, regionCode)
                .set(AdminRegion::getEnabledFlag, enabledFlag));
    }

    @Override
    public String getCityCodeByRegion(String regionCode) {
        AdminRegion region = baseMapper.selectOne(new LambdaQueryWrapper<AdminRegion>()
                .eq(AdminRegion::getRegionCode, regionCode).last("LIMIT 1"));
        return region == null ? null : region.getCityCode();
    }

    @Override
    public boolean isSameCity(String a, String b) {
        if (!StringUtils.hasText(a) || !StringUtils.hasText(b)) {
            return false;
        }
        if (a.equals(b)) {
            return true;
        }
        String cityA = getCityCodeByRegion(a);
        String cityB = getCityCodeByRegion(b);
        return cityA != null && cityA.equals(cityB);
    }
}
