package com.wjx871.population.application;

import com.wjx871.population.common.BusinessException;
import com.wjx871.population.approval.ApprovalAction;
import com.wjx871.population.approval.ApprovalLog;
import com.wjx871.population.approval.ApprovalLogMapper;
import com.wjx871.population.audit.OperationLogService;
import jakarta.servlet.http.HttpServletRequest;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApplicationService {
    private final BusinessApplicationMapper mapper;
    private final ApplicationStateMachine stateMachine;
    private final ApprovalLogMapper approvalLogMapper;
    private final OperationLogService audit;
    private final SpecializedBusinessTypeRegistry specializedTypes;

    @Transactional
    public ApplicationView create(ApplicationRequest request, HttpServletRequest httpRequest) {
        requireGenericBusinessType(request.businessType());
        return createInternal(request, httpRequest);
    }

    @Transactional
    public ApplicationView createSpecialized(ApplicationRequest request, HttpServletRequest httpRequest) {
        return createInternal(request, httpRequest);
    }

    private ApplicationView createInternal(ApplicationRequest request, HttpServletRequest httpRequest) {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        BusinessApplication value = new BusinessApplication();
        value.setApplicationNo(generateNumber("APP"));
        apply(request, value);
        value.setApplicantUserId(user.userId());
        value.setApplicantDepartmentId(user.departmentId());
        value.setApplicantRegionCode(user.regionCode());
        value.setStatus(ApplicationStatus.DRAFT);
        value.setVersion(0);
        mapper.insert(value);
        approvalLogMapper.insert(ApprovalLog.of(null, value.getApplicationId(), ApprovalAction.CREATE, null,
                ApplicationStatus.DRAFT.name(), user.userId(), null, audit.clientIp(httpRequest)));
        return ApplicationView.from(require(value.getApplicationId()));
    }

    @Transactional(readOnly = true)
    public Page<ApplicationView> search(String no, BusinessType type, ApplicationStatus status, String applicantName,
                                        LocalDateTime from, LocalDateTime to, Pageable pageable) {
        AuthenticatedUser user = CurrentUserContext.requireUser();
        boolean approvalViewer = user.permissions().contains("approval:view");
        ApplicationQuery query = new ApplicationQuery(trim(no), type, status, trim(applicantName), from, to,
                DataScopeCriteria.current(), approvalViewer, pageable.getPageSize(), pageable.getOffset());
        return new PageImpl<>(mapper.selectByQuery(query).stream().map(ApplicationView::from).toList(), pageable,
                mapper.countByQuery(query));
    }

    @Transactional(readOnly = true)
    public ApplicationView getView(Long id) {
        BusinessApplication value = require(id);
        assertCanView(value, CurrentUserContext.requireUser());
        return ApplicationView.from(value);
    }

    @Transactional
    public ApplicationView update(Long id, ApplicationRequest request) {
        requireGenericBusinessType(request.businessType());
        return updateInternal(id, request);
    }

    @Transactional
    public ApplicationView updateSpecialized(Long id, ApplicationRequest request) {
        return updateInternal(id, request);
    }

    private ApplicationView updateInternal(Long id, ApplicationRequest request) {
        BusinessApplication value = require(id);
        AuthenticatedUser user = CurrentUserContext.requireUser();
        stateMachine.requireDraft(value.getStatus());
        assertOwnerOrAdmin(value, user);
        if (request.version() == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "version 不能为空");
        apply(request, value);
        if (mapper.updateDraft(value, request.version()) == 0) conflict();
        return ApplicationView.from(require(id));
    }

    @Transactional
    public void cancelDraft(Long id, HttpServletRequest request) {
        BusinessApplication value = require(id);
        stateMachine.requireDraft(value.getStatus());
        assertOwnerOrAdmin(value, CurrentUserContext.requireUser());
        if (mapper.updateStatus(id, ApplicationStatus.DRAFT, ApplicationStatus.CANCELLED, value.getVersion()) == 0) conflict();
        AuthenticatedUser user = CurrentUserContext.requireUser();
        approvalLogMapper.insert(ApprovalLog.of(null, id, ApprovalAction.CANCEL, ApplicationStatus.DRAFT.name(),
                ApplicationStatus.CANCELLED.name(), user.userId(), null, audit.clientIp(request)));
    }

    /**
     * 执行人/复核岗退回已批准但尚未执行的申请：
     *   - 状态由 APPROVED 转为 RETURNED，保留此前审批轨迹不变；
     *   - 调用方必须具备 application:return 权限（由 Controller 上的 @PreAuthorize 强制）；
     *   - 申请人收到退回后可再次走 submit（{@link com.wjx871.population.approval.ApprovalService#submit}）
     *     或直接撤回，无需新建审批单。
     *   - 乐观锁：调用方必须携带 version（与申请表的当前 version 一致），冲突时返回 409。
     */
    @Transactional
    public void returnApplication(Long id,
                                  com.wjx871.population.application.ReturnApplicationRequest req,
                                  HttpServletRequest httpRequest) {
        BusinessApplication value = require(id);
        stateMachine.requireReturnable(value.getStatus());
        if (req.version() == null) throw new BusinessException(HttpStatus.BAD_REQUEST, "version 不能为空");
        // 乐观锁由 mapper 的 WHERE version=#{expectedVersion} 强制：客户端版本必须与当前一致，
        // 否则 update 返回 0、抛 409，前端刷新后再试。
        if (mapper.updateStatus(id, ApplicationStatus.APPROVED, ApplicationStatus.RETURNED, req.version()) == 0) {
            conflict();
        }
        AuthenticatedUser user = CurrentUserContext.requireUser();
        approvalLogMapper.insert(ApprovalLog.of(null, id, ApprovalAction.RETURN,
                ApplicationStatus.APPROVED.name(), ApplicationStatus.RETURNED.name(),
                user.userId(), req.comment(), audit.clientIp(httpRequest)));
        audit.recordTransactional(user.userId(), "APPLICATION_RETURN", httpRequest);
    }

    public BusinessApplication require(Long id) {
        return mapper.selectById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "申请不存在"));
    }

    public void assertCanView(BusinessApplication value, AuthenticatedUser user) {
        if (value.getApplicantUserId().equals(user.userId())) return;
        if (!user.permissions().contains("approval:view")) forbidden();
        if (user.dataScope() == DataScope.ALL) return;
        if (user.dataScope() == DataScope.DEPARTMENT && value.getApplicantDepartmentId() != null
                && value.getApplicantDepartmentId().equals(user.departmentId())) return;
        if (user.dataScope() == DataScope.REGION && isRegionInScope(value.getApplicantRegionCode(), user.regionCode())) return;
        forbidden();
    }

    public void assertOwner(BusinessApplication value, AuthenticatedUser user) {
        if (!value.getApplicantUserId().equals(user.userId())) forbidden();
    }

    private void assertOwnerOrAdmin(BusinessApplication value, AuthenticatedUser user) {
        if (!value.getApplicantUserId().equals(user.userId())
                && !user.permissions().contains("system:user:manage")) forbidden();
    }

    private void apply(ApplicationRequest request, BusinessApplication value) {
        value.setBusinessType(request.businessType());
        value.setTitle(request.title().trim());
        value.setTargetPersonId(request.targetPersonId());
        value.setTargetHouseholdId(request.targetHouseholdId());
        value.setReason(request.reason().trim());
        value.setRemark(trim(request.remark()));
    }

    public static String generateNumber(String prefix) {
        return prefix + "-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    public static void conflict() {
        throw new BusinessException(HttpStatus.CONFLICT, "记录已被其他用户处理，请刷新后重试");
    }

    private void requireGenericBusinessType(BusinessType type) {
        if (specializedTypes.requiresDedicatedEntry(type)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该业务必须通过对应的专业业务入口创建");
        }
    }

    /** Keeps approval detail authorization consistent with hierarchical region list filtering. */
    private boolean isRegionInScope(String targetRegionCode, String scopeRegionCode) {
        if (targetRegionCode == null || scopeRegionCode == null || scopeRegionCode.isBlank()) return false;
        String prefix = scopeRegionCode.replaceFirst("0+$", "");
        return !prefix.isEmpty() && targetRegionCode.startsWith(prefix);
    }

    private void forbidden() { throw new BusinessException(HttpStatus.FORBIDDEN, "无权访问该申请"); }
    private String trim(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
