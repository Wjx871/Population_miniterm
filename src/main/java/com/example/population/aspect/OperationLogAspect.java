package com.example.population.aspect;

import com.example.population.annotation.LogOperation;
import com.example.population.entity.OperationLog;
import com.example.population.service.OperationLogService;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

/**
 * 自动写 operation_log 切面。
 * <p>
 * 拦截标了 {@link LogOperation} 的 Controller 方法，采集：
 * <ul>
 *   <li>调用者 userId / departmentId（来自 SecurityContext）</li>
 *   <li>模块 / 操作类型 / 目标表 / 目标主键（来自注解 + SpEL）</li>
 *   <li>HTTP method / URI / 客户端 IP</li>
 *   <li>payload 摘要（自动脱敏身份证号 / 手机号，避免敏感数据原样入库）</li>
 *   <li>操作结果（SUCCESS / FAIL + 异常类名 + 简短消息）</li>
 * </ul>
 * <p>
 * 写库采用新事务（{@code REQUIRES_NEW}），失败仅 warn 不抛，避免日志影响业务。
 */
@Slf4j
@Aspect
@Component
@Order(30) // 晚于 PermissionAspect / DataScopeAspect，避免越权访问被错误审计
@RequiredArgsConstructor
public class OperationLogAspect {

    private final OperationLogService operationLogService;
    private final ObjectMapper objectMapper;

    /** payload JSON 序列化最大长度（防大对象撑爆 DB） */
    private static final int PAYLOAD_MAX_LEN = 2000;

    /** 异常消息最大长度 */
    private static final int EX_MSG_MAX_LEN = 500;

    private static final ExpressionParser SPEL_PARSER = new SpelExpressionParser();
    private static final DefaultParameterNameDiscoverer PARAM_DISCOVERER = new DefaultParameterNameDiscoverer();

    @Around("@annotation(logOp)")
    public Object around(ProceedingJoinPoint pjp, LogOperation logOp) throws Throwable {
        long start = System.currentTimeMillis();
        Object result = null;
        Throwable failure = null;
        try {
            result = pjp.proceed();
            return result;
        } catch (Throwable ex) {
            failure = ex;
            throw ex;
        } finally {
            try {
                writeLog(pjp, logOp, result, failure, System.currentTimeMillis() - start);
            } catch (Exception logEx) {
                OperationLogAspect.log.warn("OperationLogAspect 采集失败: {}", logEx.getMessage());
            }
        }
    }

    private void writeLog(ProceedingJoinPoint pjp, LogOperation logOp,
                          Object result, Throwable failure, long costMs) {
        SecurityContext ctx = SecurityContext.current();
        Long userId = ctx == null ? null : ctx.getUserId();
        Long deptId = ctx == null ? null : ctx.getDepartmentId();

        OperationLog row = new OperationLog();
        row.setUserId(userId);
        row.setDepartmentId(deptId);
        row.setModuleName(logOp.module());
        row.setOperationTypeCode(logOp.type());
        row.setTargetTable(logOp.targetTable());
        row.setTargetId(resolveTargetId(pjp, logOp));

        // HTTP 信息
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            HttpServletRequest req = attrs.getRequest();
            row.setRequestMethod(req.getMethod());
            row.setRequestUri(req.getRequestURI());
            row.setIpAddress(extractIp(req));
            row.setTraceId(req.getHeader("X-Trace-Id"));
        }

        // 失败时优先记录异常，避免 result 反序列化带来额外开销
        if (failure != null) {
            row.setOperationResultCode("FAIL");
            row.setAfterJsonMasked(buildErrorJson(failure));
        } else {
            row.setOperationResultCode("SUCCESS");
            if (logOp.recordPayload()) {
                row.setBeforeJsonMasked(marshalArgs(pjp.getArgs()));
                row.setAfterJsonMasked(marshalResult(result));
            }
        }

