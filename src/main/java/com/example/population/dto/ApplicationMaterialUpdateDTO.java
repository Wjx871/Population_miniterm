package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请材料可变更字段白名单。materialId / applicationId / verifiedBy / verifiedAt / uploadedAt / uploaderUserId 不可修改。
 * <p>
 * 修改核验状态请走专用 {@code PUT /api/application-materials/{id}/verify}。
 */
@Data
@Schema(description = "申请材料更新入参（白名单字段，仅 UNVERIFIED 状态允许）")
public class ApplicationMaterialUpdateDTO {

    @Size(max = 50)
    @Schema(description = "材料类型编码")
    private String materialTypeCode;

    @Size(max = 100)
    @Schema(description = "材料名称")
    private String materialName;

    @Size(max = 50)
    @Schema(description = "材料编号")
    private String materialNo;

    @Size(max = 255)
    @Schema(description = "文件原始名")
    private String fileName;

    @Size(max = 500)
    @Schema(description = "存储 URI")
    private String storageUri;

    @Size(max = 128)
    @Schema(description = "文件哈希")
    private String fileHash;

    @Schema(description = "是否必交 0/1")
    private Integer requiredFlag;
}