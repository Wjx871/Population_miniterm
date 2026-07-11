package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.Household;
import com.example.population.entity.Person;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HouseholdMapper extends BaseMapper<Household> {

    /**
     * 按 household_no 加行锁，用于立户/换户主前置唯一性检查。
     */
    Household findByHouseholdNoForUpdate(@Param("householdNo") String householdNo);

    /**
     * 取家庭户户主对应人口记录（LEFT JOIN 取第一行）。
     */
    Person findHeadByHousehold(@Param("householdId") Long householdId);
}