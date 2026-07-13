package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.MigrationInDTO;
import com.example.population.entity.Household;
import com.example.population.entity.MigrationIn;
import com.example.population.entity.Person;
import com.example.population.entity.ResidenceArchive;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.exception.BizException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.HouseholdMapper;
import com.example.population.mapper.HouseholdMemberMapper;
import com.example.population.mapper.MigrationInMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.mapper.ResidenceArchiveMapper;
import com.example.population.mapper.ResidenceRegistrationMapper;
import com.example.population.service.MigrationInService;
import com.example.population.util.DictionaryValidator;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import com.example.population.util.SnapshotCopier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationInServiceImpl extends ServiceImpl<MigrationInMapper, MigrationIn>
        implements MigrationInService {

    private final PersonMapper personMapper;
    private final HouseholdMapper householdMapper;
    private final HouseholdMemberMapper householdMemberMapper;
    private final ResidenceRegistrationMapper registrationMapper;
    private final ResidenceArchiveMapper archiveMapper;
    private final DictionaryValidator dictionaryValidator;

    @Override
    public IPage<MigrationIn> page(long current, long size, String keyword, String inTypeCode,
                                   String toRegionCode, LocalDate startDate, LocalDate endDate) {
        Page<MigrationIn> page = PageUtil.clamp(current, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MigrationIn> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        String safeKw = SafeLike.escape(keyword);
        if (safeKw != null && !safeKw.isEmpty()) {
            w.and(w2 -> w2.like(MigrationIn::getFromAddress, safeKw)
                    .or().like(MigrationIn::getTransferBatchNo, safeKw));
        }
        if (StringUtils.hasText(inTypeCode)) {
            w.eq(MigrationIn::getInTypeCode, inTypeCode);
        }
        if (StringUtils.hasText(toRegionCode)) {
            w.eq(MigrationIn::getToRegionCode, toRegionCode);
        }
        if (startDate != null) {
            w.ge(MigrationIn::getInDate, startDate);
        }
        if (endDate != null) {
            w.le(MigrationIn::getInDate, endDate);
        }
        w.orderByDesc(MigrationIn::getInDate);
        return this.page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MigrationIn createMigrationIn(MigrationInDTO dto) {
        // 1. 字典合法性
        dictionaryValidator.assertDictEnabled("IN_TYPE", dto.getInTypeCode(), "迁入类型");
        if (StringUtils.hasText(dto.getReasonCode())) {
            dictionaryValidator.assertDictEnabled("MIGRATION_REASON", dto.getReasonCode(), "迁入原因");
        }
        // 2. 同市跨区必须有来源区划
        if ("CROSS_DISTRICT".equalsIgnoreCase(dto.getInTypeCode())
                && !StringUtils.hasText(dto.getFromRegionCode())) {
            throw new BizException(400, "同市跨区迁入(from_region_code)必填");
        }
        // 3. 人口存在
        Person person = personMapper.selectById(dto.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + dto.getPersonId() + "]不存在");
        }
        // 4. 目标家庭户存在且未销户
        Household target = householdMapper.selectById(dto.getToHouseholdId());
        if (target == null) {
            throw new NotFoundException("目标家庭户[" + dto.getToHouseholdId() + "]不存在");
        }
        if ("CANCELLED".equalsIgnoreCase(target.getStatus())) {
            throw new BizException(409, "目标家庭户[" + dto.getToHouseholdId() + "]已销户，无法迁入");
        }
        // 5. 严格去重：同 personId + transferBatchNo + inTypeCode 已存在 → 409
        if (StringUtils.hasText(dto.getTransferBatchNo())) {
            MigrationIn dup = baseMapper.findDuplicateByBatch(
                    dto.getPersonId(), dto.getTransferBatchNo(), dto.getInTypeCode());
            if (dup != null) {
                throw new BizException(409,
                        "人口[" + dto.getPersonId() + "]已在批次[" + dto.getTransferBatchNo()
                                + "]中存在相同类型[" + dto.getInTypeCode() + "]的迁入记录 ["
                                + dup.getInId() + "]，请勿重复提交");
            }
        }
        // 6. 阻断同人未办结迁入
        java.util.List<MigrationIn> pending = baseMapper.listPendingByPerson(dto.getPersonId());
        if (!pending.isEmpty()) {
            MigrationIn first = pending.get(0);
            throw new BizException(409,
                    "人口[" + dto.getPersonId() + "]存在尚未办结的迁入记录 ["
                            + first.getInId() + "]，请先办结或撤回后再发起新迁入");
        }
        // 7. 写入主表
        MigrationIn in = new MigrationIn();
        in.setApplicationId(dto.getApplicationId());
        in.setPersonId(dto.getPersonId());
        in.setInTypeCode(dto.getInTypeCode());
        in.setTransferBatchNo(dto.getTransferBatchNo());
        in.setSourceRegistrationId(dto.getSourceRegistrationId());
        in.setFromRegionCode(dto.getFromRegionCode());
        in.setFromAddress(dto.getFromAddress());
        in.setFromHouseholdNo(dto.getFromHouseholdNo());
        in.setToHouseholdId(dto.getToHouseholdId());
        in.setToRegionCode(dto.getToRegionCode());
        in.setInDate(dto.getInDate());
        in.setReasonCode(dto.getReasonCode());
        in.setNewRegistrationId(null);
        in.setOperatorId(null);
        in.setCompletedAt(null);
        baseMapper.insert(in);
        return in;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean complete(Long inId, Long operatorId) {
        MigrationIn in = baseMapper.selectById(inId);
        if (in == null) {
            throw new NotFoundException("迁入记录[" + inId + "]不存在");
        }
        if (in.getCompletedAt() != null) {
            throw new BizException(409, "该迁入记录已办结");
        }
        // 行锁人口档案：阻止同一人口的并发迁入 / 注销 / 户籍变更
        Person person = personMapper.selectByIdForUpdate(in.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + in.getPersonId() + "]不存在");
        }
        // 行锁目标户：与换户主/销户互斥
        Household targetHousehold = householdMapper.selectByIdForUpdate(in.getToHouseholdId());
        if (targetHousehold == null) {
            throw new NotFoundException("目标家庭户[" + in.getToHouseholdId() + "]不存在");
        }
        if ("CANCELLED".equalsIgnoreCase(targetHousehold.getStatus())) {
            throw new BizException(409, "目标家庭户[" + in.getToHouseholdId() + "]已销户，无法迁入");
        }

        // 1. 同市跨区：先把旧登记归档
        ResidenceRegistration oldReg = registrationMapper.findByPersonForUpdate(in.getPersonId());
        if (oldReg != null && "CROSS_DISTRICT".equalsIgnoreCase(in.getInTypeCode())) {
            // 锁源户籍户，避免被并发销户
            Household fromHousehold = householdMapper.selectByIdForUpdate(oldReg.getHouseholdId());
            ResidenceArchive snapshot = SnapshotCopier.fromRegistration(
                    oldReg,
                    person.getName(),
                    person.getIdentityTypeCode(),
                    person.getIdentityNo(),
                    "MIGRATION_OUT",
                    in.getReasonCode(),
                    in.getInDate(),
                    operatorId,
                    in.getApplicationId(),
                    fromHousehold
            );
            archiveMapper.insert(snapshot);
            registrationMapper.deleteByPersonAndId(in.getPersonId(), oldReg.getRegistrationId());
            householdMemberMapper.updatePersonStatusLeft(in.getPersonId(), in.getInDate());
        }

        // 2. 新当前登记
        ResidenceRegistration newReg = new ResidenceRegistration();
        newReg.setPersonId(in.getPersonId());
        newReg.setHouseholdId(targetHousehold.getHouseholdId());
        newReg.setRegisterTypeCode("MIGRATION_IN");
        newReg.setRegisterDate(in.getInDate());
        newReg.setRegisteredAddress(targetHousehold.getRegisteredAddress());
        newReg.setRegionCode(targetHousehold.getRegionCode());
        newReg.setStartDate(in.getInDate());
        newReg.setSourceApplicationId(in.getApplicationId());
        registrationMapper.insert(newReg);

        // 3. 人员状态置 ACTIVE
        person.setRecordStatusCode("ACTIVE");
        personMapper.updateById(person);

        // 4. 写入家庭成员 CURRENT 行
        com.example.population.entity.HouseholdMember hm = new com.example.population.entity.HouseholdMember();
        hm.setHouseholdId(targetHousehold.getHouseholdId());
        hm.setPersonId(in.getPersonId());
        // 新成员自动为 OTHER 关系；如需指定为户主，请走 changeHead
        hm.setRelationshipCode("OTHER");
        hm.setJoinDate(in.getInDate());
        hm.setMemberStatus("CURRENT");
        hm.setSourceApplicationId(in.getApplicationId());
        try {
            householdMemberMapper.insert(hm);
        } catch (org.springframework.dao.DuplicateKeyException dup) {
            // 该人口的 CURRENT 行已存在（已被并发迁入 / 历史脏数据）
            throw new BizException(409,
                    "人口[" + in.getPersonId() + "]在目标户中已存在当前成员关系，请先办迁移/注销");
        }

        in.setNewRegistrationId(newReg.getRegistrationId());
        in.setOperatorId(operatorId);
        in.setCompletedAt(LocalDateTime.now());
        return updateById(in);
    }

    @Override
    public List<MigrationIn> listByTransferBatch(String transferBatchNo) {
        if (!StringUtils.hasText(transferBatchNo)) {
            return List.of();
        }
        return baseMapper.listByTransferBatch(transferBatchNo);
    }
}
