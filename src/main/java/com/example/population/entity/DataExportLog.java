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
@TableName("data_export_log")
public class DataExportLog implements Serializable {

    @TableId(value = "export_id", type = IdType.AUTO)
    private Long exportId;

    private String exportNo;

    private Long userId;

    private Long departmentId;

    private String exportTypeCode;

    private String queryConditionSummary;

    private Integer exportedRows;

    private Integer sensitivityLevel;

    private Long approvalId;

    private String fileName;

    private String fileHash;

    private String resultCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime exportedAt;
}