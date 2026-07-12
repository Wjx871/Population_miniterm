package com.example.population.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PasswordEncoder 单元测试。
 * <p>
 * 覆盖：
 * <ul>
 *   <li>BCrypt 主算法 encode + matches（同一密码两次 hash 不同 → 都能匹配）</li>
 *   <li>BCrypt 不匹配错误密码</li>
 *   <li>老 SHA-256 格式（sha256$...$salt）仍可校验（迁移期兼容）</li>
 *   <li>老 hash 错误密码 → 不匹配</li>
 *   <li>needsUpgrade 正确判断</li>
 *   <li>异常输入（null / 空 / 乱码 hash）→ 安全降级</li>
 * </ul>
 */
class PasswordEncoderTest {

    @Test
    @DisplayName("BCrypt: 同一密码两次 hash 不同（salt 随机），但 matches 都通过")
    void bcrypt_roundTrip() {
        String raw = "Passw0rd!";
        String h1 = PasswordEncoder.encode(raw);
        String h2 = PasswordEncoder.encode(raw);

        // salt 是随机的，两次 hash 应该不同
        assertThat(h1).isNotEqualTo(h2);
        // 都是 $2a$12$ 开头
        assertThat(h1).startsWith("$2a$12$");
        assertThat(h2).startsWith("$2a$12$");
        // 都能匹配原密码
        assertThat(PasswordEncoder.matches(raw, h1)).isTrue();
        assertThat(PasswordEncoder.matches(raw, h2)).isTrue();
    }

    @Test
    @DisplayName("BCrypt: 错误密码不匹配")
    void bcrypt_wrongPassword() {
        String hash = PasswordEncoder.encode("correct-password");
        assertThat(PasswordEncoder.matches("wrong-password", hash)).isFalse();
    }

    @Test
    @DisplayName("Legacy SHA-256: 用 PasswordEncoder.matchLegacy 等价路径（构造合法 legacy hash）匹配成功")
    void legacy_match() throws Exception {
        // 构造一个 sha256$<hex64>$<base64salt> 格式的合法 legacy hash
        String raw = "old-password";
        byte[] salt = PasswordEncoder.generateLegacySalt();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(salt);
        byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        String stored = "sha256$" + hex + "$" + java.util.Base64.getEncoder().encodeToString(salt);

        assertThat(PasswordEncoder.matches(raw, stored)).isTrue();
        assertThat(PasswordEncoder.matches("wrong", stored)).isFalse();
    }

    @Test
    @DisplayName("Legacy: needsUpgrade 正确识别")
    void needsUpgrade_recognizes() {
        assertThat(PasswordEncoder.needsUpgrade("sha256$abc$def")).isTrue();
        assertThat(PasswordEncoder.needsUpgrade("$2a$12$abc")).isFalse();
        assertThat(PasswordEncoder.needsUpgrade(null)).isFalse();
    }

    @Test
    @DisplayName("异常输入：null / 空 / 非法 hash → 安全降级（false）")
    void invalidInputs_doNotThrow() {
        assertThat(PasswordEncoder.matches(null, "$2a$12$abc")).isFalse();
        assertThat(PasswordEncoder.matches("raw", null)).isFalse();
        assertThat(PasswordEncoder.matches("raw", "")).isFalse();
        assertThat(PasswordEncoder.matches("raw", "garbage-not-a-hash")).isFalse();
        // 非法 legacy 格式
        assertThat(PasswordEncoder.matches("raw", "sha256$$$")).isFalse();
        assertThat(PasswordEncoder.matches("raw", "sha256$only_one_segment")).isFalse();
    }
}