package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限点可变更字段白名单。不允许外部修改：permissionId / permissionCode（业务主键）。
 */
@Data
@Schema(description = "权限点更新入参")
public class SysPermissionUpdateDTO {

    @Size(max = 50)
    @Schema(description = "权限名称")
    private String permissionName;

    @Size(max = 50)
    @Schema(description = "模块名称")
    private String moduleName;

    @Size(max = 50)
    @Schema(description = "动作码 CREATE/QUERY/UPDATE/DELETE/APPROVE")
    private String actionCode;

    @Min(0) @Max(3)
    @Schema(description = "敏感等级 0-3")
    private Integer sensitivityLevel;

    @Min(0) @Max(1)
    @Schema(description = "是否需要审批 0=否 1=是")
    private Integer approvalRequired;
}