package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.MigrationInDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.MigrationIn;
import com.example.population.service.MigrationInService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "迁入业务")
@RestController
@RequestMapping("/api/migration-in")
@RequiredArgsConstructor
public class MigrationInController {

    private final MigrationInService migrationInService;

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<MigrationIn>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String inTypeCode,
                                            @RequestParam(required = false) String toRegionCode,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Page<MigrationIn> p = (Page<MigrationIn>) migrationInService.page(current, size, keyword, inTypeCode, toRegionCode, startDate, endDate);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<MigrationIn> get(@PathVariable Long id) {
        return Result.success(migrationInService.getById(id));
    }

    @Operation(summary = "按联办批次号查询")
    @GetMapping("/by-batch/{transferBatchNo}")
    public Result<List<MigrationIn>> byBatch(@PathVariable String transferBatchNo) {
        return Result.success(migrationInService.listByTransferBatch(transferBatchNo));
    }

    @Operation(summary = "新增迁入登记（不办结）")
    @PostMapping
    public Result<MigrationIn> create(@Valid @RequestBody MigrationInDTO dto) {
        return Result.success("登记成功", migrationInService.createMigrationIn(dto));
    }

    @Operation(summary = "办结迁入（事务：归档旧登记 → 写新登记 → 回填）")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestParam Long operatorId) {
        migrationInService.complete(id, operatorId);
        return Result.success();
    }

    @Operation(summary = "兼容旧 PUT /{id}")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MigrationIn m) {
        m.setInId(id);
        migrationInService.updateById(m);
        return Result.success();
    }

    @Operation(summary = "删除迁入记录")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        migrationInService.removeById(id);
        return Result.success();
    }
}
