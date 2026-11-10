package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.sql.DataSource;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Independent resource observations around REAL loopback HTTP waits; no test transaction. */
@SpringBootTest(properties = {"application.upload.dir=./target/issue3-resource-uploads",
        "population.person.idcard.ocr.timeout-ms=5000"})
@AutoConfigureMockMvc
class Issue3ResourceObservationIntegrationTest {
    static final HttpServer server;
    static volatile CountDownLatch entered, release;
    static volatile int responseCode = 200;
    static final AtomicInteger requests = new AtomicInteger();
    static final String RESPONSE = "{\"result\":{\"idCard\":\"110105199001010010\",\"name\":\"SYNTHETIC\",\"confidence\":0.99}}";
    static {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/recognize/idcard", exchange -> {
                exchange.getRequestBody().readAllBytes();
                requests.incrementAndGet();
                CountDownLatch arrived = entered, gate = release;
                if (arrived != null) arrived.countDown();
                try {
                    if (gate != null) gate.await(12, TimeUnit.SECONDS);
                    byte[] body = (responseCode == 200 ? RESPONSE : "SYNTHETIC_PRIVATE_ERROR_110105199001010010").getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(responseCode, body.length);
                    exchange.getResponseBody().write(body);
                } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
                catch (java.io.IOException timedOutClient) { /* Expected after real read timeout. */ }
                finally { exchange.close(); }
            });
            server.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }));
            server.start();
        } catch (Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    @DynamicPropertySource static void endpoint(DynamicPropertyRegistry registry) {
        registry.add("population.person.idcard.ocr.url", () -> "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterAll static void stop() { server.stop(0); }
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @SpyBean PaddleOcrHttpClient client;
    final List<Boundary> boundaries = new CopyOnWriteArrayList<>();
    record Boundary(Thread thread, boolean transaction, boolean bound, String phase) {}
    @BeforeEach void prepareObservation() {
        entered = null; release = null; responseCode = 200; requests.set(0); boundaries.clear();
        doAnswer(invocation -> {
            boundary("before-http");
            try { return org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation); }
            finally { boundary("after-http"); }
        }).when(client).recognize(any(byte[].class), anyString());
    }
    void boundary(String phase) {
        boundaries.add(new Boundary(Thread.currentThread(), TransactionSynchronizationManager.isActualTransactionActive(),
                TransactionSynchronizationManager.hasResource(dataSource), phase));
    }
    boolean mysql() { return System.getenv("MYSQL84_GATE_DB_URL") != null; }
    Connection monitor() throws SQLException {
        return DriverManager.getConnection(System.getenv("MYSQL84_GATE_DB_URL"),
                System.getenv("MYSQL84_GATE_ADMIN_USERNAME"), System.getenv("MYSQL84_GATE_ADMIN_PASSWORD"));
    }
    // No business values or SQL_TEXT are logged. Restrict all probes to this run's application account.
    Map<String, Long> databaseSnapshot() throws Exception {
        if (!mysql()) return Map.of();
        String user = System.getenv("MYSQL84_GATE_DB_USERNAME");
        Map<String, Long> values = new LinkedHashMap<>();
        try (Connection observer = monitor()) {
            for (var query : Map.of(
                    "innodbTransactions", "SELECT COUNT(*) FROM information_schema.innodb_trx x JOIN performance_schema.threads t ON t.PROCESSLIST_ID=x.trx_mysql_thread_id WHERE t.PROCESSLIST_USER=?",
                    "dataLocks", "SELECT COUNT(*) FROM performance_schema.data_locks l JOIN performance_schema.threads t ON t.THREAD_ID=l.THREAD_ID WHERE t.PROCESSLIST_USER=?",
                    "lockWaits", "SELECT COUNT(*) FROM performance_schema.data_lock_waits w JOIN performance_schema.threads t ON t.THREAD_ID=w.REQUESTING_THREAD_ID WHERE t.PROCESSLIST_USER=?",
                    "activeTransactions", "SELECT COUNT(*) FROM performance_schema.events_transactions_current e JOIN performance_schema.threads t ON t.THREAD_ID=e.THREAD_ID WHERE t.PROCESSLIST_USER=? AND e.STATE='ACTIVE'").entrySet()) {
                try (PreparedStatement statement = observer.prepareStatement(query.getValue())) {
                    statement.setString(1, user);
                    try (ResultSet rows = statement.executeQuery()) { rows.next(); values.put(query.getKey(), rows.getLong(1)); }
                }
            }
        }
        return values;
    }
    void sample(int index, long started) throws Exception {
        int active = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
        Map<String,Long> snapshot = databaseSnapshot();
        assertThat(active).isZero();
        assertThat(boundaries).isNotEmpty().allSatisfy(b -> {
            assertThat(b.transaction()).isFalse(); assertThat(b.bound()).isFalse();
        });
        // The exact caller threads are blocked in actual HTTP socket input, between the two probes.
        for (Boundary b : boundaries) {
            if (b.phase().equals("before-http")) {
                assertThat(Arrays.stream(b.thread().getStackTrace()).map(StackTraceElement::getClassName).toList())
                        .anyMatch(name -> name.contains("HttpClient") || name.contains("HttpURLConnection") || name.contains("SocketInputStream"));
            }
        }
        assertThat(snapshot.values()).allSatisfy(value -> assertThat(value).isZero());
        System.out.printf("ISSUE3_RESOURCE_SAMPLE backend=%s index=%d elapsedMs=%d springTx=false bound=false hikariActive=%d database=%s%n",
                mysql() ? "MySQL84" : "H2", index, (System.nanoTime()-started)/1_000_000, active, snapshot);
    }
    @Test void twoSlowUploadersReleaseResourcesAndUnrelatedHttpBusinessCompletes() throws Exception {
        String firstToken = token("population"), secondToken = token("approver");
        byte[] a = image(), b = image();
        int auditsBefore = recognizedAudits();
        entered = new CountDownLatch(2); release = new CountDownLatch(1);
        var workers = Executors.newFixedThreadPool(3);
        long started = System.nanoTime();
        var first = workers.submit(() -> upload(a, firstToken, false));
        var second = workers.submit(() -> upload(b, secondToken, false));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            for (int i=0;i<6;i++) { sample(i, started); Thread.sleep(200); }
            long businessStarted = System.nanoTime();
            assertThat(workers.submit(() -> mvc.perform(get("/api/persons").header("Authorization", "Bearer " + firstToken)).andReturn())
                    .get(2, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            JsonNode skipped = data(workers.submit(() -> upload(image(), firstToken, true)).get(2, TimeUnit.SECONDS));
            assertThat(skipped.path("ocrStatus").asText()).isEqualTo("SKIPPED");
            assertThat(first.isDone()).isFalse(); assertThat(second.isDone()).isFalse();
            System.out.printf("ISSUE3_UNRELATED_HTTP completedMs=%d slowUploadsStillBlocked=2%n", (System.nanoTime()-businessStarted)/1_000_000);
            sample(6, started);
            release.countDown();
            for (var pending : List.of(first, second)) {
                JsonNode result = data(pending.get(10,TimeUnit.SECONDS));
                assertThat(result.path("ocrStatus").asText()).isEqualTo("SUCCESS");
                Map<String,Object> row = jdbc.queryForMap("SELECT ocr_elapsed_ms,storage_path FROM person_idcard_image WHERE image_id=?", result.path("imageId").asLong());
                assertThat(((Number) row.get("ocr_elapsed_ms")).longValue()).isGreaterThanOrEqualTo(1200);
                assertThat(Files.isRegularFile(Path.of(row.get("storage_path").toString()))).isTrue();
                System.out.printf("ISSUE3_OCR_DELAY elapsedMs=%s filePresent=true finalState=SUCCESS%n", row.get("ocr_elapsed_ms"));
            }
            assertThat(recognizedAudits()-auditsBefore).isEqualTo(2);
            assertThat(requests.get()).isEqualTo(2);
            assertThat(boundaries).hasSize(4).allSatisfy(v -> { assertThat(v.transaction()).isFalse(); assertThat(v.bound()).isFalse(); });
        } finally { release.countDown(); workers.shutdownNow(); }
    }
    @Test void timeoutHasNoInterimDraftThenCommitsOneFailedStateAndAudit() throws Exception {
        String token = token("population"); byte[] bytes = image();
        entered = new CountDownLatch(1); release = new CountDownLatch(1);
        int before = recognizedAudits(); var worker = Executors.newSingleThreadExecutor();
        long started = System.nanoTime();
        var pending = worker.submit(() -> upload(bytes, token, false));
        try {
            assertThat(entered.await(10,TimeUnit.SECONDS)).isTrue();
            for (int i=0;i<3;i++) { sample(i,started); Thread.sleep(200); }
            String hash = HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image WHERE file_sha256=?", Integer.class, hash)).isZero();
            JsonNode result = data(pending.get(10,TimeUnit.SECONDS));
            assertThat(result.path("ocrStatus").asText()).isEqualTo("FAILED");
            assertThat(result.path("ocrError").asText()).isEqualTo("OCR_UNAVAILABLE");
            long elapsed = jdbc.queryForObject("SELECT ocr_elapsed_ms FROM person_idcard_image WHERE image_id=?", Long.class, result.path("imageId").asLong());
            assertThat(elapsed).isBetween(4500L,10000L);
            assertThat(recognizedAudits()-before).isOne();
            assertThat(databaseSnapshot().values()).allSatisfy(v -> assertThat(v).isZero());
            System.out.printf("ISSUE3_TIMEOUT elapsedMs=%d rows=1 requiredAudits=1 state=FAILED error=OCR_UNAVAILABLE%n", elapsed);
        } finally { release.countDown(); worker.shutdownNow(); }
    }
    @Test void quickAnd503ResponsesCommitExactlyOneNecessaryAuditEachAndSkipHasNoHttp() throws Exception {
        String token = token("population"); int before=recognizedAudits();
        assertThat(data(upload(image(),token,false)).path("ocrStatus").asText()).isEqualTo("SUCCESS");
        responseCode=503;
        JsonNode failed=data(upload(image(),token,false));
        assertThat(failed.path("ocrError").asText()).isEqualTo("OCR_HTTP_503");
        assertThat(failed.toString()).doesNotContain("SYNTHETIC_PRIVATE_ERROR", "110105199001010010");
        assertThat(jdbc.queryForObject("SELECT ocr_error FROM person_idcard_image WHERE image_id=?",String.class,failed.path("imageId").asLong())).isEqualTo("OCR_HTTP_503");
        assertThat(jdbc.queryForList("SELECT error_message,detail FROM operation_log WHERE operation_type='PERSON_IDCARD_IMAGE_RECOGNIZED'").toString()).doesNotContain("SYNTHETIC_PRIVATE_ERROR", "110105199001010010");
        assertThat(data(upload(image(),token,true)).path("ocrStatus").asText()).isEqualTo("SKIPPED");
        assertThat(requests.get()).isEqualTo(2); assertThat(recognizedAudits()-before).isEqualTo(2);
        System.out.println("ISSUE3_HTTP quick=SUCCESS http503=FAILED sanitized=true skipHttpCalls=0 requiredRecognizedAudits=2");
    }
    int recognizedAudits() { return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='PERSON_IDCARD_IMAGE_RECOGNIZED'",Integer.class); }
    byte[] image() throws Exception {
        BufferedImage image = new BufferedImage(8,8,BufferedImage.TYPE_INT_RGB);
        var random = new Random(UUID.randomUUID().getMostSignificantBits());
        for(int x=0;x<8;x++) for(int y=0;y<8;y++) image.setRGB(x,y,random.nextInt());
        var out = new ByteArrayOutputStream(); ImageIO.write(image,"png",out); return out.toByteArray();
    }
    MvcResult upload(byte[] bytes, String token, boolean skip) throws Exception {
        return mvc.perform(multipart("/api/persons/idcard-image").file(new MockMultipartFile("file","synthetic.png","image/png",bytes))
                .param("skipOcr",Boolean.toString(skip)).header("Authorization","Bearer "+token)).andReturn();
    }
    JsonNode data(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }
    String token(String user) throws Exception {
        return data(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\""+user+"\",\"password\":\"123456\"}")).andReturn()).path("token").asText();
    }
}