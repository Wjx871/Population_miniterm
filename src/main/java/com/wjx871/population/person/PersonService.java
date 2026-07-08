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

@Service
@RequiredArgsConstructor
public class PersonService {

    private final PersonMapper personMapper;

    @Transactional(readOnly = true)
    public Page<Person> search(String name, String idCard, String status, Pageable pageable) {
        String trimmedName = trimToNull(name);
        String trimmedIdCard = normalizeIdCard(idCard);
        String trimmedStatus = trimToNull(status);
        long total = personMapper.countByCondition(trimmedName, trimmedIdCard, trimmedStatus);
        List<Person> persons = personMapper.selectListByCondition(
                trimmedName,
                trimmedIdCard,
                trimmedStatus,
                pageable.getPageSize(),
                pageable.getOffset()
        );
        return new PageImpl<>(persons, pageable, total);
    }

    @Transactional(readOnly = true)
    public Person get(Long personId) {
        return personMapper.selectById(personId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found: " + personId));
    }

    @Transactional(readOnly = true)
    public Person getByIdCard(String idCard) {
        String normalizedIdCard = normalizeIdCard(idCard);
        if (normalizedIdCard == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "ID card is required");
        }
        return personMapper.selectByIdCard(normalizedIdCard)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found: " + idCard));
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
        return get(person.getPersonId());
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
}
