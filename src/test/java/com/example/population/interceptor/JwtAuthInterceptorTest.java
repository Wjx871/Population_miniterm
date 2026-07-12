package com.example.population.interceptor;

import com.example.population.util.JwtUtil;
import com.example.population.util.PermissionCache;
import com.example.population.util.SecurityContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * JwtAuthInterceptor 单元测试（不启动 Spring 容器，纯手动 new + Mockito）。
 * <p>
 * 覆盖：
 *   - OPTIONS 预检直接放行
 *   - 缺失 Authorization → 401
 *   - "Bearer xxx" 前缀自动剥离
 *   - 非法 token → 401
 *   - token 缺少关键字段（uid / permLevel / roleCode / dataScope）→ 401
 *   - 合法 token → 通过；SecurityContext / request attribute 被填充
 *   - permCodes 为空时回退到 PermissionCache
 *   - afterCompletion 清理 ThreadLocal（防线程复用串号）
 */
class JwtAuthInterceptorTest {

    private JwtUtil jwtUtil;
    private PermissionCache permissionCache;
    private JwtAuthInterceptor interceptor;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret",
                "population-miniterm-secret-key-2024-very-long-and-secure");
        ReflectionTestUtils.setField(jwtUtil, "expiration", 86_400_000L);
        jwtUtil.init();

        permissionCache = mock(PermissionCache.class);

        interceptor = new JwtAuthInterceptor(jwtUtil, permissionCache);

        // 清理 ThreadLocal，避免用例间污染
        SecurityContext.clear();
    }

    @AfterEach
    void tearDown() {
        SecurityContext.clear();
    }

    private String genToken(Long uid, String username, Integer permLevel,
                            String roleCode, String dataScope, Set<String> perms) {
        return jwtUtil.generate(uid, username, "张三", permLevel, roleCode, dataScope, perms);
    }

    // ---------- 入口放行 / 拦截 ----------

    @Test
    @DisplayName("OPTIONS 预检：直接放行（不查 token）")
    void preHandle_optionsPassThrough() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("OPTIONS", "/api/persons");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isTrue();
        assertThat(resp.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    @DisplayName("缺失 Authorization 头 → 401 + JSON 错误体")
    void preHandle_missingAuthHeader() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isFalse();
        assertThat(resp.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(resp.getContentAsString()).contains("\"code\":401").contains("未登录");
    }

    @Test
    @DisplayName("非法 token（乱码）→ 401")
    void preHandle_invalidToken() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer not-a-jwt");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isFalse();
        assertThat(resp.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(resp.getContentAsString()).contains("无效或已过期");
    }

    @Test
    @DisplayName("Authorization 不带 Bearer 前缀也能识别（仅当 token 本身合法）")
    void preHandle_bareToken() throws Exception {
        String token = genToken(1L, "alice", 1, "L1_QUERY", "SELF",
                new HashSet<>(Set.of("person:query")));

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", token);  // 没有 Bearer
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isTrue();
    }

    // ---------- 关键字段缺失 ----------

    @Test
    @DisplayName("token 缺 permLevel → 401（旧 token 强制重登）")
    void preHandle_missingPermLevel() throws Exception {
        String token = genToken(1L, "alice", null, "L1_QUERY", "SELF", null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isFalse();
        assertThat(resp.getContentAsString()).contains("格式过旧");
    }

    @Test
    @DisplayName("token 缺 roleCode → 401")
    void preHandle_missingRoleCode() throws Exception {
        String token = genToken(1L, "alice", 1, null, "SELF", null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isFalse();
        assertThat(resp.getContentAsString()).contains("格式过旧");
    }

    @Test
    @DisplayName("token 缺 dataScope → 401")
    void preHandle_missingDataScope() throws Exception {
        String token = genToken(1L, "alice", 1, "L1_QUERY", null, null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isFalse();
    }

    // ---------- 成功路径 ----------

    @Test
    @DisplayName("合法 token：SecurityContext 被写入；request attribute 也被写入")
    void preHandle_ok() throws Exception {
        Set<String> perms = new HashSet<>();
        perms.add("person:query");
        perms.add("person:create");
        String token = genToken(42L, "alice", 2, "L2_HANDLE", "DEPARTMENT", perms);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isTrue();
        SecurityContext ctx = SecurityContext.current();
        assertThat(ctx).isNotNull();
        assertThat(ctx.getUserId()).isEqualTo(42L);
        assertThat(ctx.getUsername()).isEqualTo("alice");
        assertThat(ctx.getPermissionLevel()).isEqualTo(2);
        assertThat(ctx.getRoleCode()).isEqualTo("L2_HANDLE");
        assertThat(ctx.getDataScopeCode()).isEqualTo("DEPARTMENT");
        assertThat(ctx.getPermissionCodes()).containsExactlyInAnyOrder("person:query", "person:create");

        assertThat(req.getAttribute(JwtAuthInterceptor.ATTR_USER_ID)).isEqualTo(42L);
        assertThat(req.getAttribute(JwtAuthInterceptor.ATTR_USERNAME)).isEqualTo("alice");
        assertThat(req.getAttribute(JwtAuthInterceptor.ATTR_SECURITY_CONTEXT)).isSameAs(ctx);
    }

    @Test
    @DisplayName("token 内 permCodes 为空 → 回退到 PermissionCache")
    void preHandle_permCodesFallbackToCache() throws Exception {
        Set<String> cached = new HashSet<>();
        cached.add("user:manage");
        cached.add("user:audit");
        when(permissionCache.get(anyLong())).thenReturn(cached);

        String token = genToken(1L, "alice", 1, "L1_QUERY", "SELF", null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isTrue();
        assertThat(SecurityContext.current().getPermissionCodes())
                .containsExactlyInAnyOrder("user:manage", "user:audit");
    }

    @Test
    @DisplayName("token 内 permCodes 为空且 Cache 也无 → 最终为 empty（仍放行，等切面拦截）")
    void preHandle_permCodesEmptyEverywhere() throws Exception {
        when(permissionCache.get(anyLong())).thenReturn(null);

        String token = genToken(1L, "alice", 1, "L1_QUERY", "SELF", null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isTrue();
        assertThat(SecurityContext.current().getPermissionCodes()).isEmpty();
    }

    // ---------- ThreadLocal 清理 ----------

    @Test
    @DisplayName("afterCompletion: SecurityContext 必须清理（防线程复用串号）")
    void afterCompletion_clearsThreadLocal() throws Exception {
        String token = genToken(1L, "alice", 1, "L1_QUERY", "SELF", null);
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + token);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        interceptor.preHandle(req, resp, new Object());
        assertThat(SecurityContext.current()).isNotNull();

        interceptor.afterCompletion(req, resp, new Object(), null);
        assertThat(SecurityContext.current()).isNull();
    }

    @Test
    @DisplayName("writeUnauthorized: 设置 HTTP 401 + JSON 响应体（Content-Type 正确）")
    void writeUnauthorized_format() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        assertThat(resp.getContentType()).contains("application/json");
        assertThat(resp.getContentAsString()).startsWith("{").endsWith("}");
    }

    // ---------- 兜底 ----------

    @Test
    @DisplayName("parse 抛任何异常（不只是 Expired）→ 401 令牌解析失败")
    void preHandle_parseThrowsOther() throws Exception {
        // 用一个不同密钥签的 token → JwtUtil.parse 抛 SignatureException
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret",
                "another-secret-key-also-long-enough-for-hmac");
        ReflectionTestUtils.setField(other, "expiration", 86_400_000L);
        other.init();
        String foreignToken = other.generate(1L, "alice", null, 1, "L1_QUERY", "SELF", null);

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/persons");
        req.addHeader("Authorization", "Bearer " + foreignToken);
        MockHttpServletResponse resp = new MockHttpServletResponse();

        boolean ok = interceptor.preHandle(req, resp, new Object());

        assertThat(ok).isFalse();
        assertThat(resp.getStatus()).isEqualTo(401);
        // 实际可能落到"无效或已过期"或"解析失败"任一支线，断言 code=401 即可
        assertThat(resp.getContentAsString()).contains("\"code\":401");
    }
}