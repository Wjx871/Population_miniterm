package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.Household;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HouseholdMapper extends BaseMapper<Household> {

    /**
     * 按 household_no 加行锁，用于立户/换户主前置唯一性检查。
     */
    Household findByHouseholdNoForUpdate(@Param("householdNo") String householdNo);
}