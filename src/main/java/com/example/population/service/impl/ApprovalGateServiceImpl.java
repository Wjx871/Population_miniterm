package com.example.population.service.impl;

import com.example.population.dto.ApprovalDraftDTO;
import com.example.population.dto.HouseholdCreateDTO;
import com.example.population.dto.MigrationInDTO;
import com.example.population.dto.MigrationOutDTO;
import com.example.population.dto.PersonCreateDTO;
import com.example.population.dto.PersonUpdateDTO;
import com.example.population.entity.BusinessApplication;
import com.example.population.entity.Household;
import com.example.population.entity.SysApprovalLog;
import com.example.population.entity.SysApprovalRequest;
import com.example.population.exception.BizException;
import com.example.population.exception.ForbiddenException;
import com.example.population.exception.NotFoundException;
import com.example.population.mapper.BusinessApplicationMapper;
import com.example.population.mapper.SysApprovalLogMapper;
import com.example.population.mapper.SysApprovalRequestMapper;
import com.example.population.service.ApplicationMaterialService;
import com.example.population.service.ApprovalGateService;
import com.example.population.service.HouseholdService;
import com.example.population.service.MigrationInService;
import com.example.population.service.MigrationOutService;
import com.example.population.service.PersonService;
import com.example.population.util.SecurityContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 审批联动实现（虚拟草稿 + 复用现有 sys_approval_request / sys_approval_log 表）。
 * <p>
 * 关键变更（P0-6）：
 * <ul>
 *   <li>草稿载荷由 sys_approval_request 的独立列 {@code business_type / business_id / payload_json / apply_reason}
 *       分别承载，移除旧的 {@code buildApplyReason / parseApplyReason / extractTag} 字符串拼接逻辑，
 *       杜绝用户输入含 {@code [BT=PERSON_UPDATE]} / {@code [REASON=]} 触发的"草稿劫持"漏洞</li>
 *   <li>{@link #submit}：把 payload 写入 payload_json（JSON 列）；apply_reason 只写用户自由文本</li>
 *   <li>{@link #approve}：按 business_type dispatch，反序列化 payload_json，调对应 Service</li>
 *   <li>{@link #reject}：仅写状态 + 日志，不动业务数据</li>
 * </ul>
 * <p>
 * 兼容：旧 apply_reason 中含 {@code [BT=...][PID=...][APPID=...][REASON=...]}xxx 格式的存量数据
 *      已被 {@code sql/migration_20260712_p0_approval_struct.sql} 回填到独立列，应用层读取时
 *      优先看独立列；如全为空再尝试从 apply_reason 旧格式解析（兜底）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApprovalGateServiceImpl implements ApprovalGateService {

    private final SysApprovalRequestMapper requestMapper;
    private final SysApprovalLogMapper logMapper;
    private final BusinessApplicationMapper businessApplicationMapper;
    private final ObjectMapper objectMapper;
    private final ApplicationMaterialService applicationMaterialService;

    @Autowired
    private ApplicationContext ctx;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submit(ApprovalDraftDTO draft) {
        if (draft == null || draft.getBusinessType() == null) {
            throw new BizException(400, "审批草稿参数缺失");
        }
        SecurityContext sc = SecurityContext.current();
        if (sc == null || sc.getUserId() == null) {
            throw new ForbiddenException("未登录");
        }

        // 1) 先建占位 business_application（status=PENDING_APPROVAL），保证 sys_approval_request.application_id FK
        BusinessApplication ba = new BusinessApplication();
        ba.setApplicationNo("BA" + System.currentTimeMillis());
        ba.setBusinessTypeCode(draft.getBusinessType());
        ba.setSubmitUserId(sc.getUserId());
        ba.setApplicantName(sc.getRealName() == null ? sc.getUsername() : sc.getRealName());
        ba.setApplicantIdentityType("ID_CARD");
        ba.setApplicantIdentityNo("-");  // 占位：经办人本人可能没有身份证号，业务数据 identityNo 在 payloadJson 里
        ba.setHandlingDepartmentId(sc.getDepartmentId() == null ? 1L : sc.getDepartmentId());
        ba.setStatus("PENDING_APPROVAL");
        ba.setCurrentStep("WAITING_L3");
        ba.setSubmittedAt(LocalDateTime.now());
        try {
            businessApplicationMapper.insert(ba);
        } catch (Exception e) {
            log.error("创建 business_application 占位失败", e);
            throw new BizException(500, "提交审批失败（业务单占位）：" + e.getMessage());
        }

        // 2) 再写 sys_approval_request：载荷拆成独立列
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalNo("AP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        req.setApplicationId(ba.getApplicationId());
        req.setRequiredLevel(3);
        req.setStatus("PENDING");
        req.setBusinessType(draft.getBusinessType());
        req.setBusinessId(draft.getBusinessId());
        // payloadJson 必须是合法 JSON；如调用方传 null，写空 JSON 对象
        req.setPayloadJson(draft.getPayloadJson() == null || draft.getPayloadJson().isEmpty()
                ? "{}" : draft.getPayloadJson());
        // apply_reason 仅承载用户自由文本（不超过 500 字符，匹配 schema）
        String reason = draft.getApplyReason() == null ? "" : draft.getApplyReason();
        req.setApplyReason(reason.length() > 500 ? reason.substring(0, 500) : reason);
        try {
            requestMapper.insert(req);
        } catch (Exception e) {
            log.error("写入审批单失败", e);
            throw new BizException(500, "提交审批失败：" + e.getMessage());
        }
        log.info("提交审批单 approvalId={} applicationId={} businessType={} businessId={} applyUserId={}",
                req.getApprovalId(), ba.getApplicationId(), draft.getBusinessType(),
                draft.getBusinessId(), sc.getUserId());
        return req.getApprovalId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long approve(Long approvalId, String comment) {
        SecurityContext sc = SecurityContext.current();
        if (sc == null || sc.getUserId() == null) {
            throw new ForbiddenException("未登录");
        }
        if (approvalId == null) {
            throw new BizException(400, "approvalId 缺失");
        }

        SysApprovalRequest req = requestMapper.selectById(approvalId);
        if (req == null) {
            throw new NotFoundException("审批单[" + approvalId + "]不存在");
        }
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new BizException(409, "审批单当前状态非 PENDING，无法审批");
        }

        // P0: 申请人 ≠ 审批人 校验（设计文档 §6 / D-05：禁止"自己审自己"）
        BusinessApplication applicant = businessApplicationMapper.selectById(req.getApplicationId());
        if (applicant != null && applicant.getSubmitUserId() != null
                && applicant.getSubmitUserId().equals(sc.getUserId())) {
            log.warn("审批越权尝试：applicantUserId={} approverUserId={} approvalId={}",
                    applicant.getSubmitUserId(), sc.getUserId(), approvalId);
            throw new ForbiddenException("申请人不能审批自己的申请");
        }

        ApprovalDraftDTO draft = loadDraft(req);

        // 落地前的材料闸门：若前端在草稿里挂了 applicationId 且业务类型有最低必交要求，
        // 必须全部材料已 VERIFIED 才允许真实落地。
        if (draft.getApplicationId() != null) {
            applicationMaterialService.assertRequiredVerified(draft.getApplicationId(), draft.getBusinessType());
        }

        Long landedId;
        try {
            landedId = dispatchLanding(draft, sc, req.getApprovalId());
        } catch (Exception e) {
            log.error("审批落地业务失败 approvalId={}", approvalId, e);
            throw new BizException(500, "审批通过但落地失败：" + e.getMessage(), e);
        }

        req.setStatus("APPROVED");
        req.setCurrentApproverId(sc.getUserId());
        req.setFinishedAt(LocalDateTime.now());
        requestMapper.updateById(req);

        // 回填 business_application 状态
        BusinessApplication ba = businessApplicationMapper.selectById(req.getApplicationId());
        if (ba != null) {
            ba.setStatus("APPROVED");
            ba.setCompletedAt(LocalDateTime.now());
            businessApplicationMapper.updateById(ba);
        }

        SysApprovalLog logRow = new SysApprovalLog();
        logRow.setApprovalId(approvalId);
        logRow.setStepNo(1);
        logRow.setApproverUserId(sc.getUserId());
        logRow.setActionCode("APPROVE");
        logRow.setComment(comment == null ? "审批通过" : comment);
        logRow.setApprovedAt(LocalDateTime.now());
        logMapper.insert(logRow);

        return landedId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long approvalId, String comment) {
        SecurityContext sc = SecurityContext.current();
        if (sc == null || sc.getUserId() == null) {
            throw new ForbiddenException("未登录");
        }

        SysApprovalRequest req = requestMapper.selectById(approvalId);
        if (req == null) {
            throw new NotFoundException("审批单[" + approvalId + "]不存在");
        }
        if (!"PENDING".equalsIgnoreCase(req.getStatus())) {
            throw new BizException(409, "审批单当前状态非 PENDING，无法驳回");
        }
        // P0: 申请人 ≠ 审批人（驳回也按同一原则）
        BusinessApplication applicant = businessApplicationMapper.selectById(req.getApplicationId());
        if (applicant != null && applicant.getSubmitUserId() != null
                && applicant.getSubmitUserId().equals(sc.getUserId())) {
            log.warn("驳回越权尝试：applicantUserId={} approverUserId={} approvalId={}",
                    applicant.getSubmitUserId(), sc.getUserId(), approvalId);
            throw new ForbiddenException("申请人不能驳回自己的申请");
        }
        req.setStatus("REJECTED");
        req.setCurrentApproverId(sc.getUserId());
        req.setFinishedAt(LocalDateTime.now());
        requestMapper.updateById(req);

        BusinessApplication ba2 = businessApplicationMapper.selectById(req.getApplicationId());
        if (ba2 != null) {
            ba2.setStatus("REJECTED");
            ba2.setCompletedAt(LocalDateTime.now());
            businessApplicationMapper.updateById(ba2);
        }

        SysApprovalLog logRow = new SysApprovalLog();
        logRow.setApprovalId(approvalId);
        logRow.setStepNo(1);
        logRow.setApproverUserId(sc.getUserId());
        logRow.setActionCode("REJECT");
        logRow.setComment(comment == null ? "驳回" : comment);
        logRow.setApprovedAt(LocalDateTime.now());
        logMapper.insert(logRow);
    }

    /**
     * 从 sys_approval_request 加载草稿。
     * <p>
     * 优先使用独立列；如为旧数据（独立列为空但 apply_reason 含旧格式标记）走
     * {@link #parseLegacyReason(String)} 兜底解析。
     */
    private ApprovalDraftDTO loadDraft(SysApprovalRequest req) {
        ApprovalDraftDTO draft = new ApprovalDraftDTO();
        draft.setBusinessType(req.getBusinessType());
        draft.setBusinessId(req.getBusinessId());
        draft.setApplicationId(req.getApplicationId());
        draft.setPayloadJson(req.getPayloadJson());
        draft.setApplyReason(req.getApplyReason());

        // 兜底：旧数据无独立列
        if (draft.getBusinessType() == null && req.getApplyReason() != null
                && req.getApplyReason().startsWith("[BT=")) {
            try {
                return parseLegacyReason(req.getApplyReason());
            } catch (Exception e) {
                log.warn("审批单[{}]的旧 apply_reason 解析失败：{}", req.getApprovalId(), e.getMessage());
            }
        }

        if (draft.getBusinessType() == null) {
            throw new BizException(400, "审批单[" + req.getApprovalId() + "]业务类型缺失");
        }
        return draft;
    }

    /**
     * 按 businessType 反序列化 payload_json，调用对应 Service。
     */
    private Long dispatchLanding(ApprovalDraftDTO draft, SecurityContext sc) throws Exception {
        return dispatchLanding(draft, sc, null);
    }

    private Long dispatchLanding(ApprovalDraftDTO draft, SecurityContext sc, Long approvalId) throws Exception {
        if (draft == null || draft.getBusinessType() == null) {
            throw new BizException(400, "草稿 businessType 缺失");
        }
        String type = draft.getBusinessType();
        String json = draft.getPayloadJson();
        if (json == null || json.isEmpty()) {
            json = "{}";
        }

        return switch (type) {
            case "PERSON_CREATE" -> {
                PersonCreateDTO dto = objectMapper.readValue(json, PersonCreateDTO.class);
                yield ctx.getBean(PersonService.class).createPerson(dto).getPersonId();
            }
            case "PERSON_UPDATE" -> {
                PersonUpdateDTO dto = objectMapper.readValue(json, PersonUpdateDTO.class);
                yield ctx.getBean(PersonService.class).updatePerson(draft.getBusinessId(), dto)
                        ? draft.getBusinessId() : null;
            }
            case "HOUSEHOLD_ESTABLISH" -> {
                HouseholdCreateDTO dto = objectMapper.readValue(json, HouseholdCreateDTO.class);
                Household h = ctx.getBean(HouseholdService.class).establishHousehold(dto);
                yield h == null ? null : h.getHouseholdId();
            }
            case "MIGRATION_IN" -> {
                MigrationInDTO dto = objectMapper.readValue(json, MigrationInDTO.class);
                yield ctx.getBean(MigrationInService.class).createMigrationIn(dto).getInId();
            }
            case "MIGRATION_OUT" -> {
                MigrationOutDTO dto = objectMapper.readValue(json, MigrationOutDTO.class);
                yield ctx.getBean(MigrationOutService.class).createMigrationOut(dto).getOutId();
            }
            case "SENSITIVE_EXPORT_L2", "SENSITIVE_EXPORT_L3" -> {
                // 高敏导出审批通过 → 落 data_export_log 并返回 exportId
                com.example.population.dto.DataExportRequestDTO exportReq =
                        objectMapper.readValue(json, com.example.population.dto.DataExportRequestDTO.class);
                int sensitivity = "SENSITIVE_EXPORT_L3".equals(type) ? 3 : 2;
                com.example.population.entity.DataExportLog row =
                        ctx.getBean(com.example.population.service.SensitiveExportService.class)
                                .landApprovedExport(exportReq, sc, sensitivity, approvalId);
                yield row.getExportId();
            }
            default -> throw new BizException(400, "未知业务类型：" + type);
        };
    }

    /**
     * 兜底：旧格式 {@code [BT=...][PID=...][APPID=...][REASON=...]payloadJson} 解析。
     * <p>
     * 注意：仅作向后兼容，新提交一律使用独立列；本方法不会再被 {@link #submit} 调用。
     */
    private ApprovalDraftDTO parseLegacyReason(String s) {
        if (s == null || s.isEmpty()) {
            throw new BizException(400, "审批单草稿字段为空");
        }
        String bt = extractLegacyTag(s, "BT");
        String pid = extractLegacyTag(s, "PID");
        String appId = extractLegacyTag(s, "APPID");
        String reason = extractLegacyTag(s, "REASON");
        String payload = "";
        int idx = s.indexOf("[REASON=");
        if (idx >= 0) {
            int end = s.indexOf(']', idx);
            if (end >= 0) {
                payload = s.substring(end + 1);
            }
        }
        ApprovalDraftDTO d = new ApprovalDraftDTO();
        d.setBusinessType(bt);
        if (!pid.isEmpty()) d.setBusinessId(Long.parseLong(pid));
        if (!appId.isEmpty()) d.setApplicationId(Long.parseLong(appId));
        d.setApplyReason(reason);
        d.setPayloadJson(payload);
        return d;
    }

    private String extractLegacyTag(String s, String tag) {
        String open = "[" + tag + "=";
        int i = s.indexOf(open);
        if (i < 0) return "";
        int from = i + open.length();
        int end = s.indexOf(']', from);
        if (end < 0) return "";
        return s.substring(from, end);
    }
}