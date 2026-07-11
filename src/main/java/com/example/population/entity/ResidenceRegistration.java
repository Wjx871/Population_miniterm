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
@TableName("residence_registration")
public class ResidenceRegistration implements Serializable {

    @TableId(value = "registration_id", type = IdType.AUTO)
    private Long registrationId;

    private Long personId;

    private Long householdId;

    private String registerTypeCode;

    private LocalDate registerDate;

    private String registeredAddress;

    private String regionCode;

    private LocalDate startDate;

    private Long sourceApplicationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}