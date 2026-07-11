package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.DataExportLog;
import com.example.population.service.DataExportLogService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "数据导出审计")
@RestController
@RequestMapping("/api/data-export-logs")
@RequiredArgsConstructor
public class DataExportLogController {

    private final DataExportLogService exportService;

    @RequiresPermission("log:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<DataExportLog>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) String exportTypeCode,
                                               @RequestParam(required = false) Integer sensitivityLevel) {
        Page<DataExportLog> p = (Page<DataExportLog>) exportService.page(current, size, userId, exportTypeCode, sensitivityLevel);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("export:normal")
    @Operation(summary = "记录一次导出")
    @PostMapping
    public Result<Void> create(@RequestBody DataExportLog log) {
        exportService.save(log);
        return Result.success();
    }
}