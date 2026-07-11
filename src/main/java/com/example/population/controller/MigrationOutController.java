package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.MigrationOutDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.MigrationOut;
import com.example.population.service.MigrationOutService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Tag(name = "迁出业务")
@RestController
@RequestMapping("/api/migration-out")
@RequiredArgsConstructor
public class MigrationOutController {

    private final MigrationOutService migrationOutService;

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<MigrationOut>> page(@RequestParam(defaultValue = "1") long current,
                                             @RequestParam(defaultValue = "10") long size,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false) String outTypeCode,
                                             @RequestParam(required = false) String fromRegionCode,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        Page<MigrationOut> p = (Page<MigrationOut>) migrationOutService.page(current, size, keyword, outTypeCode, fromRegionCode, startDate, endDate);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<MigrationOut> get(@PathVariable Long id) {
        return Result.success(migrationOutService.getById(id));
    }

    @Operation(summary = "按联办批次号查询")
    @GetMapping("/by-batch/{transferBatchNo}")
    public Result<List<MigrationOut>> byBatch(@PathVariable String transferBatchNo) {
        return Result.success(migrationOutService.listByTransferBatch(transferBatchNo));
    }

    @Operation(summary = "新增迁出登记（不办结）")
    @PostMapping
    public Result<MigrationOut> create(@Valid @RequestBody MigrationOutDTO dto) {
        return Result.success("登记成功", migrationOutService.createMigrationOut(dto));
    }

    /**
     * 头号事务边界：办结迁出。
     * 事务内：归档快照 → 删除当前登记 → LEFT 成员关系 → 回填 archive_id。
     */
    @Operation(summary = "办结迁出（事务：归档、删登记、LEFT 成员）")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestParam Long operatorId) {
        migrationOutService.complete(id, operatorId);
        return Result.success();
    }

    @Operation(summary = "兼容旧 PUT /{id}")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MigrationOut m) {
        m.setOutId(id);
        migrationOutService.updateById(m);
        return Result.success();
    }

    @Operation(summary = "删除迁出记录")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        migrationOutService.removeById(id);
        return Result.success();
    }
}
