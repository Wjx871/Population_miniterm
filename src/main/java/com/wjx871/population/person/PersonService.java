package com.wjx871.population.person;

import com.wjx871.population.person.idcard.PersonIdCardImage;
import com.wjx871.population.person.idcard.PersonIdCardImageMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.wjx871.population.security.SensitiveDataMaskingService;
import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;

/**
 * 人口基础信息业务服务。
 *
 * @author Wjx871
 * @date 2026/07/08
 */
@Service
@RequiredArgsConstructor
public class PersonService {

    private final PersonMapper personMapper;
    private final SensitiveDataMaskingService masking;
    private final IdCardValidator idCards;
    private final PersonIdCardImageMapper idCardImageMapper;

    @Transactional(readOnly = true)
    public Page<Person> search(String name, String idCard, String status, Pageable pageable) {
        String trimmedName = trimToNull(name);
        String trimmedIdCard = normalizeIdCard(idCard);
        String trimmedStatus = trimToNull(status);
        DataScopeCriteria scope=DataScopeCriteria.current();
        long total = personMapper.countScopedByCondition(trimmedName, trimmedIdCard, trimmedStatus,scope);
        List<Person> persons = personMapper.selectScopedListByCondition(
                trimmedName,
                trimmedIdCard,
                trimmedStatus,
                scope,
                pageable.getPageSize(),
                pageable.getOffset()
        );
        persons.forEach(this::mask);
        return new PageImpl<>(persons, pageable, total);
    }

    @Transactional(readOnly = true)
    public Person get(Long personId) {
        Person p=personMapper.selectScopedById(personId,DataScopeCriteria.current()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No data-scope access to person: " + personId));
        mask(p);return p;
    }

    @Transactional(readOnly = true)
    public Person getByIdCard(String idCard) {
        String normalizedIdCard = normalizeIdCard(idCard);
        if (normalizedIdCard == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID card is required");
        }
        Person p=personMapper.selectScopedByIdCard(normalizedIdCard,DataScopeCriteria.current()).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No data-scope access to person"));
        mask(p);return p;
    }

    @Transactional
    public Person create(PersonRequest request) {
        IdCardValidator.Identity identity = idCards.validate(request.idCard(), request.birthDate(), request.gender());
        String idCard = identity.normalized();
        if (personMapper.countByIdCard(idCard) > 0) {
            throw new DuplicateKeyException("ID card already exists");
        }

        // V4_013 / Phase 14: 身份证影印本必传
        if (request.idCardImageId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "新增人口必须先上传身份证影印本");
        }
        PersonIdCardImage img = idCardImageMapper.selectById(request.idCardImageId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "身份证影印本不存在: " + request.idCardImageId()));
        if (img.getPersonId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该身份证影印本已被使用");
        }
        // 图与号一致性：仅 OCR 成功时有 ocrIdcardFull；FAILED/SKIPPED 跳过一致性校验
        if (img.getOcrIdcardFull() != null
                && !img.getOcrIdcardFull().equalsIgnoreCase(idCard)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "上传图片识别出的身份证号与填写不一致（识别：" + masking.identity(img.getOcrIdcardFull()) + "）");
        }

        LocalDateTime now = LocalDateTime.now();
        Person person = new Person();
        apply(request, person, identity);
        AuthenticatedUser operator = CurrentUserContext.requireUser();
        person.setIdCard(idCard);
        person.setCreatedByUserId(operator.userId());
        person.setCreatedDepartmentId(operator.departmentId());
        person.setCreatedRegionCode(operator.regionCode());
        person.setCreatedAt(now);
        person.setUpdatedAt(now);
        personMapper.insertPerson(person);
        Person created = personMapper.selectById(person.getPersonId()).orElseThrow();
        idCardImageMapper.bindPerson(img.getImageId(), created.getPersonId());
        mask(created);
        return created;
    }

    @Transactional
    public Person update(Long personId, PersonRequest request) {
        Person person = personMapper.selectScopedById(personId, DataScopeCriteria.current())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No data-scope access to person: " + personId));
        IdCardValidator.Identity identity = idCards.validate(request.idCard(), request.birthDate(), request.gender());
        String idCard = identity.normalized();
        Optional<Person> duplicated = personMapper.selectByIdCard(idCard);
        if (duplicated.isPresent() && !duplicated.get().getPersonId().equals(personId)) {
            throw new DuplicateKeyException("ID card already exists");
        }

        // V4_013: 编辑时不允许更换身份证影印本；不传图时沿用既有图。
        if (request.idCardImageId() != null) {
            idCardImageMapper.selectById(request.idCardImageId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "身份证影印本不存在: " + request.idCardImageId()));
            idCardImageMapper.selectByPersonId(personId).ifPresent(existing -> {
                if (!existing.getImageId().equals(request.idCardImageId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "编辑时不能更换身份证影印本");
                }
            });
        }

        apply(request, person, identity);
        person.setIdCard(idCard);
        person.setUpdatedAt(LocalDateTime.now());
        personMapper.updatePerson(person);
        return get(personId);
    }

    private void apply(PersonRequest request, Person person, IdCardValidator.Identity identity) {
        person.setName(request.name().trim());
        person.setGender(identity.gender());
        person.setBirthDate(request.birthDate() == null ? identity.birthDate() : request.birthDate());
        person.setEthnicity(trimToNull(request.ethnicity()));
        person.setPhone(trimToNull(request.phone()));
        person.setCurrentAddress(trimToNull(request.currentAddress()));
        person.setStatus(request.status() == null || request.status().isBlank()
                ? PersonStatus.NORMAL
                : request.status().trim());
    }

    private String normalizeIdCard(String idCard) {
        return idCard == null || idCard.isBlank() ? null : idCard.trim().toUpperCase();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private void mask(Person p){if(!masking.canViewFull()){p.setIdCard(masking.identity(p.getIdCard()));p.setPhone(masking.phone(p.getPhone()));p.setCurrentAddress(masking.address(p.getCurrentAddress()));}}
}
