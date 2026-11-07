package com.wjx871.population.stats;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.SensitiveDataMaskingService;

/**
 * 统计信息业务逻辑服务。
 * 返回当前用户数据范围内的真实统计；不可用的增长率不以演示数据替代。
 *
 * @author Gem
 * @date 2026/07/08
 */
@Service
@RequiredArgsConstructor
public class StatisticsService {

    private final StatisticsMapper statisticsMapper;
    private final SensitiveDataMaskingService masking;

    /**
     * 获取核心卡片数字面板汇总。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getSummary() {
        DataScopeCriteria scope = DataScopeCriteria.current();
        long persons = statisticsMapper.countActivePersons(scope);
        long floating = statisticsMapper.countFloatingPopulation(scope);
        long keyPop = statisticsMapper.countKeyPopulation(scope);
        long households = statisticsMapper.countHouseholds(scope);

        Map<String, Object> result = new HashMap<>();
        result.put("residentCount", persons);
        result.put("residentGrowth", null);
        result.put("floatingCount", floating);
        result.put("floatingGrowth", null);
        result.put("keyPopulationCount", keyPop);
        result.put("keyPopulationGrowth", null);
        result.put("householdCount", households);
        result.put("householdGrowth", null);
        result.put("isMock", false);
        return result;
    }

    /**
     * 获取图表需要的多维度统计数据。
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getChartData() {
        DataScopeCriteria scope = DataScopeCriteria.current();
        Map<String, Object> result = new HashMap<>();

        result.put("keyPopulationDistribution", safeRows(statisticsMapper.countKeyPopulationByType(scope)));
        result.put("certificateStatus", safeRows(statisticsMapper.countCertificateByStatus(scope)));

        LocalDate today = LocalDate.now();
        LocalDate from = today.minusDays(6);
        List<Map<String, Object>> rawMigrationIn = safeRows(statisticsMapper.countRecentMigrationIn(from, scope));
        List<Map<String, Object>> rawMigrationOut = safeRows(statisticsMapper.countRecentMigrationOut(from, scope));
        Map<LocalDate, Long> inMap = countByDate(rawMigrationIn);
        Map<LocalDate, Long> outMap = countByDate(rawMigrationOut);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");
        List<String> dates = new ArrayList<>();
        List<Long> migrationIn = new ArrayList<>();
        List<Long> migrationOut = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(today); date = date.plusDays(1)) {
            dates.add(date.format(formatter));
            migrationIn.add(inMap.getOrDefault(date, 0L));
            migrationOut.add(outMap.getOrDefault(date, 0L));
        }

        Map<String, Object> trend = new HashMap<>();
        trend.put("dates", dates);
        trend.put("inFlow", migrationIn);
        trend.put("outFlow", migrationOut);
        result.put("migrationTrend", trend);

        return result;
    }

    /**
     * 获取最近的操作日志列表。
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentLogs() {
        List<Map<String, Object>> logs = statisticsMapper.selectRecentLogs(DataScopeCriteria.current());
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : safeRows(logs)) {
            if (row == null) {
                continue;
            }
            Map<String, Object> response = new LinkedHashMap<>(row);
            Object detail = row.get("detail");
            // Audit logs always use fixed masking, including users with full sensitive-data access.
            response.put("detail", masking.auditDetail(detail instanceof String text ? text : null));
            result.add(response);
        }
        return result;
    }

    private List<Map<String, Object>> safeRows(List<Map<String, Object>> rows) {
        return rows == null ? List.of() : rows;
    }

    private Map<LocalDate, Long> countByDate(List<Map<String, Object>> rows) {
        Map<LocalDate, Long> result = new HashMap<>();
        for (Map<String, Object> row : rows) {
            Object rawDate = row.get("date");
            Object rawCount = row.get("count");
            if (rawDate != null && rawCount instanceof Number count) {
                LocalDate date = rawDate instanceof LocalDate localDate ? localDate
                        : rawDate instanceof java.sql.Date sqlDate ? sqlDate.toLocalDate()
                        : LocalDate.parse(rawDate.toString());
                result.put(date, count.longValue());
            }
        }
        return result;
    }
}
