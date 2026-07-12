package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.KeyPopulationCreateDTO;
import com.example.population.dto.KeyPopulationUpdateDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.KeyPopulation;
import com.example.population.service.KeyPopulationService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "重点人口")
@RestController
@RequestMapping("/api/key-population")
@RequiredArgsConstructor
public class KeyPopulationController {

    private final KeyPopulationService keyService;

    @RequiresPermission("key:query")
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

    @RequiresPermission("key:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<KeyPopulation> get(@PathVariable Long id) {
        return Result.success(keyService.getById(id));
    }

    @RequiresPermission("key:register")
    @Operation(summary = "新增重点登记（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody KeyPopulationCreateDTO dto) {
        KeyPopulation k = new KeyPopulation();
        BeanUtils.copyProperties(dto, k);
        if (k.getStatus() == null) k.setStatus("ACTIVE");
        keyService.save(k);
        return Result.success();
    }

    @RequiresPermission("key:register")
    @Operation(summary = "更新重点登记（白名单字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody KeyPopulationUpdateDTO dto) {
        KeyPopulation k = new KeyPopulation();
        BeanUtils.copyProperties(dto, k);
        k.setKeyId(id);
        keyService.updateById(k);
        return Result.success();
    }

    @RequiresPermission("key:release")
    @Operation(summary = "解除重点管理")
    @PutMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestParam Long releaseApplicationId) {
        keyService.release(id, releaseApplicationId);
        return Result.success();
    }

    @RequiresPermission("key:register")
    @Operation(summary = "禁用：删除重点登记（审计要求保留）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        keyService.removeById(id);
        return Result.success();
    }
}