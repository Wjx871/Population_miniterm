package com.example.population.interceptor;

import com.example.population.util.JwtUtil;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String ATTR_USER_ID = "currentUserId";
    public static final String ATTR_USERNAME = "currentUsername";

    private final JwtUtil jwtUtil;

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
            writeUnauthorized(response, "未登录或令牌缺失");
            return false;
        }
        if (!jwtUtil.isValid(token)) {
            writeUnauthorized(response, "令牌无效或已过期");
            return false;
        }
        try {
            Claims claims = jwtUtil.parse(token);
            request.setAttribute(ATTR_USER_ID, claims.get("uid", Long.class));
            request.setAttribute(ATTR_USERNAME, claims.get("uname", String.class));
        } catch (Exception e) {
            log.warn("解析 token 失败: {}", e.getMessage());
            writeUnauthorized(response, "令牌解析失败");
            return false;
        }
        return true;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws java.io.IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\"}");
    }
}