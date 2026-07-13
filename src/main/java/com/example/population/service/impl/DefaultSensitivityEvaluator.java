package com.example.population.service.impl;

import com.example.population.dto.DataExportRequestDTO;
import com.example.population.service.SensitivityEvaluator;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 默认敏感度评估实现（P0）。
 * <p>
 * 规则（按累计命中）：
 * <ol>
 *   <li>基础级别：L1（始终）</li>
 *   <li>业务类型命中 person / floating / household / residence_archive → L2</li>
 *   <li>行数 &gt; 10000 或 containsSensitiveFields=true 或 命中 key_population → L3</li>
 * </ol>
 */
@Component
public class DefaultSensitivityEvaluator implements SensitivityEvaluator {

    private static final Set<String> L2_TYPES = Set.of(
            "PERSON", "FLOATING", "HOUSEHOLD", "RESIDENCE_ARCHIVE",
            "MIGRATION", "CANCELLATION", "PERMIT"
    );

    private static final Set<String> L3_TYPES = Set.of(
            "KEY_POPULATION", "RAW_IDENTITY", "RAW_CERT",
            "OPERATION_LOG_FULL", "EXPORT_HISTORY"
    );

    private static final long L2_THRESHOLD_ROWS = 1_000L;
    private static final long L3_THRESHOLD_ROWS = 10_000L;

    @Override
    public int evaluate(DataExportRequestDTO req) {
        if (req == null) return 1;
        int level = 1;

        String type = req.getExportTypeCode();
        if (type != null) {
            String up = type.toUpperCase();
            if (L2_TYPES.contains(up)) {
                level = Math.max(level, 2);
            }
            if (L3_TYPES.contains(up)) {
                level = Math.max(level, 3);
            }
        }

        if (Boolean.TRUE.equals(req.getContainsSensitiveFields()) && type != null
                && L2_TYPES.contains(type.toUpperCase())) {
            // 含敏感字段的中敏表 → 直接升 L3
            level = Math.max(level, 3);
        }

        if (req.getExpectedRows() != null) {
            if (req.getExpectedRows() > L3_THRESHOLD_ROWS) {
                level = Math.max(level, 3);
            } else if (req.getExpectedRows() > L2_THRESHOLD_ROWS) {
                level = Math.max(level, 2);
            }
        }

        return level;
    }
}