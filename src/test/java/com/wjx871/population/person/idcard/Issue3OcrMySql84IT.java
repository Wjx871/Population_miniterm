package com.wjx871.population.person.idcard;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
/** Same 29 real HTTP/transaction/compensation scenarios, using the isolated MySQL connector. */
class Issue3OcrMySql84IT extends Issue3OcrTransactionIntegrationTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry registry) { Issue3MySqlSupport.configure(registry); }
    @Override @Test
    void simultaneousFirstUploadsUseUniqueConstraintAndOnlyOneResultWrite() throws Exception {
        int recognizedBefore = auditCount("PERSON_IDCARD_IMAGE_RECOGNIZED");
        int reusedBefore = auditCount("PERSON_IDCARD_IMAGE_UPLOAD");
        super.simultaneousFirstUploadsUseUniqueConstraintAndOnlyOneResultWrite();
        assertThat(auditCount("PERSON_IDCARD_IMAGE_RECOGNIZED")-recognizedBefore).isOne();
        assertThat(auditCount("PERSON_IDCARD_IMAGE_UPLOAD")-reusedBefore).isOne();
        System.out.println("ISSUE3_UNIQUE_RACE rows=1 retainedFiles=1 resultWrites=1 successRecognitionAudits=1 reuseAudits=1");
    }
    int auditCount(String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM operation_log WHERE operation_type=?", Integer.class, type); }
}