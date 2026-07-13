package com.example.population.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务操作日志注解。
 * <p>
 * 标注在 Controller 方法上后，{@code OperationLogAspect} 会自动捕获
 * 调用者、模块、操作类型、URI、payload 摘要、最终执行结果并写入 operation_log。
 * <p>
 * 设计参考 {@code @RequiresPermission}，避免在每个 Controller 入口重复样板代码。
 *
 * <pre>
 *   &#064;LogOperation(module = "PERSON", type = "CREATE")
 *   &#064;PostMapping
 *   public Result&lt;?&gt; create(...) { ... }
 *
 *   &#064;LogOperation(module = "MIGRATION", type = "COMPLETE", targetTable = "migration_out", targetIdSpel = "#outId")
 *   &#064;PutMapping("/{outId}/complete")
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface LogOperation {

    /**
     * 模块名（与数据字典 MODULE 对齐，方便统计/筛选）。
     * 常用值：PERSON / HOUSEHOLD / MIGRATION / FLOATING / KEY / CERTIFICATE / LOGIN_LOG / STATS 等。
     */
    String module();

    /**
     * 操作类型编码（与 operation_type 字典对齐）。
     * 常用值：CREATE / UPDATE / DELETE / REGISTER / RELEASE / LEAVE / COMPLETE / CANCEL 等。
     */
    String type();

    /**
     * 目标表名（可选）。如 person / household / migration_out / floating_population / key_population。
     */
    String targetTable() default "";

    /**
     * 目标主键的 SpEL 表达式（可选）。
     * 表达式在 ProceedingJoinPoint 上下文求值，最常见的入参为路径变量 #id / #outId 等。
     * 留空表示不记录 targetId（批量接口 / 列表查询）。
     */
    String targetIdSpel() default "";

    /**
     * 是否记录请求 payload 摘要（业务字段）。
     * 默认 true。对包含敏感长字段（身份证号全文、手机号全文）的入参，会自动改写为脱敏版本。
     */
    boolean recordPayload() default true;
}
