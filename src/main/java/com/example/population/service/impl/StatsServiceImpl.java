package com.example.population.service.impl;

import com.example.population.mapper.StatsMapper;
import com.example.population.service.StatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 统计看板/大屏查询实现。所有汇总走 SQL Map 返回。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private final StatsMapper statsMapper;

    @Override
    public List<Map<String, Object>> countByPersonStatus(LocalDate startDate, LocalDate endDate) {
        return statsMapper.countByPersonRecordStatus(startDate, endDate);
    }

    @Override
    public List<Map<String, Object>> countByPersonGender() {
        return statsMapper.countByPersonGender();
    }

    @Override
    public List<Map<String, Object>> countByPersonEthnicity(int topN) {
        return statsMapper.countByPersonEthnicity(topN <= 0 ? null : topN);
    }

    @Override
    public List<Map<String, Object>> countByHouseholdType() {
        return statsMapper.countByHouseholdType();
    }

    @Override
    public List<Map<String, Object>> countByHouseholdStatus() {
        return statsMapper.countByHouseholdStatus();
    }

    @Override
    public List<Map<String, Object>> populationByRegion() {
        return statsMapper.sumCurrentPopulationByRegion();
    }

    @Override
    public List<Map<String, Object>> floatingByRegion(String status) {
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        return statsMapper.sumFloatingByRegion(status);
    }

    @Override
    public List<Map<String, Object>> countByCertificateStatus() {
        return statsMapper.countByCertificateStatus();
    }

    @Override
    public List<Map<String, Object>> countExpiringCertificates(int warnDays) {
        return statsMapper.countExpiringCertificates(warnDays <= 0 ? 30 : warnDays);
    }

    @Override
    public List<Map<String, Object>> migrationInByMonth(int months) {
        String sinceMonth = computeSinceMonth(months <= 0 ? 12 : months);
        return statsMapper.countMigrationInByMonth(sinceMonth);
    }

    @Override
    public List<Map<String, Object>> migrationOutByMonth(int months) {
        String sinceMonth = computeSinceMonth(months <= 0 ? 12 : months);
        return statsMapper.countMigrationOutByMonth(sinceMonth);
    }

    @Override
    public List<Map<String, Object>> countByKeyPopulationType() {
        return statsMapper.countByKeyPopulationType();
    }

    /**
     * 综合数字卡：把每行 {code, value} 摊平为 {key: value}。
     * 一次性返回避免前端串行调用。
     */
    @Override
    public Map<String, Object> dashboardCounters() {
        List<Map<String, Object>> rows = statsMapper.summaryCounters();
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Object code = r.get("code");
            Object val = r.get("value");
            if (code != null) {
                out.put(code.toString(), val);
            }
        }
        out.put("generatedAt", System.currentTimeMillis());
        return out;
    }

    @Override
    public List<Map<String, Object>> pendingApprovalsByType() {
        return statsMapper.countPendingApprovalsByType();
    }

    private static String computeSinceMonth(int months) {
        LocalDate base = LocalDate.now().minusMonths(Math.max(1, months) - 1L);
        return base.format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }
}
