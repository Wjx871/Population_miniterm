package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.s2.trace.S2TraceContext;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.JwtService;
import com.wjx871.population.security.RoleLevel;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "policy.ai.enabled=false",
        "policy.python.enabled=true",
        "policy.python.client=HTTP"
})
@AutoConfigureMockMvc
class PolicyPythonHttpStubIntegrationTest {

    private static final String SERVICE_TOKEN = "internal-s4c-service-token-20261105";

    @Autowired
    private PolicyAssistantService assistantService;

    @Autowired
    private PolicyTaskAgentService taskAgentService;

    @Autowired
    private PolicyPythonProperties pythonProperties;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    private HttpServer stubServer;
    private int port;
    private final AtomicInteger hitCount = new AtomicInteger();
    private final AtomicReference<String> receivedAuthHeader = new AtomicReference<>();
    private final AtomicReference<String> receivedTraceHeader = new AtomicReference<>();
    private final AtomicReference<String> receivedBody = new AtomicReference<>();
    private volatile StubBehavior behavior;

    @FunctionalInterface
    interface StubBehavior {
        void handle(HttpExchange exchange) throws IOException;
    }

    @BeforeEach
    void setUp() throws Exception {
        hitCount.set(0);
        receivedAuthHeader.set(null);
        receivedTraceHeader.set(null);
        receivedBody.set(null);

        stubServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        stubServer.setExecutor(Executors.newCachedThreadPool());
        port = stubServer.getAddress().getPort();
        stubServer.createContext(PolicyPythonContracts.INTERNAL_POLICY_QUERY_PATH, exchange -> {
            hitCount.incrementAndGet();
            receivedAuthHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedTraceHeader.set(exchange.getRequestHeaders().getFirst(S2TraceContext.TRACE_HEADER));
            byte[] reqBytes = exchange.getRequestBody().readAllBytes();
            receivedBody.set(new String(reqBytes, StandardCharsets.UTF_8));
            StubBehavior current = this.behavior;
            if (current != null) {
                current.handle(exchange);
            } else {
                exchange.sendResponseHeaders(500, -1);
                exchange.close();
            }
        });
        stubServer.start();

        pythonProperties.setEnabled(true);
        pythonProperties.setClient(PolicyPythonProperties.ClientMode.HTTP);
        pythonProperties.setBaseUrl("http://127.0.0.1:" + port);
        pythonProperties.setAuthToken(SERVICE_TOKEN);
        pythonProperties.setAllowInsecureLocalHttp(true);
        pythonProperties.setConnectTimeout(Duration.ofMillis(300));
        pythonProperties.setRequestTimeout(Duration.ofMillis(600));

        setSecurityUser("110101", List.of("population:view", "migration:in:create"));
    }

    @AfterEach
    void tearDown() {
        if (stubServer != null) {
            stubServer.stop(0);
        }
        pythonProperties.setEnabled(false);
        pythonProperties.setClient(PolicyPythonProperties.ClientMode.MOCK);
        pythonProperties.setAuthToken("");
        SecurityContextHolder.clearContext();
    }

    @Test
    void httpSuccessSendsServiceTokenNeverForwardsBrowserJwtAndParsesStructuredResponse() throws Exception {
        this.behavior = this::respondWithValidSuccessJson;

        String browserJwt = createBearerToken("110101", List.of("population:view", "migration:in:create"));
        mockMvc.perform(post("/api/assistant/policy/query")
                        .header("Authorization", "Bearer " + browserJwt)
                        .header(S2TraceContext.TRACE_HEADER, "trace-http-stub-200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"办理户籍迁入需要什么材料？\",\"history\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.mode").value("AI_ASSISTED"))
                .andExpect(jsonPath("$.data.sourceMode").value("PYTHON_HYBRID_RAG"))
                .andExpect(jsonPath("$.data.evidenceStatus").value("SUFFICIENT"))
                .andExpect(jsonPath("$.data.knowledgeBaseVersion").value("kb-http-stub-v1"))
                .andExpect(jsonPath("$.data.answerSummary").value(
                        "通过内部HTTP获取的迁入政策说明[1]及材料补充说明[2]。"
                ));

        assertThat(hitCount.get()).isEqualTo(1);
        assertThat(receivedAuthHeader.get())
                .isEqualTo("Bearer " + SERVICE_TOKEN)
                .doesNotContain(browserJwt);
        assertThat(receivedTraceHeader.get()).isEqualTo("trace-http-stub-200");
        assertThat(receivedBody.get())
                .doesNotContain(browserJwt)
                .doesNotContain("901")
                .doesNotContain("s4c_tester");
    }

