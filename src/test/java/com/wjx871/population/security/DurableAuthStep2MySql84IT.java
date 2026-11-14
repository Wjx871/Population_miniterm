package com.wjx871.population.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Isolated MySQL 8.4.x HTTP + durable-auth integration test for Issue #64 Step 2.
 *
 * <p>Uses {@code *IT} suffix so default H2 {@code mvn test} / {@code mvn clean test} never connects
 * to MySQL. Supports system properties ({@code -Dissue64.mysql84.url},
 * {@code -Dissue64.mysql84.username}, {@code -Dissue64.mysql84.password}) with fallback to env vars
 * ({@code ISSUE64_MYSQL84_DB_URL}, {@code ISSUE64_MYSQL84_DB_USERNAME},
 * {@code ISSUE64_MYSQL84_DB_PASSWORD}). Cleanly skips only when ALL coordinates are absent and
 * {@code -Dissue64.mysql84.required=true} is not set; any partial or non-isolated coordinate fails
 * closed. Requires {@code V4_020_jwt_revocation.sql} and {@code activate_jwt_revocation_checkpoint.sql}
 * (non-zero {@code READY} cutoff) on an isolated {@code 127.0.0.1} non-3306 {@code issue64_test_*}
 * schema with a non-root user.
 */
@EnabledIf("isMysql84Configured")
@SpringBootTest(properties = {
        "spring.sql.init.mode=never",
        "spring.datasource.hikari.maximum-pool-size=12",
        "security.jwt.revocation.mode=DURABLE_DB_REQUIRED",
        "security.jwt.revocation.allow-zero-cutoff-for-test=false",
        "population.redis.enabled=true",
        "policy.ai.enabled=false"
})
@AutoConfigureMockMvc
class DurableAuthStep2MySql84IT {

    private static final Pattern ISOLATED_URL = Pattern.compile(
            "^jdbc:mysql://127\\.0\\.0\\.1:(?<port>[0-9]+)/(?<schema>issue64_test_[a-z0-9_]{4,40})\\?(?<options>.*)$");

    private static final AtomicBoolean SIMULATE_DS_OUTAGE = new AtomicBoolean(false);
    private static final AtomicBoolean SIMULATE_REVOCATION_MAPPER_OUTAGE = new AtomicBoolean(false);
    private static final AtomicBoolean FAIL_INSERT_BEFORE_COMMIT = new AtomicBoolean(false);

