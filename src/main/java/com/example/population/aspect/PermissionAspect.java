package com.example.population.aspect;

import com.example.population.annotation.RequiresPermission;
import com.example.population.exception.ForbiddenException;
import com.example.population.util.PermissionCache;
import com.example.population.util.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Set;

/**
 * 切 {@code @RequiresPermission} 注解。
 * <p>
 * 流程：从 {@code SecurityContext} 拿到当前 uid；若 SecurityContext 已有 permissionCodes 直接用，
 * 否则从 {@code PermissionCache}（Redis）拉；按 OR/AND 语义判定，不通过抛 403。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class PermissionAspect {

    private final PermissionCache permissionCache;

    @Around("@annotation(requiresPermission)")
    public Object check(ProceedingJoinPoint pjp, RequiresPermission requiresPermission) throws Throwable {
        SecurityContext ctx = SecurityContext.current();
        if (ctx == null || ctx.getUserId() == null) {
            throw new ForbiddenException("未登录");
        }

        Set<String> userPerms = ctx.getPermissionCodes();
        if (userPerms == null || userPerms.isEmpty()) {
            userPerms = permissionCache.get(ctx.getUserId());
        }
        if (userPerms == null) {
            userPerms = Set.of();
        }

        String[] required = requiresPermission.value();
        boolean all = requiresPermission.all();
        boolean ok;
        if (all) {
            ok = Arrays.stream(required).allMatch(userPerms::contains);
        } else {
            ok = Arrays.stream(required).anyMatch(userPerms::contains);
        }

        if (!ok) {
            String need = all ? "ALL of " : "ANY of ";
            log.warn("权限校验未通过 uid={} need={}{}", ctx.getUserId(), need, Arrays.toString(required));
            throw new ForbiddenException(
                    "权限不足：需要 " + need + Arrays.toString(required)
                            + "（当前等级 L" + ctx.getPermissionLevel() + "）");
        }

        return pjp.proceed();
    }
}
