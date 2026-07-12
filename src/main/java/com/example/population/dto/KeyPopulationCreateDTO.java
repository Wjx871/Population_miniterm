package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 重点人口新增入参。
 */
@Data
@Schema(description = "重点人口新增入参")
public class KeyPopulationCreateDTO {

    @NotNull
    @Schema(description = "登记业务申请 ID")
    private Long registerApplicationId;

    @NotNull
    @Schema(description = "人口 ID")
    private Long personId;

    @Size(max = 50)
    @Schema(description = "重点类型编码")
    private String keyTypeCode;

    @Size(max = 50)
    @Schema(description = "管理层级")
    private String managementLevelCode;

    @Schema(description = "登记日期")
    private LocalDate registerDate;

    @Schema(description = "管理起始日期")
    private LocalDate manageStartDate;

    @Schema(description = "管理截止日期")
    private LocalDate manageEndDate;

    @Size(max = 500)
    @Schema(description = "来源依据摘要")
    private String sourceBasisSummary;

    @Schema(description = "主管部门 ID")
    private Long responsibleDepartmentId;

    @Schema(description = "负责人 ID")
    private Long responsibleUserId;

    @Pattern(regexp = "^(ACTIVE|RELEASED|SUSPENDED)$")
    @Size(max = 20)
    @Schema(description = "状态")
    private String status;

    @Size(max = 500)
    @Schema(description = "备注")
    private String remark;
}