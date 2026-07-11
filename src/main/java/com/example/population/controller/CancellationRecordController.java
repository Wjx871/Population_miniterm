package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.CancellationDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.CancellationRecord;
import com.example.population.service.CancellationRecordService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "注销记录")
@RestController
@RequestMapping("/api/cancellation-records")
@RequiredArgsConstructor
public class CancellationRecordController {

    private final CancellationRecordService cancellationService;

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<CancellationRecord>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String cancelObjectType,
                                                    @RequestParam(required = false) String cancelReasonCode) {
        Page<CancellationRecord> p = (Page<CancellationRecord>) cancellationService.page(current, size, cancelObjectType, cancelReasonCode);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<CancellationRecord> get(@PathVariable Long id) {
        return Result.success(cancellationService.getById(id));
    }

    @Operation(summary = "人口注销前置校验")
    @GetMapping("/precheck-person/{personId}")
    public Result<CancellationRecordService.PrecheckResult> precheckPerson(@PathVariable Long personId) {
        return Result.success(cancellationService.precheckPerson(personId));
    }

    @Operation(summary = "家庭户销户前置校验")
    @GetMapping("/precheck-household/{householdId}")
    public Result<CancellationRecordService.PrecheckResult> precheckHousehold(@PathVariable Long householdId) {
        return Result.success(cancellationService.precheckHousehold(householdId));
    }

    @Operation(summary = "新增注销/销户记录（不办结）")
    @PostMapping
    public Result<CancellationRecord> create(@Valid @RequestBody CancellationDTO dto) {
        return Result.success("登记成功", cancellationService.createCancellation(dto));
    }

    @Operation(summary = "办结人口注销（事务：归档 → 删登记 → 状态更新）")
    @PutMapping("/{id}/complete-person")
    public Result<Void> completePerson(@PathVariable Long id, @RequestParam Long operatorId) {
        cancellationService.completePersonCancellation(id, operatorId);
        return Result.success();
    }

    @Operation(summary = "办结家庭户销户（事务：前置 → 归档 → 户置 CANCELLED）")
    @PutMapping("/{id}/complete-household")
    public Result<Void> completeHousehold(@PathVariable Long id, @RequestParam Long operatorId) {
        cancellationService.completeHouseholdCancellation(id, operatorId);
        return Result.success();
    }

    @Operation(summary = "兼容旧 PUT /{id}")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody CancellationRecord r) {
        r.setCancelId(id);
        cancellationService.updateById(r);
        return Result.success();
    }

    @Operation(summary = "删除注销记录")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        cancellationService.removeById(id);
        return Result.success();
    }
}
