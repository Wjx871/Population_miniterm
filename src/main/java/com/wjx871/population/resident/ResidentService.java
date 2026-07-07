package com.wjx871.population.resident;

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
public class ResidentService {

    private final ResidentMapper residentMapper;

    @Transactional(readOnly = true)
    public Page<Resident> search(String keyword, Pageable pageable) {
        String trimmedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        long total = residentMapper.countByKeyword(trimmedKeyword);
        List<Resident> residents = residentMapper.search(
                trimmedKeyword,
                pageable.getPageSize(),
                pageable.getOffset()
        );
        return new PageImpl<>(residents, pageable, total);
    }

    @Transactional(readOnly = true)
    public Resident get(Long id) {
        return residentMapper.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resident not found: " + id));
    }

    @Transactional
    public Resident create(ResidentRequest request) {
        if (residentMapper.countByIdCardNumber(request.idCardNumber()) > 0) {
            throw new DuplicateKeyException("ID card number already exists");
        }

        Resident resident = new Resident();
        apply(request, resident);
        residentMapper.insert(resident);
        return get(resident.getId());
    }

    @Transactional
    public Resident update(Long id, ResidentRequest request) {
        Resident resident = get(id);
        Optional<Resident> duplicated = residentMapper.findByIdCardNumber(request.idCardNumber());
        if (duplicated.isPresent() && !duplicated.get().getId().equals(id)) {
            throw new DuplicateKeyException("ID card number already exists");
        }

        apply(request, resident);
        residentMapper.update(resident);
        return get(id);
    }

    @Transactional
    public void delete(Long id) {
        get(id);
        residentMapper.deleteById(id);
    }

    private void apply(ResidentRequest request, Resident resident) {
        resident.setName(request.name());
        resident.setGender(request.gender());
        resident.setBirthDate(request.birthDate());
        resident.setIdCardNumber(request.idCardNumber().toUpperCase());
        resident.setPhoneNumber(request.phoneNumber());
        resident.setProvince(request.province());
        resident.setCity(request.city());
        resident.setDistrict(request.district());
        resident.setAddress(request.address());
        resident.setActive(request.active() == null || request.active());
    }
}
