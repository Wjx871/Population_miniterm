package com.wjx871.population.query;

import com.wjx871.population.common.ApiResponse;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query")
@RequiredArgsConstructor
public class Phase11QueryController {
    private final Phase11QueryService service;

    @GetMapping("/persons")
    @PreAuthorize("hasAuthority('population:view')")
    public ApiResponse<Page<ComprehensivePersonSummaryView>> persons(
            @RequestParam(required=false) String name, @RequestParam(required=false) String identityNo,
            @RequestParam(required=false) String gender, @RequestParam(required=false) Integer ageMin,
            @RequestParam(required=false) Integer ageMax, @RequestParam(required=false) String regionCode,
            @RequestParam(required=false) Long departmentId, @RequestParam(required=false) Long householdId,
            @RequestParam(required=false) String householdType, @RequestParam(required=false) String residenceStatus,
            @RequestParam(required=false) String floatingStatus, @RequestParam(required=false) String certificateType,
            @RequestParam(required=false) String keyPopulationType, @RequestParam(required=false) String currentStatus,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size,
            @RequestParam(defaultValue="personId,DESC") String sort) {
        return ApiResponse.ok(service.persons(new PersonQueryCriteria(name, identityNo, gender, ageMin, ageMax,
                regionCode, departmentId, householdId, householdType, residenceStatus, floatingStatus,
                certificateType, keyPopulationType, currentStatus, null, null), page, size, sort));
    }

    @GetMapping("/households")
    @PreAuthorize("hasAuthority('household:view')")
    public ApiResponse<Page<HouseholdQueryView>> households(
            @RequestParam(required=false) String householdNo, @RequestParam(required=false) String headPersonName,
            @RequestParam(required=false) String address, @RequestParam(required=false) String regionCode,
            @RequestParam(required=false) String householdType, @RequestParam(required=false) String status,
            @RequestParam(required=false) Integer memberCountMin, @RequestParam(required=false) Integer memberCountMax,
            @RequestParam(required=false) Boolean containsKeyPopulation, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        return ApiResponse.ok(service.households(new HouseholdQueryCriteria(householdNo, headPersonName, address,
                regionCode, householdType, status, memberCountMin, memberCountMax, containsKeyPopulation), page, size));
    }

    @GetMapping("/migration-history")
    @PreAuthorize("hasAuthority('migration:view')")
    public ApiResponse<Page<MigrationQueryView>> migrations(
            @RequestParam(required=false) Long personId, @RequestParam(required=false) String personName,
            @RequestParam(required=false) String migrationType, @RequestParam(required=false) String sourceRegionCode,
            @RequestParam(required=false) String targetRegionCode, @RequestParam(required=false) String status,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate executeDateFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate executeDateTo,
            @RequestParam(required=false) String applicationNo, @RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="10") int size) {
        return ApiResponse.ok(service.migrations(new MigrationQueryCriteria(personId, personName, migrationType,
                sourceRegionCode, targetRegionCode, status, executeDateFrom, executeDateTo, applicationNo), page, size));
    }
}
