package com.example.population.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * JWT 工具。
 * <p>
 * 在签发 token 时把以下 4 个安全字段一同写入 claims：
 * <ul>
 *   <li>permLevel : Integer，sys_role.permission_level（1/2/3）</li>
 *   <li>roleCode  : String， sys_role.role_code（如 L1_QUERY / L3_APPROVE_ADMIN）</li>
 *   <li>dataScope : String， sys_role.data_scope_code</li>
 *   <li>permCodes : Set&lt;String&gt;，当前用户拥有的所有 permissionCode</li>
 * </ul>
 * 切面/拦截器从 token 里读这些字段即可，不需要每次请求都查 DB。
 * <p>
 * 同时为 access token 引入 jti + type 字段，refresh token 单独签发，type=refresh；
 * 配合 {@link TokenBlacklist} 可对单个 access token 实现主动吊销（黑名单）。
 */
@Component
public class JwtUtil {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        if (secret == null || secret.isEmpty()) {
            throw new IllegalStateException(
                    "jwt.secret 未配置：请通过环境变量 JWT_SECRET 注入（≥256 bit 随机值，生产环境 ≥512 bit）");
        }
        byte[] keyBytes = secret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // HS256 至少需要 256 bit = 32 byte
            throw new IllegalStateException(
                    "jwt.secret 过短（" + keyBytes.length + " bytes，HS256 至少需要 32 bytes / 256 bit）；"
                            + "请用 `openssl rand -base64 64` 重新生成 ≥512 bit 随机值");
        }
        this.key = Keys.hmacShaKeyFor(keyBytes);
    }

    public long getExpirationSeconds() {
        return expiration;
    }

    public long getRefreshExpirationSeconds() {
        return refreshExpiration;
    }

    /**
     * 签发 access token（短命，含完整权限四元组）。
     * <p>
     * payload 中追加：
     * <ul>
     *   <li>jti  : UUID，全局唯一，可被 {@link TokenBlacklist} 拉黑实现主动吊销</li>
     *   <li>type : "access"</li>
     *   <li>deptId : sys_user.department_id（P0：用于数据范围过滤，无需每请求再查 sys_user）</li>
     * </ul>
     */
    public String generate(Long userId,
                           String username,
                           String realName,
                           Integer permissionLevel,
                           String roleCode,
                           String dataScopeCode,
                           Set<String> permissionCodes) {
        return generate(userId, username, realName, permissionLevel, roleCode, dataScopeCode,
                permissionCodes, null);
    }

    /**
     * 带 departmentId 的签发版本（P0 数据范围过滤使用）。
     */
    public String generate(Long userId,
                           String username,
                           String realName,
                           Integer permissionLevel,
                           String roleCode,
                           String dataScopeCode,
                           Set<String> permissionCodes,
                           Long departmentId) {
        return build(userId, username, realName, permissionLevel, roleCode, dataScopeCode,
                permissionCodes, departmentId, TOKEN_TYPE_ACCESS, expiration);
    }

    /**
     * 签发 refresh token（长命，仅承载 uid + type，权限信息由 Redis 中的最新值兜底）。
     */
    public String generateRefresh(Long userId, String username) {
        return build(userId, username, null, null, null, null,
                null, null, TOKEN_TYPE_REFRESH, refreshExpiration);
    }

    private String build(Long userId,
                         String username,
                         String realName,
                         Integer permissionLevel,
                         String roleCode,
                         String dataScopeCode,
                         Set<String> permissionCodes,
                         Long departmentId,
                         String tokenType,
                         long ttlMillis) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("uname", username);
        claims.put("type", tokenType);
        claims.put("jti", UUID.randomUUID().toString());
        if (TOKEN_TYPE_ACCESS.equals(tokenType)) {
            if (realName != null) {
                claims.put("realName", realName);
            }
            if (permissionLevel != null) {
                claims.put("permLevel", permissionLevel);
            }
            if (roleCode != null) {
                claims.put("roleCode", roleCode);
            }
            if (dataScopeCode != null) {
                claims.put("dataScope", dataScopeCode);
            }
            if (departmentId != null) {
                claims.put("deptId", departmentId);
            }
            if (permissionCodes != null && !permissionCodes.isEmpty()) {
                claims.put("permCodes", permissionCodes);
            }
        }
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + ttlMillis))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 claims 里读取 permissionLevel；缺失视为未登录态（返回 null，由拦截器报错）。
     */
    public Integer extractPermLevel(Claims claims) {
        Object v = claims.get("permLevel");
        if (v == null) return null;
        if (v instanceof Integer i) return i;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public String extractStringClaim(Claims claims, String name) {
        Object v = claims.get(name);
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    public Set<String> extractPermCodes(Claims claims) {
        Object v = claims.get("permCodes");
        if (v == null) return Collections.emptySet();
        if (v instanceof java.util.Collection<?> coll) {
            Set<String> set = new HashSet<>();
            for (Object o : coll) {
                if (o != null) set.add(o.toString());
            }
            return set;
        }
        if (v instanceof String s && !s.isEmpty()) {
            String[] parts = s.split(",");
            Set<String> set = new HashSet<>(parts.length);
            for (String p : parts) {
                if (!p.isEmpty()) set.add(p);
            }
            return set;
        }
        return Collections.emptySet();
    }

    /**
     * 读取 departmentId（P0 数据范围过滤）。缺失返回 null。
     */
    public Long extractDeptId(Claims claims) {
        Object v = claims.get("deptId");
        if (v == null) return null;
        if (v instanceof Long l) return l;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}