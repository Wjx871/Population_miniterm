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
@TableName("key_population")
public class KeyPopulation implements Serializable {

    @TableId(value = "key_id", type = IdType.AUTO)
    private Long keyId;

    private Long registerApplicationId;

    private Long releaseApplicationId;

    private Long personId;

    private String keyTypeCode;

    private String managementLevelCode;

    private LocalDate registerDate;

    private LocalDate manageStartDate;

    private LocalDate manageEndDate;

    private String sourceBasisSummary;

    private Long responsibleDepartmentId;

    private Long responsibleUserId;

    private String status;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}