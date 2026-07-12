package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 角色可变更字段白名单。
 * <p>
 * 不允许外部修改：roleId / roleCode（业务主键，唯一索引）。permissionLevel 的 1/2/3 在数据层做严格校验。
 */
@Data
@Schema(description = "角色更新入参")
public class SysRoleUpdateDTO {

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