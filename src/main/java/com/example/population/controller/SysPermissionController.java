package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.Result;
import com.example.population.entity.SysPermission;
import com.example.population.service.SysPermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
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
    @Operation(summary = "新增")
    @PostMapping
    public Result<Void> create(@RequestBody SysPermission p) {
        permissionService.save(p);
        return Result.success();
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysPermission p) {
        p.setPermissionId(id);
        permissionService.updateById(p);
        return Result.success();
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        permissionService.removeById(id);
        return Result.success();
    }
}
