package com.example.population.util;

import com.example.population.exception.IdCardInvalidException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 中华人民共和国居民身份证号校验工具（GB 11643-1999）。
 *
 * 支持 18 位（末位可为数字或 X）与 15 位（旧版）输入；
 * 仅做格式 + 校验位 + 出生日期合法性验证，不做活体/真实性判断。
 */
public final class IdCardValidator {

    private static final DateTimeFormatter YMD = DateTimeFormatter.ofPattern("yyyyMMdd");

    /** 18 位校验位加权因子（从左到右，对应身份证号前 17 位） */
    private static final int[] WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    /** 18 位校验位对照表 */
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    private IdCardValidator() {}

    /**
     * 校验身份证号是否合法（不接受 null）。
     */
    public static boolean isValid(String idCard) {
        if (idCard == null) {
            return false;
        }
        String s = idCard.trim().toUpperCase();
        try {
            if (s.length() == 18) {
                return validate18(s);
            } else if (s.length() == 15) {
                return validate15(s);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 校验并在失败时抛出业务异常。常用于 DTO / Service 层 fail-fast。
     */
    public static void assertValid(String idCard) {
        if (!isValid(idCard)) {
            throw new IdCardInvalidException("身份证号格式或校验位错误");
        }
    }

    /**
     * 提取出生日期（兼容 18/15 位），失败返回 null。
     */
    public static LocalDate extractBirthday(String idCard) {
        if (idCard == null) {
            return null;
        }
        String s = idCard.trim().toUpperCase();
        try {
            if (s.length() == 18) {
                return LocalDate.parse(s.substring(6, 14), YMD);
            } else if (s.length() == 15) {
                // 15 位年份仅 2 位，按 19xx 处理（旧版身份证）
                int yy = Integer.parseInt(s.substring(6, 8));
                String date19 = "19" + s.substring(6, 12);
                return LocalDate.parse(date19, YMD);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * 提取性别（男性 = 'MALE', 女性 = 'FEMALE', 其他 = 'UNKNOWN'）。
     * 18 位取第 17 位数字（顺序码倒数第二位）；15 位取末位。
     */
    public static String extractGenderCode(String idCard) {
        if (idCard == null) {
            return "UNKNOWN";
        }
        String s = idCard.trim().toUpperCase();
        try {
            int order;
            if (s.length() == 18) {
                order = Character.digit(s.charAt(16), 10);
            } else if (s.length() == 15) {
                order = Character.digit(s.charAt(14), 10);
            } else {
                return "UNKNOWN";
            }
            return order % 2 == 1 ? "MALE" : "FEMALE";
        } catch (Exception e) {
            return "UNKNOWN";
        }
    }

    /**
     * 15 位身份证号转 18 位（不强制校验原号）。
     */
    public static String to18(String idCard15) {
        if (idCard15 == null || idCard15.length() != 15) {
            throw new IdCardInvalidException("15 位身份证号格式错误");
        }
        String s = idCard15.trim();
        String prefix = s.substring(0, 6);
        String year2 = s.substring(6, 8);
        String monthDay = s.substring(8, 12);
        String order = s.substring(12, 15);
        // 旧版默认为 19xx
        String body17 = prefix + "19" + year2 + monthDay + order;
        char check = computeCheckCode(body17);
        return body17 + check;
    }

    // ------------------ private helpers ------------------

    private static boolean validate18(String s) {
        // 前 17 位必须为数字
        for (int i = 0; i < 17; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        // 最后一位必须为数字或 X
        char last = s.charAt(17);
        if (!((last >= '0' && last <= '9') || last == 'X')) {
            return false;
        }
        if (computeCheckCode(s.substring(0, 17)) != last) {
            return false;
        }
        // 出生日期合法性
        return extractBirthday(s) != null;
    }

    private static boolean validate15(String s) {
        for (int i = 0; i < 15; i++) {
            char c = s.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return extractBirthday(s) != null;
    }

    private static char computeCheckCode(String body17) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (body17.charAt(i) - '0') * WEIGHT[i];
        }
        return CHECK_CODES[sum % 11];
    }
}
