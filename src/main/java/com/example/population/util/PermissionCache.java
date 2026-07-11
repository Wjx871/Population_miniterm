package com.example.population.util;

import java.util.Set;

/**
 * 用户权限码集合的缓存抽象。
 * <p>
 * 登录时由 {@code SysUserServiceImpl.login} 把 {@code Set&lt;String&gt;} 写进来，
 * 切面校验时按 uid 读取。TTL 与 JWT 一致，token 过期即失效。
 */
public interface PermissionCache {

    void put(Long userId, Set<String> permissionCodes, long ttlSeconds);

    Set<String> get(Long userId);

    void evict(Long userId);
}
