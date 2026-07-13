package com.example.population.service.impl;

import com.example.population.dto.DataExportRequestDTO;
import com.example.population.entity.DataExportLog;
import com.example.population.mapper.DataExportLogMapper;
import com.example.population.service.SensitiveExportService;
import com.example.population.service.SensitivityEvaluator;
import com.example.population.util.SecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class SensitiveExportServiceImpl implements SensitiveExportService {

    private final SensitivityEvaluator sensitivityEvaluator;
    private final DataExportLogMapper dataExportLogMapper;

    @Override
    public int evaluate(DataExportRequestDTO req) {
        return sensitivityEvaluator.evaluate(req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataExportLog executeDirect(DataExportRequestDTO req, SecurityContext sc) {
        int sensitivity = sensitivityEvaluator.evaluate(req);
        DataExportLog row = buildExportLog(req, sc, sensitivity, null);
        row.setResultCode("APPROVED");
        dataExportLogMapper.insert(row);
        return row;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DataExportLog landApprovedExport(DataExportRequestDTO req, SecurityContext sc,
                                            int sensitivity, Long approvalId) {
        DataExportLog row = buildExportLog(req, sc, sensitivity, approvalId);
        row.setResultCode("APPROVED");
        dataExportLogMapper.insert(row);
        return row;
    }

    private DataExportLog buildExportLog(DataExportRequestDTO req, SecurityContext sc,
                                         int sensitivity, Long approvalId) {
        DataExportLog row = new DataExportLog();
        row.setExportNo("EX" + System.currentTimeMillis());
        if (sc != null) {
            row.setUserId(sc.getUserId());
            row.setDepartmentId(sc.getDepartmentId());
        }
        row.setExportTypeCode(req.getExportTypeCode());
        row.setQueryConditionSummary(req.getQueryConditionSummary());
        row.setExportedRows(req.getExpectedRows() == null ? 0 : req.getExpectedRows().intValue());
        row.setSensitivityLevel(sensitivity);
        row.setApprovalId(approvalId);
        row.setFileName("pending-" + row.getExportNo() + "." + defaultFormat(req));
        row.setExportedAt(LocalDateTime.now());
        return row;
    }

    private String defaultFormat(DataExportRequestDTO req) {
        if (req.getFileFormat() == null || req.getFileFormat().isBlank()) return "csv";
        return req.getFileFormat().toLowerCase();
    }
}