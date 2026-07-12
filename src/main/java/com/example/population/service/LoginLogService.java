package com.example.population.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.example.population.entity.LoginLog;

import java.time.LocalDateTime;

public interface LoginLogService extends IService<LoginLog> {

    /**
     * 记录一条登录日志。失败情况下 userId 可以为 null。
     *
     * @param username          登录用户名
     * @param userId            关联的用户 ID（登录失败时为 null）
     * @param loginStatus       登录状态：SUCCESS / FAILED / LOCKED
     * @param failureReason     失败原因（成功时为 null）
     * @param loginIp           客户端 IP
     * @param userAgent         浏览器/客户端 UA
     * @param deviceFingerprint 设备指纹（可选）
     */
    void record(String username, Long userId, String loginStatus, String failureReason,
                String loginIp, String userAgent, String deviceFingerprint);

    /**
     * 分页查询登录日志。
     */
    IPage<LoginLog> page(long current, long size, Long userId, String username,
                         String loginStatus, LocalDateTime startTime, LocalDateTime endTime);
}
