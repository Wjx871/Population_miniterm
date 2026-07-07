package com.wjx871.population.resident;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResidentRepository extends JpaRepository<Resident, Long> {

    boolean existsByIdCardNumber(String idCardNumber);

    Optional<Resident> findByIdCardNumber(String idCardNumber);

    Page<Resident> findByNameContainingIgnoreCaseOrIdCardNumberContaining(
            String name,
            String idCardNumber,
            Pageable pageable
    );
}
