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

@Mapper
public interface ResidentMapper {

    @Select("""
            SELECT id, name, gender, birth_date, id_card_number, phone_number,
                   province, city, district, address, active, created_at, updated_at
            FROM residents
            WHERE id = #{id}
            """)
    Optional<Resident> findById(Long id);

    @Select("""
            SELECT id, name, gender, birth_date, id_card_number, phone_number,
                   province, city, district, address, active, created_at, updated_at
            FROM residents
            WHERE id_card_number = #{idCardNumber}
            """)
    Optional<Resident> findByIdCardNumber(String idCardNumber);

    @Select("""
            SELECT COUNT(*)
            FROM residents
            WHERE id_card_number = #{idCardNumber}
            """)
    long countByIdCardNumber(String idCardNumber);

    @Select("""
            SELECT COUNT(*)
            FROM residents
            WHERE #{keyword} IS NULL
               OR LOWER(name) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
               OR id_card_number LIKE CONCAT('%', #{keyword}, '%')
            """)
    long countByKeyword(@Param("keyword") String keyword);

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

    @Delete("""
            DELETE FROM residents
            WHERE id = #{id}
            """)
    int deleteById(Long id);
}
