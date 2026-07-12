package com.example.population.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@TableName("login_log")
public class LoginLog implements Serializable {

    @TableId(value = "log_id", type = IdType.AUTO)
    private Long logId;

    private String username;

    private Long userId;

    private String loginStatus;

    private String failureReason;

    private String loginIp;

    private String userAgent;

    private String deviceFingerprint;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime loginTime;
}
