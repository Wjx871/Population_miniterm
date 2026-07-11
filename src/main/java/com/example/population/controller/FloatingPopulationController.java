package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.FloatingPopulation;
import com.example.population.service.FloatingPopulationService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "流动人口")
@RestController
@RequestMapping("/api/floating-population")
@RequiredArgsConstructor
public class FloatingPopulationController {

    private final FloatingPopulationService floatingService;

    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<FloatingPopulation>> page(@RequestParam(defaultValue = "1") long current,
                                                    @RequestParam(defaultValue = "10") long size,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String currentRegionCode,
                                                    @RequestParam(required = false) String status,
                                                    @RequestParam(required = false) Long personId) {
        Page<FloatingPopulation> p = (Page<FloatingPopulation>) floatingService.page(current, size, keyword, currentRegionCode, status, personId);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<FloatingPopulation> get(@PathVariable Long id) {
        return Result.success(floatingService.getById(id));
    }

    @Operation(summary = "新增登记")
    @PostMapping
    public Result<Void> create(@RequestBody FloatingPopulation f) {
        floatingService.save(f);
        return Result.success();
    }

    @Operation(summary = "更新登记")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody FloatingPopulation f) {
        f.setFloatingId(id);
        floatingService.updateById(f);
        return Result.success();
    }

    @Operation(summary = "删除登记")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        floatingService.removeById(id);
        return Result.success();
    }
}