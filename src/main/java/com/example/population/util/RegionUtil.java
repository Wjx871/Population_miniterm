package com.example.population.util;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.population.entity.AdminRegion;
import com.example.population.mapper.AdminRegionMapper;

/**
 * 行政区划工具类。判断两个区划是否同市（依赖 admin_region.city_code）。
 */
public final class RegionUtil {

    private RegionUtil() {}

    /**
     * 判断两个区划是否在同一城市（city_code 相同）。
     * 任一为 null / 不存在时返回 false。
     */
    public static boolean isSameCity(AdminRegionMapper mapper, String regionCodeA, String regionCodeB) {
        if (regionCodeA == null || regionCodeB == null) {
            return false;
        }
        if (regionCodeA.equals(regionCodeB)) {
            return true;
        }
        AdminRegion a = mapper.selectOne(new LambdaQueryWrapper<AdminRegion>()
                .eq(AdminRegion::getRegionCode, regionCodeA).last("LIMIT 1"));
        AdminRegion b = mapper.selectOne(new LambdaQueryWrapper<AdminRegion>()
                .eq(AdminRegion::getRegionCode, regionCodeB).last("LIMIT 1"));
        if (a == null || b == null) {
            return false;
        }
        return a.getCityCode() != null && a.getCityCode().equals(b.getCityCode());
    }

    /**
     * 取区划所在 city_code；未找到返回 null。
     */
    public static String getCityCode(AdminRegionMapper mapper, String regionCode) {
        if (regionCode == null) {
            return null;
        }
        AdminRegion region = mapper.selectOne(new LambdaQueryWrapper<AdminRegion>()
                .eq(AdminRegion::getRegionCode, regionCode).last("LIMIT 1"));
        return region == null ? null : region.getCityCode();
    }
}
