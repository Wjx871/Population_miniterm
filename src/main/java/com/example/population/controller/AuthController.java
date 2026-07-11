package com.example.population.controller;

import com.example.population.dto.LoginDTO;
import com.example.population.dto.Result;
import com.example.population.interceptor.JwtAuthInterceptor;
import com.example.population.service.SysUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final SysUserService userService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<Map<String, Object>> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
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
}