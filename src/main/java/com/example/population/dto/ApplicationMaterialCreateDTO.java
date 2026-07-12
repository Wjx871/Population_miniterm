package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请材料新增入参。
 */
@Data
@Schema(description = "申请材料新增入参")
public class ApplicationMaterialCreateDTO {

    @NotNull
    @Schema(description = "业务申请 ID")
    private Long applicationId;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "材料类型编码（MATERIAL_TYPE 字典）")
    private String materialTypeCode;

    @NotBlank
    @Size(max = 100)
    @Schema(description = "材料名称")
    private String materialName;

    @Size(max = 50)
    @Schema(description = "材料编号（证件号、票据号等）")
    private String materialNo;

    @Size(max = 255)
    @Schema(description = "文件原始名")
    private String fileName;

    @Size(max = 500)
    @Schema(description = "存储 URI")
    private String storageUri;

    @Size(max = 128)
    @Schema(description = "文件哈希（用于完整性 / 去重）")
    private String fileHash;

    @Schema(description = "是否必交 0/1")
    private Integer requiredFlag;
}