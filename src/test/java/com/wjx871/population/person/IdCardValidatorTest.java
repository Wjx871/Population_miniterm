package com.wjx871.population.person;
import static org.assertj.core.api.Assertions.*; import com.wjx871.population.common.BusinessException; import java.time.LocalDate; import org.junit.jupiter.api.*;
class IdCardValidatorTest {IdCardValidator v=new IdCardValidator();
@Test void validMale(){assertThat(v.validate("110105194912310011",LocalDate.of(1949,12,31),"M").gender()).isEqualTo("M");}
@Test void validFemale(){assertThat(v.validate("11010519491231002X",LocalDate.of(1949,12,31),"F").gender()).isEqualTo("F");}
@Test void lowerXNormalized(){assertThat(v.parse("11010519491231002x").normalized()).endsWith("X");}
@Test void badChecksum(){assertThatThrownBy(()->v.parse("110105194912310020")).isInstanceOf(BusinessException.class).hasMessageContaining("校验位");}
@Test void impossibleDate(){assertThatThrownBy(()->v.parse("110105194902310031")).isInstanceOf(BusinessException.class).hasMessageContaining("日期");}
@Test void futureDate(){String id=withCheck("11010529991231003");assertThatThrownBy(()->v.parse(id)).isInstanceOf(BusinessException.class).hasMessageContaining("晚于");}
@Test void birthMismatch(){assertThatThrownBy(()->v.validate("110105194912310011",LocalDate.of(1950,1,1),"M")).hasMessageContaining("出生日期");}
@Test void genderMismatch(){assertThatThrownBy(()->v.validate("110105194912310011",null,"F")).hasMessageContaining("性别");}
@Test void badLength(){assertThatThrownBy(()->v.parse("11010519491231003")).isInstanceOf(BusinessException.class);}
@Test void badArea(){assertThatThrownBy(()->v.parse(withCheck("00000019491231003"))).hasMessageContaining("地址码");}
private String withCheck(String p){int[] w={7,9,10,5,8,4,2,1,6,3,7,9,10,5,8,4,2};char[] c={'1','0','X','9','8','7','6','5','4','3','2'};int s=0;for(int i=0;i<17;i++)s+=(p.charAt(i)-'0')*w[i];return p+c[s%11];}}
