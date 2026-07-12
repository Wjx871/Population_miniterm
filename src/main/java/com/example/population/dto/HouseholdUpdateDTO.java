package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 家庭户可变更字段白名单。
 * <p>
 * 仅暴露：户类型 / 户主人口 / 户籍地址 / 区划 / 管理部门 / 状态。
 * 户号（{@code householdNo}）与 {@code householdId} 不允许外部更新（唯一约束由建户时锁定）。
 */
@Data
@Schema(description = "家庭户更新入参")
public class HouseholdUpdateDTO {

    @Pattern(regexp = "^(FAMILY|COLLECTIVE)$", message = "户类型须为 FAMILY/COLLECTIVE")
    @Schema(description = "户类型")
    private String householdTypeCode;

    @Schema(description = "户主人口 ID")
    private Long headPersonId;

    @Size(max = 255)
    @Schema(description = "户籍地址")
    private String registeredAddress;

    @Schema(description = "所属区划编码")
    private String regionCode;

    @Schema(description = "管理部门 ID")
    private Long departmentId;

    @Pattern(regexp = "^(ACTIVE|CANCELLED)$")
    @Schema(description = "户状态")
    private String status;
}
