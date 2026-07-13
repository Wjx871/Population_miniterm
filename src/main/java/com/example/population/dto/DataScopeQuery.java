package com.example.population.dto;

import com.example.population.util.DataScopeContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Set;

/**
 * 数据范围查询上下文。
 * <p>
 * Service 在拼装 MyBatis-Plus LambdaQueryWrapper 时调用
 * {@link #fromCurrentContext()} 拿当前线程的过滤快照。
 *
 * <pre>
 * if (ds != null) {
 *     if (ds.getDepartmentId() != null) w.eq(Person::getDepartmentId, ds.getDepartmentId());
 *     if (ds.getRegionCode() != null)  w.eq(Person::getRegionCode,  ds.getRegionCode());
 *     if (ds.getSubmitUserId() != null) w.eq(Person::getSubmitUserId, ds.getSubmitUserId());
 *     if (ds.getVisibleRegionCodes() != null &amp;&amp; !ds.getVisibleRegionCodes().isEmpty())
 *         w.in(Person::getRegionCode, ds.getVisibleRegionCodes());
 * }
 * </pre>
 *
 * 字段全为 null 表示 ALL 范围或未启用数据范围，无需过滤。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DataScopeQuery implements Serializable {

    /** DEPARTMENT 范围：限定 sys_user.department_id 等于本字段。 */
    private Long departmentId;

    /** REGION 范围（精确匹配）：限定 region_code 等于本字段（当前用户所在区划）。 */
    private String regionCode;

    /** REGION 范围（含下级）：限定 region_code in 本集合。 */
    private Set<String> visibleRegionCodes;

    /** SELF 范围：限定 submit_user_id = 当前 userId。 */
    private Long submitUserId;

    /** 用于日志/调试，原始 data_scope_code。 */
    private String sourceDataScope;

    /**
     * 从 ThreadLocal 当前线程上下文构造过滤条件。
     * <p>
     * 若上下文缺失（系统调用 / 未登录）则返回 ALL（字段全 null），不做过滤。
     */
    public static DataScopeQuery fromCurrentContext() {
        DataScopeContext ctx = DataScopeContext.current();
        if (ctx == null) {
            return DataScopeQuery.builder().sourceDataScope("NONE").build();
        }
        return DataScopeQuery.builder()
                .sourceDataScope(ctx.getDataScopeCode())
                .departmentId(ctx.isDepartment() ? ctx.getDepartmentId() : null)
                .regionCode((ctx.isRegion() && ctx.getDepartmentRegionCode() != null && ctx.getVisibleRegionCodes() == null)
                        ? ctx.getDepartmentRegionCode() : null)
                .visibleRegionCodes((ctx.isRegion() && ctx.getVisibleRegionCodes() != null)
                        ? ctx.getVisibleRegionCodes() : null)
                .submitUserId(ctx.isSelf() ? ctx.getUserId() : null)
                .build();
    }

    /**
     * 是否需要任何过滤（全 null 表示 ALL，无需过滤）。
     */
    public boolean isActive() {
        return departmentId != null || regionCode != null
                || (visibleRegionCodes != null && !visibleRegionCodes.isEmpty())
                || submitUserId != null;
    }
}