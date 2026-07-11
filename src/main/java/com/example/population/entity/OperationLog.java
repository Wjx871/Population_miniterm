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
@TableName("operation_log")
public class OperationLog implements Serializable {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private Long userId;

    private Long departmentId;

    private String operationTypeCode;

    private String moduleName;

    private String targetTable;

    private Long targetId;

    private String requestMethod;

    private String requestUri;

    private String operationResultCode;

    private String beforeJsonMasked;

    private String afterJsonMasked;

    private String ipAddress;

    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime operationTime;
}