package com.example.population.controller;

import com.example.population.dto.LoginDTO;
import com.example.population.dto.Result;
import com.example.population.exception.BizException;
import com.example.population.interceptor.JwtAuthInterceptor;
import com.example.population.service.LoginLogService;
import com.example.population.service.SysUserService;
import com.example.population.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;
    private final LoginLogService loginLogService;
    private final JwtUtil jwtUtil;

    @Operation(summary = "登录（返回 access + refresh token）")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto,
                                             HttpServletRequest request) {
        String ip = extractIp(request);
        String ua = request.getHeader("User-Agent");
        Map<String, Object> data;
        try {
            data = userService.login(dto);
        } catch (BizException e) {
            recordLogin(dto.getUsername(), null, statusFromCode(e.getCode(), e.getMessage()), e.getMessage(), ip, ua);
            throw e;
        } catch (RuntimeException e) {
            recordLogin(dto.getUsername(), null, "FAILED", e.getMessage(), ip, ua);
            throw e;
        }
        // 成功登录：把 userId 写入日志
        Object uidObj = data.get("userId");
        Long userId = uidObj instanceof Number ? ((Number) uidObj).longValue() : null;
        recordLogin(dto.getUsername(), userId, "SUCCESS", null, ip, ua);

        // 额外签发 refresh token（仅含 uid + username，长命 7 天）
        String refreshToken = jwtUtil.generateRefresh(userId, dto.getUsername());
        data.put("refreshToken", refreshToken);
        data.put("refreshExpiresInMs", jwtUtil.getRefreshExpirationSeconds());
        data.put("accessExpiresInMs", jwtUtil.getExpirationSeconds());
        return Result.success(data);
    }

    @Operation(summary = "用 refresh token 换发新 access token")
    @PostMapping("/refresh")
    public Result<Map<String, Object>> refresh(@RequestParam String refreshToken) {
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new BizException(400, "refreshToken 缺失");
        }
        if (!jwtUtil.isValid(refreshToken)) {
            throw new BizException(401, "refresh token 无效或已过期");
        }
        Claims claims = jwtUtil.parse(refreshToken);
        String type = jwtUtil.extractStringClaim(claims, "type");
        if (!JwtUtil.TOKEN_TYPE_REFRESH.equals(type)) {
            throw new BizException(401, "非 refresh token，请使用 refresh 接口");
        }
        Long uid = claims.get("uid", Long.class);
        String uname = jwtUtil.extractStringClaim(claims, "uname");
        if (uid == null || uname == null) {
            throw new BizException(401, "refresh token 缺少关键字段");
        }

        // 重新从 DB 加载权限四元组（解决改权限后 access token 立即生效的问题）
        Map<String, Object> newLogin = userService.issueAccessTokenForUser(uid);
        Map<String, Object> data = new HashMap<>(newLogin);
        data.put("accessExpiresInMs", jwtUtil.getExpirationSeconds());
        return Result.success(data);
    }

    @Operation(summary = "登出（前端清 token 即可；可选调用强制吊销当前 access token）")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        // 当前实现仅作占位。生产环境建议：
        // 1) 解析当前 access token 的 jti
        // 2) 调 TokenBlacklist.revoke(jti, 剩余ttl)
        // 3) 同时清空 PermissionCache
        // 这里依赖前端清 token；如需服务端主动吊销，下方代码可启用：
        // String auth = request.getHeader("Authorization");
        // ... (parse + revoke)
        return Result.success();
    }

    @Operation(summary = "获取当前登录用户")
    @GetMapping("/me")
    public Result<Map<String, Object>> me(HttpServletRequest request) {
        Long uid = (Long) request.getAttribute(JwtAuthInterceptor.ATTR_USER_ID);
        String uname = (String) request.getAttribute(JwtAuthInterceptor.ATTR_USERNAME);
        return Result.success(Map.of(
                "userId", uid == null ? -1 : uid,
                "username", uname == null ? "" : uname));
    }

    private void recordLogin(String username, Long userId, String status, String reason, String ip, String ua) {
        try {
            loginLogService.record(username, userId, status, reason, ip, ua, null);
        } catch (Exception e) {
            // 登录日志写入失败不应影响登录主流程
            log.warn("写入登录日志失败 username={}, status={}, err={}", username, status, e.getMessage());
        }
    }

    private static String extractIp(HttpServletRequest request) {
        if (request == null) return null;
        String ip = request.getHeader("X-Forwarded-For");
        if (isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int idx = ip.indexOf(',');
            return idx > 0 ? ip.substring(0, idx).trim() : ip.trim();
        }
        ip = request.getHeader("X-Real-IP");
        if (isNotBlank(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip.trim();
        }
        return request.getRemoteAddr();
    }

    private static boolean isNotBlank(String s) {
        return s != null && !s.isEmpty() && !s.trim().isEmpty();
    }

    private static String statusFromCode(int code, String message) {
        if (code == 403) return "LOCKED";
        return "FAILED";
    }
}