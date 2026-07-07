package com.wjx871.population.resident;

import jakarta.persistence.EntityNotFoundException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResidentService {

    private final ResidentRepository residentRepository;

    @Transactional(readOnly = true)
    public Page<Resident> search(String keyword, Pageable pageable) {
        if (keyword == null || keyword.isBlank()) {
            return residentRepository.findAll(pageable);
        }
        String trimmedKeyword = keyword.trim();
        return residentRepository.findByNameContainingIgnoreCaseOrIdCardNumberContaining(
                trimmedKeyword,
                trimmedKeyword,
                pageable
        );
    }

    @Transactional(readOnly = true)
    public Resident get(Long id) {
        return residentRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Resident not found: " + id));
    }

    @Transactional
    public Resident create(ResidentRequest request) {
        if (residentRepository.existsByIdCardNumber(request.idCardNumber())) {
            throw new DuplicateKeyException("ID card number already exists");
        }

        Resident resident = new Resident();
        apply(request, resident);
        return residentRepository.save(resident);
    }

    @Transactional
    public Resident update(Long id, ResidentRequest request) {
        Resident resident = get(id);
        Optional<Resident> duplicated = residentRepository.findByIdCardNumber(request.idCardNumber());
        if (duplicated.isPresent() && !duplicated.get().getId().equals(id)) {
            throw new DuplicateKeyException("ID card number already exists");
        }

        apply(request, resident);
        return residentRepository.save(resident);
    }

    @Transactional
    public void delete(Long id) {
        Resident resident = get(id);
        residentRepository.delete(resident);
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
