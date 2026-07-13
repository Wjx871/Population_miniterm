package com.example.population.service;

import com.example.population.dto.DataExportRequestDTO;

/**
 * 敏感度评估器。
 * <p>
 * 设计文档 §6 / D-07：高敏导出走三级审批。
 *
 * <p><b>分级规则（P0）</b>：
 * <ul>
 *   <li>L1 (低敏)：表名不含 person/household/raw_key，单次导出行数 &lt;= 1000 → 不需要审批</li>
 *   <li>L2 (中敏)：表名属于 person/floating/key_population，行数 &gt; 1000 → L2 审批</li>
 *   <li>L3 (高敏)：表名属于 key_population 实名字段、原始身份证件表、行数 &gt; 10000 → L3 审批</li>
 * </ul>
 *
 * 返回值语义：1=低敏，2=中敏，3=高敏。
 */
public interface SensitivityEvaluator {

    /**
     * 计算导出请求的敏感度级别。
     *
     * @param req   导出请求
     * @return 1 / 2 / 3
     */
    int evaluate(DataExportRequestDTO req);

    /**
     * 是否需要审批。L2 及以上需要走审批流。
     */
    default boolean requiresApproval(DataExportRequestDTO req) {
        return evaluate(req) >= 2;
    }

    /**
     * 是否必须 L3 审批（最高级别）。
     */
    default boolean requiresL3(DataExportRequestDTO req) {
        return evaluate(req) >= 3;
    }
}