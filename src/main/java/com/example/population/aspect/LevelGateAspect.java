package com.example.population.aspect;

import com.example.population.annotation.RequiresLevel;
import com.example.population.exception.ForbiddenException;
import com.example.population.util.SecurityContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 切 {@code @RequiresLevel} 注解。
 * <p>
 * 仅按 sys_role.permission_level 阈值判定，比 {@code @RequiresPermission} 粗，
 * 适合"任何 L3 才能调"的总体闸门（例如审批通过、角色管理）。
 */
@Slf4j
@Aspect
@Component
public class LevelGateAspect {

    @Around("@annotation(requiresLevel)")
    public Object check(ProceedingJoinPoint pjp, RequiresLevel requiresLevel) throws Throwable {
        SecurityContext ctx = SecurityContext.current();
        if (ctx == null || ctx.getPermissionLevel() == null) {
            throw new ForbiddenException("未登录");
        }
        int required = requiresLevel.value();
        if (ctx.getPermissionLevel() < required) {
            log.warn("权限等级不足 uid={} current=L{} need=L{}",
                    ctx.getUserId(), ctx.getPermissionLevel(), required);
            throw new ForbiddenException("权限不足：需要 L" + required + " 及以上，当前 L" + ctx.getPermissionLevel());
        }
        return pjp.proceed();
    }
}
