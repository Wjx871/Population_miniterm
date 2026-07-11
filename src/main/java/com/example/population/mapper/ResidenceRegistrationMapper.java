package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.ResidenceRegistration;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ResidenceRegistrationMapper extends BaseMapper<ResidenceRegistration> {

    /**
     * 按 person_id 加行锁查询当前户籍登记。事务内使用，配合 uk_registration_person 唯一约束。
     * 当出现并发 INSERT 时，先到者锁住行，后到者 select 后会发现已存在，触发业务校验。
     */
    ResidenceRegistration findByPersonForUpdate(@Param("personId") Long personId);

    /**
     * 非锁版本（普通查询）。
     */
    ResidenceRegistration findByPerson(@Param("personId") Long personId);

    /**
     * 物理删除。迁出/注销事务中使用：必须先生成 residence_archive 快照再 DELETE。
     */
    int deleteByPersonAndId(@Param("personId") Long personId,
                            @Param("registrationId") Long registrationId);
}