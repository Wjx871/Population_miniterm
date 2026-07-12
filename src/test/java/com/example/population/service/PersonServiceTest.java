package com.example.population.service;

import com.example.population.dto.PersonCreateDTO;
import com.example.population.dto.PersonUpdateDTO;
import com.example.population.entity.Person;
import com.example.population.exception.DuplicateException;
import com.example.population.exception.IdCardInvalidException;
import com.example.population.exception.NotFoundException;
import com.example.population.exception.PhoneInvalidException;
import com.example.population.mapper.PersonMapper;
import com.example.population.service.impl.PersonServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * PersonServiceImpl 单测。重点覆盖 createPerson 的 fail-fast 校验链:
 *   - 业务申请必传 + 材料必交闸门
 *   - 身份证格式
 *   - 手机号格式
 *   - 重复身份证号
 *   - 唯一键冲突兜底
 */
@ExtendWith(MockitoExtension.class)
class PersonServiceTest {

    @Mock
    private PersonMapper baseMapper;

    @Mock
    private ApplicationMaterialService applicationMaterialService;

    private PersonServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new PersonServiceImpl(applicationMaterialService);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        // 默认让材料闸门放行，避免污染 create_ok / create_dbDuplicate / update_partial 等用例
        Mockito.lenient().doNothing().when(applicationMaterialService)
                .assertRequiredVerified(anyLong(), anyString());
    }

    private PersonCreateDTO validDto() {
        PersonCreateDTO dto = new PersonCreateDTO();
        dto.setApplicationId(1L);
        dto.setName("张三");
        dto.setGenderCode("MALE");
        dto.setIdentityTypeCode("ID_CARD");
        dto.setIdentityNo("110101199001011237");  // 校验位正确
        dto.setBirthDate(LocalDate.of(1990, 1, 1));
        dto.setEthnicityCode("HAN");
        dto.setPhone("13800138000");
        return dto;
    }

    // ---------- createPerson ----------

    @Test
    @DisplayName("合法 DTO → 写入并返回实体")
    void create_ok() {
        PersonCreateDTO dto = validDto();
        when(baseMapper.findByIdentity(dto.getIdentityTypeCode(), dto.getIdentityNo())).thenReturn(null);

        Person saved = service.createPerson(dto);

        assertThat(saved.getName()).isEqualTo("张三");
        assertThat(saved.getRecordStatusCode()).isEqualTo("ACTIVE");
        verify(baseMapper).insert(any(Person.class));
    }

    @Test
    @DisplayName("身份证号已存在 → 抛 DuplicateException")
    void create_duplicateIdentity() {
        PersonCreateDTO dto = validDto();
        Person existing = new Person();
        existing.setPersonId(99L);
        when(baseMapper.findByIdentity(dto.getIdentityTypeCode(), dto.getIdentityNo())).thenReturn(existing);

        assertThatThrownBy(() -> service.createPerson(dto))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("110101199001011237");
        verify(baseMapper, never()).insert(any(Person.class));
    }

    @Test
    @DisplayName("身份证号格式错误 → 抛 IdCardInvalidException,不去查 DB")
    void create_badIdCard() {
        PersonCreateDTO dto = validDto();
        dto.setIdentityNo("not-a-card");

        assertThatThrownBy(() -> service.createPerson(dto))
                .isInstanceOf(IdCardInvalidException.class);
        verify(baseMapper, never()).findByIdentity(any(), any());
        verify(baseMapper, never()).insert(any(Person.class));
    }

    @Test
    @DisplayName("手机号格式错误 → 抛 PhoneInvalidException")
    void create_badPhone() {
        PersonCreateDTO dto = validDto();
        dto.setPhone("12345");

        assertThatThrownBy(() -> service.createPerson(dto))
                .isInstanceOf(PhoneInvalidException.class);
        verify(baseMapper, never()).insert(any(Person.class));
    }

    @Test
    @DisplayName("DB DuplicateKeyException 被翻译为 DuplicateException")
    void create_dbDuplicate() {
        PersonCreateDTO dto = validDto();
        when(baseMapper.findByIdentity(dto.getIdentityTypeCode(), dto.getIdentityNo())).thenReturn(null);
        org.mockito.Mockito.doThrow(new DuplicateKeyException("uk fail"))
                .when(baseMapper).insert(any(Person.class));

        assertThatThrownBy(() -> service.createPerson(dto))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("主键或唯一键冲突");
    }

    @Test
    @DisplayName("非 ID_CARD 类型不校验身份证号格式")
    void create_passportSkipIdCardCheck() {
        PersonCreateDTO dto = validDto();
        dto.setIdentityTypeCode("PASSPORT");
        dto.setIdentityNo("P12345678");  // 非 ID_CARD 不走 IdCardValidator
        when(baseMapper.findByIdentity(eq("PASSPORT"), eq("P12345678"))).thenReturn(null);

        Person saved = service.createPerson(dto);
        assertThat(saved.getIdentityTypeCode()).isEqualTo("PASSPORT");
    }

    @Test
    @DisplayName("phone 留空/null → 不会走 PhoneValidator")
    void create_phoneOptional() {
        PersonCreateDTO dto = validDto();
        dto.setPhone(null);
        when(baseMapper.findByIdentity(any(), any())).thenReturn(null);

        Person saved = service.createPerson(dto);
        assertThat(saved.getPhone()).isNull();
    }

    // ---------- updatePerson ----------

    @Test
    @DisplayName("updatePerson: 人口不存在 → NotFoundException")
    void update_notFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.updatePerson(1L, new PersonUpdateDTO()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("updatePerson: 部分字段更新")
    void update_partial() {
        Person existing = new Person();
        existing.setPersonId(1L);
        existing.setName("张三");
        existing.setGenderCode("MALE");
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(Person.class))).thenReturn(1);

        PersonUpdateDTO dto = new PersonUpdateDTO();
        dto.setName("李四");
        dto.setPhone("13900000000");

        boolean ok = service.updatePerson(1L, dto);
        assertThat(ok).isTrue();
        ArgumentCaptor<Person> cap = ArgumentCaptor.forClass(Person.class);
        verify(baseMapper).updateById(cap.capture());
        assertThat(cap.getValue().getName()).isEqualTo("李四");
        assertThat(cap.getValue().getPhone()).isEqualTo("13900000000");
        // 未更新的字段保持原值
        assertThat(cap.getValue().getGenderCode()).isEqualTo("MALE");
    }
}