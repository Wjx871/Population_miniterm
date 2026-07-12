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
 * 关键：
 * <ul>
 *   <li>{@link #submit}：把 payload 序列化进 applyReason 字段（沿用现有列）</li>
 *   <li>{@link #approve}：按 businessType dispatch，反序列化 payload，调对应 Service</li>
 *   <li>{@link #reject}：仅写状态 + 日志，不动业务数据</li>
 * </ul>
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

        // 2) 再写 sys_approval_request
        SysApprovalRequest req = new SysApprovalRequest();
        req.setApprovalNo("AP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 4).toUpperCase());
        req.setApplicationId(ba.getApplicationId());
        req.setRequiredLevel(3);
        req.setStatus("PENDING");
        req.setApplyReason(buildApplyReason(draft));
        try {
            requestMapper.insert(req);
        } catch (Exception e) {
            log.error("写入审批单失败", e);
            throw new BizException(500, "提交审批失败：" + e.getMessage());
        }
        log.info("提交审批单 approvalId={} applicationId={} businessType={} applyUserId={}",
                req.getApprovalId(), ba.getApplicationId(), draft.getBusinessType(), sc.getUserId());
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

        ApprovalDraftDTO draft = parseApplyReason(req.getApplyReason());

        // 落地前的材料闸门：若前端在草稿里挂了 applicationId 且业务类型有最低必交要求，
        // 必须全部材料已 VERIFIED 才允许真实落地。
        if (draft.getApplicationId() != null) {
            applicationMaterialService.assertRequiredVerified(draft.getApplicationId(), draft.getBusinessType());
        }

        Long landedId;
        try {
            landedId = dispatchLanding(draft, sc);
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
        req.setStatus("REJECTED");
        req.setCurrentApproverId(sc.getUserId());
        req.setFinishedAt(LocalDateTime.now());
        requestMapper.updateById(req);

        BusinessApplication ba = businessApplicationMapper.selectById(req.getApplicationId());
        if (ba != null) {
            ba.setStatus("REJECTED");
            ba.setCompletedAt(LocalDateTime.now());
            businessApplicationMapper.updateById(ba);
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
     * 按 businessType 反序列化 payload，调用对应 Service。
     */
    private Long dispatchLanding(ApprovalDraftDTO draft, SecurityContext sc) throws Exception {
        if (draft == null || draft.getBusinessType() == null) {
            throw new BizException(400, "草稿 businessType 缺失");
        }
        String type = draft.getBusinessType();
        String json = draft.getPayloadJson();

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
            default -> throw new BizException(400, "未知业务类型：" + type);
        };
    }

    /**
     * 把 draft 拼成单条字符串塞进 applyReason 字段。
     * 格式：{@code [BT=PERSON_CREATE][PID=123][APPID=1][REASON=xxx]}payloadJson
     */
    private String buildApplyReason(ApprovalDraftDTO d) {
        StringBuilder sb = new StringBuilder();
        sb.append("[BT=").append(d.getBusinessType()).append(']');
        if (d.getBusinessId() != null) {
            sb.append("[PID=").append(d.getBusinessId()).append(']');
        }
        if (d.getApplicationId() != null) {
            sb.append("[APPID=").append(d.getApplicationId()).append(']');
        }
        String r = d.getApplyReason() == null ? "" : d.getApplyReason();
        sb.append("[REASON=").append(r).append(']');
        String pj = d.getPayloadJson() == null ? "" : d.getPayloadJson();
        sb.append(pj);
        return sb.toString();
    }

    /**
     * 解析 applyReason 字符串回 ApprovalDraftDTO。
     */
    private ApprovalDraftDTO parseApplyReason(String s) {
        if (s == null || s.isEmpty()) throw new BizException(400, "审批单草稿字段为空");
        try {
            String bt = extractTag(s, "BT");
            String pid = extractTag(s, "PID");
            String appId = extractTag(s, "APPID");
            String reason = extractTag(s, "REASON");
            String sepIdx = "";
            // payload 是 [REASON=xxx] 之后的所有字符
            int idx = s.indexOf("[REASON=");
            if (idx >= 0) {
                int end = s.indexOf(']', idx);
                if (end >= 0) {
                    sepIdx = s.substring(end + 1);
                }
            }
            ApprovalDraftDTO d = new ApprovalDraftDTO();
            d.setBusinessType(bt);
            if (!pid.isEmpty()) d.setBusinessId(Long.parseLong(pid));
            if (!appId.isEmpty()) d.setApplicationId(Long.parseLong(appId));
            d.setApplyReason(reason);
            d.setPayloadJson(sepIdx);
            return d;
        } catch (Exception e) {
            throw new BizException(500, "解析审批草稿失败：" + e.getMessage(), e);
        }
    }

    private String extractTag(String s, String tag) {
        String open = "[" + tag + "=";
        int i = s.indexOf(open);
        if (i < 0) return "";
        int from = i + open.length();
        int end = s.indexOf(']', from);
        if (end < 0) return "";
        return s.substring(from, end);
    }
}
