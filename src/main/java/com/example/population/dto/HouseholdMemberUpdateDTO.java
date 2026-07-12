package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 户籍成员可变更字段白名单。memberId / householdId / personId 不可修改。
 * <p>
 * 如需把成员迁出本户，请走 {@code PUT /api/household-members/{memberId}/leave}。
 */
@Data
@Schema(description = "户籍成员更新入参（白名单字段）")
public class HouseholdMemberUpdateDTO {

    @Size(max = 20)
    @Schema(description = "与户主关系代码（RELATIONSHIP 字典）")
    private String relationshipCode;

    @Schema(description = "加入日期")
    private LocalDate joinDate;

    @Size(max = 20)
    @Schema(description = "成员状态：CURRENT / LEFT / DECEASED")
    private String memberStatus;
}