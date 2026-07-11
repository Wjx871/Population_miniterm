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
@TableName("sys_permission")
public class SysPermission implements Serializable {

    @TableId(value = "permission_id", type = IdType.AUTO)
    private Long permissionId;

    private String permissionCode;

    private String permissionName;

    private String moduleName;

    private String actionCode;

    private Integer sensitivityLevel;

    private Integer approvalRequired;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}