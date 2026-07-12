package com.wjx871.population.migration;
import com.wjx871.population.common.ApiResponse; import jakarta.servlet.http.HttpServletRequest; import jakarta.validation.Valid; import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus; import org.springframework.security.access.prepost.PreAuthorize; import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/migrations") @RequiredArgsConstructor
public class MigrationController {
 private final MigrationService service;
 @PostMapping("/in/applications") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('migration:in:create')") public ApiResponse<MigrationCreatedView> createIn(@Valid @RequestBody MigrationInRequest r,HttpServletRequest h){return ApiResponse.created(service.createIn(r,h));}
 @PutMapping("/in/applications/{id}") @PreAuthorize("hasAuthority('migration:in:create') and hasAuthority('application:edit')") public ApiResponse<MigrationCreatedView> updateIn(@PathVariable Long id,@Valid @RequestBody MigrationInRequest r){return ApiResponse.ok(service.updateIn(id,r));}
 @PostMapping("/out/applications") @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('migration:out:create')") public ApiResponse<MigrationCreatedView> createOut(@Valid @RequestBody MigrationOutRequest r,HttpServletRequest h){return ApiResponse.created(service.createOut(r,h));}
 @PutMapping("/out/applications/{id}") @PreAuthorize("hasAuthority('migration:out:create') and hasAuthority('application:edit')") public ApiResponse<MigrationCreatedView> updateOut(@PathVariable Long id,@Valid @RequestBody MigrationOutRequest r){return ApiResponse.ok(service.updateOut(id,r));}
 @GetMapping("/applications/{id}") @PreAuthorize("hasAuthority('migration:view')") public ApiResponse<MigrationDetailView> detail(@PathVariable Long id){return ApiResponse.ok(service.detail(id));}
 @PostMapping("/in/applications/{id}/execute") @PreAuthorize("hasAuthority('migration:execute')") public ApiResponse<Void> executeIn(@PathVariable Long id,@Valid @RequestBody ExecuteRequest r,HttpServletRequest h){service.executeIn(id,r,h);return ApiResponse.ok(null);}
 @PostMapping("/out/applications/{id}/execute") @PreAuthorize("hasAuthority('migration:execute')") public ApiResponse<Void> executeOut(@PathVariable Long id,@Valid @RequestBody ExecuteRequest r,HttpServletRequest h){service.executeOut(id,r,h);return ApiResponse.ok(null);}
}
