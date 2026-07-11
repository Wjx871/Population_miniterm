package com.example.population.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 方法级权限等级闸门。
 * <p>
 * sys_role.permission_level 取值：
 *   1 = L1 查询级
 *   2 = L2 经办级
 *   3 = L3 审批/管理级
 *
 * 当前方法被调用时，要求当前用户 permissionLevel &gt;= 本注解的 value。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresLevel {

    int value();
}
