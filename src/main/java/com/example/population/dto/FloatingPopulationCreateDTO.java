package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 流动人口新增入参。
 */
@Data
@Schema(description = "流动人口新增入参")
public class FloatingPopulationCreateDTO {

    @Schema(description = "业务申请 ID（可空）")
    private Long applicationId;

    @NotNull
    @Schema(description = "人口 ID")
    private Long personId;

    @Size(max = 20)
    @Schema(description = "来源区划")
    private String sourceRegionCode;

    @Size(max = 255)
    @Schema(description = "来源地址")
    private String sourceAddress;

    @NotNull
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

    @Size(max = 50)
    @Schema(description = "居住原因编码")
    private String residenceReasonCode;

    @Size(max = 100)
    @Schema(description = "从业/就学单位")
    private String employmentSchool;

    @Size(max = 50)
    @Schema(description = "房东姓名")
    private String landlordName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$")
    @Schema(description = "房东电话")
    private String landlordPhone;

    @Schema(description = "经办部门 ID")
    private Long handlingDepartmentId;
}