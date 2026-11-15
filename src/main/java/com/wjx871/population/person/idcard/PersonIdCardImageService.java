package com.wjx871.population.person.idcard;

import com.wjx871.population.audit.OperationLogMapper;
import com.wjx871.population.audit.OperationLog;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.LocalFileStorageService;
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
    private final LocalFileStorageService storage;
    private final PaddleOcrHttpClient ocrClient;
    private final IdCardOcrProperties ocrProperties;
    private final SensitiveDataMaskingService masking;
    private final OperationLogMapper operationLogMapper;

    public PersonIdCardImageService(PersonIdCardImageMapper mapper,
                                    LocalFileStorageService storage,
                                    PaddleOcrHttpClient ocrClient,
                                    IdCardOcrProperties ocrProperties,
                                    SensitiveDataMaskingService masking,
                                    OperationLogMapper operationLogMapper) {
        this.mapper = mapper;
        this.storage = storage;
        this.ocrClient = ocrClient;
        this.ocrProperties = ocrProperties;
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
            storage.delete(stored.stored());
            row = existing.get();
            if (row.getPersonId() != null || !Objects.equals(row.getUserId(), userId)) {
                throw new BusinessException(HttpStatus.CONFLICT, "该影印本不可复用，请重新选择文件");
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
            } catch (RuntimeException e) {
                // 落库失败时回滚物理文件
                storage.delete(stored.stored());
                throw e;
            }
        }

        // 2) OCR 调度
        if (skipOcr || !ocrProperties.isConfigured()) {
            mapper.markOcrSkipped(row.getImageId());
            recordAudit(userId, "PERSON_IDCARD_IMAGE_UPLOAD", "SKIPPED", null, http);
        } else {
            long t0 = System.currentTimeMillis();
            String provider = ocrProperties.useBaiduDirect() ? "BAIDU_AI_PLATFORM" : ocrProperties.getProvider();
            String engine = ocrProperties.useBaiduDirect() ? "baidu-ocr-idcard-v1" : ocrProperties.getEngineVersion();
            IdCardOcrProperties.OcrResult result;
            try {
                result = ocrClient.recognize(file.getBytes(), stored.contentType());
            } catch (Exception e) {
                long elapsed = System.currentTimeMillis() - t0;
                String msg = e instanceof PaddleOcrHttpClient.RecognitionFailure ? e.getMessage()
                        : "OCR 处理失败，请重新上传或手工录入";
                mapper.markOcrFailed(row.getImageId(), provider, engine, (int) elapsed, msg);
                recordAudit(userId, "PERSON_IDCARD_IMAGE_RECOGNIZED", "FAILED", msg, http);
                return view(row.getImageId(), true);
            }
            mapper.markOcrSuccess(row.getImageId(), provider, engine, (int) (System.currentTimeMillis() - t0),
                    result.confidence(), result.idCard(), masking.identity(result.idCard()), result.name(),
                    result.birthDate(), result.gender(), result.ethnicity(), result.address());
            recordAudit(userId, "PERSON_IDCARD_IMAGE_RECOGNIZED", "SUCCESS", null, http);
        }
        return view(row.getImageId(), true);
    }

    @Transactional(readOnly = true)
    public PersonIdCardImageView getById(Long imageId, Long userId) {
        return mapper.selectById(imageId)
                .filter(row -> Objects.equals(row.getUserId(), userId))
                .map(row -> PersonIdCardImageView.from(row, false))
                .orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "身份证影印本不存在"));
    }

    private PersonIdCardImageView view(Long imageId, boolean includeFullId) {
        return PersonIdCardImageView.from(mapper.selectById(imageId).orElseThrow(), includeFullId);
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
