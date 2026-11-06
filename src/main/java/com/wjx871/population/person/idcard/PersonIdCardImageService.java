package com.wjx871.population.person.idcard;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.LocalFileStorageService;
import com.wjx871.population.person.PersonMapper;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.SensitiveDataMaskingService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Synchronous OCR orchestration. Remote I/O must never inherit a caller transaction. */
@Log4j2
@Service
public class PersonIdCardImageService {
    private final PersonIdCardImageMapper mapper;
    private final PersonMapper personMapper;
    private final LocalFileStorageService storage;
    private final PaddleOcrHttpClient ocrClient;
    private final IdCardOcrProperties ocrProperties;
    private final OcrLogRedactor ocrLogRedactor;
    private final SensitiveDataMaskingService masking;
    private final IdCardImageTransactions transactions;

    public PersonIdCardImageService(PersonIdCardImageMapper mapper, PersonMapper personMapper,
                                    LocalFileStorageService storage, PaddleOcrHttpClient ocrClient,
                                    IdCardOcrProperties ocrProperties, OcrLogRedactor ocrLogRedactor,
                                    SensitiveDataMaskingService masking, IdCardImageTransactions transactions) {
        this.mapper = mapper;
        this.personMapper = personMapper;
        this.storage = storage;
        this.ocrClient = ocrClient;
        this.ocrProperties = ocrProperties;
        this.ocrLogRedactor = ocrLogRedactor;
        this.masking = masking;
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.NEVER)
    public PersonIdCardImageView uploadAndRecognize(MultipartFile file, boolean skipOcr,
                                                    Long userId, HttpServletRequest http) {
        // store() creates a new, exclusive random path; SHA never determines the cleanup path.
        LocalFileStorageService.StoredFile stored;
        try {
            stored = storage.store(file);
        } catch (BusinessException safe) {
            throw safe;
        } catch (RuntimeException failure) {
            log.warn("IDCARD_IMAGE_STORE_FAILED");
            throw persistenceFailure();
        }
        IdCardImageTransactions.Completion completion = null;
        try {
            Optional<PersonIdCardImage> existing = transactions.reuse(stored.sha256(), userId, http);
            if (existing.isPresent()) return PersonIdCardImageView.fromUpload(existing.get());

            PersonIdCardImage row = draft(stored, userId);
            recognize(row, file, skipOcr); // No transaction, connection or business lock is held here.
            completion = new IdCardImageTransactions.Completion();
            try {
                PersonIdCardImage persisted = transactions.persist(row, completion, http);
                return PersonIdCardImageView.fromUpload(persisted);
            } catch (DuplicateKeyException duplicate) {
                // The failed insert TransactionTemplate has completed before we inspect the winner.
                if (!completion.safeToDiscard()) throw persistenceFailure();
                PersonIdCardImage winner = transactions.reuse(stored.sha256(), userId, http)
                        .orElseThrow(PersonIdCardImageService::persistenceFailure);
                return PersonIdCardImageView.fromUpload(winner);
            }
        } catch (BusinessException safe) {
            throw safe;
        } catch (RuntimeException failure) {
            // Do not pass SQL/driver/audit exception bodies to the global exception logger.
            log.warn("IDCARD_IMAGE_PERSIST_FAILED file={}", stored.stored());
            throw persistenceFailure();
        } finally {
            if (completion == null || completion.safeToDiscard()) {
                discardOwnedFile(stored);
            }
            // Committed or indeterminate outcome: retain the file. This is not durable crash recovery.
        }
    }

    private PersonIdCardImage draft(LocalFileStorageService.StoredFile stored, Long userId) {
        PersonIdCardImage row = new PersonIdCardImage();
        row.setDraftUuid(UUID.randomUUID().toString());
        row.setUserId(userId);
        row.setOriginalFilename(stored.original());
        row.setStoredFilename(stored.stored());
        row.setStoragePath(stored.path());
        row.setContentType(stored.contentType());
        row.setFileSize(stored.size());
        row.setFileSha256(stored.sha256());
        row.setCreatedAt(LocalDateTime.now());
        return row;
    }

    private void recognize(PersonIdCardImage row, MultipartFile file, boolean skipOcr) {
        if (skipOcr) {
            row.setOcrStatus(OcrStatus.SKIPPED);
            return;
        }
        long started = System.nanoTime();
        row.setOcrProvider(ocrProperties.getProvider());
        row.setOcrEngineVersion(ocrProperties.getEngineVersion());
        try {
            IdCardOcrProperties.OcrResult result = ocrClient.recognize(file.getBytes(), file.getContentType());
            row.setOcrStatus(OcrStatus.SUCCESS);
            row.setOcrConfidence(result.confidence());
            row.setOcrIdcardFull(result.idCard());
            row.setOcrIdcardMasked(masking.identityAlways(result.idCard()));
            row.setOcrName(result.name());
            row.setOcrBirthDate(result.birthDate());
            row.setOcrGender(result.gender());
            row.setOcrEthnicity(result.ethnicity());
            row.setOcrAddress(result.address());
            row.setOcrRawJson(ocrLogRedactor.redact(result.rawJson()));
        } catch (Exception failure) {
            row.setOcrStatus(OcrStatus.FAILED);
            row.setOcrError(safeOcrError(failure));
        }
        row.setOcrElapsedMs((int) Math.min(Integer.MAX_VALUE, (System.nanoTime() - started) / 1_000_000));
    }

    private void discardOwnedFile(LocalFileStorageService.StoredFile stored) {
        try {
            storage.delete(stored.stored());
        } catch (RuntimeException failure) {
            // A generated opaque filename identifies the retained file without OCR data or raw paths.
            log.error("IDCARD_IMAGE_COMPENSATION_FAILED file={}", stored.stored());
            throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "身份证影印本文件补偿失败，请联系管理员");
        }
    }

    private static BusinessException persistenceFailure() {
        return new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "身份证影印本处理失败");
    }

    private String safeOcrError(Exception failure) {
        if (failure instanceof PaddleOcrHttpClient.OcrRequestException requestFailure) {
            return requestFailure.errorCode();
        }
        if (failure instanceof IllegalStateException) {
            String message = failure.getMessage();
            if ("OCR endpoint is not configured (population.person.idcard.ocr.url is empty)".equals(message)) {
                return "OCR_NOT_CONFIGURED";
            }
            if ("OCR response body is null".equals(message)
                    || (message != null && message.startsWith("OCR response missing"))) {
                return "OCR_INVALID_RESPONSE";
            }
        }
        if (failure instanceof java.io.IOException) return "OCR_INPUT_READ_FAILED";
        return "OCR_FAILED";
    }

    @Transactional(readOnly = true)
    public PersonIdCardImageView getById(Long imageId) {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        PersonIdCardImage row = mapper.selectById(imageId).orElseThrow(this::notFound);
        boolean canAccess = row.getPersonId() == null
                ? Objects.equals(row.getUserId(), user.userId()) || "SYSTEM_ADMIN".equals(user.roleCode())
                : personMapper.selectScopedById(row.getPersonId(), DataScopeCriteria.current()).isPresent();
        if (!canAccess) throw notFound();
        return PersonIdCardImageView.fromDetail(
                row, user.permissions().contains("sensitive-data:view-full"), masking);
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "身份证影印本不存在或不可访问");
    }
}
