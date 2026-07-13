package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.annotation.DataScope;
import com.example.population.dto.DataScopeQuery;
import com.example.population.dto.FloatingLeaveDTO;
import com.example.population.dto.FloatingPopulationCreateDTO;
import com.example.population.dto.FloatingPopulationUpdateDTO;
import com.example.population.entity.FloatingPopulation;
import com.example.population.entity.Person;
import com.example.population.exception.BizException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.FloatingPopulationMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.service.FloatingPopulationService;
import com.example.population.util.DataScopeHelper;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FloatingPopulationServiceImpl extends ServiceImpl<FloatingPopulationMapper, FloatingPopulation>
        implements FloatingPopulationService {

    private final PersonMapper personMapper;

    @Override
    @DataScope(DataScope.Type.MIGRATION)
    public IPage<FloatingPopulation> page(long current, long size, String keyword, String currentRegionCode,
                                          String status, Long personId) {
        Page<FloatingPopulation> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<FloatingPopulation> w = new LambdaQueryWrapper<>();
        String safeKw = SafeLike.escape(keyword);
        if (safeKw != null && !safeKw.isEmpty()) {
            w.and(w2 -> w2.like(FloatingPopulation::getCurrentAddress, safeKw)
                    .or().like(FloatingPopulation::getSourceAddress, safeKw));
        }
        if (StringUtils.hasText(currentRegionCode)) {
            w.eq(FloatingPopulation::getCurrentRegionCode, currentRegionCode);
        }
        if (StringUtils.hasText(status)) {
            w.eq(FloatingPopulation::getStatus, status);
        }
        if (personId != null) {
            w.eq(FloatingPopulation::getPersonId, personId);
        }
        // P0: 应用数据范围过滤（设计文档 §6）
        DataScopeHelper.applyBusinessScope(w, DataScopeQuery.fromCurrentContext(),
                w2 -> ((LambdaQueryWrapper<FloatingPopulation>) w2)
                        .eq(FloatingPopulation::getHandlingDepartmentId,
                                DataScopeQuery.fromCurrentContext().getDepartmentId()),
                w2 -> applyRegionFilter((LambdaQueryWrapper<FloatingPopulation>) w2,
                        DataScopeQuery.fromCurrentContext()),
                null);
        w.orderByDesc(FloatingPopulation::getRegisterDate);
        return this.page(page, w);
    }

    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<FloatingPopulation> applyRegionFilter(
            LambdaQueryWrapper<FloatingPopulation> w, DataScopeQuery ds) {
        if (ds.getVisibleRegionCodes() != null && !ds.getVisibleRegionCodes().isEmpty()) {
            return (LambdaQueryWrapper<FloatingPopulation>) w
                    .in(FloatingPopulation::getCurrentRegionCode, ds.getVisibleRegionCodes());
        }
        if (ds.getRegionCode() != null) {
            return (LambdaQueryWrapper<FloatingPopulation>) w
                    .eq(FloatingPopulation::getCurrentRegionCode, ds.getRegionCode());
        }
        return w;
    }

    // ---------------------------------------------------------
    // 业务逻辑
    // ---------------------------------------------------------

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FloatingPopulation createFloating(FloatingPopulationCreateDTO dto) {
        // 1. 人员存在性
        Person person = personMapper.selectById(dto.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + dto.getPersonId() + "]不存在，请先维护人口基础信息");
        }

        // 2. 日期范围校验
        LocalDate arrival = dto.getArrivalDate();
        LocalDate register = dto.getRegisterDate();
        LocalDate planned = dto.getPlannedLeaveDate();
        if (arrival != null && register != null && register.isBefore(arrival)) {
            throw new BizException(400, "登记日期不得早于到达日期");
        }
        if (planned != null && arrival != null && planned.isBefore(arrival)) {
            throw new BizException(400, "预计离开日期不得早于到达日期");
        }

        // 3. 同一人员不应存在多条有效（ACTIVE）流动记录（§3.18）
        List<FloatingPopulation> existing = baseMapper.listByPersonForUpdate(dto.getPersonId());
        for (FloatingPopulation f : existing) {
            if ("ACTIVE".equalsIgnoreCase(f.getStatus())) {
                throw new BizException(409,
                        "人口[" + dto.getPersonId() + "]已有有效流动记录 ["
                                + f.getFloatingId() + "]，请先办离开或到期后再登记");
            }
        }

        // 4. 入参映射
        FloatingPopulation entity = new FloatingPopulation();
        BeanUtils.copyProperties(dto, entity);
        if (!StringUtils.hasText(entity.getStatus())) {
            entity.setStatus("ACTIVE");
        } else if (!entity.getStatus().equalsIgnoreCase("ACTIVE")
                && !entity.getStatus().equalsIgnoreCase("LEFT")
                && !entity.getStatus().equalsIgnoreCase("EXPIRED")) {
            throw new BizException(400, "流动人口状态必须在 ACTIVE/LEFT/EXPIRED 内");
        }
        if (register == null && arrival != null) {
            entity.setRegisterDate(arrival);
        }
        baseMapper.insert(entity);

        // 5. 状态联动：人口档案状态 → FLOATING（§2.2.6 第 5 步）
        if (!"FLOATING".equalsIgnoreCase(person.getRecordStatusCode())
                && "ACTIVE".equalsIgnoreCase(person.getRecordStatusCode())) {
            person.setRecordStatusCode("FLOATING");
            personMapper.updateById(person);
        }
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FloatingPopulation updateFloating(Long floatingId, FloatingPopulationUpdateDTO dto) {
        FloatingPopulation entity = baseMapper.selectById(floatingId);
        if (entity == null) {
            throw new NotFoundException("流动人口登记[" + floatingId + "]不存在");
        }
        // 白名单字段
        entity.setSourceRegionCode(dto.getSourceRegionCode());
        entity.setSourceAddress(dto.getSourceAddress());
        entity.setCurrentRegionCode(dto.getCurrentRegionCode());
        entity.setCurrentAddress(dto.getCurrentAddress());
        entity.setArrivalDate(dto.getArrivalDate());
        entity.setRegisterDate(dto.getRegisterDate());
        entity.setPlannedLeaveDate(dto.getPlannedLeaveDate());
        entity.setActualLeaveDate(dto.getActualLeaveDate());
        entity.setResidenceReasonCode(dto.getResidenceReasonCode());
        entity.setEmploymentSchool(dto.getEmploymentSchool());
        entity.setLandlordName(dto.getLandlordName());
        entity.setLandlordPhone(dto.getLandlordPhone());
        if (StringUtils.hasText(dto.getStatus())) {
            entity.setStatus(dto.getStatus());
        }
        baseMapper.updateById(entity);
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FloatingPopulation leave(Long floatingId, FloatingLeaveDTO dto) {
        FloatingPopulation entity = baseMapper.selectById(floatingId);
        if (entity == null) {
            throw new NotFoundException("流动人口登记[" + floatingId + "]不存在");
        }
        if (!"ACTIVE".equalsIgnoreCase(entity.getStatus())) {
            throw new BizException(409,
                    "当前登记状态为 " + entity.getStatus() + "，不可重复办离开");
        }
        entity.setStatus("LEFT");
        entity.setActualLeaveDate(dto.getActualLeaveDate());
        baseMapper.updateById(entity);

        // 状态联动：若该人员无其他 ACTIVE 流动记录 → 回退 person.record_status_code=ACTIVE
        rollbackPersonStatusIfNoActive(entity.getPersonId());
        return entity;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int scanExpiring() {
        int updated = baseMapper.markExpiringAsExpired(LocalDate.now());
        log.info("[floating-scan] 标记 EXPIRED 记录数: {}", updated);
        if (updated > 0) {
            // 找到刚刚被标记为 EXPIRED 的 person_id 集合，回滚无 ACTIVE 记录的人员状态
            List<FloatingPopulation> expired = this.list(new LambdaQueryWrapper<FloatingPopulation>()
                    .eq(FloatingPopulation::getStatus, "EXPIRED")
                    .ge(FloatingPopulation::getPlannedLeaveDate, LocalDate.now().minusDays(1))
                    .select(FloatingPopulation::getPersonId));
            for (FloatingPopulation f : expired) {
                rollbackPersonStatusIfNoActive(f.getPersonId());
            }
        }
        return updated;
    }

    /**
     * 若该人员已经没有任何 ACTIVE 流动记录，把 person.record_status_code 回退到 ACTIVE。
     * 注意：迁出/注销场景下 person 已置为 MIGRATED_OUT/CANCELLED，本方法不能反向覆盖。
     */
    private void rollbackPersonStatusIfNoActive(Long personId) {
        if (personId == null) return;
        List<FloatingPopulation> all = baseMapper.listByPersonForUpdate(personId);
        boolean hasActive = false;
        for (FloatingPopulation f : all) {
            if ("ACTIVE".equalsIgnoreCase(f.getStatus())) {
                hasActive = true;
                break;
            }
        }
        if (hasActive) return;
        Person person = personMapper.selectById(personId);
        if (person == null) return;
        if ("FLOATING".equalsIgnoreCase(person.getRecordStatusCode())) {
            person.setRecordStatusCode("ACTIVE");
            personMapper.updateById(person);
        }
    }

    // 兼容旧 API：暴露批量更新入口（不暴露在 Controller，仅给扫描使用）
    public int updateStatusBatch(Long floatingId, String status) {
        return baseMapper.update(null, new LambdaUpdateWrapper<FloatingPopulation>()
                .eq(FloatingPopulation::getFloatingId, floatingId)
                .set(FloatingPopulation::getStatus, status));
    }
}