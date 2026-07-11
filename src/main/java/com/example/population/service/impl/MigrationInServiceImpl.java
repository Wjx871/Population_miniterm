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

    @Override
    public IPage<MigrationIn> page(long current, long size, String keyword, String inTypeCode,
                                   String toRegionCode, LocalDate startDate, LocalDate endDate) {
        Page<MigrationIn> page = new Page<>(current, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MigrationIn> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(MigrationIn::getFromAddress, keyword)
                    .or().like(MigrationIn::getTransferBatchNo, keyword);
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
        // 校验：同市跨区必须有来源区划
        if ("CROSS_DISTRICT".equalsIgnoreCase(dto.getInTypeCode())
                && !StringUtils.hasText(dto.getFromRegionCode())) {
            throw new BizException(400, "同市跨区迁入(from_region_code)必填");
        }
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
        Person person = personMapper.selectById(in.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + in.getPersonId() + "]不存在");
        }
        Household targetHousehold = householdMapper.selectById(in.getToHouseholdId());
        if (targetHousehold == null) {
            throw new NotFoundException("目标家庭户[" + in.getToHouseholdId() + "]不存在");
        }

        // 1. 同市跨区：先把旧登记归档
        ResidenceRegistration oldReg = registrationMapper.findByPersonForUpdate(in.getPersonId());
        if (oldReg != null && "CROSS_DISTRICT".equalsIgnoreCase(in.getInTypeCode())) {
            Household fromHousehold = householdMapper.selectById(oldReg.getHouseholdId());
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
        householdMemberMapper.insert(hm);

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
