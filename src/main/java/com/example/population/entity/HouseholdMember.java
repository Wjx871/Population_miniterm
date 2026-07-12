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
@TableName("household_member")
public class HouseholdMember implements Serializable {

    @TableId(value = "member_id", type = IdType.AUTO)
    private Long memberId;

    private Long householdId;

    private Long personId;

    private String relationshipCode;

    private LocalDate joinDate;

    private LocalDate leaveDate;

    private String memberStatus;

    private Long sourceApplicationId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}