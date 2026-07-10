package com.wjx871.population.material;
import java.time.LocalDateTime;
public record MaterialView(Long materialId, Long applicationId, String materialType, String materialName,
        String originalFilename, String contentType, Long fileSize, String fileSha256, Boolean requiredFlag,
        MaterialVerifyStatus verifyStatus, Long verifyUserId, String verifyComment, LocalDateTime verifiedAt,
        Long uploadedBy, LocalDateTime createdAt) {
    public static MaterialView from(ApplicationMaterial v) { return new MaterialView(v.getMaterialId(),v.getApplicationId(),v.getMaterialType(),v.getMaterialName(),v.getOriginalFilename(),v.getContentType(),v.getFileSize(),v.getFileSha256(),v.getRequiredFlag(),v.getVerifyStatus(),v.getVerifyUserId(),v.getVerifyComment(),v.getVerifiedAt(),v.getUploadedBy(),v.getCreatedAt()); }
}
