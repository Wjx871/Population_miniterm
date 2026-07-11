package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.AdminRegion;
import com.example.population.service.AdminRegionService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "行政区划")
@RestController
@RequestMapping("/api/admin-regions")
@RequiredArgsConstructor
public class AdminRegionController {

    private final AdminRegionService regionService;

    @Operation(summary = "分页查询行政区划")
    @GetMapping
    public Result<PageVO<AdminRegion>> page(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "10") long size,
                                            @RequestParam(required = false) String keyword,
                                            @RequestParam(required = false) String levelCode) {
        Page<AdminRegion> p = (Page<AdminRegion>) regionService.page(current, size, keyword, levelCode);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询子区划")
    @GetMapping("/children")
    public Result<List<AdminRegion>> children(@RequestParam(required = false) String parentCode) {
        return Result.success(regionService.listChildren(parentCode));
    }

    @Operation(summary = "判断两个区划是否同市")
    @GetMapping("/same-city")
    public Result<Boolean> sameCity(@RequestParam String a, @RequestParam String b) {
        return Result.success(regionService.isSameCity(a, b));
    }

    @Operation(summary = "查询单个区划")
    @GetMapping("/{code}")
    public Result<AdminRegion> get(@PathVariable String code) {
        return Result.success(regionService.getById(code));
    }

    @Operation(summary = "新增区划")
    @PostMapping
    public Result<Void> create(@RequestBody AdminRegion region) {
        regionService.save(region);
        return Result.success();
    }

    @Operation(summary = "更新区划")
    @PutMapping("/{code}")
    public Result<Void> update(@PathVariable String code, @RequestBody AdminRegion region) {
        region.setRegionCode(code);
        regionService.updateById(region);
        return Result.success();
    }

    @Operation(summary = "启用/停用")
    @PutMapping("/{code}/enabled")
    public Result<Void> updateEnabled(@PathVariable String code, @RequestParam Integer enabled) {
        regionService.updateEnabled(code, enabled);
        return Result.success();
    }

    @Operation(summary = "删除区划")
    @DeleteMapping("/{code}")
    public Result<Void> remove(@PathVariable String code) {
        regionService.removeById(code);
        return Result.success();
    }
}
