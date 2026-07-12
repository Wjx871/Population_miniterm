package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.Result;
import com.example.population.dto.SysPermissionCreateDTO;
import com.example.population.dto.SysPermissionUpdateDTO;
import com.example.population.entity.SysPermission;
import com.example.population.service.SysPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "权限点")
@RestController
@RequestMapping("/api/sys-permissions")
@RequiredArgsConstructor
public class SysPermissionController {

    private final SysPermissionService permissionService;

    @RequiresPermission("role:query")
    @Operation(summary = "查询所有权限")
    @GetMapping
    public Result<List<SysPermission>> list() {
        return Result.success(permissionService.list());
    }

    @RequiresPermission("role:query")
    @Operation(summary = "按模块查询")
    @GetMapping("/by-module/{module}")
    public Result<List<SysPermission>> byModule(@PathVariable String module) {
        return Result.success(permissionService.lambdaQuery()
                .eq(SysPermission::getModuleName, module)
                .list());
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "新增权限点（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysPermissionCreateDTO dto) {
        SysPermission p = new SysPermission();
        BeanUtils.copyProperties(dto, p);
        permissionService.save(p);
        return Result.success();
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "更新权限点（白名单字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysPermissionUpdateDTO dto) {
        SysPermission p = new SysPermission();
        BeanUtils.copyProperties(dto, p);
        p.setPermissionId(id);
        permissionService.updateById(p);
        return Result.success();
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "删除权限点（注意：会级联删除 sys_role_permission 关联）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        permissionService.removeById(id);
        return Result.success();
    }
}