package com.wjx871.population.resident;
import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.*; import org.junit.jupiter.api.Test; import org.springframework.beans.factory.annotation.Autowired; import org.springframework.boot.test.context.SpringBootTest; import org.springframework.jdbc.core.JdbcTemplate;
/** Legacy model retirement guards; class name retained so baseline test history is not silently removed. */
@SpringBootTest class ResidentMapperTest {@Autowired JdbcTemplate jdbc;
@Test void legacyResidentsTableIsNotPartOfFreshH2Schema(){assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME='RESIDENTS'",Integer.class)).isZero();}
@Test void canonicalPersonTableExists(){assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person",Integer.class)).isGreaterThanOrEqualTo(0);}
@Test void noResidentProductionPackageRemains()throws Exception{Path p=Path.of("src/main/java/com/wjx871/population/resident");assertThat(!Files.exists(p)||Files.list(p).noneMatch(x->x.toString().endsWith(".java"))).isTrue();}
@Test void noResidentMapperXmlRemains(){assertThat(Files.exists(Path.of("src/main/resources/mapper/ResidentMapper.xml"))).isFalse();}
@Test void personControllerHasNoDeleteEndpoint()throws Exception{String s=Files.readString(Path.of("src/main/java/com/wjx871/population/person/PersonController.java"));assertThat(s).doesNotContain("@DeleteMapping");}}
