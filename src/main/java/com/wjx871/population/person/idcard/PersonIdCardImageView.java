package com.wjx871.population.person.idcard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/** Controller 返回的影印本视图：OCR 成功时返回 18 位明文身份证号供前端回填表单，其它场景为 null。 */
public record PersonIdCardImageView(
        Long imageId,
        Long personId,
        String draftUuid,
        String originalFilename,
        String contentType,
        Long fileSize,
        OcrStatus ocrStatus,
        String ocrProvider,
        String ocrEngineVersion,
        Integer ocrElapsedMs,
        Double ocrConfidence,
        /** 18 位明文身份证号；仅 OCR 成功时回填前端，未识别/失败/跳过时为 null。 */
        String ocrIdcardFull,
        String ocrIdcardMasked,
        String ocrName,
        LocalDate ocrBirthDate,
        String ocrGender,
        String ocrEthnicity,
        String ocrAddress,
        String ocrError,
        LocalDateTime createdAt
) {
    public static PersonIdCardImageView from(PersonIdCardImage row) {
        String full = row.getOcrStatus() == OcrStatus.SUCCESS ? row.getOcrIdcardFull() : null;
        return new PersonIdCardImageView(
                row.getImageId(),
                row.getPersonId(),
                row.getDraftUuid() == null ? UUID.randomUUID().toString() : row.getDraftUuid(),
                row.getOriginalFilename(),
                row.getContentType(),
                row.getFileSize(),
                row.getOcrStatus(),
                row.getOcrProvider(),
                row.getOcrEngineVersion(),
                row.getOcrElapsedMs(),
                row.getOcrConfidence(),
                full,
                row.getOcrIdcardMasked(),
                row.getOcrName(),
                row.getOcrBirthDate(),
                row.getOcrGender(),
                row.getOcrEthnicity(),
                row.getOcrAddress(),
                row.getOcrError(),
                row.getCreatedAt()
        );
    }
}
