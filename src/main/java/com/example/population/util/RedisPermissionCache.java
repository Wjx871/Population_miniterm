package com.example.population.util;

import com.example.population.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 基于 Redis 的权限码缓存实现。
 * <p>
 * key   = "perm:user:{uid}"
 * value = 多个 permissionCode 用 ',' 拼接
 */
@Slf4j
@Component
public class RedisPermissionCache implements PermissionCache {

    private static final String KEY_PREFIX = "perm:user:";

    private final StringRedisTemplate redis;

    @Autowired
    public RedisPermissionCache(StringRedisTemplate redis) {
        if (redis == null) {
            throw new BizException(500, "RedisPermissionCache 初始化失败: StringRedisTemplate 为空（请确认 Redis 已启动）");
        }
        this.redis = redis;
    }

    @Override
    public void put(Long userId, Set<String> permissionCodes, long ttlSeconds) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return;
        }
        String key = KEY_PREFIX + userId;
        String value = String.join(",", permissionCodes);
        try {
            redis.opsForValue().set(key, value, Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("Redis 写入权限缓存失败 uid={}, err={}", userId, e.getMessage());
        }
    }

    @Override
    public Set<String> get(Long userId) {
        String key = KEY_PREFIX + userId;
        try {
            String value = redis.opsForValue().get(key);
            if (value == null || value.isEmpty()) {
                return Collections.emptySet();
            }
            String[] parts = value.split(",");
            Set<String> set = new HashSet<>(parts.length);
            for (String p : parts) {
                if (!p.isEmpty()) {
                    set.add(p);
                }
            }
            return set;
        } catch (Exception e) {
            log.warn("Redis 读取权限缓存失败 uid={}, err={}", userId, e.getMessage());
            return Collections.emptySet();
        }
    }

    @Override
    public void evict(Long userId) {
        try {
            redis.delete(KEY_PREFIX + userId);
        } catch (Exception e) {
            log.warn("Redis 删除权限缓存失败 uid={}, err={}", userId, e.getMessage());
        }
    }
}
