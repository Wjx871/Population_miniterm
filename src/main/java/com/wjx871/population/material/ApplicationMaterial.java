package com.wjx871.population.material;
import java.time.LocalDateTime;
import lombok.Data;
@Data
public class ApplicationMaterial {
    private Long materialId; private Long applicationId; private String materialType; private String materialName;
    private String originalFilename; private String storedFilename; private String storagePath; private String contentType;
    private Long fileSize; private String fileSha256; private Boolean requiredFlag; private MaterialVerifyStatus verifyStatus;
    private Long verifyUserId; private String verifyComment; private LocalDateTime verifiedAt; private Long uploadedBy;
    private LocalDateTime createdAt; private LocalDateTime updatedAt;
}
