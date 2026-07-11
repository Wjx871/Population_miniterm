package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.PageVO;
import com.example.population.dto.RegisterDTO;
import com.example.population.dto.Result;
import com.example.population.entity.SysUser;
import com.example.population.service.SysUserService;
import com.example.population.util.PageUtil;
import com.example.population.util.PasswordEncoder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
    @Operation(summary = "更新用户")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysUser u) {
        u.setUserId(id);
        u.setPasswordHash(null);
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
    @Operation(summary = "删除用户")
    @DeleteMapping("/{id}")
    public Result<Void> remove(@PathVariable Long id) {
        userService.removeById(id);
        return Result.success();
    }
}
