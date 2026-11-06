package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.auth.AuthService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.RedisStringCommands.SetOption;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/** Real HTTP security chain and real strict Redis adapter, with no external Redis or database. */
@SpringBootTest(properties = {
        "security.jwt.revocation.mode=SHARED_REDIS_REQUIRED",
        "population.redis.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:s5a1-strict-http-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc
@Transactional
class S5A1StrictRevocationHttpIntegrationTest {
    private static final long PERSON_ID = 995501L;
    private static final String ID_CARD = "11010519491231002X";
    private static final String PRIVATE_CAUSE = "redis://private-host:6379 password=do-not-expose";
    private static final String PROBE_PREFIX = "population:security:jwt:login-probe:";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private AuthService authService;
    @Autowired private JwtService jwt;
    @MockBean private StringRedisTemplate redis;

    private RedisConnection connection;
    private RedisStringCommands strings;
    private Set<String> markers;
    private Map<String, Integer> previousSuccessLogs;

    @BeforeEach
    void setUpIsolatedStore() {
        SecurityContextHolder.clearContext();
        markers = new HashSet<>();
        previousSuccessLogs = new HashMap<>();
        for (String operation : new String[]{"LOGIN_SUCCESS", "LOGOUT", "PERSON_UPDATE"}) {
            previousSuccessLogs.put(operation, countSuccessLogs(operation));
        }
        connection = mock(RedisConnection.class);
        strings = mock(RedisStringCommands.class);
        when(redis.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(redis.hasKey(anyString())).thenAnswer(call -> markers.contains(call.getArgument(0)));
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any()))
                .thenAnswer(call -> call.<RedisCallback<Boolean>>getArgument(0).doInRedis(connection));
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenAnswer(call -> {
                    markers.add(new String(call.getArgument(0, byte[].class), StandardCharsets.UTF_8));
                    return true;
                });
        jdbc.update("""
                INSERT INTO person(person_id,name,gender,id_card,birth_date,phone,current_address,status,
                                   initial_scope_user_id,initial_scope_department_id,initial_scope_region_code)
                VALUES (?, '严格模式测试人员', '女', ?, DATE '1949-12-31', '13800138000',
                        '北京市朝阳区敏感详细地址', '正常', 3, 3, '110105')
                """, PERSON_ID, ID_CARD);
    }

    @AfterEach
    void requestContextIsAlwaysCleared() {
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void confirmedAbsentMarkerAllowsRealSensitiveRead() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/persons/{id}", PERSON_ID).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.idCard").value(ID_CARD))
                .andExpect(jsonPath("$.data.phone").value("13800138000"));
        verify(redis).hasKey(blacklistKey(token));
    }

    @Test
    void confirmedRevokedMarkerReturns401BeforeSensitiveRead() throws Exception {
        String token = adminToken();
        markers.add(blacklistKey(token));
        mockMvc.perform(get("/api/persons/{id}", PERSON_ID).header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void revokedTokenCannotModifyBusinessData() throws Exception {
        String token = adminToken();
        var before = jdbc.queryForMap("SELECT * FROM person WHERE person_id = ?", PERSON_ID);
        markers.add(blacklistKey(token));
        mockMvc.perform(personUpdate(token)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForMap("SELECT * FROM person WHERE person_id = ?", PERSON_ID)).isEqualTo(before);
        assertThat(successLogs("PERSON_UPDATE")).isZero();
    }

    @Test
    void logoutWithUnavailableReadNeverAttemptsWriteOrRecordsSuccess() throws Exception {
        String token = adminToken();
        when(redis.hasKey(blacklistKey(token))).thenThrow(storeFailure("CONNECTION"));
        assertUnavailable(mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token))));
        verify(strings, never()).set(any(byte[].class), any(byte[].class), any(Expiration.class), any(SetOption.class));
        assertThat(successLogs("LOGOUT")).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONNECTION", "TIMEOUT"})
    void queryConnectionFailureAndTimeoutReturn503WithoutCauseLeak(String category) throws Exception {
        String token = adminToken();
        when(redis.hasKey(blacklistKey(token))).thenThrow(storeFailure(category));
        assertUnavailable(mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token))));
    }

    @Test
    void unconfirmedReadIs503RatherThanConfirmedAbsence() throws Exception {
        String token = adminToken();
        when(redis.hasKey(blacklistKey(token))).thenReturn(null);
        assertUnavailable(mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token))));
    }

    @Test
    void logoutWriteFailureDoesNotWriteLogoutSuccessOrLocallyRevokeToken() throws Exception {
        String token = adminToken();
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenThrow(storeFailure("CONNECTION"));
        assertUnavailable(mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token))));
        assertThat(successLogs("LOGOUT")).isZero();
        assertThat(markers).doesNotContain(blacklistKey(token));
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void unconfirmedLogoutWriteDoesNotReturnSuccess() throws Exception {
        String token = adminToken();
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenReturn(null);
        assertUnavailable(mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token))));
        assertThat(successLogs("LOGOUT")).isZero();
    }

    @Test
    void successfulLogoutWritesSharedMarkerAndOldTokenIsRejectedOnEveryRequest() throws Exception {
        String token = adminToken();
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(successLogs("LOGOUT")).isEqualTo(1);
        assertThat(markers).contains(blacklistKey(token));
        for (int request = 0; request < 2; request++) {
            mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
        }
        verify(redis, times(3)).hasKey(blacklistKey(token));
    }

    @Test
    void storeFailureBlocksSensitiveReadAndValidWriteWithoutChangingDatabase() throws Exception {
        String token = adminToken();
        var before = jdbc.queryForMap("SELECT * FROM person WHERE person_id = ?", PERSON_ID);
        when(redis.hasKey(blacklistKey(token))).thenThrow(storeFailure("TIMEOUT"));
        assertUnavailable(mockMvc.perform(get("/api/persons/{id}", PERSON_ID)
                .header("Authorization", bearer(token))));
        assertUnavailable(mockMvc.perform(personUpdate(token)));
        assertThat(jdbc.queryForMap("SELECT * FROM person WHERE person_id = ?", PERSON_ID)).isEqualTo(before);
        assertThat(successLogs("PERSON_UPDATE")).isZero();

        doReturn(false).when(redis).hasKey(blacklistKey(token));
        mockMvc.perform(personUpdate(token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("恢复后的姓名"));
        assertThat(successLogs("PERSON_UPDATE")).isEqualTo(1);
    }

    @Test
    void recoveryRequiresFreshAuthenticationAndFailureDoesNotLeakContext() throws Exception {
        String token = adminToken();
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
        assertThat(CurrentUserContext.getUserOrNull()).isNull();
        when(redis.hasKey(blacklistKey(token))).thenThrow(storeFailure("CONNECTION"));
        assertUnavailable(mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token))));
        assertThat(CurrentUserContext.getUserOrNull()).isNull();
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        doReturn(false).when(redis).hasKey(blacklistKey(token));
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    void missingAndInvalidTokensRemain401WithoutRedisAccess() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(redis);
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONNECTION", "TIMEOUT"})
    void loginWriteFailureIssuesNoTokenAndHasNoLoginSuccessSideEffects(String category) throws Exception {
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenThrow(storeFailure(category));
        assertUnavailable(mockMvc.perform(login("admin", "123456")));
        assertNoSuccessfulLogin();
        verify(redis, never()).hasKey(anyString());
    }

    @Test
    void unconfirmedLoginWriteIssuesNoTokenOrLoginSuccess() throws Exception {
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenReturn(false);
        assertUnavailable(mockMvc.perform(login("admin", "123456")));
        assertNoSuccessfulLogin();
        verify(redis, never()).hasKey(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"CONNECTION", "TIMEOUT"})
    void loginReadFailureIssuesNoTokenAndHasNoLoginSuccessSideEffects(String category) throws Exception {
        when(redis.hasKey(anyString())).thenThrow(storeFailure(category));
        assertUnavailable(mockMvc.perform(login("admin", "123456")));
        assertNoSuccessfulLogin();
    }

    @Test
    void loginProbeMustBeReadBackAsPresent() throws Exception {
        when(redis.hasKey(anyString())).thenReturn(false);
        assertUnavailable(mockMvc.perform(login("admin", "123456")));
        assertNoSuccessfulLogin();
    }

    @Test
    void successfulLoginUsesUniqueFiveSecondProbeBeforeIssuingToken() throws Exception {
        long before = Instant.now().toEpochMilli();
        String token = loginToken();
        long after = Instant.now().toEpochMilli();
        var key = ArgumentCaptor.forClass(byte[].class);
        var expiry = ArgumentCaptor.forClass(Expiration.class);
        verify(strings).set(key.capture(), any(byte[].class), expiry.capture(), eq(SetOption.UPSERT));
        String probeKey = new String(key.getValue(), StandardCharsets.UTF_8);
        assertThat(probeKey).startsWith(PROBE_PREFIX);
        assertThat(UUID.fromString(probeKey.substring(PROBE_PREFIX.length()))).isNotNull();
        assertThat(expiry.getValue().isUnixTimestamp()).isTrue();
        assertThat(expiry.getValue().getExpirationTimeInMilliseconds()).isBetween(before + 5000, after + 5000);
        verify(redis).hasKey(probeKey);
        assertThat(jwt.parseClaims(token).getId()).isNotBlank();
        assertThat(jdbc.queryForObject("SELECT last_login_time FROM sys_user WHERE username='admin'", Object.class))
                .isNotNull();
        assertThat(successLogs("LOGIN_SUCCESS")).isEqualTo(1);
    }

    @Test
    void invalidCredentialsAndDisabledAccountOrRoleTakePrecedenceOverRedisOutage() throws Exception {
        when(redis.hasKey(anyString())).thenThrow(storeFailure("CONNECTION"));
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenThrow(storeFailure("CONNECTION"));
        mockMvc.perform(login("admin", "wrong")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("missing", "123456")).andExpect(status().isUnauthorized());
        mockMvc.perform(login("disabled", "123456")).andExpect(status().isForbidden());
        mockMvc.perform(login("disabled-role", "123456")).andExpect(status().isForbidden());
        verifyNoInteractions(redis);
        assertNoSuccessfulLogin();
    }

    @Test
    void publicHealthWithoutBearerRemainsAvailableWhenRedisIsDown() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.redisEnabled").value(true))
                .andExpect(jsonPath("$.data.redisStatus").value("DOWN"));
        verify(redis, never()).hasKey(anyString());
        verify(redis, never()).execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any());
    }

    @Test
    void loginStillIgnoresInvalidAuthorizationHeaderAndRunsOwnPreflight() throws Exception {
        mockMvc.perform(login("admin", "123456").header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.token").isNotEmpty());
        assertThat(successLogs("LOGIN_SUCCESS")).isEqualTo(1);
        var key = ArgumentCaptor.forClass(String.class);
        verify(redis).hasKey(key.capture());
        assertThat(key.getValue()).startsWith(PROBE_PREFIX);
    }

    private void assertUnavailable(ResultActions result) throws Exception {
        String body = result.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")))
                .andExpect(jsonPath("$.data").doesNotExist())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(PRIVATE_CAUSE, "private-host", "do-not-expose", "RedisConnectionFailureException");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private void assertNoSuccessfulLogin() {
        var row = jdbc.queryForMap("SELECT last_login_time,last_login_ip FROM sys_user WHERE username='admin'");
        assertThat(row.values()).containsOnlyNulls();
        assertThat(successLogs("LOGIN_SUCCESS")).isZero();
    }

    private int successLogs(String operation) {
        return countSuccessLogs(operation) - previousSuccessLogs.getOrDefault(operation, 0);
    }

    private int countSuccessLogs(String operation) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type=? AND operation_result='SUCCESS'",
                Integer.class, operation);
    }

    private RuntimeException storeFailure(String category) {
        return "TIMEOUT".equals(category) ? new QueryTimeoutException(PRIVATE_CAUSE)
                : new RedisConnectionFailureException(PRIVATE_CAUSE);
    }

    private String adminToken() {
        return jwt.createToken(authService.loadUser("admin"));
    }

    private String loginToken() throws Exception {
        String body = mockMvc.perform(login("admin", "123456")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private String blacklistKey(String token) {
        return "population:security:jwt:blacklist:" + jwt.parseClaims(token).getId();
    }

    private String bearer(String token) { return "Bearer " + token; }

    private MockHttpServletRequestBuilder login(String username, String password) {
        return post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}");
    }

    private MockHttpServletRequestBuilder personUpdate(String token) {
        return put("/api/persons/{id}", PERSON_ID).header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"恢复后的姓名\",\"gender\":\"女\",\"idCard\":\"" + ID_CARD + "\"}");
    }
}
