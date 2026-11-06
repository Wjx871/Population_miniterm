package com.wjx871.population.person.idcard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zaxxer.hikari.HikariDataSource;
import com.wjx871.population.audit.OperationLog;
import com.wjx871.population.audit.OperationLogMapper;
import com.wjx871.population.material.LocalFileStorageService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Real Spring transactions, isolated H2 and a loopback HTTP OCR server. No test-level transaction. */
@SpringBootTest(properties = {
        "application.upload.dir=./target/issue3-uploads",
        "population.person.idcard.ocr.timeout-ms=5000"
})
@AutoConfigureMockMvc
class Issue3OcrTransactionIntegrationTest {
    static final String ID = "110105199001010010";
    static final String RESPONSE = """
            {"result":{"idCard":"110105199001010010","name":"合成样例",
            "birthDate":"1990-01-01","gender":"M","ethnicity":"测试",
            "address":"SYNTHETIC_PRIVATE_ADDRESS","confidence":0.99}}
            """;
    static final HttpServer SERVER;
    static volatile CountDownLatch entered;
    static volatile CountDownLatch release;
    static volatile String responseBody = RESPONSE;
    static volatile int responseCode = 200;
    static final AtomicInteger requests = new AtomicInteger();
    static {
        try {
            SERVER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            SERVER.createContext("/recognize/idcard", exchange -> {
                exchange.getRequestBody().readAllBytes();
                requests.incrementAndGet();
                CountDownLatch arrived = entered;
                CountDownLatch gate = release;
                if (arrived != null) arrived.countDown();
                try {
                    if (gate != null && !gate.await(15, TimeUnit.SECONDS)) throw new java.io.IOException("Stub gate timed out");
                    byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    exchange.sendResponseHeaders(responseCode, bytes.length);
                    exchange.getResponseBody().write(bytes);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    exchange.close();
                }
            });
            SERVER.setExecutor(Executors.newCachedThreadPool(r -> { Thread t = new Thread(r); t.setDaemon(true); return t; }));
            SERVER.start();
        } catch (java.io.IOException e) { throw new ExceptionInInitializerError(e); }
    }

