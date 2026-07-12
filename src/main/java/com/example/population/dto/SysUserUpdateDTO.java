package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 系统用户可变更字段白名单。
 * <p>
 * 不允许外部通过此 DTO 写入：userId / passwordHash / username / lastLoginAt / isDeleted / createdAt / updatedAt。
 * 修改密码请使用 {@code PUT /api/sys-users/{id}/password} 接口（写入 BCrypt 哈希）。
 */
@Data
@Schema(description = "系统用户更新入参（白名单字段）")
public class SysUserUpdateDTO {

    @Size(max = 50)
    @Schema(description = "真实姓名")
    private String realName;

    @Pattern(regexp = "^$|^1[3-9]\\d{9}$", message = "手机号格式不正确")
    @Schema(description = "手机号")
    private String phone;

    @Schema(description = "角色 ID（仅 L3 可改；其它角色变更通过专门流程）")
    private Long roleId;

    @Schema(description = "部门 ID")
    private Long departmentId;

    @Pattern(regexp = "^(ENABLED|DISABLED)$", message = "status 仅允许 ENABLED/DISABLED")
    @Schema(description = "账号状态")
    private String status;
}