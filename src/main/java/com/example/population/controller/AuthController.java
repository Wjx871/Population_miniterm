package com.example.population.controller;

import com.example.population.dto.LoginDTO;
import com.example.population.dto.Result;
import com.example.population.exception.BizException;
import com.example.population.interceptor.JwtAuthInterceptor;
import com.example.population.service.LoginLogService;
import com.example.population.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;
    private final LoginLogService loginLogService;

    @Operation(summary = "登录")
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
        return Result.success(data);
    }

    @Operation(summary = "登出（前端清 token 即可）")
    @PostMapping("/logout")
    public Result<Void> logout() {
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
