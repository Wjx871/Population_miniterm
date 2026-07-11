package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("person")
public class Person implements Serializable {

    @TableId(value = "person_id", type = IdType.AUTO)
    private Long personId;

    private String name;

    private String genderCode;

    private String identityTypeCode;

    private String identityNo;

    private LocalDate birthDate;

    private String ethnicityCode;

    private String phone;

    private String contactAddress;

    private String recordStatusCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer isDeleted;

    @TableField(exist = false)
    private Integer age;
}