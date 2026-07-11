package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.AdminRegion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Collection;
import java.util.List;

@Mapper
public interface AdminRegionMapper extends BaseMapper<AdminRegion> {

    /**
     * 按编码批量取行政区划（含 city_code），用于同市跨区判断。
     */
    List<AdminRegion> findByRegionCodes(@Param("codes") Collection<String> codes);
}