package com.example.population.scheduled;

import com.example.population.service.CertificateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：证件状态自动维护。
 *
 * <p>业务流程 §2.2.8 第 6 步："证件即将到期或已过期时，系统在列表中进行状态提醒。"</p>
 *
 * <p>本调度每天凌晨 02:30 把过期的非 CANCELLED 证件标记为 EXPIRED。
 * 前端通过 {@code GET /api/certificates/expiring?warnDays=30} 拉取即将到期 / 已过期清单用于提醒。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CertificateScheduler {

    private final CertificateService certificateService;

    @Scheduled(cron = "0 30 2 * * ?")
    public void scanExpired() {
        try {
            int updated = certificateService.scanExpired();
            log.info("[scheduler] 证件到期扫描完成, EXPIRED={}", updated);
        } catch (Exception e) {
            log.error("[scheduler] 证件到期扫描失败", e);
        }
    }
}