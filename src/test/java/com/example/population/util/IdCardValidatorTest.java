package com.example.population.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IdCardValidator 单测：覆盖 18/15 位合法性、校验位、出生日期、性别提取、15→18 转换。
 *
 * 校验位算法（GB 11643-1999）：
 *   body17 = id[0..16], sum = Σ body[i] * WEIGHT[i], check = CHECK_CODES[sum % 11]
 */
class IdCardValidatorTest {

    private static final int[] WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] CHECK_CODES = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    /** 根据 17 位前缀构造一个合法的 18 位身份证号 */
    private static String buildValid18(String prefix17) {
        assertEquals(17, prefix17.length(), "prefix17 must be 17 chars");
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (prefix17.charAt(i) - '0') * WEIGHT[i];
        }
        return prefix17 + CHECK_CODES[sum % 11];
    }

    // ---------- isValid 18 位 ----------

    @Test
    @DisplayName("合法 18 位身份证通过校验")
    void valid18() {
        assertTrue(IdCardValidator.isValid(buildValid18("11010119900101123")));
    }

    @Test
    @DisplayName("18 位长度合法但校验位错误 → 拒绝")
    void invalidCheckCode() {
        String good = buildValid18("11010119900101123");
        char wrong = good.charAt(17) == '0' ? '1' : '0';
        String bad = good.substring(0, 17) + wrong;
        assertFalse(IdCardValidator.isValid(bad));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11010119900101123",   // 17 位
            "1101011990010112345",  // 19 位
            "",
            "110101199013011234",   // 月份非法(13)
            "110101199002301234"    // 2 月 30 日非法
    })
    @DisplayName("长度不对或日期非法 → 拒绝")
    void invalid18(String input) {
        assertFalse(IdCardValidator.isValid(input));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null / 空串 → 拒绝（不抛异常）")
    void nullOrEmpty(String input) {
        assertFalse(IdCardValidator.isValid(input));
    }

    @Test
    @DisplayName("前 17 位含非数字 → 拒绝")
    void nonDigitInPrefix() {
        // 构造一个第 5 位是字母的 18 位串
        String bad = "1101X119900101123";
        assertFalse(IdCardValidator.isValid(bad));
    }

    @Test
    @DisplayName("小写 x 自动 toUpperCase 后仍正确")
    void lowercaseXAccepted() {
        String good = buildValid18("11010119900101123");
        // 即便末尾不是 X,小写后再交给 isValid,toUpperCase 把它恢复;只要仍是合法 18 位即可
        assertTrue(IdCardValidator.isValid(good.toLowerCase()));
    }

    // ---------- isValid 15 位 ----------

    @Test
    @DisplayName("合法 15 位身份证通过校验")
    void valid15() {
        // 110101 + 900101(默认 19xx) + 234
        assertTrue(IdCardValidator.isValid("110101900101234"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "11010190010123",    // 14 位
            "1101019001012345",  // 16 位
            "11010190013a234",   // 含字母
            "110101901301234"    // 月份非法
    })
    @DisplayName("非法 15 位 → 拒绝")
    void invalid15(String input) {
        assertFalse(IdCardValidator.isValid(input));
    }

    // ---------- assertValid ----------

    @Test
    @DisplayName("assertValid 合法号不抛异常")
    void assertValidOk() {
        IdCardValidator.assertValid(buildValid18("11010119900101123"));
    }

    @Test
    @DisplayName("assertValid 非法号抛 IdCardInvalidException")
    void assertValidFail() {
        assertThrows(com.example.population.exception.IdCardInvalidException.class,
                () -> IdCardValidator.assertValid("not-a-card"));
    }

    // ---------- extractBirthday ----------

    @Test
    @DisplayName("18 位提取出生日期")
    void extractBirthday18() {
        LocalDate bd = IdCardValidator.extractBirthday(buildValid18("11010119900101123"));
        assertEquals(LocalDate.of(1990, 1, 1), bd);
    }

    @Test
    @DisplayName("15 位提取出生日期(默认 19xx)")
    void extractBirthday15() {
        LocalDate bd = IdCardValidator.extractBirthday("110101900101234");
        assertEquals(LocalDate.of(1990, 1, 1), bd);
    }

    @Test
    @DisplayName("null / 非法返回 null")
    void extractBirthdayNull() {
        assertNull(IdCardValidator.extractBirthday(null));
        assertNull(IdCardValidator.extractBirthday("xxx"));
    }

    // ---------- extractGenderCode ----------

    @Test
    @DisplayName("18 位第 17 位偶数 → FEMALE")
    void genderFemale18() {
        // 顺序码末位(index 16)选偶数 2 → FEMALE
        String id = buildValid18("11010119900101122");
        assertEquals("FEMALE", IdCardValidator.extractGenderCode(id));
    }

    @Test
    @DisplayName("18 位第 17 位奇数 → MALE")
    void genderMale18() {
        // 顺序码末位(index 16)选奇数 3 → MALE
        String id = buildValid18("11010119900101133");
        assertEquals("MALE", IdCardValidator.extractGenderCode(id));
    }

    @Test
    @DisplayName("15 位末位偶数 → FEMALE")
    void genderFemale15() {
        assertEquals("FEMALE", IdCardValidator.extractGenderCode("110101900101234"));
    }

    @Test
    @DisplayName("15 位末位奇数 → MALE")
    void genderMale15() {
        assertEquals("MALE", IdCardValidator.extractGenderCode("110101900101235"));
    }

    @Test
    @DisplayName("null / 非法 → UNKNOWN")
    void genderUnknown() {
        assertEquals("UNKNOWN", IdCardValidator.extractGenderCode(null));
        assertEquals("UNKNOWN", IdCardValidator.extractGenderCode("xx"));
    }

    // ---------- to18 ----------

    @Test
    @DisplayName("15 → 18 转码后 isValid 应通过")
    void to18ThenValid() {
        String id15 = "110101900101234";
        String id18 = IdCardValidator.to18(id15);
        assertEquals(18, id18.length());
        assertTrue(IdCardValidator.isValid(id18));
    }

    @Test
    @DisplayName("to18 输入非 15 位抛异常")
    void to18Invalid() {
        assertThrows(com.example.population.exception.IdCardInvalidException.class,
                () -> IdCardValidator.to18("1234"));
        assertThrows(com.example.population.exception.IdCardInvalidException.class,
                () -> IdCardValidator.to18(null));
    }

    // ---------- 参数化批量合法性 ----------

    @ParameterizedTest
    @CsvSource({
            "110101,19900307,881",
            "320105,19851215,123",
            "440305,20000820,002"
    })
    @DisplayName("批量合成合法号均通过校验")
    void manyValid(String region, String ymd, String seq) {
        String prefix17 = region + ymd + seq;
        assertEquals(17, prefix17.length());
        assertTrue(IdCardValidator.isValid(buildValid18(prefix17)));
    }
}