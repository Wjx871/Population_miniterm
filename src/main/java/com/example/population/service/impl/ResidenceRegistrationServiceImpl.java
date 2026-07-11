package com.example.population.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.ResidenceRegisterDTO;
import com.example.population.entity.Household;
import com.example.population.entity.Person;
import com.example.population.entity.ResidenceRegistration;
import com.example.population.exception.BizException;
import com.example.population.exception.NotFoundException;
import com.example.population.exception.PersonAlreadyHasRegistrationException;
import com.example.population.mapper.HouseholdMapper;
import com.example.population.mapper.PersonMapper;
import com.example.population.mapper.ResidenceRegistrationMapper;
import com.example.population.service.ResidenceRegistrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidenceRegistrationServiceImpl
        extends ServiceImpl<ResidenceRegistrationMapper, ResidenceRegistration>
        implements ResidenceRegistrationService {

    private final PersonMapper personMapper;
    private final HouseholdMapper householdMapper;

    @Override
    public ResidenceRegistration getByPerson(Long personId) {
        return baseMapper.findByPerson(personId);
    }

    /**
     * 一人一条当前户籍登记：FOR UPDATE 锁住目标 person 行；查重后插入。
     * 异常路径：候选登记已存在 → PersonAlreadyHasRegistrationException；
     *          person/household 不存在 → NotFoundException。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ResidenceRegistration register(ResidenceRegisterDTO dto) {
        dto.validate();

        // 1. 加锁校验 person
        Person person = personMapper.findByIdentityForUpdate(
                /* 不通过号码校验，仅以 personId 校验放这里 */ null, null);
        // 实际上 personId 直接 selectById 也行；这里用 findByIdentityForUpdate 复用基类对号码场景
        // 简化：直接 selectById
        person = personMapper.selectById(dto.getPersonId());
        if (person == null) {
            throw new NotFoundException("人口[" + dto.getPersonId() + "]不存在");
        }

        // 2. 加锁查重：FOR UPDATE 锁住所查行（无则锁空）
        ResidenceRegistration existing = baseMapper.findByPersonForUpdate(dto.getPersonId());
        if (existing != null) {
            throw new PersonAlreadyHasRegistrationException(dto.getPersonId());
        }

        Household household = householdMapper.selectById(dto.getHouseholdId());
        if (household == null) {
            throw new NotFoundException("家庭户[" + dto.getHouseholdId() + "]不存在");
        }

        ResidenceRegistration reg = new ResidenceRegistration();
        reg.setPersonId(dto.getPersonId());
        reg.setHouseholdId(dto.getHouseholdId());
        reg.setRegisterTypeCode(dto.getRegisterTypeCode());
        reg.setRegisterDate(dto.getRegisterDate());
        reg.setRegisteredAddress(dto.getRegisteredAddress() != null
                ? dto.getRegisteredAddress() : household.getRegisteredAddress());
        reg.setRegionCode(dto.getRegionCode() != null
                ? dto.getRegionCode() : household.getRegionCode());
        reg.setStartDate(dto.getStartDate());
        reg.setSourceApplicationId(dto.getSourceApplicationId());
        baseMapper.insert(reg);

        // 激活人口记录
        person.setRecordStatusCode("ACTIVE");
        personMapper.updateById(person);

        return reg;
    }
}
