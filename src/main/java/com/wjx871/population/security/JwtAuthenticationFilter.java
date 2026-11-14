package com.wjx871.population.security;

import com.wjx871.population.auth.AuthService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.s2.trace.S2TraceContext;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AuthService authService;
    private final SecurityResponseWriter responseWriter;
    private final TokenRevocationService revocations;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && ("/api/auth/login".equals(request.getRequestURI())
                || (request.getContextPath() + "/api/auth/login").equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        try (S2TraceContext.Scope ignored = S2TraceContext.open(request.getHeader(S2TraceContext.TRACE_HEADER))) {
            Claims claims = jwtService.parseClaims(authorization.substring(7));
            if (claims.getId() == null || claims.getId().isBlank()
                    || claims.getSubject() == null || claims.getSubject().isBlank()
                    || claims.getIssuedAt() == null
                    || claims.getExpiration() == null) {
                throw new IllegalArgumentException("Missing required JWT claims");
            }
            if (revocations.isRevoked(
                    claims.getId(),
                    claims.getIssuedAt().toInstant(),
                    claims.getExpiration().toInstant())) {
                SecurityContextHolder.clearContext();
                responseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "身份令牌已撤销");
                return;
            }
            String username = claims.getSubject();
            AuthenticatedUser user = authService.loadUser(username);
            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
        } catch (ExpiredJwtException exception) {
            SecurityContextHolder.clearContext();
            responseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "身份令牌已过期");
        } catch (JwtException | IllegalArgumentException exception) {
            SecurityContextHolder.clearContext();
            responseWriter.write(response, HttpServletResponse.SC_UNAUTHORIZED, "身份令牌无效");
        } catch (BusinessException exception) {
            SecurityContextHolder.clearContext();
            responseWriter.write(response, exception.getStatus().value(), exception.getMessage());
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            throw exception;
        }
    }
}
