package com.example.population.interceptor;

import com.example.population.dto.Result;
import com.example.population.util.JwtUtil;
import com.example.population.util.PermissionCache;
import com.example.population.util.SecurityContext;
import com.example.population.util.TokenBlacklist;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * JWT 鉴权拦截器。
 * <p>
 * 每请求处理：
 * <ol>
 *   <li>校验 token 合法性</li>
 *   <li>校验 token 类型必须是 access（拒绝 refresh token 直接访问 API）</li>
 *   <li>校验 jti 未在 {@link TokenBlacklist} 中（支持改密码/改权限后主动吊销）</li>
 *   <li>解析 claims：uid / uname / permLevel / roleCode / dataScope / permCodes</li>
 *   <li>写入 SecurityContext（ThreadLocal）和 request attribute</li>
 * </ol>
 * <p>
 * 注意：permCodes 优先从 token 取，缺失时回退到 Redis（PermissionCache）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USERNAME = "currentUsername";
    public static final String ATTR_SECURITY_CONTEXT = "securityContext";

    private final JwtUtil jwtUtil;
    private final PermissionCache permissionCache;
    private final TokenBlacklist tokenBlacklist;
    private final ObjectMapper objectMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (token == null || token.isEmpty()) {
            writeUnauthorized(request, response, "未登录或令牌缺失");
            return false;
        }
        if (!jwtUtil.isValid(token)) {
            writeUnauthorized(request, response, "令牌无效或已过期");
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(token);

            // 强制类型校验：只有 access token 才能访问 API 端点
            String tokenType = jwtUtil.extractStringClaim(claims, "type");
            if (!JwtUtil.TOKEN_TYPE_ACCESS.equals(tokenType)) {
                writeUnauthorized(request, response, "令牌类型非法，请使用 access token 访问");
                return false;
            }

            // jti 主动吊销校验
            String jti = jwtUtil.extractStringClaim(claims, "jti");
            if (tokenBlacklist.isRevoked(jti)) {
                writeUnauthorized(request, response, "令牌已被吊销，请重新登录");
                return false;
            }

            Long uid = claims.get("uid", Long.class);
            String uname = jwtUtil.extractStringClaim(claims, "uname");
            String realName = jwtUtil.extractStringClaim(claims, "realName");
            Integer permLevel = jwtUtil.extractPermLevel(claims);
            String roleCode = jwtUtil.extractStringClaim(claims, "roleCode");
            String dataScope = jwtUtil.extractStringClaim(claims, "dataScope");
            Set<String> permCodes = jwtUtil.extractPermCodes(claims);

            // 关键安全卡点：本版本要求 token 必须包含完整权限字段，否则视为旧 token 强制重登
            if (uid == null || uname == null || permLevel == null || roleCode == null || dataScope == null) {
                writeUnauthorized(request, response, "令牌格式过旧，请重新登录");
                return false;
            }

            // 兜底：若 token 内 permCodes 为空，从 Redis 拉
            if (permCodes.isEmpty()) {
                Set<String> cached = permissionCache.get(uid);
                if (cached != null && !cached.isEmpty()) {
                    permCodes = cached;
                }
            }

            SecurityContext ctx = SecurityContext.builder()
                    .userId(uid)
                    .username(uname)
                    .realName(realName)
                    .permissionLevel(permLevel)
                    .roleCode(roleCode)
                    .dataScopeCode(dataScope)
                    .permissionCodes(permCodes)
                    .build();
            SecurityContext.set(ctx);
            request.setAttribute(ATTR_USER_ID, uid);
            request.setAttribute(ATTR_USERNAME, uname);
            request.setAttribute(ATTR_SECURITY_CONTEXT, ctx);
        } catch (Exception e) {
            log.warn("解析 token 失败: {}", e.getMessage());
            writeUnauthorized(request, response, "令牌解析失败");
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止线程复用导致串号
        SecurityContext.clear();
    }

    private void writeUnauthorized(HttpServletRequest request, HttpServletResponse response, String message) throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        if (request != null) {
            log.warn("unauthorized ip={} ua={} reason={}",
                    request.getRemoteAddr(), request.getHeader("User-Agent"), message);
        }
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(HttpStatus.UNAUTHORIZED.value(), message)));
    }
}