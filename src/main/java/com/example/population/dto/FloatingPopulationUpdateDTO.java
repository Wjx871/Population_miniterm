package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 流动人口可变更字段白名单。floatingId / applicationId / personId 不可修改。
 */
@Data
@Schema(description = "流动人口更新入参（白名单字段）")
public class FloatingPopulationUpdateDTO {

    @Size(max = 20)
    @Schema(description = "来源区划")
    private String sourceRegionCode;

    @Size(max = 255)
    @Schema(description = "来源地址")
    private String sourceAddress;

    @Size(max = 20)
    @Schema(description = "当前区划")
    private String currentRegionCode;

    @Size(max = 255)
    @Schema(description = "当前地址")
    private String currentAddress;

    @Schema(description = "到达日期")
    private LocalDate arrivalDate;

    @Schema(description = "登记日期")
    private LocalDate registerDate;

    @Schema(description = "预计离开日期")
    private LocalDate plannedLeaveDate;

    @Schema(description = "实际离开日期")
    private LocalDate actualLeaveDate;

    @Size(max = 50)
    @Schema(description = "居住原因编码")
    private String residenceReasonCode;

    @Size(max = 100)
    @Schema(description = "从业/就学单位")
    private String employmentSchool;

    @Size(max = 50)
    @Schema(description = "房东姓名")
    private String landlordName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "房东电话")
    private String landlordPhone;

    @Pattern(regexp = "^(ACTIVE|LEFT|REGISTERED|CANCELLED)$")
    @Size(max = 20)
    @Schema(description = "状态")
    private String status;
}