package com.example.population.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JwtUtil 单元测试。
 * <p>
 * 覆盖：
 *   - 签发 + 解析（基础字段、permLevel / roleCode / dataScope / permCodes 全部还原）
 *   - 缺字段容错（null permLevel、null roleCode、null dataScope）
 *   - extractPermCodes 对 Collection / 逗号分隔字符串 / null 的兼容
 *   - extractPermLevel 对 Integer / Number / String / null 的兼容
 *   - isValid（合法 / 非法签名 / 过期）
 *   - getExpirationSeconds 直接读配置
 */
class JwtUtilTest {

    private JwtUtil jwtUtil;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "population-miniterm-secret-key-2024-very-long-and-secure");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86_400_000L);
        ReflectionTestUtils.setField(jwtUtil, "refreshExpiration", 604_800_000L);
        jwtUtil.init();
        signingKey = io.jsonwebtoken.security.Keys.hmacShaKeyFor(
                "population-miniterm-secret-key-2024-very-long-and-secure"
                        .getBytes(StandardCharsets.UTF_8));
    }

    // ---------- generate / parse ----------

    @Test
    @DisplayName("签发后能解析出 uid / uname / permLevel / roleCode / dataScope / permCodes")
    void roundTrip_allClaims() {
        Set<String> perms = new HashSet<>();
        perms.add("person:query");
        perms.add("person:create");

        String token = jwtUtil.generate(1L, "alice", "张三", 2,
                "L2_HANDLE", "DEPARTMENT", perms);

        Claims claims = jwtUtil.parse(token);
        assertThat(claims.get("uid", Long.class)).isEqualTo(1L);
        assertThat(claims.get("uname", String.class)).isEqualTo("alice");
        assertThat(claims.get("realName", String.class)).isEqualTo("张三");
        assertThat(jwtUtil.extractPermLevel(claims)).isEqualTo(2);
        assertThat(jwtUtil.extractStringClaim(claims, "roleCode")).isEqualTo("L2_HANDLE");
        assertThat(jwtUtil.extractStringClaim(claims, "dataScope")).isEqualTo("DEPARTMENT");
        assertThat(jwtUtil.extractPermCodes(claims))
                .containsExactlyInAnyOrder("person:query", "person:create");
    }

    @Test
    @DisplayName("可选字段为 null 时不被写入，解析为 null（不抛 NPE）")
    void roundTrip_nullOptionalClaims() {
        String token = jwtUtil.generate(7L, "bob", null, null, null, null, null);

        Claims claims = jwtUtil.parse(token);
        assertThat(claims.get("uid", Long.class)).isEqualTo(7L);
        assertThat(claims.get("uname", String.class)).isEqualTo("bob");
        assertThat(jwtUtil.extractPermLevel(claims)).isNull();
        assertThat(jwtUtil.extractStringClaim(claims, "roleCode")).isNull();
        assertThat(jwtUtil.extractStringClaim(claims, "dataScope")).isNull();
        assertThat(jwtUtil.extractPermCodes(claims)).isEmpty();
    }

    @Test
    @DisplayName("空 permCodes 集合不应该写入 claim")
    void roundTrip_emptyPermCodes() {
        String token = jwtUtil.generate(1L, "alice", null, 1, "L1_QUERY", "SELF",
                Collections.emptySet());

        Claims claims = jwtUtil.parse(token);
        assertThat(jwtUtil.extractPermCodes(claims)).isEmpty();
    }

    // ---------- extractPermLevel ----------

    @Test
    @DisplayName("extractPermLevel: 缺失字段 → null")
    void extractPermLevel_missing() {
        String token = jwtUtil.generate(1L, "alice", null, null, null, null, null);
        Claims claims = jwtUtil.parse(token);

        assertThat(jwtUtil.extractPermLevel(claims)).isNull();
    }

    @Test
    @DisplayName("extractPermLevel: 整数 3 → 3")
    void extractPermLevel_acceptsInteger() {
        String token = jwtUtil.generate(1L, "alice", null, 3, "L3_ADMIN", "ALL", null);
        Claims claims = jwtUtil.parse(token);
        assertThat(jwtUtil.extractPermLevel(claims)).isEqualTo(3);
    }

    // ---------- extractPermCodes ----------

    @Test
    @DisplayName("extractPermCodes: null → 空 Set（不抛 NPE）")
    void extractPermCodes_null() {
        String token = jwtUtil.generate(1L, "alice", null, 1, "L1_QUERY", "SELF", null);
        Claims claims = jwtUtil.parse(token);

        assertThat(jwtUtil.extractPermCodes(claims)).isEmpty();
    }

    @Test
    @DisplayName("extractPermCodes: 字符串以逗号分隔时也能解析（兼容老 token）")
    void extractPermCodes_csvString() {
        // 模拟老 token：把 permCodes 写成 "a,b,c" 字符串（用同样的 key 签）
        String token = Jwts.builder()
                .claim("uid", 1L)
                .claim("uname", "alice")
                .claim("permCodes", "person:query,person:create,user:manage")
                .subject("alice")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(signingKey)
                .compact();

        Claims claims = jwtUtil.parse(token);
        assertThat(jwtUtil.extractPermCodes(claims))
                .containsExactlyInAnyOrder("person:query", "person:create", "user:manage");
    }

    // ---------- isValid ----------

    @Test
    @DisplayName("isValid: 合法 token → true")
    void isValid_ok() {
        String token = jwtUtil.generate(1L, "alice", null, 1, "L1_QUERY", "SELF", null);
        assertThat(jwtUtil.isValid(token)).isTrue();
    }

    @Test
    @DisplayName("isValid: 篡改签名 → false（不抛）")
    void isValid_tamperedSignature() {
        String token = jwtUtil.generate(1L, "alice", null, 1, "L1_QUERY", "SELF", null);
        // 篡改最后一截签名
        String tampered = token.substring(0, token.length() - 4) + "AAAA";
        assertThat(jwtUtil.isValid(tampered)).isFalse();
    }

    @Test
    @DisplayName("isValid: 完全乱码 → false（不抛）")
    void isValid_garbage() {
        assertThat(jwtUtil.isValid("not-a-jwt")).isFalse();
    }

    @Test
    @DisplayName("isValid: null / 空字符串 → false（不抛）")
    void isValid_nullOrEmpty() {
        assertThat(jwtUtil.isValid(null)).isFalse();
        assertThat(jwtUtil.isValid("")).isFalse();
    }

    @Test
    @DisplayName("parse: 过期 token → 抛 ExpiredJwtException")
    void parse_expired() {
        // 用过去的 issuedAt + 1ms expiration，构造一个已过期的 token
        String token = Jwts.builder()
                .claim("uid", 1L)
                .claim("uname", "alice")
                .subject("alice")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(signingKey)
                .compact();

        assertThatThrownBy(() -> jwtUtil.parse(token))
                .isInstanceOf(ExpiredJwtException.class);
    }

    // ---------- getExpirationSeconds ----------

    @Test
    @DisplayName("getExpirationSeconds: 读取配置（毫秒数原值返回）")
    void getExpirationSeconds() {
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3_600_000L);
        assertThat(jwtUtil.getExpirationSeconds()).isEqualTo(3_600_000L);
    }

    // ---------- access / refresh 双令牌类型 ----------

    @Test
    @DisplayName("access token 自动写入 type=access + jti（UUID）")
    void accessToken_hasTypeAndJti() {
        String token = jwtUtil.generate(1L, "alice", null, 2, "L2_HANDLE", "DEPARTMENT", null);
        Claims claims = jwtUtil.parse(token);

        String type = jwtUtil.extractStringClaim(claims, "type");
        String jti = jwtUtil.extractStringClaim(claims, "jti");

        assertThat(type).isEqualTo(JwtUtil.TOKEN_TYPE_ACCESS);
        assertThat(jti).isNotEmpty();
        // UUID 格式：8-4-4-4-12
        assertThat(jti).matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    }

    @Test
    @DisplayName("refresh token: type=refresh，且不含 permLevel / roleCode / dataScope / permCodes")
    void refreshToken_minimalClaims() {
        String token = jwtUtil.generateRefresh(7L, "bob");
        Claims claims = jwtUtil.parse(token);

        assertThat(jwtUtil.extractStringClaim(claims, "type")).isEqualTo(JwtUtil.TOKEN_TYPE_REFRESH);
        assertThat(jwtUtil.extractPermLevel(claims)).isNull();
        assertThat(jwtUtil.extractStringClaim(claims, "roleCode")).isNull();
        assertThat(jwtUtil.extractStringClaim(claims, "dataScope")).isNull();
        assertThat(jwtUtil.extractPermCodes(claims)).isEmpty();
        assertThat(jwtUtil.extractStringClaim(claims, "jti")).isNotEmpty();
    }

    @Test
    @DisplayName("refresh token 寿命比 access token 长（7d vs 30min 默认）")
    void refreshToken_longerLifetime() {
        long refreshTtl = jwtUtil.getRefreshExpirationSeconds();
        long accessTtl = jwtUtil.getExpirationSeconds();
        assertThat(refreshTtl).isGreaterThan(accessTtl);
        // 默认 7d = 604800000 ms
        assertThat(refreshTtl).isEqualTo(604_800_000L);
    }
}