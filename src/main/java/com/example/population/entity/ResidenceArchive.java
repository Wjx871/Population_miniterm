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
@TableName("residence_archive")
public class ResidenceArchive implements Serializable {

    @TableId(value = "archive_id", type = IdType.AUTO)
    private Long archiveId;

    private Long originalRegistrationId;

    private Long personId;

    private Long householdId;

    private String archiveTypeCode;

    private LocalDate archiveDate;

    private String archiveReasonCode;

    private String personNameSnapshot;

    private String identityTypeSnapshot;

    private String identityNoSnapshot;

    private String householdNoSnapshot;

    private String registeredAddressSnapshot;

    private String regionCodeSnapshot;

    private String registerTypeSnapshot;

    private LocalDate registerDateSnapshot;

    private LocalDate startDateSnapshot;

    private LocalDate endDateSnapshot;

    private String originalStatus;

    private Long archiveOperatorId;

    private Long sourceApplicationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}