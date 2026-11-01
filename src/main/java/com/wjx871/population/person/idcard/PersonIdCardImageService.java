package com.wjx871.population.person.idcard;

import com.wjx871.population.audit.OperationLogMapper;
import com.wjx871.population.audit.OperationLog;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Phase 14 / V4_013 人口-身份证影印本服务。 */
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
    private final OperationLogMapper operationLogMapper;

    public PersonIdCardImageService(PersonIdCardImageMapper mapper,
                                    PersonMapper personMapper,
                                    LocalFileStorageService storage,
                                    PaddleOcrHttpClient ocrClient,
                                    IdCardOcrProperties ocrProperties,
                                    OcrLogRedactor ocrLogRedactor,
                                    SensitiveDataMaskingService masking,
                                    OperationLogMapper operationLogMapper) {
        this.mapper = mapper;
        this.personMapper = personMapper;
        this.storage = storage;
        this.ocrClient = ocrClient;
        this.ocrProperties = ocrProperties;
        this.ocrLogRedactor = ocrLogRedactor;
        this.masking = masking;
        this.operationLogMapper = operationLogMapper;
    }

    /**
     * 上传身份证影印本：先落盘（去重），再视 skipOcr 决定是否调用 OCR。
     */
    @Transactional
    public PersonIdCardImageView uploadAndRecognize(MultipartFile file,
                                                    boolean skipOcr,
                                                    Long userId,
                                                    HttpServletRequest http) {
        // 1) 落盘 + 算 SHA。去重只复用同一上传者的未绑定草稿。
        LocalFileStorageService.StoredFile stored = storage.store(file);
        PersonIdCardImage row;

        Optional<PersonIdCardImage> existing = mapper.selectBySha256(stored.sha256());
        if (existing.isPresent()) {
            row = existing.get();
            storage.delete(stored.stored());
            if (!Objects.equals(row.getUserId(), userId) || row.getPersonId() != null) {
                throw conflict("该身份证影印本不可重复使用");
            }
        } else {
            row = new PersonIdCardImage();
            row.setDraftUuid(UUID.randomUUID().toString());
            row.setUserId(userId);
            row.setOriginalFilename(stored.original());
            row.setStoredFilename(stored.stored());
            row.setStoragePath(stored.path());
            row.setContentType(stored.contentType());
            row.setFileSize(stored.size());
            row.setFileSha256(stored.sha256());
            row.setCreatedAt(LocalDateTime.now());
            try {
                mapper.insertDraft(row);
            } catch (org.springframework.dao.DuplicateKeyException e) {
                storage.delete(stored.stored());
                throw conflict("该身份证影印本已存在，请重新上传");
            } catch (RuntimeException e) {
                // 落库失败时回滚物理文件
                storage.delete(stored.stored());
                throw e;
            }
        }

        // 2) OCR 调度
        if (skipOcr) {
            if (mapper.markOcrSkipped(row.getImageId(), userId) != 1) {
                throw conflict("身份证影印本状态已变化，请重新上传");
            }
            row.setOcrStatus(OcrStatus.SKIPPED);
            recordAudit(userId, "PERSON_IDCARD_IMAGE_UPLOAD", "SKIPPED", null, http);
        } else {
            long t0 = System.currentTimeMillis();
            IdCardOcrProperties.OcrResult result = null;
            Exception ocrFailure = null;
            try {
                result = ocrClient.recognize(file.getBytes(), file.getContentType());
            } catch (Exception e) {
                ocrFailure = e;
            }
            long elapsed = System.currentTimeMillis() - t0;
            if (ocrFailure == null) {
                String masked = masking.identity(result.idCard());
                String redactedRaw = ocrLogRedactor.redact(result.rawJson());
                if (mapper.markOcrSuccess(row.getImageId(),
                        ocrProperties.getProvider(),
                        ocrProperties.getEngineVersion(),
                        (int) elapsed,
                        result.confidence(),
                        result.idCard(),
                        masked,
                        result.name(),
                        result.birthDate(),
                        result.gender(),
                        result.ethnicity(),
                        result.address(),
                        redactedRaw,
                        userId) != 1) {
                    throw conflict("身份证影印本状态已变化，请重新上传");
                }
                row.setOcrStatus(OcrStatus.SUCCESS);
                row.setOcrProvider(ocrProperties.getProvider());
                row.setOcrEngineVersion(ocrProperties.getEngineVersion());
                row.setOcrElapsedMs((int) elapsed);
                row.setOcrConfidence(result.confidence());
                row.setOcrIdcardFull(result.idCard());
                row.setOcrIdcardMasked(masked);
                row.setOcrName(result.name());
                row.setOcrBirthDate(result.birthDate());
                row.setOcrGender(result.gender());
                row.setOcrEthnicity(result.ethnicity());
                row.setOcrAddress(result.address());
                row.setOcrRawJson(redactedRaw);
                recordAudit(userId, "PERSON_IDCARD_IMAGE_RECOGNIZED", "SUCCESS", null, http);
            } else {
                String msg = safeOcrError(ocrFailure);
                if (mapper.markOcrFailed(row.getImageId(),
                        ocrProperties.getProvider(),
                        ocrProperties.getEngineVersion(),
                        (int) elapsed,
                        msg,
                        userId) != 1) {
                    throw conflict("身份证影印本状态已变化，请重新上传");
                }
                row.setOcrStatus(OcrStatus.FAILED);
                row.setOcrError(msg);
                recordAudit(userId, "PERSON_IDCARD_IMAGE_RECOGNIZED", "FAILED", msg, http);
            }
        }
        return PersonIdCardImageView.fromUpload(row);
    }

    @Transactional(readOnly = true)
    public PersonIdCardImageView getById(Long imageId) {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        PersonIdCardImage row = mapper.selectById(imageId)
                .orElseThrow(() -> notFound());

        boolean canAccess = row.getPersonId() == null
                ? Objects.equals(row.getUserId(), user.userId()) || "SYSTEM_ADMIN".equals(user.roleCode())
                : personMapper.selectScopedById(row.getPersonId(), DataScopeCriteria.current()).isPresent();
        if (!canAccess) throw notFound();

        return PersonIdCardImageView.fromDetail(
                row, user.permissions().contains("sensitive-data:view-full"), masking);
    }

    private BusinessException conflict(String message) {
        return new BusinessException(HttpStatus.CONFLICT, message);
    }

    private BusinessException notFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "身份证影印本不存在或不可访问");
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

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void recordAudit(Long userId, String opType, String result, String err, HttpServletRequest http) {
        try {
            operationLogMapper.insert(OperationLog.builder()
                    .userId(userId)
                    .operationType(opType)
                    .moduleName("PERSON")
                    .requestPath(http == null ? null : http.getRequestURI())
                    .requestMethod(http == null ? null : http.getMethod())
                    .operationResult(result)
                    .errorMessage(err)
                    .ipAddress(http == null ? null : clientIp(http))
                    .userAgent(http == null ? null : truncate(http.getHeader("User-Agent"), 500))
                    .detail(opType)
                    .build());
        } catch (RuntimeException ex) {
            log.warn("Failed to persist person idcard image audit log: {}", ex.getMessage());
        }
    }

    private static String clientIp(HttpServletRequest request) {
        if (request == null) return null;
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return truncate(forwarded.split(",")[0].trim(), 50);
        }
        return truncate(request.getRemoteAddr(), 50);
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
