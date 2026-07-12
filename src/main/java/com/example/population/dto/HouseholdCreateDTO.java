package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "家庭户立户入参")
public class HouseholdCreateDTO {

    /**
     * 关联的业务申请主单 ID（必填）。
     * <p>
     * 调用方需先 POST /api/business-applications 创建申请草稿，
     * 再 POST /api/application-materials 上传身份证明 + 户口簿/住所证明、由核验岗材料 VERIFIED，
     * 最后携带此 applicationId 调 POST /api/households/establish（新增入户）。
     */
    @NotNull(message = "业务申请 ID（applicationId）不能为空；请先创建业务申请并上传身份证明 + 户口簿/住所证明")
    @Schema(description = "业务申请主单 ID（必填，关联 business_application.application_id）",
            example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long applicationId;

    @NotBlank
    @Size(max = 30)
    @Pattern(regexp = "^[A-Z0-9]{6,30}$", message = "户号须为大写字母与数字，长度 6-30")
    @Schema(description = "户号", example = "H110101001", requiredMode = Schema.RequiredMode.REQUIRED)
    private String householdNo;

    @NotBlank
    @Pattern(regexp = "^(FAMILY|COLLECTIVE)$", message = "户类型须为 FAMILY/COLLECTIVE")
    @Schema(description = "户类型（HOUSEHOLD_TYPE 字典）", example = "FAMILY")
    private String householdTypeCode;

    @Schema(description = "户主人口 ID（集体户可为 null；建户后可换户主）")
    private Long headPersonId;

    @NotBlank
    @Size(max = 255)
    @Schema(description = "户籍地址")
    private String registeredAddress;

    @NotBlank
    @Schema(description = "所属区划编码（行政区划表 region_code）")
    private String regionCode;

    @NotNull
    @Schema(description = "管理部门 ID")
    private Long departmentId;

    @NotNull
    @PastOrPresent
    @Schema(description = "立户日期")
    private LocalDate establishDate;

    @Pattern(regexp = "^(ACTIVE|CANCELLED)$")
    @Schema(description = "户状态", example = "ACTIVE")
    private String status;
}
