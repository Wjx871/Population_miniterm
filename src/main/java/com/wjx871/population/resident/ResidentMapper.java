package com.wjx871.population.resident;

import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
    @Select("""
            SELECT id, name, gender, birth_date, id_card_number, phone_number,
                   province, city, district, address, active, created_at, updated_at
            FROM residents
            WHERE id = #{id}
            """)
    Optional<Resident> findById(Long id);

    /**
     * 根据身份证号查询居民信息。
     *
     * @param idCardNumber 身份证号
     * @return 居民信息
     */
    @Select("""
            SELECT id, name, gender, birth_date, id_card_number, phone_number,
                   province, city, district, address, active, created_at, updated_at
            FROM residents
            WHERE id_card_number = #{idCardNumber}
            """)
    Optional<Resident> findByIdCardNumber(String idCardNumber);

    /**
     * 统计指定身份证号的居民记录数量。
     *
     * @param idCardNumber 身份证号
     * @return 记录数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM residents
            WHERE id_card_number = #{idCardNumber}
            """)
    long countByIdCardNumber(String idCardNumber);

    /**
     * 根据关键字统计居民记录数量。
     *
     * @param keyword 查询关键字
     * @return 记录数量
     */
    @Select("""
            SELECT COUNT(*)
            FROM residents
            WHERE #{keyword} IS NULL
               OR LOWER(name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
               OR id_card_number LIKE CONCAT('%', #{keyword}, '%')
            """)
    long countByKeyword(@Param("keyword") String keyword);

    /**
     * 根据关键字分页查询居民记录。
     *
     * @param keyword 查询关键字
     * @param limit 每页记录数
     * @param offset 起始偏移量
     * @return 居民信息列表
     */
    @Select("""
            SELECT id, name, gender, birth_date, id_card_number, phone_number,
                   province, city, district, address, active, created_at, updated_at
            FROM residents
            WHERE #{keyword} IS NULL
               OR LOWER(name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
               OR id_card_number LIKE CONCAT('%', #{keyword}, '%')
            ORDER BY id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
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
    @Insert("""
            INSERT INTO residents (
                name, gender, birth_date, id_card_number, phone_number,
                province, city, district, address, active
            ) VALUES (
                #{name}, #{gender}, #{birthDate}, #{idCardNumber}, #{phoneNumber},
                #{province}, #{city}, #{district}, #{address}, #{active}
            )
            """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(Resident resident);

    /**
     * 修改居民信息。
     *
     * @param resident 居民信息
     * @return 影响行数
     */
    @Update("""
            UPDATE residents
            SET name = #{name},
                gender = #{gender},
                birth_date = #{birthDate},
                id_card_number = #{idCardNumber},
                phone_number = #{phoneNumber},
                province = #{province},
                city = #{city},
                district = #{district},
                address = #{address},
                active = #{active}
            WHERE id = #{id}
            """)
    int update(Resident resident);

    /**
     * 根据居民编号删除居民信息。
     *
     * @param id 居民编号
     * @return 影响行数
     */
    @Delete("""
            DELETE FROM residents
            WHERE id = #{id}
            """)
    int deleteById(Long id);
}
