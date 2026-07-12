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

    /** 业务类型（PERSON_CREATE / HOUSEHOLD_ESTABLISH / MIGRATION_IN / MIGRATION_OUT / PERSON_UPDATE）。 */
    private String businessType;

    /** 业务主键 ID（PERSON_UPDATE 等已有业务时填；新增业务留空）。 */
    private Long businessId;

    /** 业务载荷 JSON。独立列，避免字符串拼接注入。 */
    @TableField(jdbcType = org.apache.ibatis.type.JdbcType.OTHER)
    private String payloadJson;

    /** 申请人提交的简短理由（仅作展示用，不再作为解析载荷）。 */
    private String applyReason;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime submittedAt;

    private LocalDateTime finishedAt;
}