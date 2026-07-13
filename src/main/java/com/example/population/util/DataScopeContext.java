package com.example.population.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 数据范围上下文（线程级快照）。
 * <p>
 * 由 {@code JwtAuthInterceptor} 在解析 token 时写入，业务 Service 通过
 * {@link #current()} 读取并拼装查询条件；不允许被 Controller/Service 直接覆盖。
 * <p>
 * 设计文档 §6 权限与审批矩阵：查询条件必须由后端统一注入，禁止前端传入任意部门或区划绕过权限。
 *
 * @see com.example.population.aspect.DataScopeAspect
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataScopeContext implements Serializable {

    /** sys_role.data_scope_code。 */
    private String dataScopeCode;

    /** 当前用户 userId。 */
    private Long userId;

    /** 当前用户所属部门 id（DEPARTMENT/REGION/SELF 范围内需要）。 */
    private Long departmentId;

    /** 当前用户所属部门对应的区划编码（REGION 范围需要）。 */
    private String departmentRegionCode;

    /** 业务可选：用户限定可见区划编码集合（REGION 范围时 = 部门区划 + 所有下级）。 */
    private Set<String> visibleRegionCodes;

    private static final ThreadLocal<DataScopeContext> HOLDER = new ThreadLocal<>();

    public static void set(DataScopeContext ctx) {
        HOLDER.set(ctx);
    }

    public static DataScopeContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }

    /** 角色 = ALL（管理员），不需要数据范围过滤。 */
    public boolean isAll() {
        return "ALL".equalsIgnoreCase(dataScopeCode);
    }

    /** 角色 = DEPARTMENT（默认），按 sys_user.department_id 过滤。 */
    public boolean isDepartment() {
        return "DEPARTMENT".equalsIgnoreCase(dataScopeCode);
    }

    /** 角色 = REGION，按区划树过滤。 */
    public boolean isRegion() {
        return "REGION".equalsIgnoreCase(dataScopeCode);
    }

    /** 角色 = SELF，仅本人经办数据。 */
    public boolean isSelf() {
        return "SELF".equalsIgnoreCase(dataScopeCode);
    }
}