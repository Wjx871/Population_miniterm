package com.example.population.service;

import com.example.population.dto.DataExportRequestDTO;
import com.example.population.service.impl.DefaultSensitivityEvaluator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * P0-4 SensitivityEvaluator 单元测试。
 */
class SensitivityEvaluatorTest {

    private final SensitivityEvaluator eval = new DefaultSensitivityEvaluator();

    @Test
    @DisplayName("L1: 不在 L2/L3 类型，行数 <= 1000 → 1")
    void evaluate_lowSensitivity() {
        DataExportRequestDTO req = new DataExportRequestDTO();
        req.setExportTypeCode("DICTIONARY");
        req.setExpectedRows(100L);
        req.setContainsSensitiveFields(false);
        assertThat(eval.evaluate(req)).isEqualTo(1);
    }

    @Test
    @DisplayName("L2: 命中 PERSON 类型 + 行数 2000 → 2")
    void evaluate_midSensitivityByTypeAndRows() {
        DataExportRequestDTO req = new DataExportRequestDTO();
        req.setExportTypeCode("PERSON");
        req.setExpectedRows(2_000L);
        assertThat(eval.evaluate(req)).isEqualTo(2);
    }

    @Test
    @DisplayName("L3: 含敏感字段的 PERSON 表 → 3")
    void evaluate_highBySensitiveFlag() {
        DataExportRequestDTO req = new DataExportRequestDTO();
        req.setExportTypeCode("PERSON");
        req.setExpectedRows(100L);
        req.setContainsSensitiveFields(true);
        assertThat(eval.evaluate(req)).isEqualTo(3);
    }

    @Test
    @DisplayName("L3: KEY_POPULATION 类型 → 3")
    void evaluate_highByType() {
        DataExportRequestDTO req = new DataExportRequestDTO();
        req.setExportTypeCode("KEY_POPULATION");
        req.setExpectedRows(10L);
        assertThat(eval.evaluate(req)).isEqualTo(3);
    }

    @Test
    @DisplayName("L3: 行数 > 10000 → 3")
    void evaluate_highByRows() {
        DataExportRequestDTO req = new DataExportRequestDTO();
        req.setExportTypeCode("DICTIONARY");
        req.setExpectedRows(20_000L);
        assertThat(eval.evaluate(req)).isEqualTo(3);
    }

    @Test
    @DisplayName("requiresApproval: L2/L3 → true")
    void requiresApproval() {
        DataExportRequestDTO mid = new DataExportRequestDTO();
        mid.setExportTypeCode("PERSON");
        mid.setExpectedRows(5_000L);
        assertThat(eval.requiresApproval(mid)).isTrue();

        DataExportRequestDTO low = new DataExportRequestDTO();
        low.setExportTypeCode("DICTIONARY");
        low.setExpectedRows(50L);
        assertThat(eval.requiresApproval(low)).isFalse();
    }
}