        // costMs 仅写入日志，便于排查慢接口；当前表无字段，不入表
        if (log.isDebugEnabled()) {
            log.debug("audit uid={} module={} type={} cost={}ms",
                    userId, logOp.module(), logOp.type(), costMs);
        }
        row.setOperationTime(LocalDateTime.now());
        operationLogService.record(row);
    }

    private Long resolveTargetId(ProceedingJoinPoint pjp, LogOperation logOp) {
        if (!StringUtils.hasText(logOp.targetIdSpel())) {
            return null;
        }
        try {
            MethodSignature sig = (MethodSignature) pjp.getSignature();
            Method method = sig.getMethod();
            EvaluationContext context = buildContext(method, pjp.getArgs(), pjp.getTarget());
            Expression exp = SPEL_PARSER.parseExpression(logOp.targetIdSpel());
            Object val = exp.getValue(context);
            if (val == null) return null;
            if (val instanceof Number n) return n.longValue();
            return Long.parseLong(val.toString());
        } catch (Exception e) {
            log.debug("targetIdSpel 求值失败 expr={} err={}", logOp.targetIdSpel(), e.getMessage());
            return null;
        }
    }

    private EvaluationContext buildContext(Method method, Object[] args, Object target) {
        StandardEvaluationContext ctx = new StandardEvaluationContext();
        String[] paramNames = PARAM_DISCOVERER.getParameterNames(method);
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                ctx.setVariable(paramNames[i], args[i]);
            }
        }
        ctx.setVariable("_target", target);
        return ctx;
    }

    private String extractIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (StringUtils.hasText(ip) && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        return req.getRemoteAddr();
    }

    private String marshalArgs(Object[] args) {
        if (args == null || args.length == 0) return null;
        // 过滤掉 HttpServletRequest/Response 等非业务入参
        Object[] filtered = Arrays.stream(args)
                .filter(a -> a != null
                        && !(a instanceof org.springframework.web.multipart.MultipartFile)
                        && !(a instanceof jakarta.servlet.ServletRequest)
                        && !(a instanceof jakarta.servlet.ServletResponse))
                .map(this::maskSensitive)
                .toArray();
        return toJson(filtered);
    }

    private String marshalResult(Object result) {
        if (result == null) return null;
        return toJson(maskSensitive(result));
    }

    private String buildErrorJson(Throwable t) {
        String cls = t.getClass().getSimpleName();
        String msg = t.getMessage() == null ? "" : t.getMessage();
        if (msg.length() > EX_MSG_MAX_LEN) {
            msg = msg.substring(0, EX_MSG_MAX_LEN);
        }
        return toJson(Map.of("exception", cls, "message", msg));
    }

    private Object maskSensitive(Object obj) {
        if (obj == null) return null;
        if (obj instanceof String s) return maskString(s);
        if (obj instanceof Collection<?> col) {
            return col.stream().map(this::maskSensitive).toList();
        }
        if (obj.getClass().isArray()) {
            return Arrays.stream((Object[]) obj).map(this::maskSensitive).toList();
        }
        if (obj instanceof Map<?, ?> map) {
            return map;
        }
        // DTO / Entity：通过反射读取字段后脱敏，避免 ObjectMapper 暴露明文身份证/手机
        try {
            java.util.LinkedHashMap<String, Object> out = new java.util.LinkedHashMap<>();
            for (java.lang.reflect.Field f : getAllFields(obj.getClass())) {
                f.setAccessible(true);
                Object v = f.get(obj);
                if (v == null) continue;
                String name = f.getName();
                String s;
                if ("identityNo".equalsIgnoreCase(name) || "idCard".equalsIgnoreCase(name)
                        || "identityNoMasked".equalsIgnoreCase(name)) {
                    s = maskIdCard(v.toString());
                } else if ("phone".equalsIgnoreCase(name) || "mobile".equalsIgnoreCase(name)
                        || "landlordPhone".equalsIgnoreCase(name) || "contactPhone".equalsIgnoreCase(name)) {
                    s = com.example.population.util.PhoneValidator.mask(v.toString());
                } else if (v instanceof String sv) {
                    s = sv;
                } else {
                    s = v.toString();
                }
                out.put(name, s);
            }
            return out;
        } catch (Exception e) {
            return obj.getClass().getSimpleName() + "@" + Integer.toHexString(System.identityHashCode(obj));
        }
    }

    private static java.util.List<java.lang.reflect.Field> getAllFields(Class<?> c) {
        java.util.List<java.lang.reflect.Field> out = new java.util.ArrayList<>();
        Class<?> cur = c;
        while (cur != null && cur != Object.class) {
            for (java.lang.reflect.Field f : cur.getDeclaredFields()) {
                if (java.lang.reflect.Modifier.isStatic(f.getModifiers())) continue;
                out.add(f);
            }
            cur = cur.getSuperclass();
        }
        return out;
    }

    private static String maskString(String s) {
        if (s == null) return null;
        // 全文里如果含有疑似身份证号，做局部脱敏
        return s.replaceAll("\\d{17}[\\dXx]", "***ID***")
                .replaceAll("1[3-9]\\d{9}", "***PHONE***");
    }

    private static String maskIdCard(String s) {
        if (s == null || s.length() < 8) return "***";
        return s.substring(0, 4) + "**********" + s.substring(s.length() - 4);
    }

    private String toJson(Object obj) {
        try {
            String json = objectMapper.writeValueAsString(obj);
            if (json.length() > PAYLOAD_MAX_LEN) {
                json = json.substring(0, PAYLOAD_MAX_LEN) + "...";
            }
            return json;
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
