package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.SysRolePermission;
import com.example.population.mapper.SysRolePermissionMapper;
import com.example.population.service.SysRolePermissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SysRolePermissionServiceImpl extends ServiceImpl<SysRolePermissionMapper, SysRolePermission>
        implements SysRolePermissionService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignPermissions(Long roleId, List<Long> permissionIds) {
        this.remove(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) {
            return true;
        }
        for (Long pid : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            this.save(rp);
        }
        return true;
    }

    @Override
    public List<Long> listPermissionIdsByRole(Long roleId) {
        return this.list(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
    }
}