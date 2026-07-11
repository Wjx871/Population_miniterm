package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "迁入业务登记入参")
public class MigrationInDTO {

    @NotNull
    @Schema(description = "业务申请单 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long applicationId;

    @NotNull
    @Schema(description = "迁入人口 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long personId;

    @NotBlank
    @Pattern(regexp = "^(CROSS_DISTRICT|EXTERNAL)$", message = "迁入类型必须在 IN_TYPE 字典内")
    @Schema(description = "迁入类型（IN_TYPE 字典）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String inTypeCode;

    @Schema(description = "联办批次号（同市跨区使用）")
    private String transferBatchNo;

    @Schema(description = "原登记 ID（同市跨区迁入时记录 source_registration_id）")
    private Long sourceRegistrationId;

    @Schema(description = "来源区划编码（CROSS_DISTRICT 时必填）")
    private String fromRegionCode;

    @Schema(description = "来源地址")
    private String fromAddress;

    @Schema(description = "原户号")
    private String fromHouseholdNo;

    @NotNull
    @Schema(description = "目标家庭户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long toHouseholdId;

    @NotBlank
    @Schema(description = "目标区划编码")
    private String toRegionCode;

    @NotNull
    @PastOrPresent
    @Schema(description = "迁入日期")
    private LocalDate inDate;

    @Schema(description = "迁入原因（MIGRATION_REASON 字典）")
    private String reasonCode;
}
