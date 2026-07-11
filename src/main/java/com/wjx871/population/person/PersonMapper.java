package com.wjx871.population.person;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import com.wjx871.population.security.DataScopeCriteria;

/**
 * 人口基础信息 Mapper。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@Mapper
public interface PersonMapper {

    /**
     * 新增人口基础信息。
     *
     * @param person 人口基础信息
     * @return 影响行数
     */
    int insertPerson(Person person);

    /**
     * 根据人口编号查询人口基础信息。
     *
     * @param personId 人口编号
     * @return 人口基础信息
     */
    Optional<Person> selectById(Long personId);

    /**
     * 根据身份证号查询人口基础信息。
     *
     * @param idCard 身份证号
     * @return 人口基础信息
     */
    Optional<Person> selectByIdCard(String idCard);
    Optional<Person> selectScopedById(@Param("personId")Long personId,@Param("scope")DataScopeCriteria scope);
    Optional<Person> selectScopedByIdCard(@Param("idCard")String idCard,@Param("scope")DataScopeCriteria scope);
    long countScopedByCondition(@Param("name")String name,@Param("idCard")String idCard,@Param("status")String status,@Param("scope")DataScopeCriteria scope);
    List<Person> selectScopedListByCondition(@Param("name")String name,@Param("idCard")String idCard,@Param("status")String status,@Param("scope")DataScopeCriteria scope,@Param("limit")int limit,@Param("offset")long offset);

    /**
     * 统计指定身份证号的人口记录数量。
     *
     * @param idCard 身份证号
     * @return 记录数量
     */
    long countByIdCard(String idCard);

    /**
     * 根据姓名、身份证号、状态统计人口记录数量。
     *
     * @param name 姓名关键字
     * @param idCard 身份证号关键字
     * @param status 人口状态
     * @return 记录数量
     */
    long countByCondition(
            @Param("name") String name,
            @Param("idCard") String idCard,
            @Param("status") String status
    );

    /**
     * 根据姓名、身份证号、状态分页查询人口记录。
     *
     * @param name 姓名关键字
     * @param idCard 身份证号关键字
     * @param status 人口状态
     * @param limit 每页记录数
     * @param offset 起始偏移量
     * @return 人口基础信息列表
     */
    List<Person> selectListByCondition(
            @Param("name") String name,
            @Param("idCard") String idCard,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    /**
     * 修改人口基础信息。
     *
     * @param person 人口基础信息
     * @return 影响行数
     */
    int updatePerson(Person person);

    /**
     * 将人口状态更新为注销状态。
     *
     * @param personId 人口编号
     * @param status 注销状态
     * @return 影响行数
     */
    int updateStatusToDeleted(
            @Param("personId") Long personId,
            @Param("status") String status
    );
}
