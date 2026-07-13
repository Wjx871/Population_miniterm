package com.example.population.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记需要数据范围过滤的 Service 方法。
 * <p>
 * 设计文档 §6：查询接口统一注入数据范围过滤，禁止由前端传入任意部门或区划绕过权限。
 * <p>
 * 切面 {@code DataScopeAspect} 会在方法返回前根据当前用户
 * {@code sys_role.data_scope_code} 把过滤条件 push 到方法参数中：
 *
 * <ul>
 *   <li>{@link Type#AUTO}  ：按方法参数中的 {@code DataScopeQuery} 类型参数自动注入</li>
 *   <li>{@link Type#PERSON}：按 person 维度（handlingDepartmentId / submitUserId）</li>
 *   <li>{@link Type#HOUSEHOLD}：按 household 维度（departmentId / regionCode）</li>
 *   <li>{@link Type#MIGRATION}：按 migration 维度（handlingDepartmentId / fromRegionCode / toRegionCode）</li>
 * </ul>
 *
 * <p>ALL 角色跳过过滤；未登录或缺包上下文的请求按"无范围"放行（依赖 {@code JwtAuthInterceptor} 兜底）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DataScope {

    /** 数据范围过滤的目标实体类型。 */
    Type value() default Type.AUTO;

    enum Type {
        /** 根据方法签名自动推断（推荐：方法参数里有 DataScopeQuery 注入对象）。 */
        AUTO,
        /** 人口/户籍主档。 */
        PERSON,
        /** 家庭户。 */
        HOUSEHOLD,
        /** 迁移/注销等业务表。 */
        MIGRATION
    }
}