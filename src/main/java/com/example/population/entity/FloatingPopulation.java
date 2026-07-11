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
@TableName("floating_population")
public class FloatingPopulation implements Serializable {

    @TableId(value = "floating_id", type = IdType.AUTO)
    private Long floatingId;

    private Long applicationId;

    private Long personId;

    private String sourceRegionCode;

    private String sourceAddress;

    private String currentRegionCode;

    private String currentAddress;

    private LocalDate arrivalDate;

    private LocalDate registerDate;

    private LocalDate plannedLeaveDate;

    private LocalDate actualLeaveDate;

    private String residenceReasonCode;

    private String employmentSchool;

    private String landlordName;

    private String landlordPhone;

    private String status;

    private Long handlingDepartmentId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}