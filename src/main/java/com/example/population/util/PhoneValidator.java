package com.example.population.util;

import com.example.population.exception.PhoneInvalidException;

import java.util.regex.Pattern;

/**
 * 中国大陆手机号工具。
 */
public final class PhoneValidator {

    /** 国内 11 位手机号正则：1[3-9] 开头的 11 位数字 */
    private static final Pattern PATTERN = Pattern.compile("^1[3-9]\\d{9}$");

    private PhoneValidator() {}

    public static boolean isValid(String phone) {
        return phone != null && PATTERN.matcher(phone).matches();
    }

    public static void assertValid(String phone) {
        if (!isValid(phone)) {
            throw new PhoneInvalidException("手机号必须为 11 位且以 1[3-9] 开头");
        }
    }

    /**
     * 脱敏：138****8000
     */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
}