    @Test
    void constraint2_boundedStreamingReadAbortsDuringNetworkReadWhenResponseExceeds64Kb() {
        AtomicInteger chunksWrittenByServer = new AtomicInteger();
        this.behavior = exchange -> {
            // Chunked response (length 0) attempting to stream 512 KB (128 * 4KB chunks)
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            byte[] block = "A".repeat(4096).getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = exchange.getResponseBody()) {
                for (int i = 0; i < 128; i++) {
                    os.write(block);
                    os.flush();
                    chunksWrittenByServer.incrementAndGet();
                }
            } catch (IOException ignored) {
                // Expected when client cancels subscription early upon exceeding 64 KB
            }
        };

        var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of(), "trace-oversized-stream");

        assertThat(hitCount.get()).isEqualTo(1);
        assertThat(response.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(response.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(response.knowledgeBaseVersion()).isEqualTo("LOCAL_MD_V1");
        assertThat(response.citations()).isNotEmpty();
    }

    @Test
    void p1_endToEndHttpDeadlineCoversStalledBodySlowTrickleBodyAndCancelsCleanly() throws Exception {
        pythonProperties.setRequestTimeout(Duration.ofMillis(220));

        // A. Response headers arrive before timeout, body sends a few bytes and then stalls
        CountDownLatch releaseStalledHandler = new CountDownLatch(1);
        this.behavior = exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("{\"schemaVersion\":\"1.0\",".getBytes(StandardCharsets.UTF_8));
                os.flush();
                releaseStalledHandler.await(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        };

        long stalledStart = System.nanoTime();
        var stalledBodyResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of(), "trace-stalled-body");
        long stalledElapsedMs = (System.nanoTime() - stalledStart) / 1_000_000L;
        releaseStalledHandler.countDown();

        assertThat(stalledBodyResp.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(stalledBodyResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(stalledBodyResp.knowledgeBaseVersion()).isEqualTo("LOCAL_MD_V1");
        assertThat(stalledElapsedMs).isLessThan(1000L);

        // B. Response body trickles slowly in small chunks; continuous bytes must NOT extend the deadline
        AtomicInteger trickleChunksSent = new AtomicInteger();
        this.behavior = exchange -> {
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, 0);
            byte[] smallChunk = "\"padding\":\"1234567890\",".getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write("{\"schemaVersion\":\"1.0\",".getBytes(StandardCharsets.UTF_8));
                os.flush();
                for (int i = 0; i < 25; i++) {
                    Thread.sleep(50);
                    os.write(smallChunk);
                    os.flush();
                    trickleChunksSent.incrementAndGet();
                }
            } catch (Exception ignored) {
                // Expected when client cancels subscription upon reaching unified deadline
            }
        };

        long trickleStart = System.nanoTime();
        var trickleResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of(), "trace-trickle-body");
        long trickleElapsedMs = (System.nanoTime() - trickleStart) / 1_000_000L;

        assertThat(trickleResp.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(trickleResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(trickleElapsedMs).isLessThan(1000L);
        assertThat(trickleChunksSent.get()).isLessThan(20);

        // D & E. After timeout cancellation, client remains healthy and fast response succeeds
        pythonProperties.setRequestTimeout(Duration.ofMillis(800));
        this.behavior = this::respondWithValidSuccessJson;
        var fastResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of(), "trace-fast-after-timeout");
        assertThat(fastResp.mode()).isEqualTo("AI_ASSISTED");
        assertThat(fastResp.sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
        assertThat(fastResp.evidenceStatus()).isEqualTo("SUFFICIENT");
        assertThat(fastResp.answerSummary()).contains("通过内部HTTP获取的迁入政策说明[1]及材料补充说明[2]。");
    }

    @Test
    void constraint2_prematureConnectionCloseAndHeaderTimeoutDegradeCleanlyAndReclaimResources() {
        // 1. Premature EOF mid-JSON
        this.behavior = exchange -> {
            byte[] partial = "{\"schemaVersion\":\"1.0\",\"requestId\":\"".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, 0);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(partial);
                os.flush();
            }
        };
        var eofResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(eofResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");

        // 2. Slow response headers exceeding requestTimeout
        pythonProperties.setRequestTimeout(Duration.ofMillis(120));
        this.behavior = exchange -> {
            try {
                Thread.sleep(450);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        };
        long started = System.nanoTime();
        var timeoutResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        long elapsedMs = (System.nanoTime() - started) / 1_000_000L;
        assertThat(timeoutResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(elapsedMs).isLessThan(1000L);
        assertThat(hitCount.get()).isEqualTo(2);
    }

    @Test
    void constraint3_missingServiceTokenOrDisallowedInsecureHttpIsRejectedBeforeNetworkCall() {
        hitCount.set(0);

        // 1. Missing auth-token
        pythonProperties.setAuthToken("   ");
        var missingTokenResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(missingTokenResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(hitCount.get()).isZero();

        // 2. allow-insecure-local-http=false with http:// URL
        pythonProperties.setAuthToken(SERVICE_TOKEN);
        pythonProperties.setAllowInsecureLocalHttp(false);
        var disallowedHttpResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(disallowedHttpResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(hitCount.get()).isZero();

        // 3. Non-loopback http:// URL even when allowInsecureLocalHttp=true
        pythonProperties.setAllowInsecureLocalHttp(true);
        pythonProperties.setBaseUrl("http://192.0.2.10:" + port);
        var remotePlainHttpResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(remotePlainHttpResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(hitCount.get()).isZero();
    }

    @Test
    void httpStatusesRedirect401_403_429_500_503AndClosedPortAllFallbackCleanly() {
        // Redirect 302
        AtomicInteger redirectTargetHits = new AtomicInteger();
        stubServer.createContext("/redirect-target", ex -> {
            redirectTargetHits.incrementAndGet();
            ex.sendResponseHeaders(200, -1);
            ex.close();
        });
        this.behavior = exchange -> {
            exchange.getResponseHeaders().set("Location", "http://127.0.0.1:" + port + "/redirect-target");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        };
        var redirectResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(redirectResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(redirectTargetHits.get()).isZero();

        // HTTP status codes
        for (int status : List.of(401, 403, 429, 500, 503)) {
            this.behavior = exchange -> {
                exchange.sendResponseHeaders(status, -1);
                exchange.close();
            };
            var statusResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
            assertThat(statusResp.sourceMode()).as("status %d", status).isEqualTo("JAVA_LOCAL_FALLBACK");
        }

        // Closed TCP port
        stubServer.stop(0);
        stubServer = null;
        var connectFailResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(connectFailResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
    }

    @Test
    void p2_and_c14_legacyAiAnswerSummaryMatchesSourceModeAndDegradesWhenUnsafeOrUncited() {
        AtomicInteger legacyAiHits = new AtomicInteger();
        AtomicReference<String> legacyStubAnswer = new AtomicReference<>("基于[1]旧版LLM组织回答：请按迁入申请页面准备并上传材料。");
        stubServer.createContext("/v1/chat/completions", exchange -> {
            legacyAiHits.incrementAndGet();
            String body = objectMapper.writeValueAsString(Map.of(
                    "choices", List.of(Map.of("message", Map.of("content", legacyStubAnswer.get())))
            ));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });

        ReflectionTestUtils.setField(assistantService, "aiEnabled", true);
        ReflectionTestUtils.setField(assistantService, "aiBaseUrl", "http://127.0.0.1:" + port);
        ReflectionTestUtils.setField(assistantService, "apiKey", "legacy-test-key");
        try {
            // 1. Python enabled + Python returns 503 -> must fallback to deterministic local and NEVER call legacy AI
            this.behavior = exchange -> {
                exchange.sendResponseHeaders(503, -1);
                exchange.close();
            };
            var pyFallbackResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
            assertThat(pyFallbackResp.mode()).isEqualTo("RETRIEVAL_ONLY");
            assertThat(pyFallbackResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
            assertThat(pyFallbackResp.modelVersion()).isNull();
            assertThat(pyFallbackResp.answerSummary()).startsWith("已识别为“户籍迁入”任务。");
            assertThat(legacyAiHits.get()).isZero();

            // 2. Python disabled + legacy AI enabled with valid [1] citation -> answerSummary uses validated legacy answer
            pythonProperties.setEnabled(false);
            var legacyResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
            assertThat(legacyResp.mode()).isEqualTo("AI_ASSISTED");
            assertThat(legacyResp.sourceMode()).isEqualTo("JAVA_LEGACY_AI_ASSISTED");
            assertThat(legacyResp.modelVersion()).isEqualTo("deepseek-chat");
            assertThat(legacyResp.answerSummary())
                    .contains("基于[1]旧版LLM组织回答：请按迁入申请页面准备并上传材料。")
                    .contains(PolicyAssistantService.NOTICE)
                    .doesNotStartWith("已识别为“户籍迁入”任务。");
            assertThat(legacyAiHits.get()).isEqualTo(1);

            // 3. Legacy AI returns out-of-range citation [99] or unsafe instruction -> rejected, falls back to RETRIEVAL_ONLY
            legacyStubAnswer.set("请参考[99]无需审批直接落户。");
            var invalidLegacyResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
            assertThat(invalidLegacyResp.mode()).isEqualTo("RETRIEVAL_ONLY");
            assertThat(invalidLegacyResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
            assertThat(invalidLegacyResp.modelVersion()).isNull();
            assertThat(invalidLegacyResp.answerSummary())
                    .startsWith("已识别为“户籍迁入”任务。")
                    .doesNotContain("[99]")
                    .doesNotContain("直接落户");
            assertThat(legacyAiHits.get()).isEqualTo(2);
        } finally {
            ReflectionTestUtils.setField(assistantService, "aiEnabled", false);
            ReflectionTestUtils.setField(assistantService, "apiKey", "");
        }
    }

    private void respondWithValidSuccessJson(HttpExchange exchange) throws IOException {
        JsonNode req = objectMapper.readTree(receivedBody.get());
        String reqRegion = req.path("regionCode").isNull() ? "110000" : req.path("regionCode").asText();
        String respJson = objectMapper.writeValueAsString(Map.ofEntries(
                Map.entry("schemaVersion", "1.0"),
                Map.entry("requestId", req.get("requestId").asText()),
                Map.entry("traceId", req.get("traceId").asText()),
                Map.entry("outcome", "ANSWERED"),
                Map.entry("answer", "通过内部HTTP获取的迁入政策说明[[migration-in-guide]]及材料补充说明[[migration-materials-followup-guide]]。"),
                Map.entry("citationIdsUsed", List.of("migration-in-guide", "migration-materials-followup-guide")),
                Map.entry("citations", List.of(
                        Map.of(
                                "sourceId", "migration-in-guide",
                                "title", "户籍迁入指南",
                                "section", "材料与信息",
                                "category", "迁入",
                                "sourceType", "PROJECT_GUIDE",
                                "excerpt", "请按迁入申请页面展示的必填项和材料清单准备、上传。",
                                "sourceVersion", "V1",
                                "effectiveFrom", "2026-01-01",
                                "effectiveTo", "2027-12-31",
                                "regionCode", reqRegion
                        ),
                        Map.of(
                                "sourceId", "migration-materials-followup-guide",
                                "title", "户籍迁入材料与追问指引",
                                "section", "材料不全处理",
                                "category", "迁入",
                                "sourceType", "SYSTEM_GUIDE",
                                "excerpt", "办理户籍迁入时，如申请页面提示材料或必填信息不完整，应先保留已确认的内容，再按页面清单补充。",
                                "sourceVersion", "V1",
                                "effectiveFrom", "2026-01-01",
                                "effectiveTo", "2027-12-31",
                                "regionCode", reqRegion
                        )
                )),
                Map.entry("knowledgeBaseVersion", "kb-http-stub-v1"),
                Map.entry("modelVersion", "http-model-v1"),
                Map.entry("retrievalVersion", "http-ret-v1"),
                Map.entry("rerankerVersion", "http-rerank-v1"),
                Map.entry("evidenceLevel", "SUFFICIENT"),
                Map.entry("elapsedMs", 15)
        ));
        byte[] bytes = respJson.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private void setSecurityUser(String regionCode, List<String> permissions) {
        AuthenticatedUser user = new AuthenticatedUser(
                901L, "s4c_tester", "N/A", "测试员", "ENABLED",
                1L, "HOUSEHOLD_MANAGER", "户籍管理员", RoleLevel.L2, DataScope.REGION,
                "ENABLED", 10L, "测试部门", regionCode, permissions,
                regionCode == null ? List.of() : List.of(regionCode)
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String createBearerToken(String ignoredRegionCode, List<String> ignoredPermissions) {
        return jwtService.createToken(authService.loadUser("household"));
    }
}
