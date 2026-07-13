package com.example.population.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * 数据导出请求 DTO。
 * <p>
 * 由 {@code DataExportController.submit} 接收；根据敏感度评估：
 * <ul>
 *   <li>L1：直接生成导出文件 + 写 data_export_log</li>
 *   <li>L2/L3：调用 ApprovalGateService.submit 走审批；审批通过后才执行导出 + 写日志</li>
 * </ul>
 */
@Data
@Schema(description = "数据导出请求")
public class DataExportRequestDTO implements Serializable {

    @NotBlank
    @Schema(description = "导出业务类型编码（导出对象，如 PERSON/FLOATING/KEY_POPULATION/RESIDENCE_ARCHIVE 等）",
            example = "PERSON", requiredMode = Schema.RequiredMode.REQUIRED)
    private String exportTypeCode;

    @Schema(description = "查询条件摘要（前端原样透传，用于审计 + 审批展示）",
            example = "{\"regionCode\":\"110101\",\"status\":\"ACTIVE\"}")
    private String queryConditionSummary;

    @Schema(description = "查询条件 JSON（解析后用于实际过滤，可选）")
    private Map<String, Object> queryParams;

    @NotNull
    @Schema(description = "预估导出行数（前端告知，用于敏感度评估）",
            example = "5000", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long expectedRows;

    @Schema(description = "是否包含身份证号、手机号等敏感字段", example = "true")
    private Boolean containsSensitiveFields;

    @Schema(description = "导出文件类型：CSV / XLSX / JSON")
    private String fileFormat;

    @Schema(description = "导出理由（高敏时必填）")
    private String exportReason;
}