package com.example.population.controller;

import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.Result;
import com.example.population.service.SysRolePermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "角色权限")
@RestController
@RequestMapping("/api/sys-role-permissions")
@RequiredArgsConstructor
public class SysRolePermissionController {

    private final SysRolePermissionService rolePermissionService;

    @RequiresPermission("role:query")
    @Operation(summary = "查询角色已分配的权限ID")
    @GetMapping("/role/{roleId}")
    public Result<List<Long>> listByRole(@PathVariable Long roleId) {
        return Result.success(rolePermissionService.listPermissionIdsByRole(roleId));
    }

    @RequiresPermission("role:manage")
    @Operation(summary = "为角色分配权限")
    @PutMapping("/role/{roleId}")
    public Result<Void> assign(@PathVariable Long roleId, @RequestBody List<Long> permissionIds) {
        rolePermissionService.assignPermissions(roleId, permissionIds);
        return Result.success();
    }
}
