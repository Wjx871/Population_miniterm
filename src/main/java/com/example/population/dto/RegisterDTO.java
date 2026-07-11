package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "用户注册请求")
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50)
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64)
    private String password;

    @NotBlank(message = "真实姓名不能为空")
    private String realName;

    private String phone;

    @NotNull(message = "角色不能为空")
    private Long roleId;

    @NotNull(message = "部门不能为空")
    private Long departmentId;
}