package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.ApprovalDraftDTO;
import com.example.population.dto.MigrationOutDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.MigrationOut;
import com.example.population.service.ApprovalGateService;
import com.example.population.service.MigrationOutService;
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

@Tag(name = "迁出业务")
@RestController
@RequestMapping("/api/migration-out")
@RequiredArgsConstructor
public class MigrationOutController {

    private final MigrationOutService migrationOutService;
    private final ApprovalGateService approvalGateService;
    private final ObjectMapper objectMapper;

    @RequiresPermission("migration:query")
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

    @RequiresPermission("migration:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<MigrationOut> get(@PathVariable Long id) {
        return Result.success(migrationOutService.getById(id));
    }

    @RequiresPermission("migration:query")
    @Operation(summary = "按联办批次号查询")
    @GetMapping("/by-batch/{transferBatchNo}")
    public Result<List<MigrationOut>> byBatch(@PathVariable String transferBatchNo) {
        return Result.success(migrationOutService.listByTransferBatch(transferBatchNo));
    }

    @RequiresPermission("migration:out:create")
    @Operation(summary = "新增迁出登记（审批流：L3 直通，L1/L2 走审批）")
    @PostMapping
    public Result<Map<String, Object>> create(@Valid @RequestBody MigrationOutDTO dto) throws Exception {
        SecurityContext sc = SecurityContext.current();
        if (sc.getPermissionLevel() != null && sc.getPermissionLevel() >= 3) {
            MigrationOut m = migrationOutService.createMigrationOut(dto);
            Map<String, Object> data = new HashMap<>();
            data.put("outId", m.getOutId());
            data.put("directLanding", true);
            return Result.success("登记成功", data);
        }
        ApprovalDraftDTO draft = new ApprovalDraftDTO();
        draft.setBusinessType("MIGRATION_OUT");
        draft.setPayloadJson(objectMapper.writeValueAsString(dto));
        Long approvalId = approvalGateService.submit(draft);
        Map<String, Object> data = new HashMap<>();
        data.put("approvalId", approvalId);
        data.put("directLanding", false);
        return Result.success("已提交审批，等待 L3 审批", data);
    }

    @RequiresPermission("migration:out:create")
    @Operation(summary = "办结迁出（事务：归档、删登记、LEFT 成员）")
    @PutMapping("/{id}/complete")
    public Result<Void> complete(@PathVariable Long id, @RequestParam Long operatorId) {
        migrationOutService.complete(id, operatorId);
        return Result.success();
    }

    @RequiresPermission("migration:out:create")
    @Operation(summary = "兼容旧 PUT /{id}")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody MigrationOut m) {
        m.setOutId(id);
        migrationOutService.updateById(m);
        return Result.success();
    }

    @RequiresPermission("migration:out:create")
    @Operation(summary = "删除迁出记录")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        migrationOutService.removeById(id);
        return Result.success();
    }
}
