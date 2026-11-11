package com.wjx871.population.person.idcard;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import com.wjx871.population.security.SensitiveDataMaskingService;

/** Controller 返回的影印本视图。上传回填和只读详情采用不同的敏感字段策略。 */
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
        /** 上传回填或获授权详情中的明文身份证号；其余情况为 null。 */
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
    public static PersonIdCardImageView fromUpload(PersonIdCardImage row, SensitiveDataMaskingService masking) {
        String full = row.getOcrStatus() == OcrStatus.SUCCESS ? row.getOcrIdcardFull() : null;
        return build(row, full, row.getOcrAddress(), safeMaskedIdentity(row, masking));
    }

    public static PersonIdCardImageView fromDetail(PersonIdCardImage row,
                                                   boolean canViewFull,
                                                   SensitiveDataMaskingService masking) {
        String full = canViewFull && row.getOcrStatus() == OcrStatus.SUCCESS ? row.getOcrIdcardFull() : null;
        String address = canViewFull ? row.getOcrAddress() : masking.address(row.getOcrAddress());
        return build(row, full, address, safeMaskedIdentity(row, masking));
    }

    private static String safeMaskedIdentity(PersonIdCardImage row, SensitiveDataMaskingService masking) {
        if (row.getOcrIdcardFull() != null) return masking.identityAlways(row.getOcrIdcardFull());
        return row.getOcrIdcardMasked() == null ? null : "****";
    }

    private static PersonIdCardImageView build(PersonIdCardImage row, String full, String address, String masked) {
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
                masked,
                row.getOcrName(),
                row.getOcrBirthDate(),
                row.getOcrGender(),
                row.getOcrEthnicity(),
                address,
                row.getOcrError(),
                row.getCreatedAt()
        );
    }
}
