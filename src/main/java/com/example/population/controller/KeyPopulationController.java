package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.LogOperation;
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
import org.springframework.web.bind.annotation.*;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @RequiresPermission("key:query")
    @Operation(summary = "查某人的重点登记历史（含已解除）")
    @GetMapping("/by-person/{personId}")
    public Result<List<KeyPopulation>> listByPerson(@PathVariable Long personId) {
        return Result.success(keyService.listActiveByPerson(personId));
    }

    @RequiresPermission("key:query")
    @Operation(summary = "查某人当前已登记的重点类型集合（前端去重用）")
    @GetMapping("/person-active-types")
    public Result<Set<String>> personActiveTypes(@RequestParam Long personId) {
        Set<String> types = new HashSet<>();
        for (KeyPopulation k : keyService.listActiveByPerson(personId)) {
            if (k.getKeyTypeCode() != null) types.add(k.getKeyTypeCode());
        }
        return Result.success(types);
    }

    @RequiresPermission("key:query")
    @Operation(summary = "查某人和某类型是否已有有效重点登记")
    @GetMapping("/exists-active")
    public Result<Map<String, Object>> existsActive(@RequestParam Long personId,
                                                     @RequestParam String keyTypeCode) {
        boolean exists = keyService.existsActiveByPersonAndType(personId, keyTypeCode);
        return Result.success(java.util.Map.of("exists", exists));
    }

    @RequiresPermission("key:register")
    @LogOperation(module = "KEY", type = "REGISTER", targetTable = "key_population")
    @Operation(summary = "新增重点登记（含同人同类型重复防护）")
    @PostMapping
    public Result<KeyPopulation> create(@Valid @RequestBody KeyPopulationCreateDTO dto) {
        return Result.success("登记成功", keyService.register(dto));
    }

    @RequiresPermission("key:register")
    @LogOperation(module = "KEY", type = "UPDATE", targetTable = "key_population", targetIdSpel = "#id")
    @Operation(summary = "更新重点登记（白名单字段）")
    @PutMapping("/{id}")
    public Result<KeyPopulation> update(@PathVariable Long id, @Valid @RequestBody KeyPopulationUpdateDTO dto) {
        KeyPopulation k = new KeyPopulation();
        org.springframework.beans.BeanUtils.copyProperties(dto, k);
        k.setKeyId(id);
        keyService.updateById(k);
        return Result.success("更新成功", keyService.getById(id));
    }

    @RequiresPermission("key:release")
    @LogOperation(module = "KEY", type = "RELEASE", targetTable = "key_population", targetIdSpel = "#id")
    @Operation(summary = "解除重点管理")
    @PutMapping("/{id}/release")
    public Result<Void> release(@PathVariable Long id, @RequestParam Long releaseApplicationId) {
        keyService.release(id, releaseApplicationId);
        return Result.success();
    }

    /**
     * 物理删除不开放（C-19 审计要求保留全部历史）。
     */
    @Operation(summary = "删除（已禁用，按设计文档 C-19 不允许物理删除）", hidden = true)
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        throw new com.example.population.exception.BizException(405,
                "重点人口登记数据受审计保护（C-19），不允许物理删除");
    }
}