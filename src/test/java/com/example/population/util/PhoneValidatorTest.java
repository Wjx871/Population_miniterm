package com.example.population.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PhoneValidatorTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "13800138000",
            "15912345678",
            "18600001111",
            "17712345678",
            "19912345678"
    })
    @DisplayName("合法 11 位 1[3-9] 开头手机号")
    void valid(String phone) {
        assertTrue(PhoneValidator.isValid(phone));
        PhoneValidator.assertValid(phone); // 不抛异常
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12800138000",   // 12 开头(不在 13-19 区间)
            "23800138000",   // 2 开头
            "10800138000",   // 10 开头
            "1380013800",    // 10 位
            "138001380000",  // 12 位
            "1380013800a",   // 含字母
            "13-8001-3800"   // 含分隔符
    })
    @DisplayName("非法格式手机号")
    void invalid(String phone) {
        assertFalse(PhoneValidator.isValid(phone));
        assertThrows(com.example.population.exception.PhoneInvalidException.class,
                () -> PhoneValidator.assertValid(phone));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @DisplayName("null / 空串 → 不合法（assertValid 抛异常）")
    void nullOrEmpty(String phone) {
        assertFalse(PhoneValidator.isValid(phone));
        assertThrows(com.example.population.exception.PhoneInvalidException.class,
                () -> PhoneValidator.assertValid(phone));
    }

    @ParameterizedTest
    @CsvSource({
            "13800138000, 138****8000",
            "17712345678, 177****5678",
            "19900001111, 199****1111"
    })
    @DisplayName("脱敏:前 3 + **** + 后 4")
    void maskNormal(String phone, String expected) {
        assertEquals(expected, PhoneValidator.mask(phone));
    }

    @ParameterizedTest
    @CsvSource({
            "null, '****'",
            ", '****'",
            "123, '****'",        // 长度不足 7
            "12345, '****'"
    })
    @DisplayName("脱敏:null 或过短返回 ****")
    void maskShortOrNull(String phone, String expected) {
        String actual = PhoneValidator.mask("null".equals(phone) ? null : phone);
        assertEquals(expected, actual);
    }
}