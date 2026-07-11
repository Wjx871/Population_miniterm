package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("migration_in")
public class MigrationIn implements Serializable {

    @TableId(value = "in_id", type = IdType.AUTO)
    private Long inId;

    private Long applicationId;

    private Long personId;

    private String inTypeCode;

    private String transferBatchNo;

    private Long sourceRegistrationId;

    private String fromRegionCode;

    private String fromAddress;

    private String fromHouseholdNo;

    private Long toHouseholdId;

    private String toRegionCode;

    private LocalDate inDate;

    private String reasonCode;

    private Long newRegistrationId;

    private Long operatorId;

    private LocalDateTime completedAt;
}