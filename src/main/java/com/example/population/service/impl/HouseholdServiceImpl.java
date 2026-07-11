package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
import com.example.population.service.HouseholdService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdServiceImpl extends ServiceImpl<HouseholdMapper, Household> implements HouseholdService {

    private final HouseholdMemberMapper householdMemberMapper;
    private final PersonMapper personMapper;

    @Override
    public IPage<Household> page(long current, long size, String keyword, String regionCode, String status) {
        Page<Household> page = new Page<>(current, size);
        LambdaQueryWrapper<Household> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(Household::getHouseholdNo, keyword)
                    .or().like(Household::getRegisteredAddress, keyword);
        }
        if (StringUtils.hasText(regionCode)) {
            w.eq(Household::getRegionCode, regionCode);
        }
        if (StringUtils.hasText(status)) {
            w.eq(Household::getStatus, status);
        }
        w.orderByDesc(Household::getCreatedAt);
        return this.page(page, w);
    }

    @Override
    public Household getDetail(Long householdId) {
        Household h = baseMapper.selectById(householdId);
        if (h == null) {
            return null;
        }
        Long count = householdMemberMapper.selectCount(new LambdaQueryWrapper<HouseholdMember>()
                .eq(HouseholdMember::getHouseholdId, householdId)
                .eq(HouseholdMember::getMemberStatus, "CURRENT"));
        h.setMemberCount(count == null ? 0 : count.intValue());
        return h;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Household establishHousehold(HouseholdCreateDTO dto) {
        Household existing = baseMapper.findByHouseholdNoForUpdate(dto.getHouseholdNo());
        if (existing != null) {
            throw new DuplicateException("户号[" + dto.getHouseholdNo() + "]已被占用");
        }
        Household h = new Household();
        h.setHouseholdNo(dto.getHouseholdNo());
        h.setHouseholdTypeCode(dto.getHouseholdTypeCode());
        h.setHeadPersonId(dto.getHeadPersonId());
        h.setRegisteredAddress(dto.getRegisteredAddress());
        h.setRegionCode(dto.getRegionCode());
        h.setDepartmentId(dto.getDepartmentId());
        h.setEstablishDate(dto.getEstablishDate());
        h.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        baseMapper.insert(h);

        // 若指定了 head_person_id，自动建一条 HEAD 成员关系
        if (h.getHeadPersonId() != null && "FAMILY".equalsIgnoreCase(h.getHouseholdTypeCode())) {
            Person p = personMapper.selectById(h.getHeadPersonId());
            if (p == null) {
                throw new NotFoundException("户主人口[" + h.getHeadPersonId() + "]不存在");
            }
            HouseholdMember head = new HouseholdMember();
            head.setHouseholdId(h.getHouseholdId());
            head.setPersonId(h.getHeadPersonId());
            head.setRelationshipCode("HEAD");
            head.setJoinDate(h.getEstablishDate());
            head.setMemberStatus("CURRENT");
            try {
                householdMemberMapper.insert(head);
            } catch (org.springframework.dao.DuplicateKeyException dup) {
                // 与 uk_member_current_dedup 冲突；保留已存在的 CURRENT 行
                log.info("户主关系已存在，跳过");
            }
        }
        return h;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeHead(Long householdId, Long newHeadPersonId) {
        Household h = baseMapper.selectById(householdId);
        if (h == null) {
            throw new NotFoundException("家庭户[" + householdId + "]不存在");
        }
        Person newHead = personMapper.selectById(newHeadPersonId);
        if (newHead == null) {
            throw new NotFoundException("新户主人口[" + newHeadPersonId + "]不存在");
        }
        // 校验新房主当前是本户的 CURRENT 成员
        HouseholdMember newHeadMember = householdMemberMapper.selectOne(new LambdaQueryWrapper<HouseholdMember>()
                .eq(HouseholdMember::getHouseholdId, householdId)
                .eq(HouseholdMember::getPersonId, newHeadPersonId)
                .eq(HouseholdMember::getMemberStatus, "CURRENT")
                .last("LIMIT 1"));
        if (newHeadMember == null) {
            throw new BizException(400, "新房主[" + newHeadPersonId + "]不是本户当前成员，无法更换户主");
        }
        // 旧户主 → OTHER
        if (h.getHeadPersonId() != null) {
            HouseholdMember oldHead = householdMemberMapper.selectOne(new LambdaQueryWrapper<HouseholdMember>()
                    .eq(HouseholdMember::getHouseholdId, householdId)
                    .eq(HouseholdMember::getPersonId, h.getHeadPersonId())
                    .eq(HouseholdMember::getMemberStatus, "CURRENT")
                    .eq(HouseholdMember::getRelationshipCode, "HEAD")
                    .last("LIMIT 1"));
            if (oldHead != null) {
                oldHead.setRelationshipCode("OTHER");
                householdMemberMapper.updateById(oldHead);
            }
        }
        // 新户主 → HEAD
        newHeadMember.setRelationshipCode("HEAD");
        householdMemberMapper.updateById(newHeadMember);
        // 户主指针更新
        h.setHeadPersonId(newHeadPersonId);
        baseMapper.updateById(h);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disableHousehold(Long householdId, Long operatorId) {
        Household h = baseMapper.selectById(householdId);
        if (h == null) {
            throw new NotFoundException("家庭户[" + householdId + "]不存在");
        }
        Long currentCount = householdMemberMapper.selectCount(new LambdaQueryWrapper<HouseholdMember>()
                .eq(HouseholdMember::getHouseholdId, householdId)
                .eq(HouseholdMember::getMemberStatus, "CURRENT"));
        if (currentCount != null && currentCount > 0) {
            throw new HouseholdNotEmptyException(householdId);
        }
        h.setStatus("CANCELLED");
        baseMapper.updateById(h);
    }
}
