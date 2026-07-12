package com.wjx871.population.query;

import com.wjx871.population.common.ApiResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/queries")
@RequiredArgsConstructor
public class ComprehensiveQueryController {
    private final ComprehensiveQueryService service;

    @GetMapping("/persons")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<Page<ComprehensivePersonSummaryView>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String personStatus,
            @RequestParam(required = false) String regionCode,
            @RequestParam(required = false) String residenceStatus,
            @RequestParam(required = false) String floatingStatus,
            @RequestParam(required = false) String permitStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "personId,DESC") String sort) {
        return ApiResponse.ok(service.search(keyword, personStatus, regionCode, residenceStatus, floatingStatus,
                permitStatus, page, size, sort));
    }

    @GetMapping("/persons/{personId}")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<ComprehensivePersonProfileView> profile(@PathVariable Long personId) {
        return ApiResponse.ok(service.profile(personId));
    }
}
