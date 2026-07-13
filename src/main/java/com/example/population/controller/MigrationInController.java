package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.LogOperation;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.ApprovalDraftDTO;
import com.example.population.dto.MigrationInDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.MigrationIn;
import com.example.population.service.ApprovalGateService;
import com.example.population.service.MigrationInService;
import com.example.population.util.DateRangeValidator;
import com.example.population.util.PageUtil;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "迁入业务")
@RestController
@RequestMapping("/api/migration-in")
@RequiredArgsConstructor
public class MigrationInController {

    private final MigrationInService migrationInService;
    private final ApprovalGateService approvalGateService;
    private final ObjectMapper objectMapper;

    @RequiresPermission("migration:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<MigrationIn>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String inTypeCode,
                                            @RequestParam(required = false) String toRegionCode,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        DateRangeValidator.assertStartBeforeEnd(startDate, endDate);
        Page<MigrationIn> p = (Page<MigrationIn>) migrationInService.page(current, size, keyword, inTypeCode, toRegionCode, startDate, endDate);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("migration:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<MigrationIn> get(@PathVariable Long id) {
        return Result.success(migrationInService.getById(id));
    }

    @RequiresPermission("migration:query")
    @Operation(summary = "按联办批次号查询")
    @GetMapping("/by-batch/{transferBatchNo}")
    public Result<List<MigrationIn>> byBatch(@PathVariable String transferBatchNo) {
        return Result.success(migrationInService.listByTransferBatch(transferBatchNo));
    }

    @RequiresPermission("migration:in:create")
    @LogOperation(module = "MIGRATION", type = "IN_CREATE", targetTable = "migration_in")
    @Operation(summary = "新增迁入登记（审批流：L3 直通，L1/L2 走审批）")
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody MigrationInDTO dto) throws Exception {
        SecurityContext sc = SecurityContext.current();
        if (sc.getPermissionLevel() != null && sc.getPermissionLevel() >= 3) {
            MigrationIn m = migrationInService.createMigrationIn(dto);
            Map<String, Object> data = new HashMap<>();
            data.put("inId", m.getInId());
            data.put("directLanding", true);
            return Result.success("登记成功", data);
        }
        ApprovalDraftDTO draft = new ApprovalDraftDTO();
        draft.setBusinessType("MIGRATION_IN");
        draft.setPayloadJson(objectMapper.writeValueAsString(dto));
        Long approvalId = approvalGateService.submit(draft);
        Map<String, Object> data = new HashMap<>();
        data.put("approvalId", approvalId);
        data.put("directLanding", false);
        return Result.success("已提交审批，等待 L3 审批", data);
    }

    @RequiresPermission("migration:out:create")
    @LogOperation(module = "MIGRATION", type = "IN_COMPLETE", targetTable = "migration_in", targetIdSpel = "#id")
    @Operation(summary = "办结迁入（事务：归档旧登记 → 写新登记 → 回填）")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestParam Long operatorId) {
        migrationInService.complete(id, operatorId);
        return Result.success();
    }

    @RequiresPermission("migration:in:create")
    @Operation(summary = "兼容旧 PUT /{id}：禁用，请走 /complete 办结或重新 POST 新建")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody(required = false) MigrationIn m) {
        throw new com.example.population.exception.BizException(405,
                "迁入记录不支持通用 PUT 更新；办结请使用 PUT /api/migration-in/{id}/complete");
    }

    /**
     * 禁用：迁入记录不支持删除。
     */
    @Operation(summary = "禁用：删除迁入记录")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        throw new com.example.population.exception.BizException(405,
                "迁入记录不支持删除；如需撤回，请联系 L3 走驳回流程");
    }
}
