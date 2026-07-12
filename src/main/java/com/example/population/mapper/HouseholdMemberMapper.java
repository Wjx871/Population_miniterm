package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.HouseholdMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;

@Mapper
public interface HouseholdMemberMapper extends BaseMapper<HouseholdMember> {

    /**
     * 某人口所有户关系历史（含 LEFT/CANCELLED）。
     */
    java.util.List<HouseholdMember> listByPerson(@Param("personId") Long personId);

    /**
     * 把某人口当前所有 CURRENT 关系置 LEFT（迁出归档使用）。
     */
    int updatePersonStatusLeft(@Param("personId") Long personId,
                               @Param("leaveDate") LocalDate leaveDate);

    /**
     * 把某人口当前所有 CURRENT 关系置 CANCELLED（人口注销使用）。
     */
    int updatePersonStatusCancelled(@Param("personId") Long personId,
                                    @Param("leaveDate") LocalDate leaveDate);
}