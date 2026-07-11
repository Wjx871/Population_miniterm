package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("certificate")
public class Certificate implements Serializable {

    @TableId(value = "certificate_id", type = IdType.AUTO)
    private Long certificateId;

    private Long personId;

    private String certificateTypeCode;

    private String certificateNo;

    private String issueAuthority;

    private LocalDate issueDate;

    private LocalDate validFrom;

    private LocalDate validUntil;

    private String certificateStatus;

    private Long materialId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}