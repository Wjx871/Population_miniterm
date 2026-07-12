package com.example.population.service;

import com.example.population.dto.HouseholdMemberDTO;
import com.example.population.dto.HouseholdMemberTransferDTO;
import com.example.population.entity.HouseholdMember;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.exception.BizException;
import com.example.population.exception.DuplicateException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.HouseholdMemberMapper;
import com.example.population.mapper.ResidenceRegistrationMapper;
import com.example.population.service.impl.HouseholdMemberServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * HouseholdMemberServiceImpl 单测。
 */
@ExtendWith(MockitoExtension.class)
class HouseholdMemberServiceTest {

    @Mock private HouseholdMemberMapper baseMapper;
    @Mock private ResidenceRegistrationMapper registrationMapper;

    private HouseholdMemberServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new HouseholdMemberServiceImpl(registrationMapper);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    private HouseholdMemberDTO validDto() {
        HouseholdMemberDTO dto = new HouseholdMemberDTO();
        dto.setHouseholdId(1L);
        dto.setPersonId(100L);
        dto.setRelationshipCode("SPOUSE");
        dto.setJoinDate(LocalDate.of(2025, 1, 1));
        return dto;
    }

    // ---------- addMember ----------

    @Test
    @DisplayName("addMember: 正常添加,默认状态 CURRENT")
    void addMember_ok() {
        when(registrationMapper.findByPerson(100L)).thenReturn(null);

        HouseholdMember saved = service.addMember(validDto());
        assertThat(saved.getMemberStatus()).isEqualTo("CURRENT");
        assertThat(saved.getRelationshipCode()).isEqualTo("SPOUSE");

        ArgumentCaptor<HouseholdMember> cap = ArgumentCaptor.forClass(HouseholdMember.class);
        verify(baseMapper).insert(cap.capture());
        assertThat(cap.getValue().getHouseholdId()).isEqualTo(1L);
        assertThat(cap.getValue().getPersonId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("addMember: 当前 person 户籍户号不一致时,只日志提示,仍继续添加")
    void addMember_diffHousehold() {
        ResidenceRegistration reg = new ResidenceRegistration();
        reg.setPersonId(100L);
        reg.setHouseholdId(999L);
        when(registrationMapper.findByPerson(100L)).thenReturn(reg);

        HouseholdMember saved = service.addMember(validDto());
        assertThat(saved.getPersonId()).isEqualTo(100L);
        verify(baseMapper).insert(any(HouseholdMember.class));
    }

    @Test
    @DisplayName("addMember: DB DuplicateKeyException → DuplicateException")
    void addMember_dbDuplicate() {
        when(registrationMapper.findByPerson(100L)).thenReturn(null);
        doThrow(new DuplicateKeyException("uk"))
                .when(baseMapper).insert(any(HouseholdMember.class));

        assertThatThrownBy(() -> service.addMember(validDto()))
                .isInstanceOf(DuplicateException.class)
                .hasMessageContaining("已是本户当前成员");
    }

    // ---------- removeMember ----------

    @Test
    @DisplayName("removeMember: 关系不存在 → NotFoundException")
    void remove_notFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.removeMember(1L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("removeMember: 正常移除 → LEFT + leaveDate=今天")
    void remove_ok() {
        HouseholdMember m = new HouseholdMember();
        m.setMemberId(1L);
        m.setMemberStatus("CURRENT");
        when(baseMapper.selectById(1L)).thenReturn(m);

        service.removeMember(1L);
        assertThat(m.getMemberStatus()).isEqualTo("LEFT");
        assertThat(m.getLeaveDate()).isEqualTo(LocalDate.now());
        verify(baseMapper).updateById(m);
    }

    // ---------- transferMembers ----------

    @Test
    @DisplayName("transferMembers: 人口无 CURRENT 关系 → BizException 400")
    void transfer_noCurrent() {
        HouseholdMember left = new HouseholdMember();
        left.setMemberStatus("LEFT");
        when(baseMapper.listByPerson(100L)).thenReturn(List.of(left));

        HouseholdMemberTransferDTO dto = new HouseholdMemberTransferDTO();
        dto.setPersonIds(List.of(100L));
        dto.setTargetHouseholdId(2L);
        dto.setTransferDate(LocalDate.of(2025, 6, 1));

        assertThatThrownBy(() -> service.transferMembers(dto))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(400);
        verify(baseMapper, never()).insert(any(HouseholdMember.class));
    }

    @Test
    @DisplayName("transferMembers: 多人都成功 → 旧行 LEFT, 新行 OTHER+CURRENT")
    void transfer_ok() {
        HouseholdMember old1 = new HouseholdMember();
        old1.setMemberId(10L);
        old1.setMemberStatus("CURRENT");
        HouseholdMember old2 = new HouseholdMember();
        old2.setMemberId(11L);
        old2.setMemberStatus("CURRENT");

        when(baseMapper.listByPerson(100L)).thenReturn(List.of(old1));
        when(baseMapper.listByPerson(101L)).thenReturn(List.of(old2));

        HouseholdMemberTransferDTO dto = new HouseholdMemberTransferDTO();
        dto.setPersonIds(List.of(100L, 101L));
        dto.setTargetHouseholdId(2L);
        dto.setTransferDate(LocalDate.of(2025, 6, 1));
        dto.setSourceApplicationId(999L);

        List<Long> newIds = service.transferMembers(dto);
        assertThat(newIds).hasSize(2);

        // 旧行被置 LEFT
        assertThat(old1.getMemberStatus()).isEqualTo("LEFT");
        assertThat(old2.getMemberStatus()).isEqualTo("LEFT");
        verify(baseMapper).updateById(old1);
        verify(baseMapper).updateById(old2);

        // 新行插入两次,默认 OTHER+CURRENT
        ArgumentCaptor<HouseholdMember> cap = ArgumentCaptor.forClass(HouseholdMember.class);
        verify(baseMapper, org.mockito.Mockito.times(2)).insert(cap.capture());
        for (HouseholdMember nm : cap.getAllValues()) {
            assertThat(nm.getRelationshipCode()).isEqualTo("OTHER");
            assertThat(nm.getMemberStatus()).isEqualTo("CURRENT");
            assertThat(nm.getHouseholdId()).isEqualTo(2L);
            assertThat(nm.getJoinDate()).isEqualTo(LocalDate.of(2025, 6, 1));
            assertThat(nm.getSourceApplicationId()).isEqualTo(999L);
        }
    }

    @Test
    @DisplayName("transferMembers: DB 唯一键冲突 → DuplicateException")
    void transfer_dbDuplicate() {
        HouseholdMember old = new HouseholdMember();
        old.setMemberId(10L);
        old.setMemberStatus("CURRENT");
        when(baseMapper.listByPerson(100L)).thenReturn(List.of(old));
        doThrow(new DuplicateKeyException("uk"))
                .when(baseMapper).insert(any(HouseholdMember.class));

        HouseholdMemberTransferDTO dto = new HouseholdMemberTransferDTO();
        dto.setPersonIds(List.of(100L));
        dto.setTargetHouseholdId(2L);
        dto.setTransferDate(LocalDate.of(2025, 6, 1));

        assertThatThrownBy(() -> service.transferMembers(dto))
                .isInstanceOf(DuplicateException.class);
    }

    @Test
    @DisplayName("transferMembers: personIds 为空 → 不做任何事,返回空集合")
    void transfer_empty() {
        HouseholdMemberTransferDTO dto = new HouseholdMemberTransferDTO();
        dto.setPersonIds(Collections.emptyList());
        dto.setTargetHouseholdId(2L);
        dto.setTransferDate(LocalDate.of(2025, 6, 1));

        assertThat(service.transferMembers(dto)).isEmpty();
    }
}