package com.wjx871.population.stats;

import com.wjx871.population.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.Map;

/**
 * 首页工作台数据大屏统计接口控制器。
 *
 * @author Gem
 * @date 2026/07/08
 */
@RestController
@RequestMapping("/api/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    /**
     * 获取大屏核心面板数字汇总。
     */
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('statistics:view')")
    public ApiResponse<Map<String, Object>> getSummary() {
        return ApiResponse.ok(statisticsService.getSummary());
    }

    /**
     * 获取大屏图表所需的数据。
     */
    @GetMapping("/charts")
    @PreAuthorize("hasAuthority('statistics:view')")
    public ApiResponse<Map<String, Object>> getCharts() {
        return ApiResponse.ok(statisticsService.getChartData());
    }

    /**
     * 获取大屏显示的最近十条系统操作日志。
     */
    @GetMapping("/logs")
    @PreAuthorize("hasAuthority('log:view')")
    public ApiResponse<List<Map<String, Object>>> getLogs() {
        return ApiResponse.ok(statisticsService.getRecentLogs());
    }
}
