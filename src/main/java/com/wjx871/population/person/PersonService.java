package com.wjx871.population.person;

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
        String idCard = normalizeIdCard(request.idCard());
        if (personMapper.countByIdCard(idCard) > 0) {
            throw new DuplicateKeyException("ID card already exists");
        }

        LocalDateTime now = LocalDateTime.now();
        Person person = new Person();
        apply(request, person);
        person.setIdCard(idCard);
        person.setCreatedAt(now);
        person.setUpdatedAt(now);
        personMapper.insertPerson(person);
        Person created=personMapper.selectById(person.getPersonId()).orElseThrow();mask(created);return created;
    }

    @Transactional
    public Person update(Long personId, PersonRequest request) {
        Person person = get(personId);
        String idCard = normalizeIdCard(request.idCard());
        Optional<Person> duplicated = personMapper.selectByIdCard(idCard);
        if (duplicated.isPresent() && !duplicated.get().getPersonId().equals(personId)) {
            throw new DuplicateKeyException("ID card already exists");
        }

        apply(request, person);
        person.setIdCard(idCard);
        person.setUpdatedAt(LocalDateTime.now());
        personMapper.updatePerson(person);
        return get(personId);
    }

    @Transactional
    public void delete(Long personId) {
        get(personId);
        personMapper.updateStatusToDeleted(personId, PersonStatus.DELETED);
    }

    private void apply(PersonRequest request, Person person) {
        person.setName(request.name().trim());
        person.setGender(request.gender().trim());
        person.setBirthDate(request.birthDate());
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
