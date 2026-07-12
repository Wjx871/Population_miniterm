package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 业务申请新增入参。applicationId / applicationNo 由后端生成。
 */
@Data
@Schema(description = "业务申请新增入参")
public class BusinessApplicationCreateDTO {

    @NotBlank
    @Size(max = 50)
    @Schema(description = "业务类型编码")
    private String businessTypeCode;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "申请人姓名")
    private String applicantName;

    @NotBlank
    @Pattern(regexp = "^(ID_CARD|PASSPORT|MILITARY|OTHER)$")
    @Schema(description = "申请人证件类型")
    private String applicantIdentityType;

    @NotBlank
    @Size(max = 30)
    @Schema(description = "申请人证件号")
    private String applicantIdentityNo;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$")
    @Schema(description = "申请人电话")
    private String applicantPhone;

    @Schema(description = "目标人口 ID")
    private Long targetPersonId;

    @Schema(description = "目标户 ID")
    private Long targetHouseholdId;

    @Schema(description = "经办部门 ID")
    private Long handlingDepartmentId;

    @Schema(description = "草稿说明")
    private String currentStep;
}