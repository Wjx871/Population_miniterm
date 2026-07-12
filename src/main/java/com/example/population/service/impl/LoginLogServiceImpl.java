package com.example.population.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.population.entity.LoginLog;
import com.example.population.mapper.LoginLogMapper;
import com.example.population.service.LoginLogService;
import com.example.population.util.PageUtil;
import com.example.population.util.SafeLike;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class LoginLogServiceImpl extends ServiceImpl<LoginLogMapper, LoginLog>
        implements LoginLogService {

    private static final int IP_MAX_LEN = 50;
    private static final int UA_MAX_LEN = 500;
    private static final int REASON_MAX_LEN = 255;
    private static final int FINGERPRINT_MAX_LEN = 128;

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public void record(String username, Long userId, String loginStatus, String failureReason,
                       String loginIp, String userAgent, String deviceFingerprint) {
        if (!StringUtils.hasText(loginStatus)) {
            throw new IllegalArgumentException("loginStatus 不能为空");
        }
        LoginLog log = new LoginLog();
        log.setUsername(truncate(username, 50));
        log.setUserId(userId);
        log.setLoginStatus(loginStatus);
        log.setFailureReason(truncate(failureReason, REASON_MAX_LEN));
        log.setLoginIp(truncate(loginIp, IP_MAX_LEN));
        log.setUserAgent(truncate(userAgent, UA_MAX_LEN));
        log.setDeviceFingerprint(truncate(deviceFingerprint, FINGERPRINT_MAX_LEN));
        // loginTime 由 MetaObjectHandler 自动填充
        this.save(log);
    }

    @Override
    public IPage<LoginLog> page(long current, long size, Long userId, String username,
                                String loginStatus, LocalDateTime startTime, LocalDateTime endTime) {
        Page<LoginLog> page = PageUtil.clamp(current, size);
        LambdaQueryWrapper<LoginLog> w = new LambdaQueryWrapper<>();
        if (userId != null) {
            w.eq(LoginLog::getUserId, userId);
        }
        if (StringUtils.hasText(username)) {
            SafeLike.apply(w, LoginLog::getUsername, username);
        }
        if (StringUtils.hasText(loginStatus)) {
            w.eq(LoginLog::getLoginStatus, loginStatus);
        }
        if (startTime != null) {
            w.ge(LoginLog::getLoginTime, startTime);
        }
        if (endTime != null) {
            w.le(LoginLog::getLoginTime, endTime);
        }
        w.orderByDesc(LoginLog::getLoginTime);
        return this.page(page, w);
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() <= max ? s : s.substring(0, max);
    }
}
