package com.example.population.service;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.example.population.dto.HouseholdCreateDTO;
import com.example.population.entity.Household;
import com.example.population.entity.HouseholdMember;
import com.example.population.entity.Person;
import com.example.population.exception.BizException;
import com.example.population.exception.DuplicateException;
import com.example.population.exception.HouseholdNotEmptyException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.HouseholdMapper;
import com.example.population.mapper.HouseholdMemberMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.service.impl.HouseholdServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HouseholdServiceImpl 单测。覆盖:
 *   - establishHousehold: 户号冲突、户主自动建关系、户主不存在
 *   - changeHead: 户不存在、新户主非本户成员、正常换户主
 *   - disableHousehold: 仍有成员禁止销户、正常销户
 *   - 材料必交闸门: 由 ApplicationMaterialGateTest 覆盖
 */
@ExtendWith(MockitoExtension.class)
class HouseholdServiceTest {

    @Mock private HouseholdMapper baseMapper;
    @Mock private HouseholdMemberMapper householdMemberMapper;
    @Mock private PersonMapper personMapper;
    @Mock private ApplicationMaterialService applicationMaterialService;

    private HouseholdServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HouseholdServiceImpl(householdMemberMapper, personMapper, applicationMaterialService);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
        Mockito.lenient().doNothing().when(applicationMaterialService)
                .assertRequiredVerified(anyLong(), anyString());
    }

    private HouseholdCreateDTO validDto() {
        HouseholdCreateDTO dto = new HouseholdCreateDTO();
        dto.setApplicationId(1L);
        dto.setHouseholdNo("H110101001");
        dto.setHouseholdTypeCode("FAMILY");
        dto.setHeadPersonId(100L);
        dto.setRegisteredAddress("北京市东城区");
        dto.setRegionCode("110101");
        dto.setDepartmentId(1L);
        dto.setEstablishDate(LocalDate.of(2025, 1, 1));
        return dto;
    }

    // ---------- establishHousehold ----------

    @Test
    @DisplayName("establishHousehold: 户号已被占用 → DuplicateException")
    void establish_duplicateHouseholdNo() {
        Household existing = new Household();
        existing.setHouseholdId(1L);
        existing.setHouseholdNo("H110101001");
        when(baseMapper.findByHouseholdNoForUpdate("H110101001")).thenReturn(existing);

        assertThatThrownBy(() -> service.establishHousehold(validDto()))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("H110101001");
        verify(baseMapper, never()).insert(any(Household.class));
    }

    @Test
    @DisplayName("establishHousehold: FAMILY 户带 headPersonId → 自动建 HEAD 成员行")
    void establish_familyWithHead() {
        when(baseMapper.findByHouseholdNoForUpdate("H110101001")).thenReturn(null);
        Person head = new Person();
        head.setPersonId(100L);
        head.setName("张三");
        when(personMapper.selectById(100L)).thenReturn(head);

        Household saved = service.establishHousehold(validDto());
        assertThat(saved.getHouseholdNo()).isEqualTo("H110101001");
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");

        ArgumentCaptor<HouseholdMember> cap = ArgumentCaptor.forClass(HouseholdMember.class);
        verify(householdMemberMapper).insert(cap.capture());
        assertThat(cap.getValue().getRelationshipCode()).isEqualTo("HEAD");
        assertThat(cap.getValue().getMemberStatus()).isEqualTo("CURRENT");
        assertThat(cap.getValue().getPersonId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("establishHousehold: 户主人口不存在 → NotFoundException")
    void establish_headMissing() {
        when(baseMapper.findByHouseholdNoForUpdate(any())).thenReturn(null);
        when(personMapper.selectById(100L)).thenReturn(null);

        assertThatThrownBy(() -> service.establishHousehold(validDto()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("100");
    }

    @Test
    @DisplayName("establishHousehold: COLLECTIVE 户不自动建成员")
    void establish_collectiveNoHeadInsert() {
        HouseholdCreateDTO dto = validDto();
        dto.setHouseholdTypeCode("COLLECTIVE");
        dto.setHeadPersonId(null);
        when(baseMapper.findByHouseholdNoForUpdate("H110101001")).thenReturn(null);

        service.establishHousehold(dto);

        verify(householdMemberMapper, never()).insert(any(HouseholdMember.class));
        verify(personMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("establishHousehold: status 缺省默认 ACTIVE")
    void establish_defaultStatus() {
        HouseholdCreateDTO dto = validDto();
        dto.setStatus(null);
        when(baseMapper.findByHouseholdNoForUpdate(any())).thenReturn(null);
        when(personMapper.selectById(100L)).thenReturn(new Person());

        Household saved = service.establishHousehold(dto);
        assertThat(saved.getStatus()).isEqualTo("ACTIVE");
    }

    // ---------- changeHead ----------

    @Test
    @DisplayName("changeHead: 户不存在 → NotFoundException")
    void changeHead_householdMissing() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.changeHead(1L, 200L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("changeHead: 新户主人口不存在 → NotFoundException")
    void changeHead_newHeadPersonMissing() {
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setHeadPersonId(100L);
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(personMapper.selectById(200L)).thenReturn(null);

        assertThatThrownBy(() -> service.changeHead(1L, 200L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("200");
    }

    @Test
    @DisplayName("changeHead: 新户主不是本户 CURRENT 成员 → BizException 400")
    void changeHead_notCurrentMember() {
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setHeadPersonId(100L);
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(personMapper.selectById(200L)).thenReturn(new Person());
        when(householdMemberMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatThrownBy(() -> service.changeHead(1L, 200L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(400);
    }

    @Test
    @DisplayName("changeHead: 正常换户主 → 旧 HEAD→OTHER,新成员→HEAD,户主指针更新")
    void changeHead_ok() {
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setHeadPersonId(100L);
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(personMapper.selectById(200L)).thenReturn(new Person());

        HouseholdMember oldHead = new HouseholdMember();
        oldHead.setMemberId(11L);
        oldHead.setRelationshipCode("HEAD");

        HouseholdMember newMember = new HouseholdMember();
        newMember.setMemberId(22L);
        newMember.setRelationshipCode("SPOUSE");

        // 第一次查(newMember),第二次查(oldHead)
        when(householdMemberMapper.selectOne(any(Wrapper.class)))
                .thenReturn(newMember)
                .thenReturn(oldHead);

        service.changeHead(1L, 200L);

        assertThat(newMember.getRelationshipCode()).isEqualTo("HEAD");
        assertThat(oldHead.getRelationshipCode()).isEqualTo("OTHER");
        assertThat(h.getHeadPersonId()).isEqualTo(200L);
        verify(householdMemberMapper).updateById(oldHead);
        verify(householdMemberMapper).updateById(newMember);
        verify(baseMapper).updateById(h);
    }

    @Test
    @DisplayName("changeHead: 老户主原记录缺失也能换户主")
    void changeHead_oldHeadRecordMissing() {
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setHeadPersonId(100L);
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(personMapper.selectById(200L)).thenReturn(new Person());

        HouseholdMember newMember = new HouseholdMember();
        newMember.setMemberId(22L);
        newMember.setRelationshipCode("SPOUSE");
        when(householdMemberMapper.selectOne(any(Wrapper.class)))
                .thenReturn(newMember)
                .thenReturn(null); // 旧 HEAD 行找不到

        service.changeHead(1L, 200L);

        assertThat(newMember.getRelationshipCode()).isEqualTo("HEAD");
        assertThat(h.getHeadPersonId()).isEqualTo(200L);
        // 只 update 一次(只更新新成员行,不抛错)
        verify(householdMemberMapper).updateById(newMember);
    }

    // ---------- disableHousehold ----------

    @Test
    @DisplayName("disableHousehold: 户不存在 → NotFoundException")
    void disable_householdMissing() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.disableHousehold(1L, 9L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("disableHousehold: 仍有 CURRENT 成员 → HouseholdNotEmptyException")
    void disable_hasMembers() {
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(householdMemberMapper.selectCount(any(Wrapper.class))).thenReturn(2L);

        assertThatThrownBy(() -> service.disableHousehold(1L, 9L))
                .isInstanceOf(HouseholdNotEmptyException.class);
        verify(baseMapper, never()).updateById(any(Household.class));
    }

    @Test
    @DisplayName("disableHousehold: 无成员 → 状态置 CANCELLED")
    void disable_ok() {
        Household h = new Household();
        h.setHouseholdId(1L);
        h.setStatus("ACTIVE");
        when(baseMapper.selectById(1L)).thenReturn(h);
        when(householdMemberMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        service.disableHousehold(1L, 9L);
        assertThat(h.getStatus()).isEqualTo("CANCELLED");
        verify(baseMapper).updateById(h);
    }
}