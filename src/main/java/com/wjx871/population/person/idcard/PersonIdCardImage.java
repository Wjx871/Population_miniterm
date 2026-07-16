package com.wjx871.population.person.idcard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 人口-身份证影印本记录（V4_013 / Phase 14）。 */
@Data
public class PersonIdCardImage {

    private Long imageId;
    private Long personId;
    private String draftUuid;
    private Long userId;
    private String originalFilename;
    private String storedFilename;
    private String storagePath;
    private String contentType;
    private Long fileSize;
    private String fileSha256;

    private OcrStatus ocrStatus;
    private String ocrProvider;
    private String ocrEngineVersion;
    private Integer ocrElapsedMs;
    private Double ocrConfidence;

    /** 明文身份证号；仅在 Service / Mapper 层使用，绝不出 Controller。 */
    private String ocrIdcardFull;

    /** 脱敏身份证号（110101********1234）；可返回前端。 */
    private String ocrIdcardMasked;

    private String ocrName;
    private LocalDate ocrBirthDate;
    private String ocrGender;
    private String ocrEthnicity;
    private String ocrAddress;
    private String ocrError;
    private String ocrRawJson;

    private LocalDateTime createdAt;
}
