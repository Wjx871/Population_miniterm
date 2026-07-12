package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.dto.SysDepartmentCreateDTO;
import com.example.population.dto.SysDepartmentUpdateDTO;
import com.example.population.entity.SysDepartment;
import com.example.population.service.SysDepartmentService;
import com.example.population.util.PageUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "部门机构")
@RestController
@RequestMapping("/api/sys-departments")
@RequiredArgsConstructor
public class SysDepartmentController {

    private final SysDepartmentService departmentService;

    @RequiresPermission("department:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<SysDepartment>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) String keyword,
                                               @RequestParam(required = false) String regionCode,
                                               @RequestParam(required = false) Long parentId) {
        Page<SysDepartment> p = (Page<SysDepartment>) departmentService.page(current, size, keyword, regionCode, parentId);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("department:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<SysDepartment> get(@PathVariable Long id) {
        return Result.success(departmentService.getById(id));
    }

    @RequiresPermission("department:manage")
    @Operation(summary = "新增部门（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysDepartmentCreateDTO dto) {
        SysDepartment dept = new SysDepartment();
        BeanUtils.copyProperties(dto, dept);
        if (dept.getStatus() == null) dept.setStatus("ENABLED");
        departmentService.save(dept);
        return Result.success();
    }

    @RequiresPermission("department:manage")
    @Operation(summary = "更新部门（白名单字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysDepartmentUpdateDTO dto) {
        SysDepartment dept = new SysDepartment();
        BeanUtils.copyProperties(dto, dept);
        dept.setDepartmentId(id);
        departmentService.updateById(dept);
        return Result.success();
    }

    @RequiresPermission("department:manage")
    @Operation(summary = "禁用：删除部门（请走 DISABLE 状态切换）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        departmentService.removeById(id);
        return Result.success();
    }
}