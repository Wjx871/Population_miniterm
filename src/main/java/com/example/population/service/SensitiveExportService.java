package com.example.population.service;

import com.example.population.dto.DataExportRequestDTO;
import com.example.population.entity.DataExportLog;
import com.example.population.util.SecurityContext;

/**
 * 高敏导出审批链路服务。
 * <p>
 * 设计文档 §6 / D-07：L2/L3 导出走三级审批链路。
 */
public interface SensitiveExportService {

    /**
     * 评估敏感度。
     */
    int evaluate(DataExportRequestDTO req);

    /**
     * 直接执行并写日志（L1 用）。
     */
    DataExportLog executeDirect(DataExportRequestDTO req, SecurityContext sc);

    /**
     * 审批通过后真正落地日志（L2/L3 用）。
     */
    DataExportLog landApprovedExport(DataExportRequestDTO req, SecurityContext sc,
                                     int sensitivity, Long approvalId);
}