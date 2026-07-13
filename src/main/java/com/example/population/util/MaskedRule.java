package com.example.population.util;

/**
 * 脱敏规则枚举。
 * <p>
 * 由 {@link Masked} 和 {@link MaskedSerializer} 共同引用。
 */
public enum MaskedRule {
    /** 身份证号（保留前 6 后 4）。 */
    ID_CARD,
    /** 手机号（中间 4 位 *）。 */
    PHONE,
    /** 中文姓名（保留姓）。 */
    NAME,
    /** 联系地址（保留前 6 后 4）。 */
    ADDRESS,
    /** 全隐藏。 */
    FULL
}