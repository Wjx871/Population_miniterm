package com.example.population.util;

import org.springframework.security.crypto.bcrypt.BCrypt;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 密码哈希工具。
 * <p>
 * 当前主算法：<b>BCrypt（cost = 12）</b>，由 spring-security-crypto 提供。
 * 老算法：<b>单轮 SHA-256 + 16-byte salt</b>（已废弃，仅作迁移期校验用）。
 * <p>
 * 存储格式：
 * <ul>
 *   <li>{@code $2a$12$...} —— BCrypt 主算法（spring-security-crypto 推荐前缀）</li>
 *   <li>{@code sha256$<hex>$<base64salt>} —— 老格式，向前兼容（登录成功后自动升级为 BCrypt）</li>
 * </ul>
 * 迁移策略：登录时若匹配到老 hash，调用方应使用 {@link #encode(String)} 生成 BCrypt 并落库。
 */
public final class PasswordEncoder {

    /** BCrypt 成本因子（默认 12，~250ms/单次哈希，兼顾安全与可用性） */
    private static final int BCRYPT_COST = 12;

    /** 老 hash 前缀 */
    private static final String LEGACY_PREFIX = "sha256$";
    private static final String LEGACY_SEPARATOR = "$";

    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordEncoder() {}

    /**
     * 对原始密码做 BCrypt 哈希。返回 {@code $2a$12$...} 形式的 60 字节字符串。
     */
    public static String encode(String rawPassword) {
        if (rawPassword == null) {
            throw new IllegalArgumentException("rawPassword 不能为空");
        }
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt(BCRYPT_COST));
    }

    /**
     * 校验密码：自动识别 BCrypt 与老 sha256 格式。
     *
     * @return true = 密码匹配
     */
    public static boolean matches(String rawPassword, String stored) {
        if (rawPassword == null || stored == null || stored.isEmpty()) {
            return false;
        }
        if (stored.startsWith(LEGACY_PREFIX)) {
            return matchLegacy(rawPassword, stored);
        }
        try {
            return BCrypt.checkpw(rawPassword, stored);
        } catch (Exception e) {
            // 非法 hash 串（旧数据格式异常或被篡改）按"不匹配"处理
            return false;
        }
    }

    /**
     * 是否需要迁移升级（仅当当前是 legacy 格式且密码正确时返回 true）。
     * 调用方在登录成功后用 {@link #encode(String)} 重新生成 BCrypt 并落库。
     */
    public static boolean needsUpgrade(String stored) {
        return stored != null && stored.startsWith(LEGACY_PREFIX);
    }

    /**
     * 校验老格式：{@code sha256$<hex64>$<base64salt>}
     */
    private static boolean matchLegacy(String rawPassword, String stored) {
        // 格式：sha256$<hex64>$<base64salt>
        String[] parts = stored.split("\\$", -1);
        if (parts.length != 3) {
            return false;
        }
        byte[] salt;
        try {
            salt = Base64.getDecoder().decode(parts[2]);
        } catch (IllegalArgumentException e) {
            return false;
        }
        String expected;
        try {
            expected = legacyHash(rawPassword, salt);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
        return constantTimeEquals(expected, parts[1]);
    }

    /**
     * 老格式 hash 实现（保留向后兼容）。
     * <p>
     * 修复点：
     * <ul>
     *   <li>{@code getBytes(StandardCharsets.UTF_8)} 显式指定 UTF-8，不再用平台默认字符集</li>
     *   <li>常量时间比较，避免时序攻击</li>
     * </ul>
     */
    private static String legacyHash(String rawPassword, byte[] salt) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        digest.update(salt);
        byte[] bytes = digest.digest(rawPassword.getBytes(StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null || a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }

    /**
     * 显式生成一个 16-byte salt（仅供迁移脚本或老 hash 构造时使用，生产主流程走 BCrypt）。
     */
    static byte[] generateLegacySalt() {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return salt;
    }
}