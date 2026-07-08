package com.wjx871.population.resident;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 居民演示模块 Mapper。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@Mapper
public interface ResidentMapper {

    /**
     * 根据居民编号查询居民信息。
     *
     * @param id 居民编号
     * @return 居民信息
     */
    Optional<Resident> findById(Long id);

    /**
     * 根据身份证号查询居民信息。
     *
     * @param idCardNumber 身份证号
     * @return 居民信息
     */
    Optional<Resident> findByIdCardNumber(String idCardNumber);

    /**
     * 统计指定身份证号的居民记录数量。
     *
     * @param idCardNumber 身份证号
     * @return 记录数量
     */
    long countByIdCardNumber(String idCardNumber);

    /**
     * 根据关键字统计居民记录数量。
     *
     * @param keyword 查询关键字
     * @return 记录数量
     */
    long countByKeyword(@Param("keyword") String keyword);

    /**
     * 根据关键字分页查询居民记录。
     *
     * @param keyword 查询关键字
     * @param limit 每页记录数
     * @param offset 起始偏移量
     * @return 居民信息列表
     */
    List<Resident> search(
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") long offset
    );

    /**
     * 新增居民信息。
     *
     * @param resident 居民信息
     * @return 影响行数
     */
    int insert(Resident resident);

    /**
     * 修改居民信息。
     *
     * @param resident 居民信息
     * @return 影响行数
     */
    int update(Resident resident);

    /**
     * 根据居民编号删除居民信息。
     *
     * @param id 居民编号
     * @return 影响行数
     */
    int deleteById(Long id);
}
