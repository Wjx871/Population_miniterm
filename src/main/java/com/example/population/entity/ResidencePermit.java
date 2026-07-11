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
@TableName("residence_permit")
public class ResidencePermit implements Serializable {

    @TableId(value = "permit_id", type = IdType.AUTO)
    private Long permitId;

    private Long applicationId;

    private Long floatingId;

    private Long personId;

    private String permitTypeCode;

    private String permitNo;

    private String issueAuthority;

    private LocalDate issueDate;

    private LocalDate validFrom;

    private LocalDate validUntil;

    private String permitStatus;

    private LocalDate cancelDate;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}