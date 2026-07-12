package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.SysRolePermission;
import com.example.population.entity.SysUser;
import com.example.population.mapper.SysRolePermissionMapper;
import com.example.population.mapper.SysUserMapper;
import com.example.population.service.SysRolePermissionService;
import com.example.population.util.PermissionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysRolePermissionServiceImpl extends ServiceImpl<SysRolePermissionMapper, SysRolePermission>
        implements SysRolePermissionService {

    private final SysUserMapper userMapper;
    private final PermissionCache permissionCache;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean assignPermissions(Long roleId, List<Long> permissionIds) {
        this.remove(new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId));
        if (permissionIds == null || permissionIds.isEmpty()) {
            evictUsersOfRole(roleId);
            return true;
        }
        for (Long pid : permissionIds) {
            SysRolePermission rp = new SysRolePermission();
            rp.setRoleId(roleId);
            rp.setPermissionId(pid);
            this.save(rp);
        }
        // 关键：权限变更后必须清掉该角色下所有受影响用户的权限缓存，
        // 否则他们旧的 permCodes 仍可访问最长 TTL 时间。
        evictUsersOfRole(roleId);
        return true;
    }

    /**
     * 找出拥有该角色的所有 userId，循环 evict。
     */
    private void evictUsersOfRole(Long roleId) {
        if (roleId == null) {
            return;
        }
        try {
            List<Long> userIds = userMapper.selectList(
                            new LambdaQueryWrapper<SysUser>().eq(SysUser::getRoleId, roleId))
                    .stream()
                    .map(SysUser::getUserId)
                    .collect(Collectors.toList());
            for (Long uid : userIds) {
                permissionCache.evict(uid);
            }
            log.info("[权限缓存] 角色[{}]权限变更，已清缓存 userIds={}", roleId, userIds);
        } catch (Exception e) {
            log.warn("[权限缓存] 清缓存失败 roleId={}, err={}", roleId, e.getMessage());
        }
    }

    @Override
    public List<Long> listPermissionIdsByRole(Long roleId) {
        return this.list(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId))
                .stream().map(SysRolePermission::getPermissionId).toList();
    }
}