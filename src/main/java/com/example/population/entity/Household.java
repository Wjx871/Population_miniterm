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
@TableName("household")
public class Household implements Serializable {

    @TableId(value = "household_id", type = IdType.AUTO)
    private Long householdId;

    private String householdNo;

    private String householdTypeCode;

    private Long headPersonId;

    private String registeredAddress;

    private String regionCode;

    private Long departmentId;

    private LocalDate establishDate;

    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableField(exist = false)
    private Integer memberCount;
}