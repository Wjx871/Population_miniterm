package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色新增入参。roleCode 一经创建不可修改（业务主键）。
 */
@Data
@Schema(description = "角色新增入参")
public class SysRoleCreateDTO {

    @NotBlank
    @Pattern(regexp = "^[A-Z0-9_]{2,32}$", message = "roleCode 仅允许大写字母/数字/下划线，长度 2-32")
    @Schema(description = "角色编码，业务主键")
    private String roleCode;

    @NotBlank
    @Size(max = 50)
    @Schema(description = "角色名称")
    private String roleName;

    @Min(1) @Max(3)
    @Schema(description = "权限等级 1=查询 2=经办 3=审批/管理员")
    private Integer permissionLevel;

    @Pattern(regexp = "^(SELF|DEPARTMENT|CITY|ALL)$", message = "dataScopeCode 仅允许 SELF/DEPARTMENT/CITY/ALL")
    @Schema(description = "数据权限范围")
    private String dataScopeCode;

    @Size(max = 255)
    @Schema(description = "角色描述")
    private String description;

    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "status 仅允许 ENABLED/DISABLED")
    @Schema(description = "状态")
    private String status;
}