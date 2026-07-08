package com.wjx871.population.person;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 人口基础信息 Mapper 单元测试。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@SpringBootTest
class PersonMapperTest {

    @Autowired
    private PersonMapper personMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM person");
        jdbcTemplate.update("""
                INSERT INTO person (
                    name, gender, id_card, birth_date, ethnicity, phone,
                    current_address, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "Zhang San", "男", "110101199901010011", "1999-01-01", "Han",
                "13800138000", "No. 1 Test Road", PersonStatus.NORMAL,
                "2026-01-01 10:00:00", "2026-01-01 10:00:00");
        jdbcTemplate.update("""
                INSERT INTO person (
                    name, gender, id_card, birth_date, ethnicity, phone,
                    current_address, status, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, "Li Si", "女", "110101200002020022", "2000-02-02", "Han",
                "13900139000", "No. 2 Test Road", PersonStatus.NORMAL,
                "2026-01-02 10:00:00", "2026-01-02 10:00:00");
    }

    @Test
    void insertCreatesPersonAndDatabaseRecordMatches() {
        Person person = new Person();
        person.setName("Wang Wu");
        person.setGender("男");
        person.setIdCard("110101200103030033");
        person.setBirthDate(LocalDate.of(2001, 3, 3));
        person.setEthnicity("Han");
        person.setPhone("13700137000");
        person.setCurrentAddress("No. 3 Test Road");
        person.setStatus(PersonStatus.NORMAL);
        person.setCreatedAt(LocalDateTime.of(2026, 1, 3, 10, 0));
        person.setUpdatedAt(LocalDateTime.of(2026, 1, 3, 10, 0));

        int affectedRows = personMapper.insertPerson(person);
        Integer actualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM person WHERE id_card = ?",
                Integer.class,
                "110101200103030033"
        );

        assertThat(affectedRows).isEqualTo(1);
        assertThat(person.getPersonId()).isNotNull();
        assertThat(actualCount).isEqualTo(1);
    }

    @Test
    void selectByIdReturnsExpectedPerson() {
        Person existing = personMapper.selectByIdCard("110101199901010011").orElseThrow();

        Person result = personMapper.selectById(existing.getPersonId()).orElseThrow();

        assertThat(result.getName()).isEqualTo("Zhang San");
        assertThat(result.getGender()).isEqualTo("男");
        assertThat(result.getPhone()).isEqualTo("13800138000");
        assertThat(result.getCurrentAddress()).isEqualTo("No. 1 Test Road");
    }

    @Test
    void selectByIdCardReturnsExpectedPerson() {
        Person result = personMapper.selectByIdCard("110101200002020022").orElseThrow();

        assertThat(result.getName()).isEqualTo("Li Si");
        assertThat(result.getGender()).isEqualTo("女");
        assertThat(result.getEthnicity()).isEqualTo("Han");
        assertThat(result.getStatus()).isEqualTo(PersonStatus.NORMAL);
    }

    @Test
    void selectListByConditionReturnsMatchingPersons() {
        long total = personMapper.countByCondition("Li", "110101", PersonStatus.NORMAL);
        List<Person> persons = personMapper.selectListByCondition("Li", "110101", PersonStatus.NORMAL, 10, 0);

        assertThat(total).isEqualTo(1);
        assertThat(persons).hasSize(1);
        assertThat(persons.get(0).getName()).isEqualTo("Li Si");
        assertThat(persons.get(0).getIdCard()).isEqualTo("110101200002020022");
    }

    @Test
    void updateChangesPersonAndDatabaseRecordMatches() {
        Person person = personMapper.selectByIdCard("110101199901010011").orElseThrow();
        person.setPhone("13600136000");
        person.setCurrentAddress("Updated Test Road");
        person.setStatus("迁出");
        person.setUpdatedAt(LocalDateTime.of(2026, 1, 4, 10, 0));

        int affectedRows = personMapper.updatePerson(person);
        Person actual = personMapper.selectById(person.getPersonId()).orElseThrow();

        assertThat(affectedRows).isEqualTo(1);
        assertThat(actual.getPhone()).isEqualTo("13600136000");
        assertThat(actual.getCurrentAddress()).isEqualTo("Updated Test Road");
        assertThat(actual.getStatus()).isEqualTo("迁出");
    }

    @Test
    void updateStatusToDeletedKeepsPersonRecordAndMarksDeleted() {
        Person person = personMapper.selectByIdCard("110101200002020022").orElseThrow();

        int affectedRows = personMapper.updateStatusToDeleted(person.getPersonId(), PersonStatus.DELETED);
        Person actual = personMapper.selectById(person.getPersonId()).orElseThrow();
        Integer actualCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM person WHERE person_id = ?",
                Integer.class,
                person.getPersonId()
        );

        assertThat(affectedRows).isEqualTo(1);
        assertThat(actualCount).isEqualTo(1);
        assertThat(actual.getStatus()).isEqualTo(PersonStatus.DELETED);
    }
}
