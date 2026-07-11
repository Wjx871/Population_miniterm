package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.dto.LoginDTO;
import com.example.population.entity.SysPermission;
import com.example.population.entity.SysRole;
import com.example.population.entity.SysRolePermission;
import com.example.population.entity.SysUser;
import com.example.population.exception.BizException;
import com.example.population.mapper.SysPermissionMapper;
import com.example.population.mapper.SysRoleMapper;
import com.example.population.mapper.SysRolePermissionMapper;
import com.example.population.mapper.SysUserMapper;
import com.example.population.service.SysUserService;
import com.example.population.util.JwtUtil;
import com.example.population.util.PasswordEncoder;
import com.example.population.util.PermissionCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    private final JwtUtil jwtUtil;
    private final PermissionCache permissionCache;
    private final SysRoleMapper roleMapper;
    private final SysRolePermissionMapper rolePermissionMapper;
    private final SysPermissionMapper permissionMapper;

    @Override
    public Map<String, Object> login(LoginDTO dto) {
        SysUser user = getByUsername(dto.getUsername());
        if (user == null) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (!PasswordEncoder.matches(dto.getPassword(), user.getPasswordHash())) {
            throw new BizException(400, "用户名或密码错误");
        }
        if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(403, "账号已停用");
        }

        SysRole role = roleMapper.selectById(user.getRoleId());
        if (role == null) {
            throw new BizException(500, "用户角色未配置 (userId=" + user.getUserId() + ")");
        }
        Integer permLevel = role.getPermissionLevel();
        String roleCode = role.getRoleCode();
        String dataScope = role.getDataScopeCode();
        if (permLevel == null || roleCode == null || dataScope == null) {
            throw new BizException(500, "角色字段缺失 (roleCode=" + roleCode + ")");
        }

        Set<String> permCodes = loadPermissionCodes(role.getRoleId());

        permissionCache.put(user.getUserId(), permCodes, jwtUtil.getExpirationSeconds());

        user.setLastLoginAt(LocalDateTime.now());
        this.updateById(user);

        String token = jwtUtil.generate(user.getUserId(), user.getUsername(), user.getRealName(), permLevel, roleCode, dataScope, permCodes);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("userId", user.getUserId());
        result.put("username", user.getUsername());
        result.put("realName", user.getRealName());
        result.put("roleId", user.getRoleId());
        result.put("roleCode", roleCode);
        result.put("permissionLevel", permLevel);
        result.put("dataScopeCode", dataScope);
        result.put("departmentId", user.getDepartmentId());
        result.put("permissionCodes", permCodes);
        return result;
    }

    private Set<String> loadPermissionCodes(Long roleId) {
        if (roleId == null) return Collections.emptySet();
        try {
            List<Long> pids = rolePermissionMapper.selectList(
                            new LambdaQueryWrapper<SysRolePermission>().eq(SysRolePermission::getRoleId, roleId))
                    .stream()
                    .map(SysRolePermission::getPermissionId)
                    .collect(Collectors.toList());
            if (pids.isEmpty()) return Collections.emptySet();
            Set<String> codes = new HashSet<>();
            for (SysPermission p : permissionMapper.selectBatchIds(pids)) {
                if (p.getPermissionCode() != null) codes.add(p.getPermissionCode());
            }
            return codes;
        } catch (Exception e) {
            log.warn("加载权限码失败 roleId={}, err={}", roleId, e.getMessage());
            return Collections.emptySet();
        }
    }

    @Override
    public SysUser getByUsername(String username) {
        return this.getOne(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
    }

    @Override
    public IPage<SysUser> page(long current, long size, String keyword, Long roleId, Long departmentId) {
        Page<SysUser> page = new Page<>(current, size);
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            w.like(SysUser::getUsername, keyword)
                    .or().like(SysUser::getRealName, keyword);
        }
        if (roleId != null) {
            w.eq(SysUser::getRoleId, roleId);
        }
        if (departmentId != null) {
            w.eq(SysUser::getDepartmentId, departmentId);
        }
        w.orderByDesc(SysUser::getCreatedAt);
        return this.page(page, w);
    }

    @Override
    public boolean resetPassword(Long userId, String newPassword) {
        SysUser user = this.getById(userId);
        if (user == null) {
            return false;
        }
        user.setPasswordHash(PasswordEncoder.encode(newPassword));
        return this.updateById(user);
    }
}
