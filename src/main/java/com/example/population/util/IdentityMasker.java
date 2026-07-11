package com.example.population.util;

/**
 * 敏感字段脱敏工具。仅做展示层脱敏，不影响存储。
 */
public final class IdentityMasker {

    private IdentityMasker() {}

    /**
     * 身份证号脱敏：保留前 6 后 4，中间 ************。
     * 入参不足 10 位时返回 ****。
     */
    public static String maskIdCard(String idCard) {
        if (idCard == null) {
            return null;
        }
        String s = idCard.trim();
        if (s.length() < 10) {
            return "****";
        }
        return s.substring(0, 6) + "************" + s.substring(s.length() - 4);
    }

    /**
     * 手机号脱敏：复用 PhoneValidator.mask。
     */
    public static String maskPhone(String phone) {
        return PhoneValidator.mask(phone);
    }

    /**
     * 中文姓名脱敏：姓保留，名变 *（单字名保留姓）。
     * 例：张三 -> 张*；欧阳娜娜 -> 欧阳**；诸葛亮 -> 诸葛*。
     */
    public static String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return null;
        }
        if (name.length() == 1) {
            return name;
        }
        // 取前两个字为"姓"，剩余字符置 *（简化的中文姓名规则）
        int split = name.length() >= 4 ? 2 : 1;
        StringBuilder sb = new StringBuilder(name.substring(0, split));
        for (int i = split; i < name.length(); i++) {
            sb.append('*');
        }
        return sb.toString();
    }
}
