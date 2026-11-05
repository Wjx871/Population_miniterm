package com.wjx871.population.material;

import static org.assertj.core.api.Assertions.assertThat;

import com.wjx871.population.auth.AuthService;
import com.wjx871.population.application.ApplicationStatus;
import com.wjx871.population.security.AuthenticatedUser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@SpringBootTest(properties = {
        "population.ai.precheck.enabled=false",
        "AI_PRECHECK_CLEANUP_POLL_MS=3600000"
})
class ApplicationMaterialFeatureOffIntegrationTest {
    private static final Path STORAGE_ROOT = Path.of("target", "s4b-ai-stage-off-test-" + UUID.randomUUID())
            .toAbsolutePath().normalize();

    @org.springframework.test.context.DynamicPropertySource
    static void storage(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("population.ai.precheck.storage-dir", STORAGE_ROOT::toString);
    }

    @Autowired ApplicationContext applicationContext;
    @Autowired JdbcTemplate jdbc;
    @Autowired AuthService authService;
    @Autowired MaterialService materialService;
    @Autowired ApplicationMaterialMapper materialMapper;
    @Autowired LocalFileStorageService localStorage;

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void boundMaterialRemainsReadableAfterAiFeatureIsDisabledAndApplicationStartsWithoutAiStorageBean() throws Exception {
        assertThat(applicationContext.getBeansOfType(com.wjx871.population.ai.precheck.AiPrivateFileStorageService.class))
                .isEmpty();
        AuthenticatedUser owner = authService.loadUser("household");
        String taskId = "task_" + UUID.randomUUID().toString().replace("-", "");
        String stageId = "stg_" + UUID.randomUUID().toString().replace("-", "");
        String objectKey = "obj_" + UUID.randomUUID().toString().replace("-", "") + ".pdf";
        String applicationNo = "S4BOFF-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        byte[] contents = ("%PDF-1.7\nfeature-off bound material\n%%EOF").getBytes(StandardCharsets.UTF_8);
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
        Path objectDirectory = STORAGE_ROOT.resolve("objects");
        Files.createDirectories(objectDirectory);
        Files.write(objectDirectory.resolve(objectKey), contents);

        LocalDateTime now = LocalDateTime.now();
        jdbc.update("INSERT INTO ai_precheck_task(task_id,owner_user_id,owner_data_scope,task_type,business_type,"
                        + "migration_type,request_schema_version,result_schema_version,status,trace_id,request_digest,"
                        + "idempotency_endpoint,idempotency_key,created_at,updated_at) "
                        + "VALUES(?,?,'SELF','DOCUMENT_PRECHECK','MIGRATION_IN','OUTSIDE_CITY','1.0','1.0','SUCCEEDED',?,?,?, ?,?,?)",
                taskId, owner.userId(), "feature-off-trace", "a".repeat(64), "POST /api/ai/tasks",
                "feature-off-" + UUID.randomUUID(), java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now));
        jdbc.update("INSERT INTO ai_stage_material(stage_material_id,owner_user_id,owner_data_scope,business_type,"
                        + "document_role_hint,original_filename,stored_object_key,content_type,file_extension,file_size,"
                        + "sha256,request_digest,idempotency_endpoint,idempotency_key,status,created_at,updated_at,version) "
                        + "VALUES(?,?,'SELF','MIGRATION_IN','UNKNOWN','feature-off.pdf',?,'application/pdf','pdf',?,?,?,"
                        + "'POST /api/ai/materials',?,'BOUND',?,?,1)",
                stageId, owner.userId(), objectKey, contents.length, sha, "b".repeat(64),
                "feature-off-" + UUID.randomUUID(), java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now));
        jdbc.update("INSERT INTO ai_precheck_task_material(task_id,stage_material_id,material_order,document_role_hint,"
                        + "content_type,file_size,sha256,original_filename,created_at) VALUES(?,?,0,'UNKNOWN','application/pdf',?,?,?,?)",
                taskId, stageId, contents.length, sha, "feature-off.pdf", java.sql.Timestamp.valueOf(now));

        jdbc.update("INSERT INTO business_application(application_no,business_type,title,applicant_user_id,"
                        + "applicant_department_id,applicant_region_code,target_person_id,target_household_id,status,reason,"
                        + "version,created_at,updated_at) VALUES(?,'MIGRATION_IN','feature-off draft',?,?,?,9801,9801,'DRAFT',?,0,?,?)",
                applicationNo, owner.userId(), owner.departmentId(), owner.regionCode(), "synthetic test",
                java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now));
        long applicationId = jdbc.queryForObject("SELECT application_id FROM business_application WHERE application_no=?",
                Long.class, applicationNo);
        jdbc.update("INSERT INTO application_material(application_id,material_type,material_name,original_filename,"
                        + "stored_filename,storage_path,storage_kind,storage_reference,content_type,file_size,file_sha256,"
                        + "required_flag,verify_status,uploaded_by) VALUES(?,'IDENTITY_PROOF','feature-off.pdf',"
                        + "'feature-off.pdf',?,'AI_STAGE_REFERENCE','AI_STAGE',?,'application/pdf',?,?,'0','PENDING',?)",
                applicationId, stageId, stageId, contents.length, sha, owner.userId());
        long materialId = jdbc.queryForObject("SELECT material_id FROM application_material WHERE application_id=?",
                Long.class, applicationId);
        jdbc.update("INSERT INTO ai_stage_material_binding(stage_material_id,material_id,task_id,application_id,"
                        + "owner_user_id,status,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',?,?)",
                stageId, materialId, taskId, applicationId, owner.userId(),
                java.sql.Timestamp.valueOf(now), java.sql.Timestamp.valueOf(now));

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(owner, null, owner.getAuthorities()));
        MaterialService.Download downloaded = materialService.download(materialId);
        assertThat(downloaded.resource().getInputStream().readAllBytes()).isEqualTo(contents);
        assertThat(downloaded.filename()).isEqualTo("feature-off.pdf");
        ApplicationMaterial persisted = materialMapper.selectById(materialId).orElseThrow();
        ApplicationMaterialStorageResolver freshResolver = new ApplicationMaterialStorageResolver(localStorage,
                new BoundStageMaterialStorage(STORAGE_ROOT.toString(), "10"), jdbc);
        assertThat(freshResolver.load(persisted).getInputStream().readAllBytes()).isEqualTo(contents);
        assertThat(jdbc.queryForObject("SELECT status FROM ai_stage_material WHERE stage_material_id=?",
                String.class, stageId)).isEqualTo("BOUND");
        assertThat(ApplicationStatus.DRAFT.name()).isEqualTo(jdbc.queryForObject(
                "SELECT status FROM business_application WHERE application_id=?", String.class, applicationId));
    }
}
