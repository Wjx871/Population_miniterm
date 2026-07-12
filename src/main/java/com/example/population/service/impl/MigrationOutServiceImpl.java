package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.MigrationOutDTO;
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
import com.example.population.service.MigrationOutService;
import com.example.population.util.SnapshotCopier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 迁出业务 ServiceImpl。
 *
 * <p>头号事务边界：complete 方法负责在事务内完成
 * <ol>
 *   <li>FOR UPDATE 锁定 out 行（防重入）</li>
 *   <li>FOR UPDATE 锁定 person 的当前居住登记</li>
 *   <li>INSERT residence_archive 快照（包含 person_name 从外部注入）</li>
 *   <li>DELETE 旧当前登记（满足一人一条）</li>
 *   <li>UPDATE household_member 状态 LEFT/leave_date</li>
 *   <li>UPDATE migration_out 回填 archive_id + completed_at</li>
 * </ol>
 * 任何一步失败，整体回滚，保证「不可分离的原子性」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrationOutServiceImpl extends ServiceImpl<MigrationOutMapper, MigrationOut>
        implements MigrationOutService {

    private final PersonMapper personMapper;
    private final HouseholdMapper householdMapper;
    private final HouseholdMemberMapper householdMemberMapper;
    private final ResidenceRegistrationMapper registrationMapper;
    private final ResidenceArchiveMapper archiveMapper;

    @Override
    public IPage<MigrationOut> page(long current, long size, String keyword, String outTypeCode,
                                    String fromRegionCode, LocalDate startDate, LocalDate endDate) {
        Page<MigrationOut> page = new Page<>(current, size);
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<MigrationOut> w =
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(MigrationOut::getToAddress, keyword)
                    .or().like(MigrationOut::getTransferBatchNo, keyword);
        }
        if (StringUtils.hasText(outTypeCode)) {
            w.eq(MigrationOut::getOutTypeCode, outTypeCode);
        }
        if (StringUtils.hasText(fromRegionCode)) {
            w.eq(MigrationOut::getFromRegionCode, fromRegionCode);
        }
        if (startDate != null) {
            w.ge(MigrationOut::getOutDate, startDate);
        }
        if (endDate != null) {
            w.le(MigrationOut::getOutDate, endDate);
        }
        w.orderByDesc(MigrationOut::getOutDate);
        return this.page(page, w);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MigrationOut createMigrationOut(MigrationOutDTO dto) {
        MigrationOut out = new MigrationOut();
        out.setApplicationId(dto.getApplicationId());
        out.setPersonId(dto.getPersonId());
        out.setOutTypeCode(dto.getOutTypeCode());
        out.setTransferBatchNo(dto.getTransferBatchNo());
        out.setFromHouseholdId(dto.getFromHouseholdId());
        out.setFromRegionCode(dto.getFromRegionCode());
        out.setToRegionCode(dto.getToRegionCode());
        out.setToAddress(dto.getToAddress());
        out.setOutDate(dto.getOutDate());
        out.setReasonCode(dto.getReasonCode());
        out.setArchiveId(null);
        out.setOperatorId(null);
        out.setCompletedAt(null);
        baseMapper.insert(out);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean complete(Long outId, Long operatorId) {
        // 1. 加锁迁出记录（按主键 outId），防并发重入
        MigrationOut out = baseMapper.findByOutIdForUpdate(outId);
        if (out == null) {
            throw new NotFoundException("迁出记录[" + outId + "]不存在");
        }
        if (out.getCompletedAt() != null) {
            throw new BizException(409, "该迁出记录已办结，不可重复提交");
        }
        Long personId = out.getPersonId();
        Long fromHouseholdId = out.getFromHouseholdId();
        LocalDate outDate = out.getOutDate();

        // 2. 锁住 person 与当前登记
        Person person = personMapper.selectById(personId);
        if (person == null) {
            throw new NotFoundException("人口[" + personId + "]不存在");
        }
        ResidenceRegistration oldReg = registrationMapper.findByPersonForUpdate(personId);

        // 3. 若有当前登记 → 落快照
        if (oldReg != null) {
            Household fromHousehold = householdMapper.selectById(fromHouseholdId);

            ResidenceArchive snapshot = SnapshotCopier.fromRegistration(
                    oldReg,
                    person.getName(),
                    person.getIdentityTypeCode(),
                    person.getIdentityNo(),
                    "MIGRATION_OUT",
                    out.getReasonCode(),
                    outDate,
                    operatorId,
                    out.getApplicationId(),
                    fromHousehold
            );
            archiveMapper.insert(snapshot);
            out.setArchiveId(snapshot.getArchiveId());

            // 4. 删除当前登记
            registrationMapper.deleteByPersonAndId(personId, oldReg.getRegistrationId());

            // 5. 同步家庭成员状态 LEFT
            householdMemberMapper.updatePersonStatusLeft(personId, outDate);
        } else {
            log.warn("人口[{}]无当前登记可归档，仅更新迁出记录", personId);
        }

        // 6. 回写迁出记录
        out.setOperatorId(operatorId);
        out.setCompletedAt(LocalDateTime.now());
        return updateById(out);
    }

    @Override
    public List<MigrationOut> listByTransferBatch(String transferBatchNo) {
        if (!StringUtils.hasText(transferBatchNo)) {
            return List.of();
        }
        return baseMapper.listByTransferBatch(transferBatchNo);
    }
}
