package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.FloatingPopulation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface FloatingPopulationMapper extends BaseMapper<FloatingPopulation> {

    /**
     * 按人员 ID 锁住所有流动记录（事务内）。
     */
    List<FloatingPopulation> listByPersonForUpdate(@Param("personId") Long personId);

    /**
     * 批量将预计离开日期早于阈值的 ACTIVE 记录置为 EXPIRED。返回受影响行数。
     */
    int markExpiringAsExpired(@Param("today") LocalDate today);
}