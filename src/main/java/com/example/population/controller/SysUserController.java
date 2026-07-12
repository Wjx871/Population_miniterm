package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.RegisterDTO;
import com.example.population.dto.Result;
import com.example.population.dto.SysUserUpdateDTO;
import com.example.population.entity.SysUser;
import com.example.population.service.SysUserService;
import com.example.population.util.PageUtil;
import com.example.population.util.PasswordEncoder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

@Tag(name = "系统用户")
@RestController
@RequestMapping("/api/sys-users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService userService;

    @RequiresPermission("user:query")
    @Operation(summary = "分页查询")
    @GetMapping
    public Result<PageVO<SysUser>> page(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "10") long size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Long roleId,
                                          @RequestParam(required = false) Long departmentId) {
        Page<SysUser> p = (Page<SysUser>) userService.page(current, size, keyword, roleId, departmentId);
        p.getRecords().forEach(u -> u.setPasswordHash(null));
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission("user:query")
    @Operation(summary = "查询单个")
    @GetMapping("/{id}")
    public Result<SysUser> get(@PathVariable Long id) {
        SysUser u = userService.getById(id);
        if (u != null) {
            u.setPasswordHash(null);
        }
        return Result.success(u);
    }

    @RequiresPermission("user:manage")
    @Operation(summary = "新增用户")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody RegisterDTO dto) {
        if (userService.getByUsername(dto.getUsername()) != null) {
            return Result.error(400, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(dto.getUsername());
        u.setPasswordHash(PasswordEncoder.encode(dto.getPassword()));
        u.setRealName(dto.getRealName());
        u.setPhone(dto.getPhone());
        u.setRoleId(dto.getRoleId());
        u.setDepartmentId(dto.getDepartmentId());
        u.setStatus("ENABLED");
        userService.save(u);
        return Result.success();
    }

    @RequiresPermission("user:manage")
    @Operation(summary = "更新用户（白名单字段）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody SysUserUpdateDTO dto) {
        // 白名单拷贝：userId / username / passwordHash / lastLoginAt / isDeleted / createdAt / updatedAt 不可被外部覆盖
        SysUser u = new SysUser();
        BeanUtils.copyProperties(dto, u);
        u.setUserId(id);
        userService.updateById(u);
        return Result.success();
    }

    @RequiresPermission("user:manage")
    @Operation(summary = "重置密码")
    @PutMapping("/{id}/password")
    public Result<Void> resetPwd(@PathVariable Long id, @RequestParam String newPassword) {
        userService.resetPassword(id, newPassword);
        return Result.success();
    }

    @RequiresPermission("user:manage")
    @Operation(summary = "停用用户（业务流：保留审计轨迹，不物理/逻辑删除）")
    @PutMapping("/{id}/disable")
    public Result<Void> disable(@PathVariable Long id) {
        userService.disableUser(id);
        return Result.success();
    }

    @RequiresPermission("user:manage")
    @Operation(summary = "修改用户角色（业务流：会清权限缓存）")
    @PutMapping("/{id}/role")
    public Result<Void> updateRole(@PathVariable Long id, @RequestParam Long roleId) {
        userService.updateRole(id, roleId);
        return Result.success();
    }

    /**
     * 物理/逻辑删除已禁用：审计要求保留 sys_user 全量历史。如需停用请走
     * {@code PUT /api/sys-users/{id}/disable}。
     */
    @RequiresPermission("user:manage")
    @Operation(summary = "禁用：删除用户，请走 /disable")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        throw new com.example.population.exception.BizException(405,
                "系统用户删除已禁用，请使用 PUT /api/sys-users/{id}/disable 走停用流程");
    }
}