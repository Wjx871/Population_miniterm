package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.HouseholdMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface HouseholdMemberMapper extends BaseMapper<HouseholdMember> {

    /**
     * 取某户 CURRENT 成员并加行锁，用于事务内换户主/销户前置检查。
     */
    List<HouseholdMember> listCurrentMembersForUpdate(@Param("householdId") Long householdId);

    /**
     * 某人口所有户关系历史（含 LEFT/CANCELLED）。
     */
    List<HouseholdMember> listByPerson(@Param("personId") Long personId);

    /**
     * 批量更新成员状态 + 离开日期。
     */
    int updateStatusBatch(@Param("memberIds") List<Long> memberIds,
                          @Param("status") String status,
                          @Param("leaveDate") LocalDate leaveDate);

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