package com.wjx871.population.query;

import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.SensitiveDataMaskingService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class Phase11QueryService {
    private final Phase11QueryMapper mapper;
    private final SensitiveDataMaskingService masking;
    private final Clock clock;

    @Transactional(readOnly = true)
    public Page<ComprehensivePersonSummaryView> persons(PersonQueryCriteria input, int page, int size, String sort) {
        validatePage(page, size);
        LocalDate today = LocalDate.now(clock);
        LocalDate from = input.ageMax() == null ? null : today.minusYears(input.ageMax() + 1L).plusDays(1);
        LocalDate to = input.ageMin() == null ? null : today.minusYears(input.ageMin());
        PersonQueryCriteria q = new PersonQueryCriteria(trim(input.name()), trim(input.identityNo()), trim(input.gender()),
                input.ageMin(), input.ageMax(), trim(input.regionCode()), input.departmentId(), input.householdId(),
                trim(input.householdType()), trim(input.residenceStatus()), trim(input.floatingStatus()),
                trim(input.certificateType()), trim(input.keyPopulationType()), trim(input.currentStatus()), from, to);
        SortSpec order = SortSpec.parse(sort);
        DataScopeCriteria scope = DataScopeCriteria.current();
        List<ComprehensivePersonSummaryView> rows = mapper.selectPersons(q, scope, order.column(), order.direction(),
                size, (long) page * size);
        rows.forEach(v -> {
            v.setMaskedIdentityNo(masking.identityAlways(v.getMaskedIdentityNo()));
            v.setMaskedPhone(masking.phoneAlways(v.getMaskedPhone()));
            v.setCurrentAddress(masking.address(v.getCurrentAddress()));
        });
        return new PageImpl<>(rows, PageRequest.of(page, size), mapper.countPersons(q, scope));
    }

    @Transactional(readOnly = true)
    public Page<HouseholdQueryView> households(HouseholdQueryCriteria input, int page, int size) {
        validatePage(page, size);
        if (input.memberCountMin() != null && input.memberCountMax() != null
                && input.memberCountMin() > input.memberCountMax()) {
            throw badRequest("memberCountMin 不能大于 memberCountMax");
        }
        HouseholdQueryCriteria q = new HouseholdQueryCriteria(trim(input.householdNo()), trim(input.headPersonName()),
                trim(input.address()), trim(input.regionCode()), trim(input.householdType()), trim(input.status()),
                input.memberCountMin(), input.memberCountMax(), input.containsKeyPopulation());
        DataScopeCriteria scope = DataScopeCriteria.current();
        List<HouseholdQueryView> rows = mapper.selectHouseholds(q, scope, size, (long) page * size);
        rows.forEach(v -> v.setAddress(masking.address(v.getAddress())));
        return new PageImpl<>(rows, PageRequest.of(page, size), mapper.countHouseholds(q, scope));
    }

    @Transactional(readOnly = true)
    public Page<MigrationQueryView> migrations(MigrationQueryCriteria input, int page, int size) {
        validatePage(page, size);
        if (input.executeDateFrom() != null && input.executeDateTo() != null
                && input.executeDateFrom().isAfter(input.executeDateTo())) {
            throw badRequest("executeDateFrom 不能晚于 executeDateTo");
        }
        MigrationQueryCriteria q = new MigrationQueryCriteria(input.personId(), trim(input.personName()),
                trim(input.migrationType()), trim(input.sourceRegionCode()), trim(input.targetRegionCode()),
                trim(input.status()), input.executeDateFrom(), input.executeDateTo(), trim(input.applicationNo()));
        DataScopeCriteria scope = DataScopeCriteria.current();
        List<MigrationQueryView> rows = mapper.selectMigrations(q, scope, size, (long) page * size);
        return new PageImpl<>(rows, PageRequest.of(page, size), mapper.countMigrations(q, scope));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw badRequest("分页参数必须满足 page >= 0 且 1 <= size <= 100");
    }
    private static String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
    private record SortSpec(String column, String direction) {
        static SortSpec parse(String raw) {
            String[] parts = (raw == null || raw.isBlank() ? "personId,DESC" : raw).split(",", 2);
            String column = switch (parts[0].trim()) {
                case "personId" -> "p.person_id";
                case "name" -> "p.name";
                case "birthDate" -> "p.birth_date";
                case "createdAt" -> "p.created_at";
                default -> throw badRequest("不支持的排序字段");
            };
            String direction = parts.length == 1 ? "DESC" : parts[1].trim().toUpperCase();
            if (!List.of("ASC", "DESC").contains(direction)) throw badRequest("不支持的排序方向");
            return new SortSpec(column, direction);
        }
    }
}
