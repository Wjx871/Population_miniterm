package com.example.population.service;

import com.example.population.entity.Household;
import com.example.population.entity.MigrationOut;
import com.example.population.entity.Person;
import com.example.population.entity.ResidenceArchive;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.exception.BizException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.HouseholdMapper;
import com.example.population.mapper.HouseholdMemberMapper;
import com.example.population.mapper.MigrationOutMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.mapper.ResidenceArchiveMapper;
import com.example.population.mapper.ResidenceRegistrationMapper;
import com.example.population.service.impl.MigrationOutServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MigrationOutServiceImpl#complete 单元测试。
 * <p>
 * 重点验证 P0-1 修复点：行锁改为按 out_id（主键）锁、且不再做冗余 selectById。
 */
@ExtendWith(MockitoExtension.class)
class MigrationOutServiceCompleteTest {

    @Mock private MigrationOutMapper outMapper;
    @Mock private PersonMapper personMapper;
    @Mock private HouseholdMapper householdMapper;
    @Mock private HouseholdMemberMapper memberMapper;
    @Mock private ResidenceRegistrationMapper regMapper;
    @Mock private ResidenceArchiveMapper archiveMapper;
    @Mock private com.example.population.util.DictionaryValidator dictionaryValidator;

    private MigrationOutServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MigrationOutServiceImpl(personMapper, householdMapper, memberMapper,
                regMapper, archiveMapper, dictionaryValidator);
        ReflectionTestUtils.setField(service, "baseMapper", outMapper);
    }

    @Test
    @DisplayName("行锁查询必须按 out_id 加锁（修复点）")
    void complete_mustLockByOutId() {
        // 无旧登记分支：跳过归档路径
        when(outMapper.findByOutIdForUpdate(7L)).thenReturn(newOut(7L, 100L, 200L, LocalDate.of(2026, 7, 1), null));
        when(personMapper.selectById(100L)).thenReturn(person(100L, "张三"));
        when(regMapper.findByPersonForUpdate(100L)).thenReturn(null);
        when(outMapper.updateById(any(MigrationOut.class))).thenReturn(1);

        service.complete(7L, 999L);

        // 关键断言：行锁查询用的是新方法 findByOutIdForUpdate，参数是 outId
        verify(outMapper).findByOutIdForUpdate(7L);
        // 必须用 updateById 回写
        verify(outMapper).updateById(any(MigrationOut.class));
        // 关键负面断言：complete 路径中 outMapper.selectById 不应该再被调用
        // （修复前的旧实现会先调 findByApplicationIdForUpdate(outId)（必然空）+ 兜底 selectById(outId)）
        verify(outMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("complete: outId 不存在 → NotFoundException")
    void complete_notFound() {
        when(outMapper.findByOutIdForUpdate(7L)).thenReturn(null);

        assertThatThrownBy(() -> service.complete(7L, 999L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("7");

        // 没找到就不应继续往下走
        verify(personMapper, never()).selectById(any());
        verify(regMapper, never()).findByPersonForUpdate(any());
    }

    @Test
    @DisplayName("complete: 已办结 → BizException(409)")
    void complete_alreadyCompleted() {
        MigrationOut existed = newOut(7L, 100L, 200L, LocalDate.of(2026, 7, 1), null);
        existed.setCompletedAt(LocalDateTime.now().minusDays(1));
        when(outMapper.findByOutIdForUpdate(7L)).thenReturn(existed);

        assertThatThrownBy(() -> service.complete(7L, 999L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已办结");

        verify(personMapper, never()).selectById(any());
    }

    @Test
    @DisplayName("complete: 有人 + 有旧登记 → 落快照 + 删登记 + LEFT 成员 + 回填")
    void complete_withOldRegistration() {
        LocalDate outDate = LocalDate.of(2026, 7, 1);
        MigrationOut out = newOut(7L, 100L, 200L, outDate, "WORK_TRANSFER");
        when(outMapper.findByOutIdForUpdate(7L)).thenReturn(out);
        when(personMapper.selectById(100L)).thenReturn(person(100L, "张三"));
        ResidenceRegistration oldReg = new ResidenceRegistration();
        oldReg.setRegistrationId(555L);
        oldReg.setPersonId(100L);
        oldReg.setHouseholdId(200L);
        oldReg.setRegisteredAddress("原户籍地址");
        oldReg.setRegionCode("110101");
        oldReg.setRegisterTypeCode("INITIAL");
        oldReg.setRegisterDate(LocalDate.of(2010, 1, 1));
        oldReg.setStartDate(LocalDate.of(2010, 1, 1));
        when(regMapper.findByPersonForUpdate(100L)).thenReturn(oldReg);
        Household fromHousehold = new Household();
        fromHousehold.setHouseholdId(200L);
        fromHousehold.setHouseholdNo("H200");
        when(householdMapper.selectById(200L)).thenReturn(fromHousehold);
        when(archiveMapper.insert(any(ResidenceArchive.class))).thenAnswer(inv -> {
            ResidenceArchive a = inv.getArgument(0);
            a.setArchiveId(88L);
            return 1;
        });
        when(outMapper.updateById(any(MigrationOut.class))).thenReturn(1);

        boolean ok = service.complete(7L, 999L);

        assertThat(ok).isTrue();

        // 1. 归档插入：捕捉参数，校验字段
        ArgumentCaptor<ResidenceArchive> arcCap = ArgumentCaptor.forClass(ResidenceArchive.class);
        verify(archiveMapper).insert(arcCap.capture());
        ResidenceArchive inserted = arcCap.getValue();
        assertThat(inserted.getPersonId()).isEqualTo(100L);
        assertThat(inserted.getHouseholdId()).isEqualTo(200L);
        assertThat(inserted.getArchiveTypeCode()).isEqualTo("MIGRATION_OUT");
        assertThat(inserted.getArchiveReasonCode()).isEqualTo("WORK_TRANSFER");
        assertThat(inserted.getPersonNameSnapshot()).isEqualTo("张三");
        assertThat(inserted.getHouseholdNoSnapshot()).isEqualTo("H200");
        assertThat(inserted.getEndDateSnapshot()).isEqualTo(outDate);
        assertThat(inserted.getArchiveOperatorId()).isEqualTo(999L);

        // 2. 删除旧登记
        verify(regMapper).deleteByPersonAndId(100L, 555L);

        // 3. 成员状态 LEFT
        verify(memberMapper).updatePersonStatusLeft(eq(100L), eq(outDate));

        // 4. 回填 out
        ArgumentCaptor<MigrationOut> outCap = ArgumentCaptor.forClass(MigrationOut.class);
        verify(outMapper).updateById(outCap.capture());
        MigrationOut updated = outCap.getValue();
        assertThat(updated.getArchiveId()).isEqualTo(88L);
        assertThat(updated.getOperatorId()).isEqualTo(999L);
        assertThat(updated.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("complete: 有人 + 无旧登记 → 跳过归档、只回填 out")
    void complete_withoutOldRegistration() {
        MigrationOut out = newOut(7L, 100L, 200L, LocalDate.of(2026, 7, 1), "OTHER");
        when(outMapper.findByOutIdForUpdate(7L)).thenReturn(out);
        when(personMapper.selectById(100L)).thenReturn(person(100L, "李四"));
        when(regMapper.findByPersonForUpdate(100L)).thenReturn(null);
        when(outMapper.updateById(any(MigrationOut.class))).thenReturn(1);

        boolean ok = service.complete(7L, 999L);

        assertThat(ok).isTrue();
        verify(archiveMapper, never()).insert(any());
        verify(regMapper, never()).deleteByPersonAndId(any(), any());
        verify(memberMapper, never()).updatePersonStatusLeft(any(), any());
        verify(outMapper).updateById(any(MigrationOut.class));
    }

    // ---------- helpers ----------

    private static MigrationOut newOut(Long outId, Long personId, Long fromHouseholdId,
                                       LocalDate outDate, String reason) {
        MigrationOut o = new MigrationOut();
        o.setOutId(outId);
        o.setPersonId(personId);
        o.setFromHouseholdId(fromHouseholdId);
        o.setOutDate(outDate);
        o.setReasonCode(reason);
        o.setApplicationId(1L);
        return o;
    }

    private static Person person(Long id, String name) {
        Person p = new Person();
        p.setPersonId(id);
        p.setName(name);
        p.setIdentityTypeCode("ID_CARD");
        p.setIdentityNo("110101199001011234");
        // P1 强化：complete() 在写迁出前要求人口档案状态必须 ACTIVE。
        // 这里给测试 helper 默认补 ACTIVE，避免单测因 mock 缺字段而误挂。
        p.setRecordStatusCode("ACTIVE");
        return p;
    }
}
