package com.example.population.util;

import com.example.population.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 基于 Redis 的 JWT 主动吊销黑名单。
 * <p>
 * key   = "jwt:bl:{jti}"
 * value = "1"
 * TTL   = 该 token 剩余有效时间（过期后自动从 Redis 移除，节约内存）
 * <p>
 * 业务流程：
 * 1) 用户改密码 / 角色 / 权限后，调用 {@link #revoke(String, long)} 把当前 access token 加入黑名单
 * 2) {@link com.example.population.interceptor.JwtAuthInterceptor} 解析 token 时会调用
 *    {@link #isRevoked(String)} 判定 jti 是否在黑名单中
 * 3) Refresh token 换发新 access token 时也校验其 jti 未被吊销
 * <p>
 * Redis 不可用时降级为"放行+告警"，避免影响主流程可用性。
 */
@Slf4j
@Component
public class TokenBlacklist {

    private static final String KEY_PREFIX = "jwt:bl:";

    private final StringRedisTemplate redis;

    @Autowired(required = false)
    public TokenBlacklist(StringRedisTemplate redis) {
        this.redis = redis;
    }

    /**
     * 将 jti 拉黑（仅当 Redis 可用时生效）。
     *
     * @param jti           token 的 jti claim
     * @param ttlSeconds    token 剩余有效秒数（过期后无需保留）
     */
    public void revoke(String jti, long ttlSeconds) {
        if (jti == null || jti.isEmpty() || ttlSeconds <= 0) {
            return;
        }
        if (redis == null) {
            log.warn("Redis 未配置，token 主动吊销降级为不可用（jti={}）", jti);
            return;
        }
        try {
            redis.opsForValue().set(KEY_PREFIX + jti, "1", Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            log.warn("写入 token 黑名单失败 jti={}, err={}", jti, e.getMessage());
        }
    }

    public boolean isRevoked(String jti) {
        if (jti == null || jti.isEmpty()) {
            return false;
        }
        if (redis == null) {
            return false;
        }
        try {
            return "1".equals(redis.opsForValue().get(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("查询 token 黑名单失败 jti={}, err={}", jti, e.getMessage());
            return false;
        }
    }

    /**
     * 校验 Redis 配置存在。生产环境部署时如果未启动 Redis 应抛出明确错误。
     */
    public void assertAvailable() {
        if (redis == null) {
            throw new BizException(500, "TokenBlacklist 未配置 Redis，部署环境异常");
        }
    }
}