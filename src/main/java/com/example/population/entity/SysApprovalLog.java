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
@TableName("sys_approval_log")
public class SysApprovalLog implements Serializable {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private Long approvalId;

    private Integer stepNo;

    private Long approverUserId;

    private String actionCode;

    private String comment;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime approvedAt;
}