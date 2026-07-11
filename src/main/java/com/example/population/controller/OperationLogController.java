package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.OperationLog;
import com.example.population.service.OperationLogService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "操作日志")
@RestController
@RequestMapping("/api/operation-logs")
@RequiredArgsConstructor
public class OperationLogController {

    private final OperationLogService logService;

    @RequiresPermission("log:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<OperationLog>> page(@RequestParam(defaultValue = "1") long current,
                                              @RequestParam(defaultValue = "10") long size,
                                              @RequestParam(required = false) Long userId,
                                              @RequestParam(required = false) String operationTypeCode,
                                              @RequestParam(required = false) String moduleName) {
        Page<OperationLog> p = (Page<OperationLog>) logService.page(current, size, userId, operationTypeCode, moduleName);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("log:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<OperationLog> get(@PathVariable Long id) {
        return Result.success(logService.getById(id));
    }
}