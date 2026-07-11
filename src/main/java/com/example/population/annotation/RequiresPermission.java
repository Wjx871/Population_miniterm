package com.example.population.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限码注解。
 * <p>
 * 配合 {@code PermissionAspect} 使用。任何标了本注解的方法被调用前，
 * 切面会读取当前 SecurityContext 中的 permissionCodes 集合进行校验。
 *
 * <pre>
 *   &#064;RequiresPermission("person:create")                          // 单个
 *   &#064;RequiresPermission({"person:create", "person:update"})       // 多选 OR 语义
 *   &#064;RequiresPermission(value = "user:manage", all = true)        // 显式单选
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresPermission {

    String[] value();

    /**
     * 当 {@link #value()} 为多个时，是否要求全部命中（AND）。
     * 默认 false（OR 语义，任一命中即放行）。
     */
    boolean all() default false;
}
