package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.Result;
import com.example.population.dto.SysRoleCreateDTO;
import com.example.population.dto.SysRoleUpdateDTO;
import com.example.population.entity.SysRole;
import com.example.population.service.SysRoleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "系统角色")
@RestController
@RequestMapping("/api/sys-roles")
@RequiredArgsConstructor
public class SysRoleController {

    private final SysRoleService roleService;

    @RequiresPermission("role:query")
    @Operation(summary = "全部角色")
    @GetMapping
    public Result<List<SysRole>> list() {
        return Result.success(roleService.list());
    }

    @RequiresPermission("role:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<SysRole> get(@PathVariable Long id) {
        return Result.success(roleService.getById(id));
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "新增角色（白名单字段）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody SysRoleCreateDTO dto) {
        SysRole role = new SysRole();
        BeanUtils.copyProperties(dto, role);
        if (role.getStatus() == null) role.setStatus("ENABLED");
        roleService.save(role);
        return Result.success();
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "更新角色（白名单字段，roleCode 不可修改）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysRoleUpdateDTO dto) {
        SysRole role = new SysRole();
        BeanUtils.copyProperties(dto, role);
        role.setRoleId(id);
        roleService.updateById(role);
        return Result.success();
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "禁用：角色删除（审计要求保留，关联用户需先迁出）")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        roleService.removeById(id);
        return Result.success();
    }
}