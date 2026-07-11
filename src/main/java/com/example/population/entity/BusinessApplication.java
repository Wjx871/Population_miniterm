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
@TableName("business_application")
public class BusinessApplication implements Serializable {

    @TableId(value = "application_id", type = IdType.AUTO)
    private Long applicationId;

    private String applicationNo;

    private String businessTypeCode;

    private String applicantName;

    private String applicantIdentityType;

    private String applicantIdentityNo;

    private String applicantPhone;

    private Long targetPersonId;

    private Long targetHouseholdId;

    private Long handlingDepartmentId;

    private Long submitUserId;

    private String status;

    private String currentStep;

    private LocalDateTime submittedAt;

    private LocalDateTime completedAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private String targetPersonName;

    @TableField(exist = false)
    private String departmentName;
}