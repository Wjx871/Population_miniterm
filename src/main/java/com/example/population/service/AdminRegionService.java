package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.AdminRegion;

import java.util.List;

public interface AdminRegionService extends IService<AdminRegion> {

    IPage<AdminRegion> page(long current, long size, String keyword, String levelCode);

    List<AdminRegion> listChildren(String parentCode);

    boolean updateEnabled(String regionCode, Integer enabledFlag);

    /**
     * 取区划 city_code；不存在返回 null。
     */
    String getCityCodeByRegion(String regionCode);

    /**
     * 判断两个区划是否同市。
     */
    boolean isSameCity(String regionCodeA, String regionCodeB);
}