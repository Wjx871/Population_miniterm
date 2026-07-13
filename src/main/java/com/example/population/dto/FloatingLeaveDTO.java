package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;

/**
 * 流动人口离开登记入参（标记为 LEFT）。
 */
@Data
@Schema(description = "流动人口离开登记入参")
public class FloatingLeaveDTO {

    @NotNull
    @Schema(description = "流动人口登记 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long floatingId;

    @NotNull
    @PastOrPresent
    @Schema(description = "实际离开日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate actualLeaveDate;

    @Schema(description = "离开原因/备注")
    private String remark;
}