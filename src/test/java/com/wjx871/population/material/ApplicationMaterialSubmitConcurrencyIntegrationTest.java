package com.wjx871.population.material;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import com.wjx871.population.approval.ApprovalService;
import com.wjx871.population.application.ApplicationRequest;
import com.wjx871.population.application.ApplicationService;
import com.wjx871.population.application.BusinessType;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.util.AopTestUtils;

@SpringBootTest(properties = {
        "population.ai.precheck.enabled=false",
        "AI_PRECHECK_CLEANUP_POLL_MS=3600000"
})
class ApplicationMaterialSubmitConcurrencyIntegrationTest {
    private static final Path AI_STORAGE = Path.of("target", "s4b-submit-lock-" + UUID.randomUUID())
            .toAbsolutePath().normalize();

    @DynamicPropertySource
    static void configureStorage(DynamicPropertyRegistry registry) {
        registry.add("population.ai.precheck.storage-dir", AI_STORAGE::toString);
    }

    @Autowired ApplicationService applications;
    @Autowired ApprovalService approvals;
    @Autowired MaterialService materialService;
    @Autowired ApplicationMaterialMapper materials;
    @Autowired AuthService authService;
    @Autowired JdbcTemplate jdbc;
    @SpyBean ApplicationService applicationServiceSpy;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
        reset(applicationServiceTarget());
    }

    @Test
    void ordinaryDeleteLocksDraftBeforeSubmitAndRequiredMaterialIsRechecked() throws Exception {
        exerciseDeleteBeforeSubmit(false);
    }

    @Test
    void aiStageDeleteLocksDraftBeforeSubmitAndRequiredMaterialIsRechecked() throws Exception {
        exerciseDeleteBeforeSubmit(true);
    }

    @Test
    void ordinarySubmitLocksDraftBeforeDeleteAndPreservesFileAfterReviewStarts() throws Exception {
        exerciseSubmitBeforeDelete(false);
    }

    @Test
    void aiStageSubmitLocksDraftBeforeDeleteAndPreservesBindingAfterReviewStarts() throws Exception {
        exerciseSubmitBeforeDelete(true);
    }

    @Test
    void ordinaryUploadLocksDraftBeforeSubmitAndSubmitSeesCommittedMaterial() throws Exception {
        AuthenticatedUser owner = authService.loadUser("population");
        long applicationId = createDraft(owner);
        CountDownLatch uploadPausedAfterLock = new CountDownLatch(1);
        CountDownLatch releaseUpload = new CountDownLatch(1);
        CountDownLatch submitLockAttempt = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = lockCalls.incrementAndGet();
            if (call == 1) {
                Object application = invocation.callRealMethod();
                uploadPausedAfterLock.countDown();
                assertThat(releaseUpload.await(10, TimeUnit.SECONDS)).isTrue();
                return application;
            }
            if (call == 2) submitLockAttempt.countDown();
            return invocation.callRealMethod();
        }).when(applicationServiceTarget()).requireForUpdate(applicationId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> upload = executor.submit(() -> captureAs(owner, () -> {
                materialService.upload(applicationId, "PROOF", "并发上传证明", true,
                        new MockMultipartFile("file", "upload-race.pdf", "application/pdf", pdf("upload-race")));
                return null;
            }));
            assertHookReached(uploadPausedAfterLock, upload, "upload did not acquire the application lock");
            Future<Throwable> submission = executor.submit(() -> captureAs(owner, () -> {
                approvals.submit(applicationId, new MockHttpServletRequest());
                return null;
            }));
            assertHookReached(submitLockAttempt, submission, "submit did not attempt the application lock");
            assertThat(submission.isDone()).isFalse();
            releaseUpload.countDown();

            assertThat(upload.get(10, TimeUnit.SECONDS)).isNull();
            assertThat(submission.get(10, TimeUnit.SECONDS)).isNull();
        } finally {
            releaseUpload.countDown();
            executor.shutdownNow();
        }
        assertThat(status(applicationId)).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE application_id=? AND required_flag=1",
                Integer.class, applicationId)).isOne();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_request WHERE application_id=?",
                Integer.class, applicationId)).isOne();
    }

    @Test
    void submitLocksDraftBeforeOrdinaryUploadAndUploadIsRejectedAfterReviewStarts() throws Exception {
        Fixture fixture = createFixture(false);
        CountDownLatch submitPausedAfterLock = new CountDownLatch(1);
        CountDownLatch releaseSubmit = new CountDownLatch(1);
        CountDownLatch uploadLockAttempt = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = lockCalls.incrementAndGet();
            if (call == 1) {
                Object application = invocation.callRealMethod();
                submitPausedAfterLock.countDown();
                assertThat(releaseSubmit.await(10, TimeUnit.SECONDS)).isTrue();
                return application;
            }
            if (call == 2) uploadLockAttempt.countDown();
            return invocation.callRealMethod();
        }).when(applicationServiceTarget()).requireForUpdate(fixture.applicationId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> submission = executor.submit(() -> captureAs(fixture.owner(), () -> {
                approvals.submit(fixture.applicationId(), new MockHttpServletRequest());
                return null;
            }));
            assertHookReached(submitPausedAfterLock, submission, "submit did not acquire the application lock");
            Future<Throwable> upload = executor.submit(() -> captureAs(fixture.owner(), () -> {
                materialService.upload(fixture.applicationId(), "OPTIONAL", "并发可选材料", false,
                        new MockMultipartFile("file", "upload-rejected.pdf", "application/pdf", pdf("upload-rejected")));
                return null;
            }));
            assertHookReached(uploadLockAttempt, upload, "upload did not attempt the application lock");
            assertThat(upload.isDone()).isFalse();
            releaseSubmit.countDown();

            assertThat(submission.get(10, TimeUnit.SECONDS)).isNull();
            assertConflict(upload.get(10, TimeUnit.SECONDS), "只有草稿或退回补正申请可以上传材料");
        } finally {
            releaseSubmit.countDown();
            executor.shutdownNow();
        }
        assertThat(status(fixture.applicationId())).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE application_id=?",
                Integer.class, fixture.applicationId())).isOne();
    }

    private void exerciseDeleteBeforeSubmit(boolean aiStage) throws Exception {
        Fixture fixture = createFixture(aiStage);
        CountDownLatch deletePaused = new CountDownLatch(1);
        CountDownLatch releaseDelete = new CountDownLatch(1);
        CountDownLatch submitLockAttempt = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = lockCalls.incrementAndGet();
            if (call == 1) {
                Object application = invocation.callRealMethod();
                deletePaused.countDown();
                assertThat(releaseDelete.await(10, TimeUnit.SECONDS)).isTrue();
                return application;
            }
            if (call == 2) {
                submitLockAttempt.countDown();
            }
            return invocation.callRealMethod();
        }).when(applicationServiceTarget()).requireForUpdate(fixture.applicationId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> deletion = executor.submit(() -> captureAs(fixture.owner(), () -> {
                materialService.delete(fixture.materialId());
                return null;
            }));
            assertHookReached(deletePaused, deletion, "material delete did not reach its controlled pause");

            Future<Throwable> submission = executor.submit(() -> captureAs(fixture.owner(), () -> {
                approvals.submit(fixture.applicationId(), new MockHttpServletRequest());
                return null;
            }));
            assertHookReached(submitLockAttempt, submission, "submit did not attempt the application lock");
            assertThat(submission.isDone()).isFalse();
            releaseDelete.countDown();

            assertThat(deletion.get(10, TimeUnit.SECONDS)).isNull();
            Throwable submitFailure = submission.get(10, TimeUnit.SECONDS);
            assertConflict(submitFailure, "缺少必需材料，无法提交");
        } finally {
            releaseDelete.countDown();
            executor.shutdownNow();
        }

        assertThat(status(fixture.applicationId())).isEqualTo("DRAFT");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE material_id=?",
                Integer.class, fixture.materialId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_request WHERE application_id=?",
                Integer.class, fixture.applicationId())).isZero();
        if (aiStage) {
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, fixture.stageId())).isEqualTo("CANCELLED");
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material_binding WHERE stage_material_id=?",
                    String.class, fixture.stageId())).isEqualTo("RELEASED");
            assertThat(Files.exists(fixture.file())).isFalse();
        } else {
            assertThat(Files.exists(fixture.file())).isFalse();
        }
    }

    private void exerciseSubmitBeforeDelete(boolean aiStage) throws Exception {
        Fixture fixture = createFixture(aiStage);
        CountDownLatch submitPausedAfterLock = new CountDownLatch(1);
        CountDownLatch releaseSubmit = new CountDownLatch(1);
        CountDownLatch deleteLockAttempt = new CountDownLatch(1);
        AtomicInteger lockCalls = new AtomicInteger();
        doAnswer(invocation -> {
            int call = lockCalls.incrementAndGet();
            if (call == 1) {
                Object application = invocation.callRealMethod();
                submitPausedAfterLock.countDown();
                assertThat(releaseSubmit.await(10, TimeUnit.SECONDS)).isTrue();
                return application;
            }
            if (call == 2) {
                deleteLockAttempt.countDown();
            }
            return invocation.callRealMethod();
        }).when(applicationServiceTarget()).requireForUpdate(fixture.applicationId());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> submission = executor.submit(() -> captureAs(fixture.owner(), () -> {
                approvals.submit(fixture.applicationId(), new MockHttpServletRequest());
                return null;
            }));
            assertHookReached(submitPausedAfterLock, submission, "submit did not acquire the application lock");

            Future<Throwable> deletion = executor.submit(() -> captureAs(fixture.owner(), () -> {
                materialService.delete(fixture.materialId());
                return null;
            }));
            assertHookReached(deleteLockAttempt, deletion, "delete did not attempt the application lock");
            assertThat(deletion.isDone()).isFalse();
            releaseSubmit.countDown();

            assertThat(submission.get(10, TimeUnit.SECONDS)).isNull();
            Throwable deleteFailure = deletion.get(10, TimeUnit.SECONDS);
            assertConflict(deleteFailure, "仅草稿或退回补正申请可以删除材料");
        } finally {
            releaseSubmit.countDown();
            executor.shutdownNow();
        }

        assertThat(status(fixture.applicationId())).isEqualTo("UNDER_REVIEW");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM application_material WHERE material_id=?",
                Integer.class, fixture.materialId())).isOne();
        assertThat(Files.exists(fixture.file())).isTrue();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_approval_request WHERE application_id=? AND status='PENDING'",
                Integer.class, fixture.applicationId())).isOne();
        if (aiStage) {
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                    String.class, fixture.stageId())).isEqualTo("BOUND");
            assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material_binding WHERE stage_material_id=?",
                    String.class, fixture.stageId())).isEqualTo("ACTIVE");
        }
    }

    private Fixture createFixture(boolean aiStage) throws Exception {
        AuthenticatedUser owner = authService.loadUser("population");
        long applicationId = createDraft(owner);
        if (!aiStage) {
            MaterialView material = invokeAs(owner, () -> materialService.upload(applicationId, "PROOF", "必需证明",
                    true, new MockMultipartFile("file", "proof.pdf", "application/pdf", pdf("ordinary-proof"))));
            ApplicationMaterial row = materials.selectById(material.materialId()).orElseThrow();
            reset(applicationServiceTarget());
            return new Fixture(owner, applicationId, material.materialId(), null,
                    Path.of(row.getStoragePath()), false);
        }
        return createAiStageFixture(owner, applicationId);
    }

    private long createDraft(AuthenticatedUser owner) throws Exception {
        return invokeAs(owner, () -> applications.create(new ApplicationRequest(
                BusinessType.GENERAL_SERVICE, "并发材料测试-" + UUID.randomUUID(), null, null,
                "锁顺序回归", null, null), new MockHttpServletRequest()).applicationId());
    }

    private Fixture createAiStageFixture(AuthenticatedUser owner, long applicationId) throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String taskId = "task_" + suffix;
        String stageId = "stg_" + suffix;
        String objectKey = "obj_" + suffix + ".pdf";
        byte[] bytes = pdf("bound-stage-proof");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        Path file = AI_STORAGE.resolve("objects").resolve(objectKey);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
        LocalDateTime now = LocalDateTime.now();

        jdbc.update("INSERT INTO ai_precheck_task(task_id,owner_user_id,owner_department_id,owner_region_code,"
                        + "owner_data_scope,task_type,business_type,migration_type,request_schema_version,result_schema_version,"
                        + "status,trace_id,request_digest,idempotency_endpoint,idempotency_key,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,'DOCUMENT_PRECHECK','MIGRATION_IN','OUTSIDE_CITY','1.0','1.0','SUCCEEDED',?,?,?,?,?,?)",
                taskId, owner.userId(), owner.departmentId(), owner.regionCode(), "SELF", "trace-" + suffix,
                "a".repeat(64), "POST /api/ai/tasks", "lock-" + suffix,
                Timestamp.valueOf(now), Timestamp.valueOf(now));
        jdbc.update("INSERT INTO ai_stage_material(stage_material_id,owner_user_id,owner_department_id,owner_region_code,"
                        + "owner_data_scope,business_type,document_role_hint,original_filename,stored_object_key,content_type,"
                        + "file_extension,file_size,sha256,request_digest,idempotency_endpoint,idempotency_key,status,uploaded_at,"
                        + "expires_at,version) VALUES(?,?,?,?,?,'MIGRATION_IN','UNKNOWN','bound-proof.pdf',?,'application/pdf',"
                        + "'pdf',?,?,?,'POST /api/ai/materials',?,'BOUND',?,?,1)",
                stageId, owner.userId(), owner.departmentId(), owner.regionCode(), "SELF", objectKey,
                bytes.length, hash, "b".repeat(64), "lock-" + suffix, Timestamp.valueOf(now),
                Timestamp.valueOf(now.plusHours(1)));
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,"
                        + "stored_filename,storage_path,storage_kind,storage_reference,content_type,file_size,file_sha256,"
                        + "required_flag,verify_status,uploaded_by) VALUES(?,'IDENTITY_PROOF','bound proof','bound-proof.pdf',"
                        + "?,'AI_STAGE_REFERENCE','AI_STAGE',?,'application/pdf',?,?,'1','PENDING',?)",
                applicationId, stageId, stageId, bytes.length, hash, owner.userId());
        long materialId = jdbc.queryForObject("SELECT material_id FROM application_material WHERE storage_reference=?",
                Long.class, stageId);
        jdbc.update("INSERT INTO ai_stage_material_binding(stage_material_id,material_id,task_id,application_id,owner_user_id,"
                        + "status,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',?,?)",
                stageId, materialId, taskId, applicationId, owner.userId(), Timestamp.valueOf(now), Timestamp.valueOf(now));
        reset(applicationServiceTarget());
        return new Fixture(owner, applicationId, materialId, stageId, file, true);
    }

    private <T> T invokeAs(AuthenticatedUser user, ThrowingSupplier<T> action) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private Throwable captureAs(AuthenticatedUser user, ThrowingSupplier<Void> action) {
        try {
            invokeAs(user, action);
            return null;
        } catch (Throwable failure) {
            return failure;
        }
    }

    private ApplicationService applicationServiceTarget() {
        return AopTestUtils.getUltimateTargetObject(applicationServiceSpy);
    }

    private void assertConflict(Throwable failure, String message) {
        assertThat(failure).isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.getMessage()).isEqualTo(message));
    }

    private void assertHookReached(CountDownLatch latch, Future<?> operation, String failureMessage) throws Exception {
        if (!latch.await(10, TimeUnit.SECONDS)) {
            operation.get(1, TimeUnit.SECONDS);
            throw new AssertionError(failureMessage);
        }
    }

    private String status(long applicationId) {
        return jdbc.queryForObject("SELECT status FROM business_application WHERE application_id=?",
                String.class, applicationId);
    }

    private byte[] pdf(String value) {
        return ("%PDF-1.7\n" + value + "\n%%EOF").getBytes(StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> { T get() throws Exception; }

    private record Fixture(AuthenticatedUser owner, long applicationId, long materialId, String stageId,
                          Path file, boolean aiStage) { }
}
