package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.ResidenceArchive;
import com.example.population.service.ResidenceArchiveService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "户籍历史快照")
@RestController
@RequestMapping("/api/residence-archives")
@RequiredArgsConstructor
public class ResidenceArchiveController {

    private final ResidenceArchiveService archiveService;

    @RequiresPermission("archive:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<ResidenceArchive>> page(@RequestParam(defaultValue = "1") long current,
                                                   @RequestParam(defaultValue = "10") long size,
                                                   @RequestParam(required = false) Long personId,
                                                   @RequestParam(required = false) Long householdId,
                                                   @RequestParam(required = false) String archiveTypeCode) {
        Page<ResidenceArchive> p = (Page<ResidenceArchive>) archiveService.page(current, size, personId, householdId, archiveTypeCode);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("archive:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<ResidenceArchive> get(@PathVariable Long id) {
        return Result.success(archiveService.getById(id));
    }

    @RequiresPermission("archive:manage")
    @Operation(summary = "新建快照")
    @PostMapping
    public Result<Void> create(@RequestBody ResidenceArchive a) {
        archiveService.save(a);
        return Result.success();
    }
}