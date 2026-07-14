package com.wjx871.population.auth;

import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.JwtService;
import com.wjx871.population.security.TokenRevocationService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService implements UserDetailsService {

    private final AuthMapper authMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final OperationLogService operationLogService;
    private final TokenRevocationService revocations;

    public LoginVO login(LoginDTO request, HttpServletRequest httpRequest) {
        SystemUser systemUser = authMapper.selectByUsername(request.getUsername().trim()).orElse(null);
        if (systemUser == null || !passwordEncoder.matches(request.getPassword(), systemUser.getPasswordHash())) {
            operationLogService.record(systemUser == null ? null : systemUser.getUserId(),
                    "LOGIN_FAILED", "FAILED", "用户名或密码错误", httpRequest);
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!"ENABLED".equals(systemUser.getStatus())) {
            operationLogService.record(systemUser.getUserId(), "LOGIN_FAILED", "FAILED", "账号已停用", httpRequest);
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号已停用");
        }
        if (!"ENABLED".equals(systemUser.getRoleStatus())) {
            operationLogService.record(systemUser.getUserId(), "LOGIN_FAILED", "FAILED", "角色已停用", httpRequest);
            throw new BusinessException(HttpStatus.FORBIDDEN, "角色已停用");
        }

        AuthenticatedUser user = toAuthenticatedUser(systemUser);
        String token = jwtService.createToken(user);
        authMapper.updateLastLogin(user.userId(), operationLogService.clientIp(httpRequest));
        operationLogService.record(user.userId(), "LOGIN_SUCCESS", "SUCCESS", null, httpRequest);
        return new LoginVO(token, "Bearer", jwtService.getExpireSeconds(), UserVO.from(user));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser loadUser(String username) {
        SystemUser user = authMapper.selectByUsername(username)
                .orElseThrow(() -> new BusinessException(HttpStatus.UNAUTHORIZED, "Token 对应用户不存在"));
        if (!"ENABLED".equals(user.getStatus()) || !"ENABLED".equals(user.getRoleStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "账号或角色已停用");
        }
        return toAuthenticatedUser(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return loadUser(username);
    }

    public void logout(AuthenticatedUser user, HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            Claims claims = jwtService.parseClaims(authorization.substring(7));
            revocations.revoke(claims.getId(), claims.getExpiration().toInstant());
        }
        operationLogService.record(user.userId(), "LOGOUT", "SUCCESS", null, request);
    }

    private AuthenticatedUser toAuthenticatedUser(SystemUser user) {
        List<String> permissions = resolvePermissions(user);
        return new AuthenticatedUser(user.getUserId(), user.getUsername(), user.getPasswordHash(),
                user.getRealName(), user.getStatus(), user.getRoleId(), user.getRoleCode(), user.getRoleName(),
                user.getRoleLevel(), user.getDataScope(), user.getRoleStatus(), user.getDepartmentId(),
                user.getDepartmentName(), user.getRegionCode(), List.copyOf(permissions));
    }

    private List<String> resolvePermissions(SystemUser user) {
        // SYSTEM_ADMIN 始终拥有全部启用权限，保证最高操作权不被角色授权表遗漏影响。
        if ("SYSTEM_ADMIN".equals(user.getRoleCode())) {
            return authMapper.selectAllEnabledPermissionCodes();
        }
        return authMapper.selectPermissionCodesByRoleId(user.getRoleId());
    }
}
