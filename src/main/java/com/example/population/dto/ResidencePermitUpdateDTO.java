package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 居住证可变更字段白名单。permitId / applicationId / floatingId / personId 不可修改。
 */
@Data
@Schema(description = "居住证更新入参（白名单字段）")
public class ResidencePermitUpdateDTO {

    @Size(max = 50)
    @Schema(description = "证件类型编码")
    private String permitTypeCode;

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

    @Schema(description = "注销日期")
    private LocalDate cancelDate;
}