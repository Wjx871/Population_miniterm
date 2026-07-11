package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.SysApprovalRequest;
import com.example.population.service.SysApprovalRequestService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "审批主单")
@RestController
@RequestMapping("/api/sys-approval-requests")
@RequiredArgsConstructor
public class SysApprovalRequestController {

    private final SysApprovalRequestService approvalService;

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<SysApprovalRequest>> page(@RequestParam(defaultValue = "1") long current,
                                                     @RequestParam(defaultValue = "10") long size,
                                                     @RequestParam(required = false) String status,
                                                     @RequestParam(required = false) Long currentApproverId) {
        Page<SysApprovalRequest> p = (Page<SysApprovalRequest>) approvalService.page(current, size, status, currentApproverId);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<SysApprovalRequest> get(@PathVariable Long id) {
        return Result.success(approvalService.getById(id));
    }

    @Operation(summary = "同意")
    @PutMapping("/{id}/approve")
    public Result<Void> approve(@PathVariable Long id,
                                 @RequestParam Long approverId,
                                 @RequestParam(required = false) String comment) {
        approvalService.approve(id, approverId, comment);
        return Result.success();
    }

    @Operation(summary = "驳回")
    @PutMapping("/{id}/reject")
    public Result<Void> reject(@PathVariable Long id,
                                @RequestParam Long approverId,
                                @RequestParam(required = false) String comment) {
        approvalService.reject(id, approverId, comment);
        return Result.success();
    }
}