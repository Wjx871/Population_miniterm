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
 */
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    private SecretKey key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public long getExpirationSeconds() {
        return expiration;
    }

    /**
     * 签发 token（最小集合：uid/uname + 权限四元组）。
     */
    public String generate(Long userId,
                           String username,
                           String realName,
                           Integer permissionLevel,
                           String roleCode,
                           String dataScopeCode,
                           Set<String> permissionCodes) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("uid", userId);
        claims.put("uname", username);
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
        if (permissionCodes != null && !permissionCodes.isEmpty()) {
            claims.put("permCodes", permissionCodes);
        }
        Date now = new Date();
        return Jwts.builder()
                .claims(claims)
                .subject(username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiration))
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
}
