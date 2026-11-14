package com.wjx871.population.auth;

import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.JwtService;
import com.wjx871.population.security.TokenRevocationService;
import com.wjx871.population.s2.trace.S2TraceContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Log4j2
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

        AuthenticatedUser user;
        JwtService.IssuedJwt issued;
        try (S2TraceContext.Scope ignored = S2TraceContext.open(httpRequest.getHeader(S2TraceContext.TRACE_HEADER))) {
            revocations.requireLoginStore();
            user = toAuthenticatedUser(systemUser);
            issued = jwtService.createIssuedToken(user);
            revocations.verifyIssuedTokenNotSuperseded(issued.issuedAt());
        }
        authMapper.updateLastLogin(user.userId(), operationLogService.clientIp(httpRequest));
        operationLogService.record(user.userId(), "LOGIN_SUCCESS", "SUCCESS", null, httpRequest);
        return new LoginVO(issued.token(), "Bearer", jwtService.getExpireSeconds(), UserVO.from(user));
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

    @Transactional(readOnly = true)
    public AuthenticatedUser loadUserById(Long userId) {
        SystemUser user = authMapper.selectByUserId(userId)
                .orElseThrow(() -> new BusinessException(HttpStatus.FORBIDDEN, "申请人当前授权无法验证"));
        if (!"ENABLED".equals(user.getStatus()) || !"ENABLED".equals(user.getRoleStatus())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "申请人账号或角色已停用");
        }
        return toAuthenticatedUser(user);
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        return loadUser(username);
    }

    public void logout(AuthenticatedUser user, HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "缺少有效身份令牌");
        }
        Claims claims;
        try {
            claims = jwtService.parseClaims(authorization.substring(7));
        } catch (ExpiredJwtException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "身份令牌已过期");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "身份令牌无效");
        }
        if (claims.getId() == null || claims.getId().isBlank() || claims.getExpiration() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "身份令牌无效");
        }
        Long userId = user != null ? user.userId() : null;
        revocations.revoke(claims.getId(), claims.getExpiration().toInstant(), userId);
        try {
            operationLogService.record(userId, "LOGOUT", "SUCCESS", null, request);
        } catch (RuntimeException exception) {
            log.error("Failed to record LOGOUT audit log after revocation commit userId={} errorType={}",
                    userId, exception.getClass().getSimpleName());
        }
    }

    private AuthenticatedUser toAuthenticatedUser(SystemUser user) {
        List<String> permissions = resolvePermissions(user);
        List<String> regionCodes = user.getDataScope() == DataScope.REGION
                ? regionCodesFor(user.getRegionCode())
                : List.of();
        return new AuthenticatedUser(user.getUserId(), user.getUsername(), user.getPasswordHash(),
                user.getRealName(), user.getStatus(), user.getRoleId(), user.getRoleCode(), user.getRoleName(),
                user.getRoleLevel(), user.getDataScope(), user.getRoleStatus(), user.getDepartmentId(),
                user.getDepartmentName(), user.getRegionCode(), List.copyOf(permissions), regionCodes);
    }

    public List<String> regionCodesFor(String regionCode) {
        if (regionCode == null || regionCode.isBlank()) return List.of();
        List<String> regionCodes = authMapper.selectEnabledRegionCodes(regionCode);
        return regionCodes == null ? List.of() : List.copyOf(regionCodes);
    }

    private List<String> resolvePermissions(SystemUser user) {
        // SYSTEM_ADMIN 始终拥有全部启用权限，保证最高操作权不被角色授权表遗漏影响。
        if ("SYSTEM_ADMIN".equals(user.getRoleCode())) {
            return authMapper.selectAllEnabledPermissionCodes();
        }
        return authMapper.selectPermissionCodesByRoleId(user.getRoleId());
    }
}
