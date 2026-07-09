package com.wjx871.population.auth;

import com.wjx871.population.common.ApiResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@RequestBody LoginDTO loginDTO) {
        if ("admin".equals(loginDTO.getUsername()) && "123456".equals(loginDTO.getPassword())) {
            LoginVO vo = new LoginVO();
            vo.setAccessToken("mock-jwt-token-123456");
            vo.setTokenType("Bearer");
            vo.setUserId(1L);
            vo.setUsername("admin");
            vo.setRealName("超级管理员");
            vo.setRoleName("系统管理员");
            return ApiResponse.ok(vo);
        } else {
            return ApiResponse.fail("账号或密码错误");
        }
    }
}
