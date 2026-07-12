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
import com.example.population.util.PageUtil;
import com.example.population.util.PasswordEncoder;
import com.example.population.util.PermissionCache;
import com.example.population.util.SafeLike;
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

        // 迁移期：登录成功且仍使用 legacy SHA-256 时自动升级为 BCrypt
        if (PasswordEncoder.needsUpgrade(user.getPasswordHash())) {
            try {
                user.setPasswordHash(PasswordEncoder.encode(dto.getPassword()));
                this.updateById(user);
                log.info("用户[{}]密码哈希已从 SHA-256 升级为 BCrypt", user.getUsername());
            } catch (Exception e) {
                log.warn("用户[{}]密码哈希升级失败，下次登录将重试: {}", user.getUsername(), e.getMessage());
            }
        }

        Map<String, Object> result = buildLoginPayload(user);
        user.setLastLoginAt(LocalDateTime.now());
        this.updateById(user);
        return result;
    }

    @Override
    public Map<String, Object> issueAccessTokenForUser(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException(404, "用户[" + userId + "]不存在");
        }
        if (!"ENABLED".equalsIgnoreCase(user.getStatus())) {
            throw new BizException(403, "账号已停用");
        }
        // 重新从 DB 加载最新权限四元组（refresh 接口专用，不写 lastLoginAt）
        return buildLoginPayload(user);
    }

    /**
     * 加载角色 + 权限码 + 签发 token + 写 Redis 缓存；所有"获得新 access token"的入口共用此逻辑。
     */
    private Map<String, Object> buildLoginPayload(SysUser user) {
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

        String token = jwtUtil.generate(user.getUserId(), user.getUsername(), user.getRealName(),
                permLevel, roleCode, dataScope, permCodes);

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
        Page<SysUser> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<SysUser> w = new LambdaQueryWrapper<>();
        String safeKw = SafeLike.escape(keyword);
        if (safeKw != null && !safeKw.isEmpty()) {
            w.and(w2 -> w2.like(SysUser::getUsername, safeKw)
                    .or().like(SysUser::getRealName, safeKw));
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
        boolean ok = this.updateById(user);
        if (ok) {
            // 改密后立即清除该用户的权限缓存（防止旧 token 内嵌 permCodes 继续生效）
            // 注：access token 中 jti 需要主动吊销才能立刻失效，本接口仅清理 Redis 中缓存；
            // access token 中的 permCodes 不变但只要它和 role 对应就仍然可访问到对应接口；
            // 真正即时失效需要配合 TokenBlacklist（更彻底的方案是缩短 access token 寿命）。
            permissionCache.evict(userId);
        }
        return ok;
    }

    @Override
    public Set<Long> updateRole(Long userId, Long newRoleId) {
        if (userId == null) {
            throw new BizException(400, "userId 缺失");
        }
        SysUser user = this.getById(userId);
        if (user == null) {
            throw new BizException(404, "用户[" + userId + "]不存在");
        }
        if (newRoleId != null) {
            SysRole role = roleMapper.selectById(newRoleId);
            if (role == null) {
                throw new BizException(400, "角色[" + newRoleId + "]不存在");
            }
        }
        Long oldRoleId = user.getRoleId();
        user.setRoleId(newRoleId);
        boolean ok = this.updateById(user);
        if (ok && (oldRoleId == null || !oldRoleId.equals(newRoleId))) {
            // 角色变更：清权限缓存
            permissionCache.evict(userId);
        }
        return Collections.singleton(userId);
    }

    @Override
    public boolean disableUser(Long userId) {
        SysUser user = this.getById(userId);
        if (user == null) {
            return false;
        }
        user.setStatus("DISABLED");
        boolean ok = this.updateById(user);
        if (ok) {
            permissionCache.evict(userId);
        }
        return ok;
    }
}