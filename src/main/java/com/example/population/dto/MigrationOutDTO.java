package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "迁出业务登记入参")
public class MigrationOutDTO {

    @NotNull
    @Schema(description = "业务申请单 ID")
    private Long applicationId;

    @NotNull
    @Schema(description = "迁出人口 ID")
    private Long personId;

    @NotBlank
    @Pattern(regexp = "^(CROSS_DISTRICT|EXTERNAL)$", message = "迁出类型必须在 OUT_TYPE 字典内")
    @Schema(description = "迁出类型（OUT_TYPE 字典）")
    private String outTypeCode;

    @Schema(description = "联办批次号（同市跨区使用）")
    private String transferBatchNo;

    @NotNull
    @Schema(description = "原家庭户 ID")
    private Long fromHouseholdId;

    @NotBlank
    @Schema(description = "原区划编码")
    private String fromRegionCode;

    @Schema(description = "迁往区划编码（迁往市外时必填；同市跨区为目标区划）")
    private String toRegionCode;

    @NotBlank
    @Schema(description = "迁往地址")
    private String toAddress;

    @NotNull
    @PastOrPresent
    @Schema(description = "迁出日期")
    private LocalDate outDate;

    @Schema(description = "迁出原因（MIGRATION_REASON 字典）")
    private String reasonCode;
}
