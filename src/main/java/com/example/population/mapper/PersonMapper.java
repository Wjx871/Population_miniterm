package com.example.population.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.population.entity.Person;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PersonMapper extends BaseMapper<Person> {

    /**
     * 按 (identityType, identityNo) 加行锁查询；只在事务内有效。
     * 用于新增人口前的唯一约束复核，以及 update 时锁住号码组合。
     */
    Person findByIdentityForUpdate(@Param("identityTypeCode") String identityTypeCode,
                                   @Param("identityNo") String identityNo);

    /**
     * 按主键加行锁，仅在事务内有效。
     */
    Person selectByIdForUpdate(@Param("id") Long id);

    /**
     * 非加锁版本，service 层做常规查重 / 校验。
     */
    Person findByIdentity(@Param("identityTypeCode") String identityTypeCode,
                          @Param("identityNo") String identityNo);
}