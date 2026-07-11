package com.example.population.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 当前线程登录用户的安全上下文快照。
 * <p>
 * 由 {@code JwtAuthInterceptor} 写入，业务代码（Controller / Service / Aspect）通过
 * {@link #current()} 或 {@link #requireCurrentUserId()} 读取。
 * <p>
 * 字段一律存"权限判定需要的最小集合"，避免在 ThreadLocal 里放整个 Entity。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SecurityContext implements Serializable {

    private Long userId;
    private String username;
    private String realName;
    private Long roleId;
    private String roleCode;
    private Integer permissionLevel;
    private String dataScopeCode;
    private Long departmentId;
    private Set<String> permissionCodes;

    private static final ThreadLocal<SecurityContext> HOLDER = new ThreadLocal<>();

    public static void set(SecurityContext ctx) {
        HOLDER.set(ctx);
    }

    public static SecurityContext current() {
        return HOLDER.get();
    }

    public static Long requireCurrentUserId() {
        SecurityContext ctx = HOLDER.get();
        if (ctx == null || ctx.getUserId() == null) {
            throw new IllegalStateException("未登录或会话已过期");
        }
        return ctx.getUserId();
    }

    public static void clear() {
        HOLDER.remove();
    }

    public boolean hasPermission(String code) {
        return permissionCodes != null && permissionCodes.contains(code);
    }

    public boolean hasAnyLevel() {
        return permissionLevel != null && permissionLevel >= 1;
    }
}
