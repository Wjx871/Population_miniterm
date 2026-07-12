package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门新增入参。departmentCode 一经创建不可修改。
 */
@Data
@Schema(description = "部门新增入参")
public class SysDepartmentCreateDTO {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]{2,32}$", message = "departmentCode 仅允许大写字母/数字/下划线")
    @Schema(description = "部门编码")
    private String departmentCode;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "部门名称")
    private String departmentName;

    @Pattern(regexp = "^(POLICE_STATION|SUBCOMMITTEE|OTHER)$")
    @Schema(description = "部门类型")
    private String departmentTypeCode;

    @Size(max = 20)
    @Schema(description = "所属区划")
    private String regionCode;

    @Schema(description = "上级部门 ID")
    private Long parentId;

    @Pattern(regexp = "^(ENABLED|DISABLED)$")
    @Schema(description = "状态")
    private String status;
}