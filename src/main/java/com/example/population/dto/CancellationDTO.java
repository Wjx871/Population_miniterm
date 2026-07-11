package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "注销 / 销户入参")
public class CancellationDTO {

    @NotBlank
    @Schema(description = "注销业务号（系统可生成）")
    private String cancellationNo;

    @NotNull
    @Schema(description = "业务申请单 ID")
    private Long applicationId;

    @NotBlank
    @Pattern(regexp = "^(PERSON|HOUSEHOLD)$", message = "注销对象类型必须为 PERSON/HOUSEHOLD")
    @Schema(description = "注销对象类型（CANCEL_OBJECT_TYPE 字典）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cancelObjectType;

    @Schema(description = "人口 ID（PERSON 注销时必填）")
    private Long personId;

    @Schema(description = "家庭户 ID（HOUSEHOLD 销户时必填）")
    private Long householdId;

    @NotBlank
    @Schema(description = "注销原因（CANCEL_REASON 字典）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String cancelReasonCode;

    @NotNull
    @PastOrPresent
    @Schema(description = "注销日期")
    private LocalDate cancelDate;
}
