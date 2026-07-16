package com.wjx871.population.person.idcard;

import com.wjx871.population.audit.OperationLogMapper;
import com.wjx871.population.audit.OperationLog;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.LocalFileStorageService;
import com.wjx871.population.security.SensitiveDataMaskingService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
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
    private final LocalFileStorageService storage;
    private final PaddleOcrHttpClient ocrClient;
    private final IdCardOcrProperties ocrProperties;
    private final OcrLogRedactor ocrLogRedactor;
    private final SensitiveDataMaskingService masking;
    private final OperationLogMapper operationLogMapper;

    public PersonIdCardImageService(PersonIdCardImageMapper mapper,
                                    LocalFileStorageService storage,
                                    PaddleOcrHttpClient ocrClient,
                                    IdCardOcrProperties ocrProperties,
                                    OcrLogRedactor ocrLogRedactor,
                                    SensitiveDataMaskingService masking,
                                    OperationLogMapper operationLogMapper) {
        this.mapper = mapper;
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
        // 1) 落盘 + 算 SHA
        LocalFileStorageService.StoredFile stored = storage.store(file);
        PersonIdCardImage row;

        Optional<PersonIdCardImage> existing = mapper.selectBySha256(stored.sha256());
        if (existing.isPresent()) {
            row = existing.get();
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
            } catch (RuntimeException e) {
                // 落库失败时回滚物理文件
                storage.delete(stored.stored());
                throw e;
            }
        }

        // 2) OCR 调度
        if (skipOcr) {
            mapper.markOcrSkipped(row.getImageId());
            row.setOcrStatus(OcrStatus.SKIPPED);
            recordAudit(userId, "PERSON_IDCARD_IMAGE_UPLOAD", "SKIPPED", null, http);
        } else {
            long t0 = System.currentTimeMillis();
            try {
                IdCardOcrProperties.OcrResult r = ocrClient.recognize(file.getBytes(), file.getContentType());
                long elapsed = System.currentTimeMillis() - t0;
                String masked = masking.identity(r.idCard());
                String redactedRaw = ocrLogRedactor.redact(r.rawJson());
                mapper.markOcrSuccess(row.getImageId(),
                        ocrProperties.getProvider(),
                        ocrProperties.getEngineVersion(),
                        (int) elapsed,
                        r.confidence(),
                        r.idCard(),
                        masked,
                        r.name(),
                        r.birthDate(),
                        r.gender(),
                        r.ethnicity(),
                        r.address(),
                        redactedRaw);
                row.setOcrStatus(OcrStatus.SUCCESS);
                row.setOcrIdcardFull(r.idCard());
                row.setOcrIdcardMasked(masked);
                recordAudit(userId, "PERSON_IDCARD_IMAGE_RECOGNIZED", "SUCCESS", null, http);
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - t0;
                String msg = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                if (msg.length() > 240) msg = msg.substring(0, 240);
                mapper.markOcrFailed(row.getImageId(),
                        ocrProperties.getProvider(),
                        ocrProperties.getEngineVersion(),
                        (int) elapsed,
                        msg);
                row.setOcrStatus(OcrStatus.FAILED);
                row.setOcrError(msg);
                recordAudit(userId, "PERSON_IDCARD_IMAGE_RECOGNIZED", "FAILED", msg, http);
            }
        }
        return PersonIdCardImageView.from(row);
    }

    @Transactional(readOnly = true)
    public PersonIdCardImageView getById(Long imageId) {
        return mapper.selectById(imageId)
                .map(PersonIdCardImageView::from)
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "身份证影印本不存在: " + imageId));
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
