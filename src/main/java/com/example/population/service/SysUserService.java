package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.dto.LoginDTO;
import com.example.population.entity.SysUser;

import java.util.Map;

public interface SysUserService extends IService<SysUser> {

    Map<String, Object> login(LoginDTO dto);

    /**
     * 凭 userId 重新签发 access token（用于 refresh 接口）。
     * <p>
     * 重新从 DB 加载权限四元组，确保改权限 / 改角色后下一次 access token 立即生效。
     */
    Map<String, Object> issueAccessTokenForUser(Long userId);

    SysUser getByUsername(String username);

    IPage<SysUser> page(long current, long size, String keyword, Long roleId, Long departmentId);

    boolean resetPassword(Long userId, String newPassword);

    /**
     * 更新用户角色，返回受影响 userId 列表（用于 evict PermissionCache）
     */
    java.util.Set<Long> updateRole(Long userId, Long newRoleId);

    /**
     * 启用 / 停用用户
     */
    boolean disableUser(Long userId);
}