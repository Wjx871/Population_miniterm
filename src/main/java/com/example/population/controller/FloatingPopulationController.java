package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.FloatingPopulationCreateDTO;
import com.example.population.dto.FloatingPopulationUpdateDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.FloatingPopulation;
import com.example.population.service.FloatingPopulationService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "流动人口")
@RestController
@RequestMapping("/api/floating-population")
@RequiredArgsConstructor
public class FloatingPopulationController {

    private final FloatingPopulationService floatingService;

    @RequiresPermission("floating:query")
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

    @RequiresPermission("floating:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<FloatingPopulation> get(@PathVariable Long id) {
        return Result.success(floatingService.getById(id));
    }

    @RequiresPermission("floating:register")
    @Operation(summary = "新增登记（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody FloatingPopulationCreateDTO dto) {
        FloatingPopulation f = new FloatingPopulation();
        BeanUtils.copyProperties(dto, f);
        if (f.getStatus() == null) f.setStatus("ACTIVE");
        floatingService.save(f);
        return Result.success();
    }

    @RequiresPermission("floating:register")
    @Operation(summary = "更新登记（白名单字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody FloatingPopulationUpdateDTO dto) {
        FloatingPopulation f = new FloatingPopulation();
        BeanUtils.copyProperties(dto, f);
        f.setFloatingId(id);
        floatingService.updateById(f);
        return Result.success();
    }

    @RequiresPermission("floating:register")
    @Operation(summary = "禁用：删除登记（审计要求保留）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        floatingService.removeById(id);
        return Result.success();
    }
}