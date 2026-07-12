package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.CancellationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface CancellationRecordMapper extends BaseMapper<CancellationRecord> {

    /**
     * 目标人口的未办结业务申请单数量。
     */
    Long countOutstandingApplicationsByPerson(@Param("personId") Long personId);

    /**
     * 目标户的未办结业务申请单数量。
     */
    Long countOutstandingApplicationsByHousehold(@Param("householdId") Long householdId);

    /**
     * 户内 CURRENT 成员数（销户前置）。
     */
    Long countCurrentMembers(@Param("householdId") Long householdId);
}