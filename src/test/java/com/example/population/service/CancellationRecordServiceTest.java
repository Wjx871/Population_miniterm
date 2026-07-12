package com.example.population.service;

import com.example.population.entity.CancellationRecord;
import com.example.population.entity.Household;
import com.example.population.entity.Person;
import com.example.population.entity.ResidenceArchive;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.exception.BizException;
import com.example.population.exception.HouseholdHasOutstandingApplicationException;
import com.example.population.exception.HouseholdNotEmptyException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.CancellationRecordMapper;
import com.example.population.mapper.HouseholdMapper;
import com.example.population.mapper.HouseholdMemberMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.mapper.ResidenceArchiveMapper;
import com.example.population.mapper.ResidenceRegistrationMapper;
import com.example.population.service.impl.CancellationRecordServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * CancellationRecordServiceImpl 单测。覆盖:
 *   - precheckPerson / precheckHousehold(无事务,纯计数)
 *   - completePersonCancellation 关键异常分支
 *   - completeHouseholdCancellation 关键异常分支
 */
@ExtendWith(MockitoExtension.class)
class CancellationRecordServiceTest {

    @Mock private CancellationRecordMapper baseMapper;
    @Mock private PersonMapper personMapper;
    @Mock private HouseholdMapper householdMapper;
    @Mock private HouseholdMemberMapper householdMemberMapper;
    @Mock private ResidenceRegistrationMapper registrationMapper;
    @Mock private ResidenceArchiveMapper archiveMapper;

