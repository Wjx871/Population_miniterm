package com.example.population.scheduled;

import com.example.population.service.FloatingPopulationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时任务：维护流动人口状态。
 *
 * <p>业务流程 §2.2.6 第 6 步："居住期限临近或已超过时，系统可在列表中显示提醒。"</p>
 *
 * <p>本定时任务每天凌晨 03:00 把预计离开日期已过期的 ACTIVE 流动记录批量置为 EXPIRED，
 * 同时回滚无 ACTIVE 记录的人口档案状态。提供 Controller 端的 {@code scan-expiring}
 * 端点用于开发态手动触发。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FloatingPopulationScheduler {

    private final FloatingPopulationService floatingService;

    /**
     * 每天凌晨 03:00 扫描过期流动登记。
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void scanExpired() {
        try {
            int updated = floatingService.scanExpiring();
            log.info("[scheduler] 流动人口到期扫描完成, EXPIRED={}", updated);
        } catch (Exception e) {
            log.error("[scheduler] 流动人口到期扫描失败", e);
        }
    }
}