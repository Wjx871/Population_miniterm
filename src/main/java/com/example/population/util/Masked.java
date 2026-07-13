package com.example.population.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 Jackson 序列化时对该字段进行脱敏处理。
 * <p>
 * 配合 {@link MaskedSerializer} 使用。脱敏后的字符串原文 <b>不会</b> 进入序列化输出；
 * 仅展示给前端展示层（结合当前用户权限决定是否进一步暴露完整值）。
 *
 * <pre>
 *   &#064;Masked(MaskedRule.ID_CARD)
 *   private String identityNo;
 *
 *   &#064;Masked(MaskedRule.PHONE)
 *   private String phone;
 *
 *   // 名字 + 手机号 + 身份证 三合一脱敏，按需叠加
 * </pre>
 *
 * 设计文档 §6 / D-04：所有身份证号、手机号、姓名等敏感字段统一脱敏；L3 管理员才能通过"查看原文"接口获取原始值。
 */
@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface Masked {

    /** 脱敏规则。 */
    MaskedRule value() default MaskedRule.ID_CARD;
}