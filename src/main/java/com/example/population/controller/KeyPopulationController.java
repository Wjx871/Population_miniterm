package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.KeyPopulation;
import com.example.population.service.KeyPopulationService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "重点人口")
@RestController
@RequestMapping("/api/key-population")
@RequiredArgsConstructor
public class KeyPopulationController {

    private final KeyPopulationService keyService;

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<KeyPopulation>> page(@RequestParam(defaultValue = "1") long current,
                                                @RequestParam(defaultValue = "10") long size,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(required = false) String keyTypeCode,
                                                @RequestParam(required = false) String managementLevelCode,
                                                @RequestParam(required = false) String status,
                                                @RequestParam(required = false) Long responsibleDepartmentId) {
        Page<KeyPopulation> p = (Page<KeyPopulation>) keyService.page(current, size, keyword, keyTypeCode, managementLevelCode, status, responsibleDepartmentId);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<KeyPopulation> get(@PathVariable Long id) {
        return Result.success(keyService.getById(id));
    }

    @Operation(summary = "新增重点登记")
    @PostMapping
    public Result<Void> create(@RequestBody KeyPopulation k) {
        keyService.save(k);
        return Result.success();
    }

    @Operation(summary = "更新重点登记")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody KeyPopulation k) {
        k.setKeyId(id);
        keyService.updateById(k);
        return Result.success();
    }

    @Operation(summary = "解除重点管理")
    @PutMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestParam Long releaseApplicationId) {
        keyService.release(id, releaseApplicationId);
        return Result.success();
    }

    @Operation(summary = "删除重点登记")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        keyService.removeById(id);
        return Result.success();
    }
}