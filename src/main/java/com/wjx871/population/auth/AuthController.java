package com.wjx871.population.auth;

import com.wjx871.population.common.ApiResponse;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginDTO request, HttpServletRequest httpRequest) {
        return ApiResponse.ok(authService.login(request, httpRequest));
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<UserVO> me() {
        return ApiResponse.ok(UserVO.from(CurrentUserContext.requireUser()));
    }

    @PostMapping("/logout")
    @PreAuthorize("isAuthenticated()")
    public ApiResponse<Void> logout(HttpServletRequest request) {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        authService.logout(user, request);
        return ApiResponse.ok(null);
    }
}
