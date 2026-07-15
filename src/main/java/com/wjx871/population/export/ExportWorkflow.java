package com.wjx871.population.export;import com.wjx871.population.application.*;import com.wjx871.population.common.BusinessException;import java.util.*;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Component;
@Component class ExportSubmissionValidator implements ApplicationSubmissionValidator {private final ExportMapper mapper;private final ExportPolicy policy;ExportSubmissionValidator(ExportMapper m,ExportPolicy p){mapper=m;policy=p;}public boolean supports(BusinessType t){return t==BusinessType.SENSITIVE_DATA_EXPORT;}public void validate(BusinessApplication a){DataExportRequest r=mapper.findRequest(a.getApplicationId()).orElseThrow(()->new BusinessException(HttpStatus.CONFLICT,"导出专业详情不存在"));if(r.getReason()==null||r.getReason().isBlank())throw new BusinessException(HttpStatus.CONFLICT,"敏感导出理由不能为空");policy.validate(r.getExportModule(),Map.of(),List.of(r.getRequestedFields().split(",")),true);}}
@Component class ExportStatusListener implements ApplicationStatusListener {private final ExportMapper mapper;ExportStatusListener(ExportMapper m){mapper=m;}public boolean supports(BusinessType t){return t==BusinessType.SENSITIVE_DATA_EXPORT;}// 以专业表自身的当前状态作为 `from`：导出申请可能存在「申请已 RETURNED 但专业表未联动」的场景，
// 反推 from 会与实际 business_status 不一致，0 行更新并抛 409；与 MigrationStatusListener 保持一致。
public void onStatusChanged(BusinessApplication a, ApplicationStatus s) {
    com.wjx871.population.export.DataExportRequest r = mapper.findRequest(a.getApplicationId()).orElse(null);
    if (r == null) return;
    if (mapper.updateRequestStatus(a.getApplicationId(), r.getBusinessStatus(), s.name(), null, null) == 0) {
        throw new BusinessException(HttpStatus.CONFLICT, "导出申请状态已变化，请刷新后重试");
    }
}}
