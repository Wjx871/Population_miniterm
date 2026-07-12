package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 权限点新增入参。permissionCode 一经创建不可修改。
 */
@Data
@Schema(description = "权限点新增入参")
public class SysPermissionCreateDTO {

    @NotBlank
    @Pattern(regexp = "^[a-z]+:[a-z_]+$", message = "permissionCode 格式应为 module:action（小写字母、数字、下划线、冒号）")
    @Size(max = 50)
    @Schema(description = "权限编码")
    private String permissionCode;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "权限名称")
    private String permissionName;

    @Size(max = 50)
    @Schema(description = "模块名称")
    private String moduleName;

    @Pattern(regexp = "^(CREATE|QUERY|UPDATE|DELETE|APPROVE|RELEASE|EXPORT)$")
    @Schema(description = "动作码")
    private String actionCode;

    @Min(0) @Max(3)
    @Schema(description = "敏感等级 0-3")
    private Integer sensitivityLevel;

    @Min(0) @Max(1)
    @Schema(description = "是否需要审批")
    private Integer approvalRequired;
}