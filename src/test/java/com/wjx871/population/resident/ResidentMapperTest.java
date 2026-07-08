package com.wjx871.population.resident;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 居民演示模块 Mapper 单元测试。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@SpringBootTest
class ResidentMapperTest {

    @Autowired
    private ResidentMapper residentMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM residents");
        jdbcTemplate.update("""
                INSERT INTO residents (
                    name, gender, birth_date, id_card_number, phone_number,
                    province, city, district, address, active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "Zhang San", "MALE", "1999-01-01", "110101199901010011", "13800138000",
                "Beijing", "Beijing", "Dongcheng", "No. 1 Test Road", true);
        jdbcTemplate.update("""
                INSERT INTO residents (
                    name, gender, birth_date, id_card_number, phone_number,
                    province, city, district, address, active
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "Li Si", "FEMALE", "2000-02-02", "110101200002020022", "13900139000",
                "Shanghai", "Shanghai", "Pudong", "No. 2 Test Road", true);
    }

    @Test
    void insertCreatesResidentAndDatabaseRecordMatches() {
        Resident resident = new Resident();
        resident.setName("Wang Wu");
        resident.setGender(Gender.MALE);
        resident.setBirthDate(LocalDate.of(2001, 3, 3));
        resident.setIdCardNumber("110101200103030033");
        resident.setPhoneNumber("13700137000");
        resident.setProvince("Guangdong");
        resident.setCity("Guangzhou");
        resident.setDistrict("Tianhe");
        resident.setAddress("No. 3 Test Road");
        resident.setActive(true);

        int affectedRows = residentMapper.insert(resident);
        Integer actualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM residents WHERE id_card_number = ?",
                Integer.class,
                "110101200103030033"
        );

        assertThat(affectedRows).isEqualTo(1);
        assertThat(resident.getId()).isNotNull();
        assertThat(actualCount).isEqualTo(1);
    }

    @Test
    void selectReturnsExpectedResident() {
        Optional<Resident> result = residentMapper.findByIdCardNumber("110101199901010011");

        assertThat(result).isPresent();
        Resident resident = result.get();
        assertThat(resident.getName()).isEqualTo("Zhang San");
        assertThat(resident.getGender()).isEqualTo(Gender.MALE);
        assertThat(resident.getPhoneNumber()).isEqualTo("13800138000");
        assertThat(resident.getCity()).isEqualTo("Beijing");
    }

    @Test
    void searchReturnsMatchingResidents() {
        long total = residentMapper.countByKeyword("Li");
        List<Resident> residents = residentMapper.search("Li", 10, 0);

        assertThat(total).isEqualTo(1);
        assertThat(residents).hasSize(1);
        assertThat(residents.get(0).getName()).isEqualTo("Li Si");
        assertThat(residents.get(0).getIdCardNumber()).isEqualTo("110101200002020022");
    }

    @Test
    void updateChangesResidentAndDatabaseRecordMatches() {
        Resident resident = residentMapper.findByIdCardNumber("110101199901010011").orElseThrow();
        resident.setPhoneNumber("13600136000");
        resident.setCity("Shenzhen");
        resident.setAddress("Updated Test Road");

        int affectedRows = residentMapper.update(resident);
        Resident actual = residentMapper.findById(resident.getId()).orElseThrow();

        assertThat(affectedRows).isEqualTo(1);
        assertThat(actual.getPhoneNumber()).isEqualTo("13600136000");
        assertThat(actual.getCity()).isEqualTo("Shenzhen");
        assertThat(actual.getAddress()).isEqualTo("Updated Test Road");
    }

    @Test
    void deleteRemovesResidentAndDatabaseRecordMatches() {
        Resident resident = residentMapper.findByIdCardNumber("110101200002020022").orElseThrow();

        int affectedRows = residentMapper.deleteById(resident.getId());
        Integer actualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM residents WHERE id = ?",
                Integer.class,
                resident.getId()
        );

        assertThat(affectedRows).isEqualTo(1);
        assertThat(actualCount).isZero();
        assertThat(residentMapper.findById(resident.getId())).isEmpty();
    }
}
