package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
@Schema(description = "批量过户入参（同市跨区随迁）。在事务内把指定 personIds 从原户置 LEFT 并写入新户 CURRENT 行")
public class HouseholdMemberTransferDTO {

    @NotEmpty
    @Schema(description = "被迁移的人口 ID 列表")
    private List<Long> personIds;

    @NotNull
    @Schema(description = "目标家庭户 ID")
    private Long targetHouseholdId;

    @Schema(description = "原家庭户 ID（null 表示按人口当前归属推断）")
    private Long sourceHouseholdId;

    @NotNull
    @PastOrPresent
    @Schema(description = "过户生效日期")
    private LocalDate transferDate;

    @Schema(description = "来源业务申请单 ID（迁入登记回填）")
    private Long sourceApplicationId;
}
