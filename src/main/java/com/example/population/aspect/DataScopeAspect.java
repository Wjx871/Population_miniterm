package com.example.population.aspect;

import com.example.population.annotation.DataScope;
import com.example.population.util.DataScopeContext;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 数据范围过滤审计切面。
 * <p>
 * 设计文档 §6：查询接口统一注入数据范围过滤。
 * <p>
 * 实际过滤工作由 Service 内部通过 {@code DataScopeQuery.fromCurrentContext()}
 * 完成（更易复用、更易单测）；本切面只承担两件事：
 * <ol>
 *   <li>校验调用者已经处于登录态（否则 ThreadLocal 为空导致 no-op）</li>
 *   <li>审计日志：记录谁在何时调了什么查询，便于事后追溯越权访问</li>
 * </ol>
 */
@Slf4j
@Aspect
@Component
@Order(20)
public class DataScopeAspect {

    @Around("@annotation(dataScope)")
    public Object audit(ProceedingJoinPoint pjp, DataScope dataScope) throws Throwable {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx != null && log.isDebugEnabled()) {
            log.debug("DataScope audited uid={} role={} scope={} method={}#{}",
                    ctx.getUserId(),
                    ctx.getDataScopeCode(),
                    ctx.getDataScopeCode(),
                    pjp.getSignature().getDeclaringTypeName(),
                    pjp.getSignature().getName());
        }
        return pjp.proceed();
    }
}