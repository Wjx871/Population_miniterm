package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 居住证新增入参。
 */
@Data
@Schema(description = "居住证新增入参")
public class ResidencePermitCreateDTO {

    @Schema(description = "申请业务 ID（可空）")
    private Long applicationId;

    @Schema(description = "流动人口登记 ID（可空）")
    private Long floatingId;

    @NotNull
    @Schema(description = "人口 ID")
    private Long personId;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "证件类型编码")
    private String permitTypeCode;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "证件号码")
    private String permitNo;

    @Size(max = 100)
    @Schema(description = "签发机关")
    private String issueAuthority;

    @Schema(description = "签发日期")
    private LocalDate issueDate;

    @Schema(description = "生效日期")
    private LocalDate validFrom;

    @Schema(description = "失效日期")
    private LocalDate validUntil;

    @Pattern(regexp = "^(VALID|EXPIRED|CANCELLED|SUSPENDED)$")
    @Size(max = 20)
    @Schema(description = "证件状态")
    private String permitStatus;
}