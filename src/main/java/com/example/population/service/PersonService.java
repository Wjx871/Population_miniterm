package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.PersonCreateDTO;
import com.example.population.dto.PersonQueryDTO;
import com.example.population.dto.PersonUpdateDTO;
import com.example.population.entity.Person;

public interface PersonService extends IService<Person> {

    IPage<Person> queryPage(PersonQueryDTO queryDTO);

    Person getByIdentity(String identityType, String identityNo);

    /**
     * 新增人口。事务内完成唯一约束复核 + insert。
     */
    Person createPerson(PersonCreateDTO dto);

    /**
     * 更新人口基础信息。身份证号组合锁住不能改。
     */
    boolean updatePerson(Long personId, PersonUpdateDTO dto);

    /**
     * 按 ID 列表查询，应用数据范围过滤（设计文档 §6：人员列表须按部门/区划过滤）。
     */
    java.util.List<Person> listByIdsWithScope(java.util.List<Long> personIds);
}