    @DynamicPropertySource
    static void ocrUrl(DynamicPropertyRegistry properties) {
        properties.add("population.person.idcard.ocr.url", () -> "http://127.0.0.1:" + SERVER.getAddress().getPort());
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired PersonIdCardImageService service;
    @SpyBean PaddleOcrHttpClient ocrClient;
    @SpyBean PersonIdCardImageMapper mapper;
    @SpyBean OperationLogMapper audit;
    @SpyBean LocalFileStorageService storage;
    final AtomicBoolean transactionAtOcr = new AtomicBoolean();
    final AtomicBoolean resourceAtOcr = new AtomicBoolean();

    @BeforeEach
    void prepare() throws Exception {
        entered = null;
        release = null;
        responseBody = RESPONSE;
        responseCode = 200;
        requests.set(0);
        doAnswer(invocation -> {
            transactionAtOcr.set(TransactionSynchronizationManager.isActualTransactionActive());
            resourceAtOcr.set(TransactionSynchronizationManager.hasResource(dataSource));
            return org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
        }).when(ocrClient).recognize(any(byte[].class), anyString());
    }

    @AfterAll
    static void stopServer() { SERVER.stop(0); }

    @Test
    void ocrWaitHasNoSpringTransactionOrBorrowedConnection() throws Exception {
        String token = token("population");
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var future = executor.submit(() -> upload(content(), token, false));
        boolean tx;
        boolean bound;
        int active;
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            tx = transactionAtOcr.get();
            bound = resourceAtOcr.get();
            active = dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections();
            System.out.printf("ISSUE3_OCR_OBSERVATION springTransaction=%s resourceBound=%s activeConnections=%d%n", tx, bound, active);
        } finally { release.countDown(); }
        try {
            assertThat(future.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
            assertThat(tx).as("OCR caller has no actual Spring transaction").isFalse();
            assertThat(bound).as("OCR caller has no thread-bound datasource resource").isFalse();
            assertThat(active).as("OCR wait borrows no H2 connection").isZero();
        } finally { executor.shutdownNow(); }
    }

    @Test
    void blockedOcrDoesNotHoldTheShaUniqueLockAndReturnsCommittedWinner() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var first = executor.submit(() -> upload(bytes, token, false));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            var second = executor.submit(() -> upload(bytes, token, true));
            JsonNode winner = data(second.get(3, TimeUnit.SECONDS));
            assertThat(winner.path("ocrStatus").asText()).isEqualTo("SKIPPED");
            assertThat(first.isDone()).isFalse();
            assertThat(count(bytes)).isOne();
            release.countDown();
            assertThat(data(first.get(10, TimeUnit.SECONDS))).isEqualTo(winner);
            assertThat(requests.get()).isOne();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void uncompletedOcrHasNoBindableDraftAndMismatchIsCheckedAfterCompletion() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        long guessed = jdbc.queryForObject("SELECT COALESCE(MAX(image_id),0)+1 FROM person_idcard_image", Long.class);
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var first = executor.submit(() -> upload(bytes, token, false));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(count(bytes)).isZero();
            assertThat(createPerson(guessed, "110105196001020047", "F", token).getResponse().getStatus()).isEqualTo(400);
            release.countDown();
            long imageId = data(first.get(10, TimeUnit.SECONDS)).path("imageId").asLong();
            assertThat(createPerson(imageId, "110105196001020047", "F", token).getResponse().getStatus()).isEqualTo(400);
            assertThat(jdbc.queryForObject("SELECT person_id FROM person_idcard_image WHERE image_id=?", Long.class, imageId)).isNull();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void successAndNecessaryAuditCommitInRealWritableTransactions() throws Exception {
        String token = token("admin");
        List<Boolean> observed = new CopyOnWriteArrayList<>();
        List<Integer> completions = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            OperationLog event = invocation.getArgument(0);
            if (event.getOperationType().startsWith("PERSON_IDCARD_IMAGE")) {
                observed.add(TransactionSynchronizationManager.isActualTransactionActive());
                observed.add(!TransactionSynchronizationManager.isCurrentTransactionReadOnly());
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int status) { completions.add(status); }
                });
            }
            return org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
        }).when(audit).insert(any(OperationLog.class));
        JsonNode result = data(upload(content(), token, false));
        long id = result.path("imageId").asLong();
        Map<String, Object> row = row(id);
        assertThat(row.get("ocr_status")).isEqualTo("SUCCESS");
        assertThat(row.get("ocr_idcard_full")).isEqualTo(ID);
        assertThat(row.get("ocr_idcard_masked")).isEqualTo("1101**********0010");
        assertThat(row.get("ocr_name")).isEqualTo("合成样例");
        assertThat(row.get("ocr_address")).isEqualTo("SYNTHETIC_PRIVATE_ADDRESS");
        assertThat(row.get("ocr_raw_json").toString()).doesNotContain(ID, "SYNTHETIC_PRIVATE_ADDRESS", "合成样例");
        assertThat(result.path("ocrIdcardFull").asText()).isEqualTo(ID);
        assertThat(observed).containsExactly(true, true);
        assertThat(completions).containsExactly(TransactionSynchronization.STATUS_COMMITTED);
        assertThat(Files.exists(Path.of(row.get("storage_path").toString()))).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='PERSON_IDCARD_IMAGE_RECOGNIZED' AND operation_result='SUCCESS'", Integer.class)).isPositive();
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "{\"result\":[]}", "{\"result\":{}}", "{\"result\":{\"idCard\":123}}",
            "{\"result\":{\"idCard\":\"110105199001010010\",\"birthDate\":\"invalid\"}}",
            "{\"result\":{\"idCard\":\"110105199001010010\",\"confidence\":2}}"})
    void illegalPayloadIsFailedNotSuccess(String payload) throws Exception {
        responseBody = payload;
        JsonNode result = data(upload(content(), token("population"), false));
        assertThat(result.path("ocrStatus").asText()).isEqualTo("FAILED");
        assertThat(result.path("ocrIdcardFull").isNull()).isTrue();
        assertThat(row(result.path("imageId").asLong()).get("ocr_status")).isEqualTo("FAILED");
    }

    @Test
    void realHttpErrorDoesNotLeakItsSensitiveBody() throws Exception {
        responseCode = 503;
        responseBody = "SYNTHETIC_PRIVATE_ERROR_" + ID;
        JsonNode result = data(upload(content(), token("population"), false));
        assertThat(result.path("ocrStatus").asText()).isEqualTo("FAILED");
        assertThat(result.path("ocrError").asText()).isEqualTo("OCR_HTTP_503");
        assertThat(result.toString()).doesNotContain(ID, "SYNTHETIC_PRIVATE_ERROR");
        Map<String,Object> row = row(result.path("imageId").asLong());
        assertThat(row.get("ocr_error")).isEqualTo("OCR_HTTP_503");
        assertThat(jdbc.queryForList("SELECT error_message,detail FROM operation_log WHERE operation_type='PERSON_IDCARD_IMAGE_RECOGNIZED'").toString()).doesNotContain("SYNTHETIC_PRIVATE_ERROR");
    }

    @Test
    void realReadTimeoutPersistsFailedAndReleasesResources() throws Exception {
        String token = token("population");
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        try {
            JsonNode result = data(upload(content(), token, false));
            assertThat(result.path("ocrStatus").asText()).isEqualTo("FAILED");
            assertThat(result.path("ocrError").asText()).isEqualTo("OCR_UNAVAILABLE");
            assertThat(transactionAtOcr.get()).isFalse();
            assertThat(dataSource.unwrap(HikariDataSource.class).getHikariPoolMXBean().getActiveConnections()).isZero();
        } finally { release.countDown(); }
    }

    @Test
    void skipOcrMakesNoHttpCall() throws Exception {
        assertThat(data(upload(content(), token("population"), true)).path("ocrStatus").asText()).isEqualTo("SKIPPED");
        assertThat(requests.get()).isZero();
    }

    @ParameterizedTest
    @EnumSource(OcrStatus.class)
    void ownedUnboundDraftReplaysItsFinalStateWithoutAnotherOcr(OcrStatus expected) throws Exception {
        byte[] bytes = content();
        String token = token("population");
        if (expected == OcrStatus.FAILED) responseCode = 503;
        JsonNode first = data(upload(bytes, token, expected == OcrStatus.SKIPPED));
        int calls = requests.get();
        responseCode = 200;
        assertThat(first.path("ocrStatus").asText()).isEqualTo(expected.name());
        assertThat(data(upload(bytes, token, true))).isEqualTo(first);
        assertThat(data(upload(bytes, token, false))).isEqualTo(first);
        assertThat(count(bytes)).isOne();
        assertThat(requests.get()).isEqualTo(calls);
    }

    @Test
    void crossOwnerDuplicateIsRejectedWithoutOcrOrFileChanges() throws Exception {
        byte[] bytes = content();
        JsonNode first = data(upload(bytes, token("population"), true));
        Map<String,Object> original = row(first.path("imageId").asLong());
        Set<String> files = files();
        assertThat(upload(bytes, token("approver"), false).getResponse().getStatus()).isEqualTo(409);
        assertThat(row(first.path("imageId").asLong())).isEqualTo(original);
        assertThat(files()).isEqualTo(files);
        assertThat(requests.get()).isZero();
    }

    @Test
    void alreadyBoundImageCannotBeReusedOrReRecognized() throws Exception {
        byte[] bytes = content();
        String token = token("population");
        long id = data(upload(bytes, token, true)).path("imageId").asLong();
        jdbc.update("UPDATE person_idcard_image SET person_id=? WHERE image_id=?", syntheticPerson(), id);
        Map<String,Object> before = row(id);
        Set<String> files = files();
        assertThat(upload(bytes, token, false).getResponse().getStatus()).isEqualTo(409);
        assertThat(upload(bytes, token, true).getResponse().getStatus()).isEqualTo(409);
        assertThat(row(id)).isEqualTo(before);
        assertThat(files()).isEqualTo(files);
        assertThat(requests.get()).isZero();
    }

    @Test
    void simultaneousFirstUploadsUseUniqueConstraintAndOnlyOneResultWrite() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        Set<String> before = files();
        AtomicInteger resultWrites = new AtomicInteger();
        doAnswer(invocation -> { resultWrites.incrementAndGet(); return org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation); })
                .when(mapper).markOcrSuccess(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
        entered = new CountDownLatch(2);
        release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var first = executor.submit(() -> upload(bytes, token, false));
        var second = executor.submit(() -> upload(bytes, token, false));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            JsonNode a = data(first.get(10, TimeUnit.SECONDS));
            JsonNode b = data(second.get(10, TimeUnit.SECONDS));
            assertThat(a).isEqualTo(b);
            assertThat(count(bytes)).isOne();
            assertThat(resultWrites.get()).isOne();
            assertThat(requests.get()).isEqualTo(2); // Exactly-once OCR is deliberately NOT promised.
            Set<String> after = files(); after.removeAll(before);
            assertThat(after).containsExactly(row(a.path("imageId").asLong()).get("stored_filename").toString());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void concurrentDifferentUploadersRecheckWinnerOwnership() throws Exception {
        String a = token("population");
        String b = token("approver");
        byte[] bytes = content();
        entered = new CountDownLatch(2);
        release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        var first = executor.submit(() -> upload(bytes, a, false));
        var second = executor.submit(() -> upload(bytes, b, false));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            MvcResult ra = first.get(10, TimeUnit.SECONDS), rb = second.get(10, TimeUnit.SECONDS);
            assertThat(List.of(ra.getResponse().getStatus(), rb.getResponse().getStatus())).containsExactlyInAnyOrder(200, 409);
            assertThat(count(bytes)).isOne();
            String winner = ra.getResponse().getStatus() == 200 ? "population" : "approver";
            assertThat(jdbc.queryForObject("SELECT user_id FROM person_idcard_image WHERE file_sha256=?", Long.class, sha(bytes)))
                    .isEqualTo(jdbc.queryForObject("SELECT user_id FROM sys_user WHERE username=?", Long.class, winner));
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void loserRejectsWinnerThatWasBoundWhileItsOcrWasStillRunning() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        entered = new CountDownLatch(1);
        release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var first = executor.submit(() -> upload(bytes, token, false));
        try {
            assertThat(entered.await(10, TimeUnit.SECONDS)).isTrue();
            long winner = data(upload(bytes, token, true)).path("imageId").asLong();
            jdbc.update("UPDATE person_idcard_image SET person_id=? WHERE image_id=?", syntheticPerson(), winner);
            Map<String,Object> before = row(winner);
            release.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(409);
            assertThat(row(winner)).isEqualTo(before);
            assertThat(Files.exists(Path.of(before.get("storage_path").toString()))).isTrue();
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void databaseInsertFailureRollsBackAndRemovesOnlyOwnedFile() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        Set<String> before = files();
        doThrow(new DataIntegrityViolationException("SYNTHETIC_SQL_SECRET_" + ID)).when(mapper).insertDraft(any());
        MvcResult result = upload(bytes, token, false);
        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(ID, "SYNTHETIC_SQL_SECRET");
        assertThat(count(bytes)).isZero();
        assertThat(files()).isEqualTo(before);
    }

    @Test
    void fileSaveFailureIsSanitizedBeforeAnyOcrOrImageInsert() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        Set<String> before = files();
        doThrow(new IllegalStateException("SYNTHETIC_FILE_SAVE_SECRET_" + ID)).when(storage).store(any());
        MvcResult result = upload(bytes, token, false);
        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(ID, "SYNTHETIC_FILE_SAVE_SECRET");
        assertThat(count(bytes)).isZero();
        assertThat(files()).isEqualTo(before);
        assertThat(requests.get()).isZero();
    }

    @Test
    void auditFailureActuallyRollsBackInsertedAuditAndImage() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        Set<String> before = files();
        int auditBefore = jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Integer.class);
        List<Integer> completions = new CopyOnWriteArrayList<>();
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCompletion(int status) { completions.add(status); }
            });
            throw new DataIntegrityViolationException("SYNTHETIC_AUDIT_SECRET_" + ID);
        }).when(audit).insert(any());
        MvcResult result = upload(bytes, token, false);
        assertThat(result.getResponse().getStatus()).isEqualTo(500);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(ID, "SYNTHETIC_AUDIT_SECRET");
        assertThat(count(bytes)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM operation_log", Integer.class)).isEqualTo(auditBefore);
        assertThat(completions).containsExactly(TransactionSynchronization.STATUS_ROLLED_BACK);
        assertThat(files()).isEqualTo(before);
    }

    @Test
    void beforeCommitFailureIsNotReturnedAsSuccessAndCompensates() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        Set<String> before = files();
        doAnswer(invocation -> {
            Object result = org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void beforeCommit(boolean readOnly) { throw new IllegalStateException("SYNTHETIC_COMMIT_SECRET"); }
            });
            return result;
        }).when(mapper).insertDraft(any());
        assertThat(upload(bytes, token, false).getResponse().getStatus()).isEqualTo(500);
        assertThat(count(bytes)).isZero();
        assertThat(files()).isEqualTo(before);
    }

    @Test
    void postCommitFailureNeverDeletesACommittedFile() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        doAnswer(invocation -> {
            Object result = org.mockito.Mockito.mockingDetails(invocation.getMock()).getMockCreationSettings().getDefaultAnswer().answer(invocation);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { throw new IllegalStateException("SYNTHETIC_POST_COMMIT_SECRET"); }
            });
            return result;
        }).when(mapper).insertDraft(any());
        assertThat(upload(bytes, token, false).getResponse().getStatus()).isEqualTo(500);
        assertThat(count(bytes)).isOne();
        assertThat(Files.exists(Path.of(jdbc.queryForObject("SELECT storage_path FROM person_idcard_image WHERE file_sha256=?", String.class, sha(bytes))))).isTrue();
    }

    @Test
    void reuseAuditFailureKeepsOriginalDraftAndCompensatesRedundantFile() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        long id = data(upload(bytes, token, true)).path("imageId").asLong();
        Map<String,Object> original = row(id);
        Set<String> before = files();
        doThrow(new DataIntegrityViolationException("SYNTHETIC_REUSE_AUDIT_SECRET")).when(audit).insert(any());
        assertThat(upload(bytes, token, false).getResponse().getStatus()).isEqualTo(500);
        assertThat(row(id)).isEqualTo(original);
        assertThat(files()).isEqualTo(before);
        assertThat(requests.get()).isZero();
    }

    @Test
    void cleanupFailureIsExplicitAndDoesNotClaimSuccessOrDeleteOtherFiles() throws Exception {
        String token = token("population");
        byte[] bytes = content();
        Set<String> before = files();
        doThrow(new DataIntegrityViolationException("SYNTHETIC_INSERT_FAILURE")).when(mapper).insertDraft(any());
        doThrow(new IllegalStateException("SYNTHETIC_FILESYSTEM_SECRET")).when(storage).delete(anyString());
        try {
            MvcResult result = upload(bytes, token, true);
            assertThat(result.getResponse().getStatus()).isEqualTo(500);
            assertThat(result.getResponse().getContentAsString()).contains("补偿失败").doesNotContain("SYNTHETIC_FILESYSTEM_SECRET");
            assertThat(count(bytes)).isZero();
            Set<String> retained = files(); retained.removeAll(before);
            assertThat(retained).hasSize(1);
            assertThat(files()).containsAll(before);
        } finally {
            reset(storage);
            // Explicit test-only recovery of this invocation's new, verified-unreferenced file.
            Set<String> retained = files(); retained.removeAll(before);
            for (String file : retained) {
                assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image WHERE stored_filename=?", Integer.class, file)).isZero();
                storage.delete(file);
            }
        }
    }

    @Test
    void callerTransactionIsRejectedBeforeCreatingAFile() throws Exception {
        Set<String> before = files();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            assertThatThrownBy(() -> service.uploadAndRecognize(
                    new MockMultipartFile("file", "issue3-outer.jpg", "image/jpeg", content()), false, 2L, null))
                    .isInstanceOf(IllegalTransactionStateException.class);
        });
        assertThat(files()).isEqualTo(before);
        assertThat(requests.get()).isZero();
    }

    Map<String,Object> row(long id) { return jdbc.queryForMap("SELECT * FROM person_idcard_image WHERE image_id=?", id); }

    int count(byte[] bytes) throws Exception {
        return jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image WHERE file_sha256=?", Integer.class, sha(bytes));
    }

    String sha(byte[] bytes) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }

    Set<String> files() throws Exception {
        Path root = Path.of("target/issue3-uploads");
        if (!Files.exists(root)) return new java.util.HashSet<>();
        try (var paths = Files.list(root)) { return paths.map(p -> p.getFileName().toString()).collect(Collectors.toSet()); }
    }

    long syntheticPerson() {
        String identity = "SYNTHETIC" + UUID.randomUUID().toString().substring(0,9);
        jdbc.update("INSERT INTO person(name,gender,id_card,status) VALUES('合成绑定','M',?,'正常')", identity);
        return jdbc.queryForObject("SELECT person_id FROM person WHERE id_card=?", Long.class, identity);
    }

    MvcResult createPerson(long image, String identity, String gender, String token) throws Exception {
        return mvc.perform(post("/api/persons").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"合成样例\",\"idCard\":\"" + identity
                        + "\",\"gender\":\"" + gender + "\",\"idCardImageId\":" + image + "}")).andReturn();
    }

    byte[] content() { return ("issue3-synthetic-" + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8); }

    MvcResult upload(byte[] bytes, String token, boolean skip) throws Exception {
        return mvc.perform(multipart("/api/persons/idcard-image")
                .file(new MockMultipartFile("file", "issue3-" + UUID.randomUUID() + ".jpg", "image/jpeg", bytes))
                .param("skipOcr", Boolean.toString(skip)).header("Authorization", "Bearer " + token)).andReturn();
    }

    JsonNode data(MvcResult result) throws Exception {
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return json.readTree(result.getResponse().getContentAsString()).path("data");
    }

    String token(String user) throws Exception {
        var result = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"" + user + "\",\"password\":\"123456\"}")).andReturn();
        return data(result).path("token").asText();
    }
}
