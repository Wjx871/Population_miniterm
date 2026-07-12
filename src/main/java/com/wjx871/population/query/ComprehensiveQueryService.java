package com.wjx871.population.query;

import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.SensitiveDataMaskingService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ComprehensiveQueryService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final String ACTIVE_STATUS = "ACTIVE";

    private final ComprehensiveQueryMapper mapper;
    private final SensitiveDataMaskingService masking;

    @Transactional(readOnly = true)
    public Page<ComprehensivePersonSummaryView> search(String keyword, String personStatus, String regionCode,
            String residenceStatus, String floatingStatus, String permitStatus, int page, int size, String sort) {
        if (size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size 不能超过 100");
        }
        SortSpec spec = SortSpec.parse(sort);
        DataScopeCriteria scope = DataScopeCriteria.current();
        String normalizedKeyword = trimToNull(keyword);
        String normalizedPersonStatus = trimToNull(personStatus);
        String normalizedRegion = trimToNull(regionCode);
        String normalizedResidenceStatus = requireActiveOrNull(residenceStatus, "户籍状态");
        String normalizedFloatingStatus = requireActiveOrNull(floatingStatus, "流动登记状态");
        String normalizedPermitStatus = requireActiveOrNull(permitStatus, "居住证状态");
        long total = mapper.countSummaries(normalizedKeyword, normalizedPersonStatus, normalizedRegion,
                normalizedResidenceStatus, normalizedFloatingStatus, normalizedPermitStatus, scope);
        List<ComprehensivePersonSummaryView> rows = mapper.selectSummaries(normalizedKeyword, normalizedPersonStatus,
                normalizedRegion, normalizedResidenceStatus, normalizedFloatingStatus, normalizedPermitStatus, scope,
                spec.column(), spec.direction(), size, (long) page * size);
        rows.forEach(this::maskSummary);
        Pageable pageable = PageRequest.of(page, size);
        return new PageImpl<>(rows, pageable, total);
    }

    @Transactional(readOnly = true)
    public ComprehensivePersonProfileView profile(Long personId) {
        DataScopeCriteria scope = DataScopeCriteria.current();
        ComprehensivePersonSummaryView person = mapper.selectScopedSummary(personId, scope)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无权查看该人员数据"));
        maskSummary(person);
        ComprehensivePersonProfileView profile = new ComprehensivePersonProfileView();
        profile.setPerson(person);
        profile.setCurrentHousehold(mapper.selectCurrentHousehold(personId).orElse(null));
        profile.setCurrentResidence(mapper.selectCurrentResidence(personId).orElse(null));
        profile.setActiveFloating(mapper.selectActiveFloating(personId).orElse(null));
        profile.setCurrentPermit(mapper.selectCurrentPermit(personId).orElse(null));
        profile.setMigrationHistory(mapper.selectMigrationHistory(personId, 20));
        maskProfile(profile);
        return profile;
    }

    private void maskSummary(ComprehensivePersonSummaryView view) {
        view.setMaskedIdentityNo(masking.identityAlways(view.getMaskedIdentityNo()));
        view.setMaskedPhone(masking.phoneAlways(view.getMaskedPhone()));
        view.setMaskedPermitNo(masking.permitAlways(view.getMaskedPermitNo()));
        view.setCurrentAddress(masking.address(view.getCurrentAddress()));
    }

    private void maskProfile(ComprehensivePersonProfileView profile) {
        if (profile.getCurrentHousehold() != null) {
            profile.getCurrentHousehold().setAddress(masking.address(profile.getCurrentHousehold().getAddress()));
        }
        if (profile.getCurrentResidence() != null) {
            profile.getCurrentResidence().setRegisteredAddress(
                    masking.address(profile.getCurrentResidence().getRegisteredAddress()));
        }
        if (profile.getActiveFloating() != null) {
            profile.getActiveFloating().setCurrentAddress(
                    masking.address(profile.getActiveFloating().getCurrentAddress()));
        }
        if (profile.getCurrentPermit() != null) {
            profile.getCurrentPermit().setMaskedPermitNo(
                    masking.permitAlways(profile.getCurrentPermit().getMaskedPermitNo()));
        }
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * M5 综合查询仅支持当前有效关联记录：null/空表示不叠加状态过滤，ACTIVE 表示显式当前有效。
     */
    private static String requireActiveOrNull(String value, String label) {
        String normalized = trimToNull(value);
        if (normalized == null || ACTIVE_STATUS.equals(normalized)) {
            return normalized;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "M5 综合查询仅支持当前有效" + label + " ACTIVE");
    }

    private record SortSpec(String column, String direction) {
        static SortSpec parse(String value) {
            String raw = value == null || value.isBlank() ? "personId,DESC" : value.trim();
            String[] parts = raw.split(",", 2);
            String field = parts[0].trim();
            String direction = parts.length == 2 ? parts[1].trim().toUpperCase() : "DESC";
            String column = switch (field) {
                case "personId" -> "p.person_id";
                case "name" -> "p.name";
                case "birthDate" -> "p.birth_date";
                case "lastMigrationDate" -> "last_migration_date";
                case "permitValidUntil" -> "rp.valid_until";
                default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的排序字段");
            };
            if (!"ASC".equals(direction) && !"DESC".equals(direction)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的排序方向");
            }
            return new SortSpec(column, direction);
        }
    }
}
