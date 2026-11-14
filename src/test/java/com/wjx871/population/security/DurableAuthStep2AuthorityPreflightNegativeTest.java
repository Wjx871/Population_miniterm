package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.cache.OptionalRedisService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Security review negative and contract tests for Issue #64 Step 2 authority preflight:
 * proves startup, login preflight, post-issuance check, /api/health, and logout pre-commit
 * all verify BOTH jwt_revocation_checkpoint and jwt_revocation via real read-only SQL probes.
 */
@SpringBootTest(properties = {
        "security.jwt.revocation.mode=DURABLE_DB_REQUIRED",
        "security.jwt.revocation.allow-zero-cutoff-for-test=true",
        "population.redis.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:step2-authority-preflight-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
class DurableAuthStep2AuthorityPreflightNegativeTest {

    private static final long TRUSTED_NON_ZERO_CUTOFF_MS = 1_000_000_000_000L;

    private static final AtomicBoolean HIDE_REVOCATION_ON_SECOND_AUTH_QUERY = new AtomicBoolean(false);
    private static final AtomicBoolean CORRUPT_CHECKPOINT_ON_SECOND_AUTH_QUERY = new AtomicBoolean(false);
    private static final AtomicInteger AUTH_QUERY_COUNT = new AtomicInteger(0);

    @TestConfiguration
    static class PreflightFaultHooksConfig {
        @Bean
        static BeanPostProcessor authQueryFaultHook(DataSource dataSource) {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (!(bean instanceof JwtRevocationMapper target)) {
                        return bean;
                    }
                    return Proxy.newProxyInstance(
                            JwtRevocationMapper.class.getClassLoader(),
                            new Class<?>[] {JwtRevocationMapper.class},
                            (proxy, method, args) -> {
                                if ("selectAuthoritativeState".equals(method.getName())) {
                                    int call = AUTH_QUERY_COUNT.incrementAndGet();
                                    if (call == 2 && HIDE_REVOCATION_ON_SECOND_AUTH_QUERY.get()) {
                                        executeSql(dataSource, "ALTER TABLE jwt_revocation RENAME TO jwt_revocation_hidden");
                                    }
                                    if (call == 2 && CORRUPT_CHECKPOINT_ON_SECOND_AUTH_QUERY.get()) {
                                        executeSql(dataSource,
                                                "UPDATE jwt_revocation_checkpoint "
                                                        + "SET checkpoint_status = 'UNINITIALIZED', min_valid_iat_epoch_ms = NULL "
                                                        + "WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF'");
                                    }
                                }
                                try {
                                    return method.invoke(target, args);
                                } catch (InvocationTargetException ex) {
                                    throw ex.getTargetException();
                                }
                            });
                }
            };
        }

        private static void executeSql(DataSource dataSource, String sql) {
            try (Connection conn = dataSource.getConnection();
                    Statement st = conn.createStatement()) {
                st.execute(sql);
            } catch (Exception ex) {
                throw new IllegalStateException("Hook SQL failed: " + sql, ex);
            }
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DurableRevocationStore durableStore;
    @Autowired private TokenRevocationService tokenRevocationService;
    @Autowired private RevocationProperties revocationProperties;
    @Autowired private Clock clock;
    @MockBean private StringRedisTemplate redis;

    private Set<String> redisKeys;
    private volatile RuntimeException redisFailure;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        HIDE_REVOCATION_ON_SECOND_AUTH_QUERY.set(false);
        CORRUPT_CHECKPOINT_ON_SECOND_AUTH_QUERY.set(false);
        AUTH_QUERY_COUNT.set(0);
        restoreRevocationTableIfHidden();
        revocationProperties.setAllowZeroCutoffForTest(false);
        jdbc.update("DELETE FROM jwt_revocation");
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("UPDATE sys_user SET last_login_time = NULL, last_login_ip = NULL");
        resetCheckpointReady(TRUSTED_NON_ZERO_CUTOFF_MS);

        redisKeys = ConcurrentHashMap.newKeySet();
        redisFailure = null;
        mockRedis();
    }

    @AfterEach
    void tearDown() {
        HIDE_REVOCATION_ON_SECOND_AUTH_QUERY.set(false);
        CORRUPT_CHECKPOINT_ON_SECOND_AUTH_QUERY.set(false);
        AUTH_QUERY_COUNT.set(0);
        restoreRevocationTableIfHidden();
        revocationProperties.setAllowZeroCutoffForTest(false);
        resetCheckpointReady(TRUSTED_NON_ZERO_CUTOFF_MS);
        SecurityContextHolder.clearContext();
    }

    @Test
    void probeContract_readOnlyNoRedisNoWriteAndCollisionTolerant() {
        // 1. Healthy DB with READY non-zero checkpoint: probe succeeds, writes nothing, never uses Redis
        OptionalRedisService unusedRedis = mock(OptionalRedisService.class);
        RevocationProperties props = new RevocationProperties();
        props.setMode(RevocationProperties.Mode.DURABLE_DB_REQUIRED);
        TokenRevocationService isolatedSvc = new TokenRevocationService(
                unusedRedis, props, clock, new RevocationEvents(), new MockEnvironment(), durableStore);

        isolatedSvc.validateConfiguration();
        CheckpointSnapshot probed = durableStore.probeAuthoritativeStore();
        assertThat(probed.isReady()).isTrue();
        assertThat(probed.minValidIatEpochMs()).isEqualTo(TRUSTED_NON_ZERO_CUTOFF_MS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isZero();
        verifyNoInteractions(unusedRedis);

        // 2. Collision edge case: if probe digest already exists in jwt_revocation, store is STILL healthy
        String probeDigest = "a".repeat(64);
        long futureExp = clock.millis() + 60_000L;
        jdbc.update("""
                INSERT INTO jwt_revocation (
                    jti_sha256, user_id, expires_at_epoch_ms, token_expires_at, revoked_at, revocation_reason, created_at
                ) VALUES (?, 1, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'LOGOUT', CURRENT_TIMESTAMP)
                """, probeDigest, futureExp);
        CheckpointSnapshot collisionSnapshot = durableStore.probeAuthoritativeStore(probeDigest);
        assertThat(collisionSnapshot.isReady()).isTrue();
        assertThat(collisionSnapshot.minValidIatEpochMs()).isEqualTo(TRUSTED_NON_ZERO_CUTOFF_MS);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isEqualTo(1);
    }

    @Test
    void realSqlFault_readyCheckpointWithUnreadableJwtRevocationFailsAllTenChecklistScenariosAndRecovers()
            throws Exception {
        // Pre-issue one valid JWT and one revoked-with-Redis-hit JWT while store is healthy
        String validToken = loginToken("admin", "123456");
        String revokedToken = loginToken("admin", "123456");
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + revokedToken))
                .andExpect(status().isOk());
        jdbc.update("DELETE FROM operation_log");
        jdbc.update("UPDATE sys_user SET last_login_time = NULL, last_login_ip = NULL");

        // Break ONLY jwt_revocation while jwt_revocation_checkpoint remains READY + valid non-zero cutoff
        jdbc.execute("ALTER TABLE jwt_revocation RENAME TO jwt_revocation_hidden");
        try {
            // (1) Startup fails fast, without repairing tables or touching checkpoint
            assertThatThrownBy(() -> tokenRevocationService.validateConfiguration())
                    .isInstanceOf(IllegalStateException.class);
            assertThat(jdbc.queryForObject(
                    "SELECT checkpoint_status FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                    String.class, DurableRevocationStore.GLOBAL_CHECKPOINT_KEY)).isEqualTo("READY");

            // (2) Valid user login fails closed 503 with zero token, zero LOGIN_SUCCESS, null last_login_time/ip
            String validLoginBody = mockMvc.perform(login("admin", "123456"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503))
                    .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(validLoginBody).doesNotContain("eyJ", "\"token\":\"", "jwt_revocation_hidden", "jdbc:");
            assertNoSuccessfulLoginForAdmin();

            // (3) Invalid password login still returns 401
            mockMvc.perform(login("admin", "wrong")).andExpect(status().isUnauthorized());

            // (4) Non-existent user login still returns 401
            mockMvc.perform(login("missing", "123456")).andExpect(status().isUnauthorized());

            // (5) Disabled user and disabled role still return 403
            mockMvc.perform(login("disabled", "123456")).andExpect(status().isForbidden());
            mockMvc.perform(login("disabled-role", "123456")).andExpect(status().isForbidden());

            // (6) GET /api/health returns 200, database=UP, NEVER DB_PRIMARY_UP, reports DB_REVOCATION_UNAVAILABLE
            String healthBody = mockMvc.perform(get("/api/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.database").value("UP"))
                    .andExpect(jsonPath("$.data.revocationMode").value("DURABLE_DB_REQUIRED"))
                    .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_REVOCATION_UNAVAILABLE"))
                    .andReturn().getResponse().getContentAsString();
            assertThat(healthBody).doesNotContain("DB_PRIMARY_UP", "SQL", "jdbc:", "password");

            // (7) Already-issued valid JWT on /api/auth/me (Redis miss + down) fails closed 503
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503));
            redisFailure = new RedisConnectionFailureException("simulated-redis-down");
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503));
            redisFailure = null;

            // (8) Revoked JWT with Redis cache hit STILL returns 401 (401 > 503 precedence)
            mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + revokedToken))
                    .andExpect(status().isUnauthorized());

            // (9) Logout when jwt_revocation fails before commit -> 503, zero LOGOUT SUCCESS
            mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503));
            assertThat(logoutSuccessCount()).isZero();
        } finally {
            restoreRevocationTableIfHidden();
        }

        // (10) Recovery after restoring jwt_revocation: health, login, /me, logout, 401 all succeed
        tokenRevocationService.validateConfiguration();
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_PRIMARY_UP"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());
        String recoveredToken = loginToken("admin", "123456");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + recoveredToken))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + recoveredToken))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + recoveredToken))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void realSqlFault_issuanceWindowJwtRevocationFailureFailsClosed503WithZeroJwtAndSideEffects()
            throws Exception {
        // Call #1 (requireLoginStore) succeeds with healthy table; right before call #2
        // (verifyIssuedTokenNotSuperseded), jwt_revocation becomes missing -> real SQL failure -> 503
        AUTH_QUERY_COUNT.set(0);
        HIDE_REVOCATION_ON_SECOND_AUTH_QUERY.set(true);
        try {
            String body = mockMvc.perform(login("admin", "123456"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503))
                    .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")))
                    .andReturn().getResponse().getContentAsString();
            assertThat(body).doesNotContain("eyJ", "\"token\":\"");
            assertNoSuccessfulLoginForAdmin();
        } finally {
            HIDE_REVOCATION_ON_SECOND_AUTH_QUERY.set(false);
            restoreRevocationTableIfHidden();
        }
    }

    @Test
    void logoutPreCommitProbe_checkpointCorruptedAfterFilterAuthFailsClosed503WithNoCommit()
            throws Exception {
        String token = loginToken("admin", "123456");
        jdbc.update("DELETE FROM operation_log");

        // Call #1 (JwtAuthenticationFilter) sees READY; right before call #2 (revokeDurable pre-commit probe),
        // checkpoint flips UNINITIALIZED -> 503 before saveRevocation()
        AUTH_QUERY_COUNT.set(0);
        CORRUPT_CHECKPOINT_ON_SECOND_AUTH_QUERY.set(true);
        try {
            mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.code").value(503));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation", Integer.class)).isZero();
            assertThat(logoutSuccessCount()).isZero();
        } finally {
            CORRUPT_CHECKPOINT_ON_SECOND_AUTH_QUERY.set(false);
            resetCheckpointReady(TRUSTED_NON_ZERO_CUTOFF_MS);
        }

        // Token remains valid once checkpoint is restored because pre-commit probe blocked revocation write
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void mockRedis() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);

        when(redis.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenAnswer(call -> {
            if (redisFailure != null) {
                throw redisFailure;
            }
            return connection;
        });
        when(connection.ping()).thenAnswer(call -> {
            if (redisFailure != null) {
                throw redisFailure;
            }
            return "PONG";
        });
        when(redis.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any()))
                .thenAnswer(call -> call.<RedisCallback<Boolean>>getArgument(0).doInRedis(connection));
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenAnswer(call -> {
                    if (redisFailure != null) {
                        throw redisFailure;
                    }
                    redisKeys.add(new String(call.getArgument(0, byte[].class), StandardCharsets.UTF_8));
                    return true;
                });
        when(redis.hasKey(anyString())).thenAnswer(call -> {
            if (redisFailure != null) {
                throw redisFailure;
            }
            return redisKeys.contains(call.getArgument(0, String.class));
        });
    }

    private void restoreRevocationTableIfHidden() {
        Integer hiddenCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                        + "WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = 'jwt_revocation_hidden'",
                Integer.class);
        if (hiddenCount != null && hiddenCount > 0) {
            Integer activeCount = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES "
                            + "WHERE TABLE_SCHEMA = SCHEMA() AND LOWER(TABLE_NAME) = 'jwt_revocation'",
                    Integer.class);
            if (activeCount == null || activeCount == 0) {
                jdbc.execute("ALTER TABLE jwt_revocation_hidden RENAME TO jwt_revocation");
            } else {
                jdbc.execute("DROP TABLE jwt_revocation_hidden");
            }
        }
    }

    private void resetCheckpointReady(long cutoffMs) {
        jdbc.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbc.update("INSERT INTO jwt_revocation_checkpoint "
                        + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                        + "VALUES (?, 'READY', ?, CURRENT_TIMESTAMP, 'TEST_RESET')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY, cutoffMs);
    }

    private void assertNoSuccessfulLoginForAdmin() {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT last_login_time, last_login_ip FROM sys_user WHERE username = 'admin'");
        assertThat(row.values()).containsOnlyNulls();
        assertThat(loginSuccessCount()).isZero();
    }

    private int loginSuccessCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGIN_SUCCESS' AND operation_result = 'SUCCESS'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private int logoutSuccessCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGOUT' AND operation_result = 'SUCCESS'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private String loginToken(String username, String password) throws Exception {
        String body = mockMvc.perform(login(username, password))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder login(
            String username, String password) {
        return post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }
}