    private CancellationRecordServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new CancellationRecordServiceImpl(personMapper, householdMapper,
                householdMemberMapper, registrationMapper, archiveMapper);
        ReflectionTestUtils.setField(service, "baseMapper", baseMapper);
    }

    // ---------- precheckPerson ----------

    @Test
    @DisplayName("precheckPerson: 无未办结申请 → 可注销")
    void precheckPerson_passable() {
        when(baseMapper.countOutstandingApplicationsByPerson(1L)).thenReturn(0L);
        var r = service.precheckPerson(1L);
        assertThat(r.passable()).isTrue();
        assertThat(r.message()).contains("可注销");
        assertThat(r.outstandingApplications()).isEqualTo(0L);
    }

    @Test
    @DisplayName("precheckPerson: 存在未办结申请 → 不可注销")
    void precheckPerson_blocked() {
        when(baseMapper.countOutstandingApplicationsByPerson(1L)).thenReturn(2L);
        var r = service.precheckPerson(1L);
        assertThat(r.passable()).isFalse();
        assertThat(r.outstandingApplications()).isEqualTo(2L);
        assertThat(r.message()).contains("未办结");
    }

    // ---------- precheckHousehold ----------

    @Test
    @DisplayName("precheckHousehold: 无未办结且无成员 → 可销户")
    void precheckHousehold_passable() {
        when(baseMapper.countOutstandingApplicationsByHousehold(1L)).thenReturn(0L);
        when(baseMapper.countCurrentMembers(1L)).thenReturn(0L);
        var r = service.precheckHousehold(1L);
        assertThat(r.passable()).isTrue();
    }

    @Test
    @DisplayName("precheckHousehold: 仍有未办结 → 不可销户")
    void precheckHousehold_outstanding() {
        when(baseMapper.countOutstandingApplicationsByHousehold(1L)).thenReturn(1L);
        var r = service.precheckHousehold(1L);
        assertThat(r.passable()).isFalse();
        assertThat(r.message()).contains("未办结");
    }

    @Test
    @DisplayName("precheckHousehold: 有当前成员 → 不可销户,返回成员数")
    void precheckHousehold_currentMembers() {
        when(baseMapper.countOutstandingApplicationsByHousehold(1L)).thenReturn(0L);
        when(baseMapper.countCurrentMembers(1L)).thenReturn(3L);
        var r = service.precheckHousehold(1L);
        assertThat(r.passable()).isFalse();
        assertThat(r.currentMembers()).isEqualTo(3L);
        assertThat(r.message()).contains("3");
    }

    // ---------- completePersonCancellation ----------

    @Test
    @DisplayName("completePersonCancellation: 注销记录不存在 → NotFoundException")
    void completePerson_notFound() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThatThrownBy(() -> service.completePersonCancellation(1L, 9L))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("completePersonCancellation: 记录类型不是 PERSON → BizException 400")
    void completePerson_wrongType() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("HOUSEHOLD");
        rec.setPersonId(11L);
        when(baseMapper.selectById(1L)).thenReturn(rec);

        assertThatThrownBy(() -> service.completePersonCancellation(1L, 9L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(400);
    }

    @Test
    @DisplayName("completePersonCancellation: 已办结不可重复 → BizException 409")
    void completePerson_alreadyDone() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("PERSON");
        rec.setPersonId(11L);
        rec.setCompletedAt(java.time.LocalDateTime.now());
        when(baseMapper.selectById(1L)).thenReturn(rec);

        assertThatThrownBy(() -> service.completePersonCancellation(1L, 9L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(409);
    }

    @Test
    @DisplayName("completePersonCancellation: 仍有未办结申请 → HouseholdHasOutstandingApplicationException")
    void completePerson_outstanding() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("PERSON");
        rec.setPersonId(11L);
        rec.setCancelReasonCode("DEATH");
        rec.setCancelDate(java.time.LocalDate.of(2025, 1, 1));
        when(baseMapper.selectById(1L)).thenReturn(rec);
        when(baseMapper.countOutstandingApplicationsByPerson(11L)).thenReturn(1L);

        assertThatThrownBy(() -> service.completePersonCancellation(1L, 9L))
                .isInstanceOf(HouseholdHasOutstandingApplicationException.class)
                .hasMessageContaining("人口");
    }

    @Test
    @DisplayName("completePersonCancellation: 人口不存在 → NotFoundException")
    void completePerson_personMissing() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("PERSON");
        rec.setPersonId(11L);
        rec.setCancelDate(java.time.LocalDate.of(2025, 1, 1));
        when(baseMapper.selectById(1L)).thenReturn(rec);
        when(baseMapper.countOutstandingApplicationsByPerson(11L)).thenReturn(0L);
        when(personMapper.selectById(11L)).thenReturn(null);

        assertThatThrownBy(() -> service.completePersonCancellation(1L, 9L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("11");
    }

    // ---------- completeHouseholdCancellation ----------

    @Test
    @DisplayName("completeHouseholdCancellation: 户内仍有成员 → HouseholdNotEmptyException")
    void completeHousehold_notEmpty() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("HOUSEHOLD");
        rec.setHouseholdId(10L);
        rec.setCancelDate(java.time.LocalDate.of(2025, 1, 1));
        when(baseMapper.selectById(1L)).thenReturn(rec);
        when(baseMapper.countOutstandingApplicationsByHousehold(10L)).thenReturn(0L);
        when(baseMapper.countCurrentMembers(10L)).thenReturn(2L);

        assertThatThrownBy(() -> service.completeHouseholdCancellation(1L, 9L))
                .isInstanceOf(HouseholdNotEmptyException.class);
    }

    @Test
    @DisplayName("completeHouseholdCancellation: 仍有未办结 → HouseholdHasOutstandingApplicationException")
    void completeHousehold_outstanding() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("HOUSEHOLD");
        rec.setHouseholdId(10L);
        when(baseMapper.selectById(1L)).thenReturn(rec);
        when(baseMapper.countOutstandingApplicationsByHousehold(10L)).thenReturn(1L);

        assertThatThrownBy(() -> service.completeHouseholdCancellation(1L, 9L))
                .isInstanceOf(HouseholdHasOutstandingApplicationException.class);
    }

    @Test
    @DisplayName("completeHouseholdCancellation: 记录类型不对 → BizException 400")
    void completeHousehold_wrongType() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("PERSON");
        when(baseMapper.selectById(1L)).thenReturn(rec);

        assertThatThrownBy(() -> service.completeHouseholdCancellation(1L, 9L))
                .isInstanceOf(BizException.class)
                .extracting("code").isEqualTo(400);
    }

    @Test
    @DisplayName("completeHouseholdCancellation: 户不存在 → NotFoundException")
    void completeHousehold_householdMissing() {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancelId(1L);
        rec.setCancelObjectType("HOUSEHOLD");
        rec.setHouseholdId(10L);
        when(baseMapper.selectById(1L)).thenReturn(rec);
        when(baseMapper.countOutstandingApplicationsByHousehold(10L)).thenReturn(0L);
        when(baseMapper.countCurrentMembers(10L)).thenReturn(0L);
        when(householdMapper.selectByIdForUpdate(10L)).thenReturn(null);

        assertThatThrownBy(() -> service.completeHouseholdCancellation(1L, 9L))
                .isInstanceOf(NotFoundException.class);
    }
}