package com.wjx871.population.person.idcard;

import com.wjx871.population.audit.OperationLog;
import com.wjx871.population.audit.OperationLogMapper;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.SensitiveDataMaskingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Objects;
import java.util.Optional;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Explicit short JDBC transactions; never performs OCR or changes the application's default manager. */
@Service
public class IdCardImageTransactions {
    private final PersonIdCardImageMapper mapper;
    private final OperationLogMapper audit;
    private final SensitiveDataMaskingService masking;
    private final TransactionTemplate transaction;

    public IdCardImageTransactions(PersonIdCardImageMapper mapper, OperationLogMapper audit,
                                   SensitiveDataMaskingService masking, DataSource dataSource) {
        this.mapper = mapper;
        this.audit = audit;
        this.masking = masking;
        // Plain JDBC manager wraps commit SQLException as TransactionSystemException (UNKNOWN).
        // JdbcTransactionManager can translate it into a RuntimeException and report ROLLED_BACK
        // after a rollback attempt, even when the server already committed and its ACK was lost.
        // Keep this manager local: do not replace the application's transaction manager bean.
        DataSourceTransactionManager manager = new DataSourceTransactionManager(dataSource);
        manager.setRollbackOnCommitFailure(false);
        this.transaction = new TransactionTemplate(manager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Optional<PersonIdCardImage> reuse(String sha, Long userId, HttpServletRequest http) {
        return transaction.execute(status -> reuseWithinTransaction(sha, userId, http));
    }

    private Optional<PersonIdCardImage> reuseWithinTransaction(String sha, Long userId, HttpServletRequest http) {
        Optional<PersonIdCardImage> found = mapper.selectBySha256ForUpdate(sha);
        found.ifPresent(row -> {
            if (!Objects.equals(row.getUserId(), userId) || row.getPersonId() != null) {
                throw new BusinessException(HttpStatus.CONFLICT, "该身份证影印本不可重复使用");
            }
            recordAudit(userId, "PERSON_IDCARD_IMAGE_UPLOAD", row.getOcrStatus().name(), null, http);
        });
        return found;
    }

    public PersonIdCardImage persist(PersonIdCardImage row, Completion completion, HttpServletRequest http) {
        return transaction.execute(status -> persistWithinTransaction(row, completion, http));
    }

    private PersonIdCardImage persistWithinTransaction(PersonIdCardImage row, Completion completion, HttpServletRequest http) {
        completion.started = true;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { completion.status = status; }
        });
        if (mapper.insertDraft(row) != 1) throw new IllegalStateException("IDCARD_IMAGE_INSERT_FAILED");
        int updated = switch (row.getOcrStatus()) {
            case SKIPPED -> mapper.markOcrSkipped(row.getImageId(), row.getUserId());
            case FAILED -> mapper.markOcrFailed(row.getImageId(), row.getOcrProvider(), row.getOcrEngineVersion(),
                    row.getOcrElapsedMs(), row.getOcrError(), row.getUserId());
            case SUCCESS -> mapper.markOcrSuccess(row.getImageId(), row.getOcrProvider(), row.getOcrEngineVersion(),
                    row.getOcrElapsedMs(), row.getOcrConfidence(), row.getOcrIdcardFull(), row.getOcrIdcardMasked(),
                    row.getOcrName(), row.getOcrBirthDate(), row.getOcrGender(), row.getOcrEthnicity(),
                    row.getOcrAddress(), row.getOcrRawJson(), row.getUserId());
        };
        if (updated != 1) throw new BusinessException(HttpStatus.CONFLICT, "身份证影印本状态已变化，请重新上传");
        recordAudit(row.getUserId(), row.getOcrStatus() == OcrStatus.SKIPPED
                ? "PERSON_IDCARD_IMAGE_UPLOAD" : "PERSON_IDCARD_IMAGE_RECOGNIZED",
                row.getOcrStatus().name(), row.getOcrError(), http);
        return mapper.selectById(row.getImageId()).orElseThrow();
    }

    // Required audit shares the result transaction. Failures must propagate and roll it back.
    private void recordAudit(Long userId, String type, String result, String error, HttpServletRequest http) {
        int inserted = audit.insert(OperationLog.builder().userId(userId).operationType(type).moduleName("PERSON")
                .requestPath(http == null ? null : http.getRequestURI())
                .requestMethod(http == null ? null : http.getMethod()).operationResult(result).errorMessage(error)
                .ipAddress(http == null ? null : truncate(http.getRemoteAddr(), 50))
                .userAgent(http == null ? null : truncate(masking.auditDetail(http.getHeader("User-Agent")), 500))
                .detail(type).build());
        if (inserted != 1) throw new IllegalStateException("IDCARD_IMAGE_AUDIT_FAILED");
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    /** In-memory observation of this invocation, not durable crash recovery. */
    public static final class Completion {
        private boolean started;
        private int status = TransactionSynchronization.STATUS_UNKNOWN;

        public boolean safeToDiscard() {
            return !started || status == TransactionSynchronization.STATUS_ROLLED_BACK;
        }
    }
}
