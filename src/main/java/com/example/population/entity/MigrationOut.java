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
@TableName("migration_out")
public class MigrationOut implements Serializable {

    @TableId(value = "out_id", type = IdType.AUTO)
    private Long outId;

    private Long applicationId;

    private Long personId;

    private String outTypeCode;

    private String transferBatchNo;

    private Long fromHouseholdId;

    private String fromRegionCode;

    private String toRegionCode;

    private String toAddress;

    private LocalDate outDate;

    private String reasonCode;

    private Long archiveId;

    private Long operatorId;

    private LocalDateTime completedAt;
}