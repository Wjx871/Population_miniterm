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
@TableName("cancellation_record")
public class CancellationRecord implements Serializable {

    @TableId(value = "cancel_id", type = IdType.AUTO)
    private Long cancelId;

    private String cancellationNo;

    private Long applicationId;

    private String cancelObjectType;

    private Long personId;

    private Long householdId;

    private String cancelReasonCode;

    private LocalDate cancelDate;

    private Long archiveId;

    private Long operatorId;

    private LocalDateTime completedAt;
}