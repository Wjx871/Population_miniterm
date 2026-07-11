package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("sys_role_permission")
public class SysRolePermission implements Serializable {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    private Long roleId;

    private Long permissionId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}