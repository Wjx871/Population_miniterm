package com.wjx871.population.application;

import com.wjx871.population.approval.ApprovalLogView;
import com.wjx871.population.approval.ApprovalService;
import com.wjx871.population.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
public class ApplicationController {
    private final ApplicationService service;
    private final ApprovalService approvalService;

    @PostMapping @ResponseStatus(HttpStatus.CREATED) @PreAuthorize("hasAuthority('application:create')")
    public ApiResponse<ApplicationView> create(@Valid @RequestBody ApplicationRequest request, HttpServletRequest httpRequest) { return ApiResponse.created(service.create(request, httpRequest)); }

    @GetMapping @PreAuthorize("hasAuthority('application:view')")
    public ApiResponse<Page<ApplicationView>> search(@RequestParam(required=false) String applicationNo,
            @RequestParam(required=false) BusinessType businessType, @RequestParam(required=false) ApplicationStatus status,
            @RequestParam(required=false) String applicantName,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            Pageable pageable) { return ApiResponse.ok(service.search(applicationNo,businessType,status,applicantName,createdFrom,createdTo,pageable)); }

    @GetMapping("/{id}") @PreAuthorize("hasAuthority('application:view')")
    public ApiResponse<ApplicationView> get(@PathVariable Long id) { return ApiResponse.ok(service.getView(id)); }

    @PutMapping("/{id}") @PreAuthorize("hasAuthority('application:edit')")
    public ApiResponse<ApplicationView> update(@PathVariable Long id, @Valid @RequestBody ApplicationRequest request) { return ApiResponse.ok(service.update(id,request)); }

    @DeleteMapping("/{id}") @PreAuthorize("hasAuthority('application:edit')")
    public ApiResponse<Void> delete(@PathVariable Long id, HttpServletRequest request) { service.cancelDraft(id, request); return ApiResponse.ok(null); }

    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('application:submit')")
    public ApiResponse<Void> submit(@PathVariable Long id, HttpServletRequest request) { approvalService.submit(id,request); return ApiResponse.ok(null); }

    @PostMapping("/{id}/withdraw") @PreAuthorize("hasAuthority('application:withdraw')")
    public ApiResponse<Void> withdraw(@PathVariable Long id, HttpServletRequest request) { approvalService.withdraw(id,request); return ApiResponse.ok(null); }

    @GetMapping("/{id}/approval-logs") @PreAuthorize("hasAnyAuthority('application:view','approval:view')")
    public ApiResponse<List<ApprovalLogView>> logs(@PathVariable Long id) { return ApiResponse.ok(approvalService.logs(id)); }
}
