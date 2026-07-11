package com.example.population.controller;

import com.example.population.dto.Result;
import com.example.population.entity.SysRole;
import com.example.population.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统角色")
@RestController
@RequestMapping("/api/sys-roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @Operation(summary = "全部角色")
    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.success(roleService.list());
    }

    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<SysRole> get(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    @Operation(summary = "新增")
    @PostMapping
    public Result<Void> create(@RequestBody SysRole role) {
        roleService.save(role);
        return Result.success();
    }

    @Operation(summary = "更新")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysRole role) {
        role.setRoleId(id);
        roleService.updateById(role);
        return Result.success();
    }

    @Operation(summary = "删除")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.success();
    }
}