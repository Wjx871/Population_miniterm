package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.HouseholdMemberDTO;
import com.example.population.dto.HouseholdMemberTransferDTO;
import com.example.population.entity.HouseholdMember;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.exception.BizException;
import com.example.population.exception.DuplicateException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.HouseholdMemberMapper;
import com.example.population.mapper.ResidenceRegistrationMapper;
import com.example.population.service.HouseholdMemberService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class HouseholdMemberServiceImpl
        extends ServiceImpl<HouseholdMemberMapper, HouseholdMember>
        implements HouseholdMemberService {

    private final ResidenceRegistrationMapper registrationMapper;

    @Override
    public List<HouseholdMember> listCurrentMembers(Long householdId) {
        return this.list(new LambdaQueryWrapper<HouseholdMember>()
                .eq(HouseholdMember::getHouseholdId, householdId)
                .eq(HouseholdMember::getMemberStatus, "CURRENT")
                .orderByAsc(HouseholdMember::getMemberId));
    }

    @Override
    public List<HouseholdMember> listHistoryByPerson(Long personId) {
        return this.list(new LambdaQueryWrapper<HouseholdMember>()
                .eq(HouseholdMember::getPersonId, personId)
                .orderByDesc(HouseholdMember::getJoinDate));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public HouseholdMember addMember(HouseholdMemberDTO dto) {
        // 校验当前 person 的登记属于本户
        ResidenceRegistration reg = registrationMapper.findByPerson(dto.getPersonId());
        if (reg != null && !reg.getHouseholdId().equals(dto.getHouseholdId())) {
            // 兼容业务上独立加成员场景（如新生儿登记到母亲户时，人口登记尚未创建）
            log.info("person[{}] 当前户籍[{}]，与目标户[{}]不一致，继续添加成员关系",
                    dto.getPersonId(), reg.getHouseholdId(), dto.getHouseholdId());
        }
        HouseholdMember m = new HouseholdMember();
        m.setHouseholdId(dto.getHouseholdId());
        m.setPersonId(dto.getPersonId());
        m.setRelationshipCode(dto.getRelationshipCode());
        m.setJoinDate(dto.getJoinDate());
        m.setMemberStatus(dto.getMemberStatus() != null ? dto.getMemberStatus() : "CURRENT");
        m.setSourceApplicationId(dto.getSourceApplicationId());
        try {
            baseMapper.insert(m);
        } catch (DuplicateKeyException e) {
            throw new DuplicateException("该人口已是本户当前成员", e);
        }
        return m;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeMember(Long memberId) {
        HouseholdMember m = baseMapper.selectById(memberId);
        if (m == null) {
            throw new NotFoundException("成员关系[" + memberId + "]不存在");
        }
        m.setMemberStatus("LEFT");
        m.setLeaveDate(LocalDate.now());
        baseMapper.updateById(m);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> transferMembers(HouseholdMemberTransferDTO dto) {
        List<Long> newMemberIds = new ArrayList<>();
        // 逐人处理：旧 CURRENT 行置 LEFT；新建目标户的 CURRENT 行（关系继承 OTHER）
        for (Long pid : dto.getPersonIds()) {
            // 校验人口当前有 CURRENT 关系
            List<HouseholdMember> oldList = baseMapper.listByPerson(pid);
            HouseholdMember current = null;
            for (HouseholdMember hm : oldList) {
                if ("CURRENT".equalsIgnoreCase(hm.getMemberStatus())) {
                    current = hm;
                    break;
                }
            }
            if (current == null) {
                throw new BizException(400, "人口[" + pid + "]在原户无 CURRENT 关系，无法过户");
            }
            // 旧行 LEFT
            current.setMemberStatus("LEFT");
            current.setLeaveDate(dto.getTransferDate());
            baseMapper.updateById(current);

            // 新建目标户 CURRENT
            HouseholdMember newRow = new HouseholdMember();
            newRow.setHouseholdId(dto.getTargetHouseholdId());
            newRow.setPersonId(pid);
            newRow.setRelationshipCode("OTHER");
            newRow.setJoinDate(dto.getTransferDate());
            newRow.setMemberStatus("CURRENT");
            newRow.setSourceApplicationId(dto.getSourceApplicationId());
            try {
                baseMapper.insert(newRow);
                newMemberIds.add(newRow.getMemberId());
            } catch (DuplicateKeyException e) {
                throw new DuplicateException("该人口已是目标户当前成员", e);
            }
        }
        return newMemberIds;
    }
}
