package com.example.population.service;

import com.example.population.dto.ResidenceRegisterDTO;
import com.example.population.entity.Household;
import com.example.population.entity.Person;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.exception.BizException;
import com.example.population.exception.NotFoundException;
import com.example.population.exception.PersonAlreadyHasRegistrationException;
import com.example.population.mapper.HouseholdMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.mapper.ResidenceRegistrationMapper;
import com.example.population.service.impl.ResidenceRegistrationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ResidenceRegistrationServiceImpl 单测。
 */
@ExtendWith(MockitoExtension.class)
class ResidenceRegistrationServiceTest {

    @Mock private ResidenceRegistrationMapper baseMapper;
    @Mock private PersonMapper personMapper;
    @Mock private HouseholdMapper householdMapper;

    private ResidenceRegistrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ResidenceRegistrationServiceImpl(personMapper, householdMapper);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private ResidenceRegisterDTO validDto() {
        ResidenceRegisterDTO dto = new ResidenceRegisterDTO();
        dto.setPersonId(100L);
        dto.setHouseholdId(1L);
        dto.setRegisterTypeCode("INITIAL");
        dto.setRegisterDate(LocalDate.of(2025, 1, 1));
        dto.setStartDate(LocalDate.of(2025, 1, 1));
        return dto;
    }

    @Test
    @DisplayName("register: startDate < registerDate → BizException")
    void register_dateOrder() {
        ResidenceRegisterDTO dto = validDto();
        dto.setRegisterDate(LocalDate.of(2025, 6, 1));
        dto.setStartDate(LocalDate.of(2025, 5, 1));

        assertThatThrownBy(() -> service.register(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("生效日期");
        verify(baseMapper, never()).insert(any(ResidenceRegistration.class));
    }

    @Test
    @DisplayName("register: 人口不存在 → NotFoundException")
    void register_personMissing() {
        when(personMapper.selectById(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.register(validDto()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("100");
        verify(baseMapper, never()).insert(any(ResidenceRegistration.class));
    }

    @Test
    @DisplayName("register: 已存在有效登记 → PersonAlreadyHasRegistrationException")
    void register_alreadyHas() {
        Person p = new Person();
        p.setPersonId(100L);
        when(personMapper.selectById(100L)).thenReturn(p);
        ResidenceRegistration existing = new ResidenceRegistration();
        existing.setRegistrationId(99L);
        existing.setPersonId(100L);
        when(baseMapper.findByPersonForUpdate(100L)).thenReturn(existing);

        assertThatThrownBy(() -> service.register(validDto()))
                .isInstanceOf(PersonAlreadyHasRegistrationException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("register: 户不存在 → NotFoundException")
    void register_householdMissing() {
        Person p = new Person();
        p.setPersonId(100L);
        when(personMapper.selectById(100L)).thenReturn(p);
        when(baseMapper.findByPersonForUpdate(100L)).thenReturn(null);
        when(householdMapper.selectById(1L)).thenReturn(null);

        assertThatThrownBy(() -> service.register(validDto()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("1");
    }

    @Test
    @DisplayName("register: 正常登记 → 写入 + 激活人口 + 默认 address/regionCode 来自户")
    void register_ok() {
        Person p = new Person();
        p.setPersonId(100L);
        p.setRecordStatusCode("INIT");
        when(personMapper.selectById(100L)).thenReturn(p);
        when(baseMapper.findByPersonForUpdate(100L)).thenReturn(null);
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setRegisteredAddress("北京市东城区");
        h.setRegionCode("110101");
        when(householdMapper.selectById(1L)).thenReturn(h);

        ResidenceRegistration saved = service.register(validDto());

        ArgumentCaptor<ResidenceRegistration> cap = ArgumentCaptor.forClass(ResidenceRegistration.class);
        verify(baseMapper).insert(cap.capture());
        ResidenceRegistration inserted = cap.getValue();
        assertThat(inserted.getPersonId()).isEqualTo(100L);
        assertThat(inserted.getHouseholdId()).isEqualTo(1L);
        assertThat(inserted.getRegisteredAddress()).isEqualTo("北京市东城区");
        assertThat(inserted.getRegionCode()).isEqualTo("110101");
        assertThat(inserted.getRegisterTypeCode()).isEqualTo("INITIAL");

        // 人口被激活
        assertThat(p.getRecordStatusCode()).isEqualTo("ACTIVE");
        verify(personMapper).updateById(p);
        assertThat(saved).isNotNull();
    }

    @Test
    @DisplayName("register: DTO 显式传入 address/regionCode 时优先使用 DTO")
    void register_dtoOverridesHousehold() {
        Person p = new Person();
        p.setPersonId(100L);
        when(personMapper.selectById(100L)).thenReturn(p);
        when(baseMapper.findByPersonForUpdate(100L)).thenReturn(null);
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setRegisteredAddress("北京市东城区");
        h.setRegionCode("110101");
        when(householdMapper.selectById(1L)).thenReturn(h);

        ResidenceRegisterDTO dto = validDto();
        dto.setRegisteredAddress("实际居住地址");
        dto.setRegionCode("110102");

        service.register(dto);

        ArgumentCaptor<ResidenceRegistration> cap = ArgumentCaptor.forClass(ResidenceRegistration.class);
        verify(baseMapper).insert(cap.capture());
        assertThat(cap.getValue().getRegisteredAddress()).isEqualTo("实际居住地址");
        assertThat(cap.getValue().getRegionCode()).isEqualTo("110102");
    }
}