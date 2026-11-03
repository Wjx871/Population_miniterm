package com.wjx871.population.s2;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.s2.rmi.RmiServerMain;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Tag("integration")
@SpringBootTest(properties = "population.s2.remote.enabled=true")
@AutoConfigureMockMvc
class S2RmiHttpEndToEndIntegrationTest {

    private static final int REGISTRY_PORT = freePort();
    private static final int OBJECT_PORT = freePortOtherThan(REGISTRY_PORT);
    private static final Path SERVER_LOG = Path.of("target", "s2-test-rmi-" + UUID.randomUUID() + ".log")
            .toAbsolutePath();
    private static Process serverProcess;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @DynamicPropertySource
    static void remotePorts(DynamicPropertyRegistry registry) {
        registry.add("population.s2.rmi.registry-port", () -> REGISTRY_PORT);
        registry.add("population.s2.rmi.object-port", () -> OBJECT_PORT);
    }

    @BeforeAll
    static void startIndependentRmiProcess() throws Exception {
        Files.createDirectories(SERVER_LOG.getParent());
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path logConfig = Path.of("src", "main", "resources", "log4j2-spring.xml").toAbsolutePath();
        serverProcess = new ProcessBuilder(java,
                "-Dlog4j.configurationFile=" + logConfig,
                "-DLOG_DIR=" + SERVER_LOG.getParent(),
                "-Ds2.rmi.registry-port=" + REGISTRY_PORT,
                "-Ds2.rmi.object-port=" + OBJECT_PORT,
                "-cp", classPath, RmiServerMain.class.getName())
                .redirectErrorStream(true)
                .redirectOutput(SERVER_LOG.toFile())
                .start();

        Instant deadline = Instant.now().plus(Duration.ofSeconds(20));
        while (Instant.now().isBefore(deadline)) {
            if (!serverProcess.isAlive()) {
                throw new IllegalStateException("RMI server process exited before opening its Registry: "
                        + Files.readString(SERVER_LOG, StandardCharsets.UTF_8));
            }
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress("127.0.0.1", REGISTRY_PORT), 200);
                return;
            } catch (IOException ignored) {
                Thread.sleep(100);
            }
        }
        throw new IllegalStateException("Timed out waiting for the independent RMI Registry process.");
    }

    @AfterAll
    static void stopOnlyTheChildRmiProcess() throws Exception {
        if (serverProcess == null || !serverProcess.isAlive()) {
            return;
        }
        serverProcess.destroy();
        if (!serverProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) {
            serverProcess.destroyForcibly();
            serverProcess.waitFor(5, java.util.concurrent.TimeUnit.SECONDS);
        }
    }

    @Test
    void authenticatedHttpRequestPropagatesTraceIdToAnIndependentRmiJvm() throws Exception {
        ensureStatisticsPermissionForAdmin();
        String traceId = "http-rmi-e2e-" + UUID.randomUUID().toString().substring(0, 8);
        String token = loginAndReadToken("admin");

        var response = mockMvc.perform(get("/api/s2/statistics/aggregate")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Trace-Id", traceId))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(response.getHeader("X-Trace-Id")).isEqualTo(traceId);
        JsonNode result = objectMapper.readTree(response.getContentAsString()).path("data");
        assertThat(result.path("traceId").asText()).isEqualTo(traceId);
        assertThat(result.path("schemaVersion").asText()).isEqualTo("1.0");

        Instant deadline = Instant.now().plusSeconds(3);
        String serverLog = "";
        while (Instant.now().isBefore(deadline)) {
            serverLog = Files.readString(SERVER_LOG, StandardCharsets.UTF_8);
            if (serverLog.contains("traceId=" + traceId)) {
                break;
            }
            Thread.sleep(100);
        }
        assertThat(serverLog).contains("traceId=" + traceId, "instanceId=", "S2 remote aggregate completed");
        assertThat(serverProcess.pid()).isNotEqualTo(ProcessHandle.current().pid());
    }

    private String loginAndReadToken(String username) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                        .contentType(APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("token").asText();
    }

    private void ensureStatisticsPermissionForAdmin() {
        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_permission WHERE permission_code='statistics:view'", Integer.class);
        if (existing == null || existing == 0) {
            Long id = jdbcTemplate.queryForObject("SELECT COALESCE(MAX(permission_id),0)+1 FROM sys_permission", Long.class);
            jdbcTemplate.update("INSERT INTO sys_permission(permission_id,permission_code,permission_name,module_name,permission_type,status) "
                            + "VALUES(?, 'statistics:view', '查看统计', 'STATISTICS', 'API', 'ENABLED')", id);
        }
        jdbcTemplate.update("INSERT INTO sys_role_permission(role_id,permission_id) "
                        + "SELECT 3, permission_id FROM sys_permission WHERE permission_code='statistics:view' "
                        + "AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id=3 "
                        + "AND rp.permission_id=sys_permission.permission_id)");
    }

    private static int freePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to allocate a local test port", exception);
        }
    }

    private static int freePortOtherThan(int excluded) {
        int port;
        do {
            port = freePort();
        } while (port == excluded);
        return port;
    }
}
