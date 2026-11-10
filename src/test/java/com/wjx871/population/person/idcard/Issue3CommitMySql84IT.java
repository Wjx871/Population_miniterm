package com.wjx871.population.person.idcard;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
/** Real MySQL commit/rollback plus synthetic lost JDBC acknowledgement; UNKNOWN must retain files. */
class Issue3CommitMySql84IT extends Issue3CommitOutcomeIntegrationTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) { Issue3MySqlSupport.configure(registry); }
    @Override @ParameterizedTest @ValueSource(booleans={true,false})
    void uncertainCommitRetainsOwnedFileWhetherOrNotDatabaseCommitted(boolean physicalCommit) throws Exception {
        int imagesBefore=jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image",Integer.class);
        int auditsBefore=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='PERSON_IDCARD_IMAGE_UPLOAD'",Integer.class);
        super.uncertainCommitRetainsOwnedFileWhetherOrNotDatabaseCommitted(physicalCommit);
        int images=jdbc.queryForObject("SELECT COUNT(*) FROM person_idcard_image",Integer.class)-imagesBefore;
        int audits=jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type='PERSON_IDCARD_IMAGE_UPLOAD'",Integer.class)-auditsBefore;
        assertThat(images).isEqualTo(physicalCommit?1:0); assertThat(audits).isEqualTo(images);
        System.out.printf("ISSUE3_COMMIT_ACK_LOST physicalCommit=%s completion=UNKNOWN images=%d requiredAudits=%d fileRetainedAtFailure=true%n",physicalCommit,images,audits);
    }
}