package com.example.population.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.population.annotation.RequiresPermission;
import com.example.population.dto.ApprovalDraftDTO;
import com.example.population.dto.DataExportRequestDTO;
import com.example.population.dto.PageVO;
import com.example.population.dto.Result;
import com.example.population.entity.DataExportLog;
import com.example.population.exception.BizException;
import com.example.population.service.ApprovalGateService;
import com.example.population.service.DataExportLogService;
import com.example.population.service.SensitiveExportService;
import com.example.population.util.PageUtil;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 数据导出审计 + 审批控制器。
 * <p>
 * 设计文档 §6 / D-07：高敏导出走三级审批。
 *
 * <p><b>流程</b>：
 * <ul>
 *   <li>L1 (低敏)：直接写 data_export_log，返回 exportId</li>
 *   <li>L2 (中敏)：调用 ApprovalGateService.submit，businessType=SENSITIVE_EXPORT_L2，requiredLevel=2</li>
 *   <li>L3 (高敏)：businessType=SENSITIVE_EXPORT_L3，requiredLevel=3（必须 L3 审批）</li>
 * </ul>
 */
@Slf4j
@Tag(name = "数据导出审计")
@RestController
@RequestMapping("/api/data-export-logs")
@RequiredArgsConstructor
public class DataExportLogController {

    private final DataExportLogService exportService;
    private final SensitiveExportService sensitiveExportService;
    private final ApprovalGateService approvalGateService;
    private final ObjectMapper objectMapper;

    @RequiresPermission("log:query")
    @Operation(summary = "分页查询导出日志")
    @GetMapping
    public Result<PageVO<DataExportLog>> page(@RequestParam(defaultValue = "1") long current,
                                               @RequestParam(defaultValue = "10") long size,
                                               @RequestParam(required = false) Long userId,
                                               @RequestParam(required = false) String exportTypeCode,
                                               @RequestParam(required = false) Integer sensitivityLevel) {
        Page<DataExportLog> p = (Page<DataExportLog>) exportService.page(current, size, userId, exportTypeCode, sensitivityLevel);
        return Result.success(PageUtil.toPageVO(p, p.getRecords()));
    }

    @RequiresPermission({"export:normal", "export:sensitive"})
    @Operation(summary = "提交导出请求（L1 直接执行；L2/L3 走审批流）")
    @PostMapping("/submit")
    public Result<Map<String, Object>> submit(@Valid @RequestBody DataExportRequestDTO req) throws Exception {
        SecurityContext sc = SecurityContext.current();
        if (sc == null || sc.getUserId() == null) {
            throw new BizException(401, "未登录");
        }

        int sensitivity = sensitiveExportService.evaluate(req);
        log.info("data export submitted uid={} type={} rows={} sensitive={} level={}",
                sc.getUserId(), req.getExportTypeCode(), req.getExpectedRows(),
                req.getContainsSensitiveFields(), sensitivity);

        if (sensitivity < 2) {
            DataExportLog logRow = sensitiveExportService.executeDirect(req, sc);
            Map<String, Object> data = new HashMap<>();
            data.put("exportId", logRow.getExportId());
            data.put("sensitivityLevel", sensitivity);
            data.put("requiresApproval", false);
            return Result.success("导出已记录，请执行业务导出", data);
        }

        if (req.getExportReason() == null || req.getExportReason().isBlank()) {
            throw new BizException(400, "敏感导出必须填写导出理由（exportReason）");
        }

        ApprovalDraftDTO draft = new ApprovalDraftDTO();
        draft.setBusinessType(sensitivity >= 3 ? "SENSITIVE_EXPORT_L3" : "SENSITIVE_EXPORT_L2");
        draft.setPayloadJson(objectMapper.writeValueAsString(req));
        draft.setApplyReason(req.getExportReason());
        Long approvalId = approvalGateService.submit(draft);

        Map<String, Object> data = new HashMap<>();
        data.put("approvalId", approvalId);
        data.put("sensitivityLevel", sensitivity);
        data.put("requiresApproval", true);
        data.put("requiredLevel", sensitivity >= 3 ? 3 : 2);
        return Result.success("高敏导出已提交审批（" + (sensitivity >= 3 ? "L3" : "L2") + "）", data);
    }
}