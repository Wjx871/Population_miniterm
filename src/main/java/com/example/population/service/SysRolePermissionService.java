package com.example.population.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.SysRolePermission;

import java.util.List;

public interface SysRolePermissionService extends IService<SysRolePermission> {

    boolean assignPermissions(Long roleId, List<Long> permissionIds);

    List<Long> listPermissionIdsByRole(Long roleId);
}