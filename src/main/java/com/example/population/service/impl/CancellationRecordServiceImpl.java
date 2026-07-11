package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.CancellationDTO;
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
import com.example.population.service.CancellationRecordService;
import com.example.population.util.SnapshotCopier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CancellationRecordServiceImpl
        extends ServiceImpl<CancellationRecordMapper, CancellationRecord>
        implements CancellationRecordService {

    private final PersonMapper personMapper;
    private final HouseholdMapper householdMapper;
    private final HouseholdMemberMapper householdMemberMapper;
    private final ResidenceRegistrationMapper registrationMapper;
    private final ResidenceArchiveMapper archiveMapper;

    @Override
    public IPage<CancellationRecord> page(long current, long size, String cancelObjectType, String cancelReasonCode) {
        Page<CancellationRecord> page = new Page<>(current, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<CancellationRecord> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (StringUtils.hasText(cancelObjectType)) {
            w.eq(CancellationRecord::getCancelObjectType, cancelObjectType);
        }
        if (StringUtils.hasText(cancelReasonCode)) {
            w.eq(CancellationRecord::getCancelReasonCode, cancelReasonCode);
        }
        w.orderByDesc(CancellationRecord::getCancelDate);
        return this.page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CancellationRecord createCancellation(CancellationDTO dto) {
        CancellationRecord rec = new CancellationRecord();
        rec.setCancellationNo(dto.getCancellationNo());
        rec.setApplicationId(dto.getApplicationId());
        rec.setCancelObjectType(dto.getCancelObjectType());
        rec.setPersonId(dto.getPersonId());
        rec.setHouseholdId(dto.getHouseholdId());
        rec.setCancelReasonCode(dto.getCancelReasonCode());
        rec.setCancelDate(dto.getCancelDate());
        rec.setArchiveId(null);
        rec.setOperatorId(null);
        rec.setCompletedAt(null);
        baseMapper.insert(rec);
        return rec;
    }

    @Override
    public PrecheckResult precheckPerson(Long personId) {
        Long outstanding = baseMapper.countOutstandingApplicationsByPerson(personId);
        if (outstanding != null && outstanding > 0) {
            return new PrecheckResult(false, "该人口仍有未办结的业务申请", outstanding, null);
        }
        return new PrecheckResult(true, "可注销", outstanding, null);
    }

    @Override
    public PrecheckResult precheckHousehold(Long householdId) {
        Long outstanding = baseMapper.countOutstandingApplicationsByHousehold(householdId);
        if (outstanding != null && outstanding > 0) {
            return new PrecheckResult(false, "该户仍有未办结的业务申请", outstanding, null);
        }
        Long current = baseMapper.countCurrentMembers(householdId);
        if (current != null && current > 0) {
            return new PrecheckResult(false,
                    "该户仍有 " + current + " 名当前成员，请先办理成员迁出/注销",
                    outstanding, current);
        }
        return new PrecheckResult(true, "可销户", outstanding, current);
    }

    /**
     * 人口注销：事务内走 archiveAndRemove（同迁移出），再回填 cancellation_record。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completePersonCancellation(Long cancelId, Long operatorId) {
        CancellationRecord rec = baseMapper.selectById(cancelId);
        if (rec == null) {
            throw new NotFoundException("注销记录[" + cancelId + "]不存在");
        }
        if (!"PERSON".equalsIgnoreCase(rec.getCancelObjectType())) {
            throw new BizException(400, "该记录非人口注销");
        }
        if (rec.getCompletedAt() != null) {
            throw new BizException(409, "该注销记录已办结，不可重复提交");
        }
        // 前置校验：未办结申请单
        PrecheckResult pre = precheckPerson(rec.getPersonId());
        if (!pre.passable()) {
            throw new HouseholdHasOutstandingApplicationException("人口", rec.getPersonId());
        }

        Person person = personMapper.selectById(rec.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + rec.getPersonId() + "]不存在");
        }
        // 锁当前登记
        ResidenceRegistration oldReg = registrationMapper.findByPersonForUpdate(rec.getPersonId());

        if (oldReg != null) {
            Household household = householdMapper.selectById(oldReg.getHouseholdId());
            ResidenceArchive snapshot = SnapshotCopier.fromRegistration(
                    oldReg,
                    person.getName(),
                    person.getIdentityTypeCode(),
                    person.getIdentityNo(),
                    "PERSON_CANCEL",
                    rec.getCancelReasonCode(),
                    rec.getCancelDate(),
                    operatorId,
                    rec.getApplicationId(),
                    household
            );
            archiveMapper.insert(snapshot);
            rec.setArchiveId(snapshot.getArchiveId());

            // 物理删除当前登记
            registrationMapper.deleteByPersonAndId(rec.getPersonId(), oldReg.getRegistrationId());

            // 家庭成员同步置 CANCELLED
            householdMemberMapper.updatePersonStatusCancelled(rec.getPersonId(), rec.getCancelDate());
        } else {
            log.warn("人口[{}]无当前登记可归档", rec.getPersonId());
        }

        // 更新人口记录状态
        person.setRecordStatusCode("CANCELLED");
        personMapper.updateById(person);

        // 回写注销记录
        rec.setOperatorId(operatorId);
        rec.setCompletedAt(LocalDateTime.now());
        return updateById(rec);
    }

    /**
     * 家庭户销户：前置校验无 CURRENT 成员；逐人不办业务（在销户之前应先逐人办迁移/注销），
     * 或者直接 INSERT household_cancel_archive 快照（针对无成员场景）。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean completeHouseholdCancellation(Long cancelId, Long operatorId) {
        CancellationRecord rec = baseMapper.selectById(cancelId);
        if (rec == null) {
            throw new NotFoundException("注销记录[" + cancelId + "]不存在");
        }
        if (!"HOUSEHOLD".equalsIgnoreCase(rec.getCancelObjectType())) {
            throw new BizException(400, "该记录非家庭户销户");
        }
        if (rec.getCompletedAt() != null) {
            throw new BizException(409, "该销户记录已办结，不可重复提交");
        }
        // 前置校验
        PrecheckResult pre = precheckHousehold(rec.getHouseholdId());
        if (!pre.passable()) {
            if (pre.currentMembers() != null && pre.currentMembers() > 0) {
                throw new HouseholdNotEmptyException(rec.getHouseholdId());
            }
            throw new HouseholdHasOutstandingApplicationException("家庭户", rec.getHouseholdId());
        }

        Household household = householdMapper.selectById(rec.getHouseholdId());
        if (household == null) {
            throw new NotFoundException("家庭户[" + rec.getHouseholdId() + "]不存在");
        }

        // 写户级归档快照（不依赖 person，household_cancel_archive 类型）
        // 因 residence_archive 要求 person_id 非空，我们借用 head_person_id（户主）做快照挂载
        Long headPersonId = household.getHeadPersonId();
        if (headPersonId != null) {
            Person head = personMapper.selectById(headPersonId);
            ResidenceRegistration headReg = registrationMapper.findByPerson(headPersonId);
            if (headReg != null) {
                ResidenceArchive snapshot = SnapshotCopier.fromRegistration(
                        headReg,
                        head != null ? head.getName() : "未知户主",
                        head != null ? head.getIdentityTypeCode() : "OTHER",
                        head != null ? head.getIdentityNo() : "",
                        "HOUSEHOLD_CANCEL",
                        rec.getCancelReasonCode(),
                        rec.getCancelDate(),
                        operatorId,
                        rec.getApplicationId(),
                        household
                );
                archiveMapper.insert(snapshot);
                rec.setArchiveId(snapshot.getArchiveId());
                registrationMapper.deleteByPersonAndId(headPersonId, headReg.getRegistrationId());
                householdMemberMapper.updatePersonStatusCancelled(headPersonId, rec.getCancelDate());
                if (head != null) {
                    head.setRecordStatusCode("CANCELLED");
                    personMapper.updateById(head);
                }
            }
        }

        // 户状态置 CANCELLED
        household.setStatus("CANCELLED");
        householdMapper.updateById(household);

        rec.setOperatorId(operatorId);
        rec.setCompletedAt(LocalDateTime.now());
        return updateById(rec);
    }
}
