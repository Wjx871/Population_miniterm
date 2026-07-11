package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.Result;
import com.example.population.entity.SysApprovalLog;
import com.example.population.service.SysApprovalLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "审批日志")
@RestController
@RequestMapping("/api/sys-approval-logs")
@RequiredArgsConstructor
public class SysApprovalLogController {

    private final SysApprovalLogService logService;

    @RequiresPermission("log:query")
    @Operation(summary = "按审批主单查询日志")
    @GetMapping("/by-approval/{approvalId}")
    public Result<List<SysApprovalLog>> list(@PathVariable Long approvalId) {
        return Result.success(logService.listByApproval(approvalId));
    }
}