    @TestConfiguration
    static class MysqlFaultHooksConfig {
        @Bean
        static BeanPostProcessor mysqlDataSourceAndMapperFaultProxy() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    if (bean instanceof DataSource target && "dataSource".equals(beanName)) {
                        return Proxy.newProxyInstance(
                                DataSource.class.getClassLoader(),
                                new Class<?>[] {DataSource.class, AutoCloseable.class},
                                (proxy, method, args) -> {
                                    if ("getConnection".equals(method.getName()) && SIMULATE_DS_OUTAGE.get()) {
                                        throw new SQLException("simulated mysql outage on 127.0.0.1 password=masked");
                                    }
                                    try {
                                        return method.invoke(target, args);
                                    } catch (InvocationTargetException ex) {
                                        throw ex.getTargetException();
                                    }
                                });
                    }
                    if (bean instanceof JwtRevocationMapper target) {
                        return Proxy.newProxyInstance(
                                JwtRevocationMapper.class.getClassLoader(),
                                new Class<?>[] {JwtRevocationMapper.class},
                                (proxy, method, args) -> {
                                    if (SIMULATE_REVOCATION_MAPPER_OUTAGE.get()
                                            && ("selectAuthoritativeState".equals(method.getName())
                                                    || "selectCheckpoint".equals(method.getName())
                                                    || "insertRevocation".equals(method.getName()))) {
                                        throw new DataAccessResourceFailureException(
                                                "simulated revocation store outage on 127.0.0.1 password=masked");
                                    }
                                    if ("insertRevocation".equals(method.getName())) {
                                        Object result;
                                        try {
                                            result = method.invoke(target, args);
                                        } catch (InvocationTargetException ex) {
                                            throw ex.getTargetException();
                                        }
                                        if (FAIL_INSERT_BEFORE_COMMIT.get()) {
                                            throw new DataAccessResourceFailureException(
                                                    "simulated mysql rollback before commit");
                                        }
                                        return result;
                                    }
                                    try {
                                        return method.invoke(target, args);
                                    } catch (InvocationTargetException ex) {
                                        throw ex.getTargetException();
                                    }
                                });
                    }
                    return bean;
                }
            };
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper json;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private JwtService jwtService;
    @Autowired private DurableRevocationStore durableStore;
    @Autowired private OptionalRedisService optionalRedis;
    @Autowired private RevocationProperties revocationProperties;
    @Autowired private RevocationEvents events;
    @Autowired private Clock clock;
    @MockBean private StringRedisTemplate redis;

    private Set<String> redisMarkers;
    private volatile RuntimeException redisReadFailure;
    private volatile RuntimeException redisWriteFailure;
    private RedisStringCommands strings;

    static boolean isMysql84Configured() {
        String url = resolveConfig("issue64.mysql84.url", "ISSUE64_MYSQL84_DB_URL");
        String user = resolveConfig("issue64.mysql84.username", "ISSUE64_MYSQL84_DB_USERNAME");
        String pass = resolveConfig("issue64.mysql84.password", "ISSUE64_MYSQL84_DB_PASSWORD");
        boolean required = "true".equalsIgnoreCase(System.getProperty("issue64.mysql84.required"));
        if (!required && isBlank(url) && isBlank(user) && isBlank(pass)) {
            return false;
        }
        guardUrl();
        return true;
    }

    private static String resolveConfig(String sysProp, String envVar) {
        String prop = System.getProperty(sysProp);
        if (prop != null && !prop.isBlank()) {
            return prop.trim();
        }
        String env = System.getenv(envVar);
        return env == null ? "" : env.trim();
    }

    private static String requiredConfig(String sysProp, String envVar) {
        String value = resolveConfig(sysProp, envVar);
        if (value.isBlank()) {
            throw new IllegalStateException(
                    "Missing isolated MySQL 8.4 configuration (-D" + sysProp + " or " + envVar + ")");
        }
        return value;
    }

    private static Matcher guardUrl() {
        String url = requiredConfig("issue64.mysql84.url", "ISSUE64_MYSQL84_DB_URL");
        String user = requiredConfig("issue64.mysql84.username", "ISSUE64_MYSQL84_DB_USERNAME");
        requiredConfig("issue64.mysql84.password", "ISSUE64_MYSQL84_DB_PASSWORD");
        Matcher matcher = ISOLATED_URL.matcher(url);
        if (!matcher.matches()
                || "3306".equals(matcher.group("port"))
                || url.toLowerCase().contains("population_miniterm")
                || "root".equalsIgnoreCase(user)
                || "admin".equalsIgnoreCase(user)
                || "sa".equalsIgnoreCase(user)
                || "rdsadmin".equalsIgnoreCase(user)
                || !Pattern.compile("(?:^|&)createDatabaseIfNotExist=false(?:&|$)")
                        .matcher(matcher.group("options")).find()) {
            throw new IllegalStateException(
                    "Refusing non-isolated MySQL target; require 127.0.0.1 non-3306 issue64_test_* non-root user");
        }
        return matcher;
    }

    @DynamicPropertySource
    static void configureIsolatedMysql(DynamicPropertyRegistry registry) {
        Matcher match = guardUrl();
        String url = requiredConfig("issue64.mysql84.url", "ISSUE64_MYSQL84_DB_URL");
        String user = requiredConfig("issue64.mysql84.username", "ISSUE64_MYSQL84_DB_USERNAME");
        String pass = requiredConfig("issue64.mysql84.password", "ISSUE64_MYSQL84_DB_PASSWORD");

        verifyMysql84IdentityAndBootstrapAuthTables(url, user, pass, match);

        registry.add("spring.sql.init.mode", () -> "never");
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> user);
        registry.add("spring.datasource.password", () -> pass);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
    }

    private static void verifyMysql84IdentityAndBootstrapAuthTables(
            String url, String user, String pass, Matcher match) {
        try (Connection conn = DriverManager.getConnection(url, user, pass);
                Statement st = conn.createStatement()) {
            try (ResultSet rs = st.executeQuery(
                    "SELECT @@version, @@port, @@bind_address, @@transaction_isolation, "
                            + "@@read_only, DATABASE(), CURRENT_USER()")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString(1)).startsWith("8.4.");
                assertThat(rs.getInt(2)).isEqualTo(Integer.parseInt(match.group("port")));
                assertThat(rs.getString(3)).isEqualTo("127.0.0.1");
                assertThat(rs.getString(4)).isEqualTo("REPEATABLE-READ");
                assertThat(rs.getInt(5)).isZero();
                assertThat(rs.getString(6)).isEqualTo(match.group("schema"));
                assertThat(rs.getString(7)).startsWith(user + "@");
            }
            // Verify V4_020 tables already exist; never auto-create jwt_revocation* or auto-activate checkpoint
            try (ResultSet tables = st.executeQuery(
                    "SELECT COUNT(*) FROM information_schema.tables "
                            + "WHERE table_schema = DATABASE() "
                            + "AND table_name IN ('jwt_revocation', 'jwt_revocation_checkpoint')")) {
                assertThat(tables.next()).isTrue();
                assertThat(tables.getInt(1))
                        .as("V4_020_jwt_revocation.sql must be applied before running DurableAuthStep2MySql84IT")
                        .isEqualTo(2);
            }
            // Ensure minimal auth/audit tables exist for HTTP login/logout tests on fresh issue64_test_* DB
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sys_department (
                        department_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        department_code VARCHAR(50) NOT NULL UNIQUE,
                        department_name VARCHAR(100) NOT NULL,
                        parent_id BIGINT NULL,
                        region_code VARCHAR(50) NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
                        sort_no INT NOT NULL DEFAULT 0,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sys_role (
                        role_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        role_code VARCHAR(50) NOT NULL UNIQUE,
                        role_name VARCHAR(50) NOT NULL,
                        role_level VARCHAR(10) NOT NULL,
                        data_scope VARCHAR(20) NOT NULL,
                        description VARCHAR(255) NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sys_permission (
                        permission_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        permission_code VARCHAR(100) NOT NULL UNIQUE,
                        permission_name VARCHAR(100) NOT NULL,
                        module_name VARCHAR(100) NULL,
                        permission_type VARCHAR(20) NOT NULL DEFAULT 'API',
                        status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sys_user (
                        user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        username VARCHAR(50) NOT NULL UNIQUE,
                        password_hash VARCHAR(255) NOT NULL,
                        role_id BIGINT NOT NULL,
                        department_id BIGINT NULL,
                        real_name VARCHAR(50) NULL,
                        phone VARCHAR(20) NULL,
                        status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
                        last_login_time DATETIME NULL,
                        last_login_ip VARCHAR(50) NULL,
                        created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS sys_role_permission (
                        role_id BIGINT NOT NULL,
                        permission_id BIGINT NOT NULL,
                        PRIMARY KEY (role_id, permission_id)
                    ) ENGINE=InnoDB
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS admin_region (
                        region_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        region_code VARCHAR(20) NOT NULL UNIQUE,
                        region_name VARCHAR(100) NOT NULL,
                        parent_id BIGINT NULL,
                        region_level INT NOT NULL,
                        full_name VARCHAR(255) NOT NULL,
                        sort_no INT NOT NULL DEFAULT 0,
                        status VARCHAR(20) NOT NULL DEFAULT 'ENABLED'
                    ) ENGINE=InnoDB
                    """);
            st.execute("""
                    CREATE TABLE IF NOT EXISTS operation_log (
                        log_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        user_id BIGINT NULL,
                        operation_type VARCHAR(50) NOT NULL,
                        module_name VARCHAR(100) NULL,
                        request_path VARCHAR(255) NULL,
                        request_method VARCHAR(20) NULL,
                        target_table VARCHAR(100) NULL,
                        target_id VARCHAR(100) NULL,
                        operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        ip_address VARCHAR(50) NULL,
                        operation_result VARCHAR(50) NULL,
                        error_message VARCHAR(500) NULL,
                        user_agent VARCHAR(500) NULL,
                        detail TEXT NULL
                    ) ENGINE=InnoDB
                    """);
            st.execute("""
                    INSERT IGNORE INTO sys_department (department_id, department_code, department_name, region_code, status, sort_no)
                    VALUES (1, 'QUERY', '查询部门', '110000', 'ENABLED', 1),
                           (3, 'SYSTEM', '系统管理部门', '110000', 'ENABLED', 3)
                    """);
            st.execute("""
                    INSERT IGNORE INTO sys_role (role_id, role_code, role_name, role_level, data_scope, status)
                    VALUES (1, 'QUERY_VIEWER', '查询统计人员', 'L1', 'ALL', 'ENABLED'),
                           (3, 'SYSTEM_ADMIN', '系统管理员', 'L3', 'ALL', 'ENABLED')
                    """);
            st.execute("""
                    INSERT IGNORE INTO sys_permission (permission_id, permission_code, permission_name, module_name, permission_type, status)
                    VALUES (1, 'population:view', '查看人口', 'POPULATION', 'API', 'ENABLED')
                    """);
            st.execute("""
                    INSERT IGNORE INTO sys_role_permission (role_id, permission_id) VALUES (1, 1), (3, 1)
                    """);
            String hash = new BCryptPasswordEncoder().encode("123456");
            try (var ps = conn.prepareStatement(
                    "INSERT IGNORE INTO sys_user (user_id, username, password_hash, role_id, department_id, real_name, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, 'ENABLED')")) {
                ps.setLong(1, 1L);
                ps.setString(2, "viewer");
                ps.setString(3, hash);
                ps.setLong(4, 1L);
                ps.setLong(5, 1L);
                ps.setString(6, "查询统计人员");
                ps.executeUpdate();

                ps.setLong(1, 3L);
                ps.setString(2, "admin");
                ps.setString(3, hash);
                ps.setLong(4, 3L);
                ps.setLong(5, 3L);
                ps.setString(6, "系统管理员");
                ps.executeUpdate();
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("Failed pre-launch isolated MySQL 8.4 identity/bootstrap", ex);
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        SecurityContextHolder.clearContext();
        SIMULATE_DS_OUTAGE.set(false);
        SIMULATE_REVOCATION_MAPPER_OUTAGE.set(false);
        FAIL_INSERT_BEFORE_COMMIT.set(false);
        redisReadFailure = null;
        redisWriteFailure = null;
        redisMarkers = ConcurrentHashMap.newKeySet();

        jdbc.update("DELETE FROM jwt_revocation");
        jdbc.update("DELETE FROM operation_log");
        activateReadyCutoffAndWaitUtc();

        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        strings = mock(RedisStringCommands.class);
        when(redis.getConnectionFactory()).thenReturn(connectionFactory);
        when(connectionFactory.getConnection()).thenReturn(connection);
        when(connection.ping()).thenReturn("PONG");
        when(redis.getStringSerializer()).thenReturn(StringRedisSerializer.UTF_8);
        when(redis.hasKey(anyString())).thenAnswer(call -> {
            if (redisReadFailure != null) {
                throw redisReadFailure;
            }
            return redisMarkers.contains(call.getArgument(0, String.class));
        });
        when(connection.stringCommands()).thenReturn(strings);
        when(redis.execute(org.mockito.ArgumentMatchers.<RedisCallback<Boolean>>any()))
                .thenAnswer(call -> call.<RedisCallback<Boolean>>getArgument(0).doInRedis(connection));
        when(strings.set(any(byte[].class), any(byte[].class), any(Expiration.class), eq(SetOption.UPSERT)))
                .thenAnswer(call -> {
                    if (redisWriteFailure != null) {
                        throw redisWriteFailure;
                    }
                    redisMarkers.add(new String(call.getArgument(0, byte[].class), StandardCharsets.UTF_8));
                    return true;
                });
    }

    @AfterEach
    void tearDown() throws Exception {
        SIMULATE_DS_OUTAGE.set(false);
        SIMULATE_REVOCATION_MAPPER_OUTAGE.set(false);
        FAIL_INSERT_BEFORE_COMMIT.set(false);
        activateReadyCutoffAndWaitUtc();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void startupCheckpointAnomaliesFailFastOnRealMysql84WithoutAutoRepair() throws Exception {
        TokenRevocationService svc = new TokenRevocationService(
                optionalRedis, revocationProperties, clock, events, new MockEnvironment(), durableStore);

        // 1. Missing row
        jdbc.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assertThatThrownBy(svc::validateConfiguration).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM jwt_revocation_checkpoint", Integer.class)).isZero();

        // 2. UNINITIALIZED + NULL
        jdbc.update("INSERT INTO jwt_revocation_checkpoint(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, updated_by) "
                        + "VALUES (?, 'UNINITIALIZED', NULL, 'IT_UNINIT')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assertThatThrownBy(svc::validateConfiguration).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject(
                "SELECT checkpoint_status FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                String.class, DurableRevocationStore.GLOBAL_CHECKPOINT_KEY)).isEqualTo("UNINITIALIZED");

        // 3. INVALID
        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'INVALID', min_valid_iat_epoch_ms = 1000 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assertThatThrownBy(svc::validateConfiguration).isInstanceOf(IllegalStateException.class);

        // 4. READY + 0 (forbidden when allowZeroCutoffForTest=false)
        jdbc.update("UPDATE jwt_revocation_checkpoint SET checkpoint_status = 'READY', min_valid_iat_epoch_ms = 0 "
                        + "WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        assertThatThrownBy(svc::validateConfiguration).isInstanceOf(IllegalStateException.class);

        // 5. Real UTC next-whole-second READY activation succeeds after UTC crosses cutoff
        activateReadyCutoffAndWaitUtc();
        svc.validateConfiguration();
    }

    @Test
    void realHttpPrimaryAuthTransactionalLogoutAndRedisMissOrOutageFallbackOnMysql84() throws Exception {
        String token = loginToken("admin", "123456");
        String jti = jwtService.parseClaims(token).getId();
        String sha = DurableRevocationStore.sha256Hex(jti);

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"));

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ? AND revocation_reason = 'LOGOUT'",
                Integer.class, sha)).isEqualTo(1);
        assertThat(logoutSuccessCount()).isEqualTo(1);

        // Redis MISS -> MySQL primary fallback returns 401 and backfills Redis
        redisMarkers.clear();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));
        assertThat(redisMarkers).contains("population:security:jwt:blacklist:" + sha);

        // Redis outage -> MySQL primary fallback rejects revoked token (401) and accepts valid token (200)
        String fresh = loginToken("viewer", "123456");
        redisReadFailure = new RedisConnectionFailureException("simulated-redis-down");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + fresh))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("viewer"));
    }

    @Test
    void runtimeCutoffAdvanceTakesEffectImmediatelyOnRealMysql84() throws Exception {
        String oldToken = loginToken("admin", "123456");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isOk());

        // Advance cutoff in MySQL 8.4 using (UNIX_TIMESTAMP() + 1) * 1000 and wait real UTC cross-over
        activateReadyCutoffAndWaitUtc();

        // Very next request with oldToken immediately returns 401 (0s local cache window)
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + oldToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("身份令牌已撤销"));

        // Fresh login after cutoff returns 200 and authenticates cleanly
        String freshToken = loginToken("admin", "123456");
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + freshToken))
                .andExpect(status().isOk());
    }

    @Test
    void redisWriteFailureOnLogoutAndMysqlRollbackSemanticsOnRealMysql84() throws Exception {
        // 1. Redis write failure after MySQL commit -> 200 + auth 401
        String token1 = loginToken("admin", "123456");
        String sha1 = DurableRevocationStore.sha256Hex(jwtService.parseClaims(token1).getId());
        redisWriteFailure = new RedisConnectionFailureException("simulated-redis-write-down");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token1))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha1)).isEqualTo(1);
        assertThat(logoutSuccessCount()).isEqualTo(1);
        assertThat(redisMarkers).isEmpty();
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token1))
                .andExpect(status().isUnauthorized());

        // 2. Real MySQL InnoDB transaction rollback before commit -> 503 + no Redis write + no LOGOUT SUCCESS
        redisWriteFailure = null;
        String token2 = loginToken("viewer", "123456");
        String sha2 = DurableRevocationStore.sha256Hex(jwtService.parseClaims(token2).getId());
        FAIL_INSERT_BEFORE_COMMIT.set(true);
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token2))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andExpect(jsonPath("$.message", containsString("REVOCATION_STORE_UNAVAILABLE")));
        FAIL_INSERT_BEFORE_COMMIT.set(false);

        verify(strings, never()).set(
                eq(("population:security:jwt:blacklist:" + sha2).getBytes(StandardCharsets.UTF_8)),
                any(), any(), any());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM jwt_revocation WHERE jti_sha256 = ?", Integer.class, sha2)).isZero();
        assertThat(logoutSuccessCount()).isEqualTo(1);
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token2))
                .andExpect(status().isOk());
    }

    @Test
    void mysqlOutageFailsClosed503AndRecoversCleanly() throws Exception {
        String token = loginToken("admin", "123456");

        // 1. Revocation store query/write outage -> auth /me, logout, and login all fail closed with 503
        SIMULATE_REVOCATION_MAPPER_OUTAGE.set(true);
        String mapperAuthBody = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andReturn().getResponse().getContentAsString();
        assertThat(mapperAuthBody).doesNotContain("password=masked", "DataAccessResourceFailureException");
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
        mockMvc.perform(login("admin", "123456"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503));
        SIMULATE_REVOCATION_MAPPER_OUTAGE.set(false);

        // 2. Full DataSource connection outage -> /me & /logout fail closed 503, /api/health reports DOWN / DB_PRIMARY_DOWN
        SIMULATE_DS_OUTAGE.set(true);
        String authBody = mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(503))
                .andReturn().getResponse().getContentAsString();
        assertThat(authBody).doesNotContain("password=masked", "SQLException");

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable());
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("DOWN"))
                .andExpect(jsonPath("$.data.revocationMode").value("DURABLE_DB_REQUIRED"))
                .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_PRIMARY_DOWN"));

        // Recover MySQL connection
        SIMULATE_DS_OUTAGE.set(false);
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.database").value("UP"))
                .andExpect(jsonPath("$.data.revocationStoreStatus").value("DB_PRIMARY_UP"));
        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private void activateReadyCutoffAndWaitUtc() throws InterruptedException {
        jdbc.update("DELETE FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        jdbc.update("INSERT INTO jwt_revocation_checkpoint "
                + "(checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, activated_at, updated_by) "
                + "VALUES (?, 'READY', (UNIX_TIMESTAMP() + 1) * 1000, UTC_TIMESTAMP(3), 'IT_ACTIVATE')",
                DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        Long cutoffMs = jdbc.queryForObject(
                "SELECT min_valid_iat_epoch_ms FROM jwt_revocation_checkpoint WHERE checkpoint_key = ?",
                Long.class, DurableRevocationStore.GLOBAL_CHECKPOINT_KEY);
        long deadline = System.currentTimeMillis() + 5000L;
        while (System.currentTimeMillis() < deadline) {
            Long dbNowSec = jdbc.queryForObject("SELECT UNIX_TIMESTAMP()", Long.class);
            long localSec = clock.instant().getEpochSecond();
            if (cutoffMs != null && dbNowSec != null
                    && dbNowSec * 1000L >= cutoffMs
                    && localSec * 1000L >= cutoffMs) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new IllegalStateException("Timed out waiting for MySQL/local UTC to reach cutoffMs=" + cutoffMs);
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

    private int logoutSuccessCount() {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM operation_log WHERE operation_type = 'LOGOUT' AND operation_result = 'SUCCESS'",
                Integer.class);
        return count == null ? 0 : count;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
