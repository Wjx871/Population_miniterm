package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.cache.OptionalRedisService;
import com.wjx871.population.cache.RedisProperties;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
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
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Opt-in executable RED gate for #64 (JWT revocation durability across Redis empty recovery).
 *
 * <p>Run explicitly on baseline:
 * {@code .\mvnw.cmd test -Dissue64.red=true -Dtest=Issue64DurabilityRedGateTest}
 *
 * <p>Asserts the target security contract (INV-01..INV-05): once a JWT is revoked, wiping Redis
 * to an empty store must NOT resurrect the unexpired JWT as HTTP 200. Expected to fail (RED) on
 * the unmodified baseline {@code c74867721adea57ad16012b4785b4f952b92fe86}.
 */
@EnabledIfSystemProperty(named = "issue64.red", matches = "true")
@SpringBootTest(properties = {
        "security.jwt.revocation.mode=SHARED_REDIS_REQUIRED",
        "population.redis.enabled=true",
        "spring.datasource.url=jdbc:h2:mem:issue64-red-${random.uuid};MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE"
})
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Transactional
class Issue64DurabilityRedGateTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JwtService jwt;
    @Autowired private RevocationProperties revocationProperties;
    @Autowired private RedisProperties redisProperties;
    @Autowired private java.time.Clock clock;
    @Autowired private RevocationEvents events;
    @MockBean private StringRedisTemplate redis;

    private Set<String> markers;
    private volatile boolean storeUnavailable;
    private TokenRevocationService instanceB;

    @BeforeEach
    void setUpResettableSharedStore() {
        SecurityContextHolder.clearContext();
        markers = ConcurrentHashMap.newKeySet();
        storeUnavailable = false;

        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        RedisStringCommands strings = mock(RedisStringCommands.class);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(redis.getConnectionFactory()).thenReturn(connectionFactory);
        when(connection.ping()).thenAnswer(call -> {
            if (storeUnavailable) {
                throw new RedisConnectionFailureException("simulated-redis-down");
            }
            return "PONG";
        });
        when(redis.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(redis.hasKey(anyString())).thenAnswer(call -> {
            if (storeUnavailable) {
                throw new RedisConnectionFailureException("simulated-redis-down");
            }
            return markers.contains(call.getArgument(0, String.class));
        });
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any()))
                .thenAnswer(call -> call.<RedisCallback<Boolean>>getArgument(0).doInRedis(connection));
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenAnswer(call -> {
                    if (storeUnavailable) {
                        throw new RedisConnectionFailureException("simulated-redis-down");
                    }
                    markers.add(new String(call.getArgument(0, byte[].class), StandardCharsets.UTF_8));
                    return true;
                });

        OptionalRedisService redisB = new OptionalRedisService(redis, json, redisProperties);
        instanceB = new TokenRevocationService(redisB, revocationProperties, clock, events, new MockEnvironment());
        instanceB.validateConfiguration();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedJwtMustRemainRejectedAfterRedisEmptyRecovery() throws Exception {
        // 1. Pre-logout: old synthetic JWT is valid (HTTP 200).
        String oldToken = loginToken("population", "123456");
        String oldJti = jwt.parseClaims(oldToken).getId();
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isOk());
        assertThat(instanceB.isRevoked(oldJti)).isFalse();

        // 2. Logout on instance A: A rejects via HTTP 401 and independent instance B observes revocation.
        mockMvc.perform(post("/api/auth/logout").header("Authorization", bearer(oldToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
        assertThat(instanceB.isRevoked(oldJti)).isTrue();

        // 3. New login issues a distinct JTI isolated from the revoked JTI.
        String freshToken = loginToken("population", "123456");
        String freshJti = jwt.parseClaims(freshToken).getId();
        assertThat(freshJti).isNotBlank().isNotEqualTo(oldJti);
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(freshToken)))
                .andExpect(status().isOk());
        assertThat(instanceB.isRevoked(freshJti)).isFalse();

        // 4. Redis outage fails closed (503) for token auth while public /api/health remains 200.
        storeUnavailable = true;
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(freshToken)))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.redisStatus").value("DOWN"));

        // 5. Preserved-data recovery still rejects oldToken (does NOT prove durability against data loss).
        storeUnavailable = false;
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isUnauthorized());

        // 6. Empty-store recovery (simulating lost RDB/AOF or empty data directory):
        // Security contract INV-02/INV-03 requires oldToken to remain rejected (401).
        markers.clear();
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(freshToken)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/me").header("Authorization", bearer(oldToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
    }

    private String loginToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return json.readTree(body).path("data").path("token").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
