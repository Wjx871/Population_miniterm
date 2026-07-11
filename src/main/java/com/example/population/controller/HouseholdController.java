package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.HouseholdCreateDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.Household;
import com.example.population.service.HouseholdService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "家庭户档案")
@RestController
@RequestMapping("/api/households")
@RequiredArgsConstructor
public class HouseholdController {

    private final HouseholdService householdService;

    @Operation(summary = "分页查询户籍")
    @GetMapping
    public Result<PageVO<Household>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String regionCode,
                                          @RequestParam(required = false) String status) {
        Page<Household> p = (Page<Household>) householdService.page(current, size, keyword, regionCode, status);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询详情（含成员数）")
    @GetMapping("/{id}")
    public Result<Household> detail(@PathVariable Long id) {
        return Result.success(householdService.getDetail(id));
    }

    @Operation(summary = "立户")
    @PostMapping("/establish")
    public Result<Household> establish(@Valid @RequestBody HouseholdCreateDTO dto) {
        return Result.success("立户成功", householdService.establishHousehold(dto));
    }

    @Operation(summary = "新增家庭户（兼容旧 POST /）")
    @PostMapping
    public Result<Void> create(@RequestBody Household h) {
        householdService.save(h);
        return Result.success();
    }

    @Operation(summary = "更新家庭户")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody Household h) {
        h.setHouseholdId(id);
        householdService.updateById(h);
        return Result.success();
    }

    @Operation(summary = "更换户主")
    @PutMapping("/{id}/head")
    public Result<Void> changeHead(@PathVariable Long id, @RequestParam Long newHeadPersonId) {
        householdService.changeHead(id, newHeadPersonId);
        return Result.success();
    }

    @Operation(summary = "销户（停用户；前置校验无 CURRENT 成员）")
    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id, @RequestParam Long operatorId) {
        householdService.disableHousehold(id, operatorId);
        return Result.success();
    }

    @Operation(summary = "删除家庭户（软删已禁用，请走 /disable）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        householdService.removeById(id);
        return Result.success();
    }
}
