package com.example.population.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.HouseholdMemberDTO;
import com.example.population.dto.HouseholdMemberTransferDTO;
import com.example.population.entity.HouseholdMember;

import java.util.List;

public interface HouseholdMemberService extends IService<HouseholdMember> {

    List<HouseholdMember> listCurrentMembers(Long householdId);

    List<HouseholdMember> listHistoryByPerson(Long personId);

    /**
     * 添加成员关系。事务内：校验目标 person 当前户籍归属；
     * 不与现有 CURRENT 行冲突（uk_member_current_dedup）。
     */
    HouseholdMember addMember(HouseholdMemberDTO dto);

    /**
     * 移除成员（迁出/注销前置）。把 CURRENT 行置 LEFT/CANCELLED。
     */
    void removeMember(Long memberId);

    /**
     * 批量过户（同市跨区随迁）。事务内 INSERT 新 CURRENT 行 + 老行 LEFT。
     */
    List<Long> transferMembers(HouseholdMemberTransferDTO dto);
}