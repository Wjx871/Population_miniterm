package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门可变更字段白名单。
 * <p>
 * 不允许外部修改：departmentId / departmentCode（业务主键）。
 */
@Data
@Schema(description = "部门更新入参")
public class SysDepartmentUpdateDTO {

    @Size(max = 50)
    @Schema(description = "部门名称")
    private String departmentName;

    @Pattern(regexp = "^(POLICE_STATION|SUBCOMMITTEE|OTHER)$", message = "departmentTypeCode 仅允许枚举值")
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