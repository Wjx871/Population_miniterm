package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.PersonCreateDTO;
import com.example.population.dto.PersonQueryDTO;
import com.example.population.dto.PersonUpdateDTO;
import com.example.population.entity.Person;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.PersonMapper;
import com.example.population.service.ApplicationMaterialService;
import com.example.population.service.PersonService;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class PersonServiceImpl extends ServiceImpl<PersonMapper, Person> implements PersonService {

    private final ApplicationMaterialService applicationMaterialService;

    @Override
    public IPage<Person> queryPage(PersonQueryDTO q) {
        Page<Person> page = PageUtil.clamp(q.getCurrent(), q.getSize());
        LambdaQueryWrapper<Person> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(q.getName())) {
            SafeLike.apply(w, Person::getName, q.getName());
        }
        if (StringUtils.hasText(q.getIdentityType())) {
            w.eq(Person::getIdentityTypeCode, q.getIdentityType());
        }
        if (StringUtils.hasText(q.getIdentityNo())) {
            w.eq(Person::getIdentityNo, q.getIdentityNo());
        }
        if (StringUtils.hasText(q.getGender())) {
            w.eq(Person::getGenderCode, q.getGender());
        }
        if (StringUtils.hasText(q.getEthnicity())) {
            w.eq(Person::getEthnicityCode, q.getEthnicity());
        }
        if (StringUtils.hasText(q.getStatus())) {
            w.eq(Person::getRecordStatusCode, q.getStatus());
        }
        if (StringUtils.hasText(q.getPhone())) {
            SafeLike.apply(w, Person::getPhone, q.getPhone());
        }
        if (q.getBirthDateStart() != null) {
            w.ge(Person::getBirthDate, q.getBirthDateStart());
        }
        if (q.getBirthDateEnd() != null) {
            w.le(Person::getBirthDate, q.getBirthDateEnd());
        }
        w.orderByDesc(Person::getCreatedAt);
        return this.page(page, w);
    }

    @Override
    public Person getByIdentity(String identityType, String identityNo) {
        return baseMapper.findByIdentity(identityType, identityNo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Person createPerson(PersonCreateDTO dto) {
        dto.validate();
        // 最低必交材料闸门：要求对应的业务申请已上传身份证明并完成核验
        applicationMaterialService.assertRequiredVerified(dto.getApplicationId(), "PERSON_REGISTER");
        // 走行锁查重，避免 SELECT-then-INSERT 竞态（FOR UPDATE 仅在事务内有效）
        Person existing = baseMapper.findByIdentityForUpdate(dto.getIdentityTypeCode(), dto.getIdentityNo());
        if (existing != null) {
            throw new com.example.population.exception.DuplicateException(
                    "人口[" + dto.getIdentityNo() + "]已存在");
        }
        Person p = new Person();
        p.setName(dto.getName());
        p.setGenderCode(dto.getGenderCode());
        p.setIdentityTypeCode(dto.getIdentityTypeCode());
        p.setIdentityNo(dto.getIdentityNo());
        p.setBirthDate(dto.getBirthDate());
        p.setEthnicityCode(dto.getEthnicityCode());
        p.setPhone(dto.getPhone());
        p.setContactAddress(dto.getContactAddress());
        p.setRecordStatusCode("ACTIVE");
        try {
            baseMapper.insert(p);
        } catch (DuplicateKeyException e) {
            throw new com.example.population.exception.DuplicateException(
                    "人口[" + dto.getIdentityNo() + "]主键或唯一键冲突", e);
        }
        return p;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePerson(Long personId, PersonUpdateDTO dto) {
        Person p = baseMapper.selectById(personId);
        if (p == null) {
            throw new NotFoundException("人口[" + personId + "]不存在");
        }
        dto.validate();
        if (StringUtils.hasText(dto.getName())) p.setName(dto.getName());
        if (StringUtils.hasText(dto.getGenderCode())) p.setGenderCode(dto.getGenderCode());
        if (dto.getBirthDate() != null) p.setBirthDate(dto.getBirthDate());
        if (StringUtils.hasText(dto.getEthnicityCode())) p.setEthnicityCode(dto.getEthnicityCode());
        if (dto.getPhone() != null) p.setPhone(dto.getPhone());
        if (dto.getContactAddress() != null) p.setContactAddress(dto.getContactAddress());
        return updateById(p);
    }
}