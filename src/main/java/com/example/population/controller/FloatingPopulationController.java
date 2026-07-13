package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.LogOperation;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.FloatingLeaveDTO;
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
    @LogOperation(module = "FLOATING", type = "REGISTER", targetTable = "floating_population")
    @Operation(summary = "新增登记")
    @PostMapping
    public Result<FloatingPopulation> create(@Valid @RequestBody FloatingPopulationCreateDTO dto) {
        return Result.success("登记成功", floatingService.createFloating(dto));
    }

    @RequiresPermission("floating:register")
    @LogOperation(module = "FLOATING", type = "UPDATE", targetTable = "floating_population", targetIdSpel = "#id")
    @Operation(summary = "更新登记（白名单字段）")
    @PutMapping("/{id}")
    public Result<FloatingPopulation> update(@PathVariable Long id,
                                             @Valid @RequestBody FloatingPopulationUpdateDTO dto) {
        return Result.success("更新成功", floatingService.updateFloating(id, dto));
    }

    @RequiresPermission("floating:register")
    @LogOperation(module = "FLOATING", type = "LEAVE", targetTable = "floating_population", targetIdSpel = "#id")
    @Operation(summary = "离开登记（标记为 LEFT，写实际离开日期）")
    @PutMapping("/{id}/leave")
    public Result<FloatingPopulation> leave(@PathVariable Long id,
                                            @Valid @RequestBody FloatingLeaveDTO dto) {
        dto.setFloatingId(id);
        return Result.success("离开登记成功", floatingService.leave(id, dto));
    }

    @RequiresPermission("floating:register")
    @Operation(summary = "到期扫描：把 planned_leave_date<CURDATE() 的 ACTIVE 自动置 EXPIRED")
    @PostMapping("/scan-expiring")
    public Result<Integer> scanExpiring() {
        return Result.success("扫描完成", floatingService.scanExpiring());
    }

    @RequiresPermission("floating:query")
    @Operation(summary = "查询单个人员的流动历史（含 LEFT/EXPIRED）")
    @GetMapping("/by-person/{personId}")
    public Result<java.util.List<FloatingPopulation>> listByPerson(@PathVariable Long personId) {
        return Result.success(floatingService.lambdaQuery()
                .eq(FloatingPopulation::getPersonId, personId)
                .orderByDesc(FloatingPopulation::getRegisterDate)
                .list());
    }

    /**
     * 删除接口不开放（设计文档 C-19 核心数据不物理删除）。
     */
    @Operation(summary = "删除（已禁用，按设计文档 C-19 不允许物理删除）", hidden = true)
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        throw new com.example.population.exception.BizException(405,
                "流动人口登记数据受审计保护（C-19），不允许物理删除");
    }
}