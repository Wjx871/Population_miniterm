package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.HouseholdCreateDTO;
import com.example.population.entity.Household;

public interface HouseholdService extends IService<Household> {

    IPage<Household> page(long current, long size, String keyword, String regionCode, String status);

    Household getDetail(Long householdId);

    /**
     * 立户。事务内校验户号唯一。
     */
    Household establishHousehold(HouseholdCreateDTO dto);

    /**
     * 更换户主。事务内：旧户主 relationship_code -> OTHER；新房主 -> HEAD；household.head_person_id 更新。
     */
    void changeHead(Long householdId, Long newHeadPersonId);

    /**
     * 销户。事务内：前置校验户内无 CURRENT 成员；逐人办归档；最后 household.status='CANCELLED'。
     */
    void disableHousehold(Long householdId, Long operatorId);
}