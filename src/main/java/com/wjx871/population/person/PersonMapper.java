package com.wjx871.population.person;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface PersonMapper {

    int insertPerson(Person person);

    Optional<Person> selectById(Long personId);

    Optional<Person> selectByIdCard(String idCard);

    long countByIdCard(String idCard);

    long countByCondition(
            @Param("name") String name,
            @Param("idCard") String idCard,
            @Param("status") String status
    );

    List<Person> selectListByCondition(
            @Param("name") String name,
            @Param("idCard") String idCard,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    int updatePerson(Person person);

    int updateStatusToDeleted(
            @Param("personId") Long personId,
            @Param("status") String status
    );
}
