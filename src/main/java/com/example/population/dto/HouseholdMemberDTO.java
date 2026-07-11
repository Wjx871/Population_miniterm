package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
@Schema(description = "户-人关系入参")
public class HouseholdMemberDTO {

    @NotNull
    @Schema(description = "家庭户 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long householdId;

    @NotNull
    @Schema(description = "人口 ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long personId;

    @NotNull
    @Pattern(regexp = "^(HEAD|SPOUSE|CHILD|PARENT|OTHER)$", message = "关系必须在 RELATIONSHIP 字典内")
    @Schema(description = "与户主关系（RELATIONSHIP 字典）", requiredMode = Schema.RequiredMode.REQUIRED)
    private String relationshipCode;

    @NotNull
    @PastOrPresent
    @Schema(description = "加入日期", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate joinDate;

    @Pattern(regexp = "^(CURRENT|LEFT|CANCELLED)$")
    @Schema(description = "成员状态（MEMBER_STATUS 字典）", example = "CURRENT")
    private String memberStatus;

    @Schema(description = "来源申请单 ID（业务流水可追溯）")
    private Long sourceApplicationId;
}
