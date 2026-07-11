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
@TableName("sys_approval_request")
public class SysApprovalRequest implements Serializable {

    @TableId(value = "approval_id", type = IdType.AUTO)
    private Long approvalId;

    private String approvalNo;

    private Long applicationId;

    private Integer requiredLevel;

    private Long currentApproverId;

    private String status;

    private String applyReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime submittedAt;

    private LocalDateTime finishedAt;
}