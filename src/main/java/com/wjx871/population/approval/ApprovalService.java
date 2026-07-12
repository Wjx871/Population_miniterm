package com.wjx871.population.approval;

import com.wjx871.population.application.*;
import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.*;
import com.wjx871.population.security.*;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ApprovalService {
    private final ApplicationService applications;
    private final BusinessApplicationMapper applicationMapper;
    private final ApplicationStateMachine stateMachine;
    private final ApprovalRequestMapper mapper;
    private final ApprovalLogMapper logs;
    private final ApplicationMaterialMapper materials;
    private final OperationLogService audit;
    private final List<ApplicationSubmissionValidator> submissionValidators;
    private final List<ApplicationStatusListener> statusListeners;

    @Transactional
    public void submit(Long applicationId, HttpServletRequest request) {
        BusinessApplication a = applications.require(applicationId);
        AuthenticatedUser u = CurrentUserContext.requireUser();
        applications.assertOwner(a, u);
        stateMachine.requireDraft(a.getStatus());
        submissionValidators.stream().filter(v -> v.supports(a.getBusinessType())).forEach(v -> v.validate(a));
        if (materials.countRequired(applicationId) == 0) throw conflict("缺少必需材料，无法提交");
        if (applicationMapper.updateStatus(applicationId, ApplicationStatus.DRAFT, ApplicationStatus.UNDER_REVIEW, a.getVersion()) == 0) ApplicationService.conflict();
        notifyStatus(a, ApplicationStatus.UNDER_REVIEW);
        ApprovalRequest r = new ApprovalRequest();
        r.setApprovalNo(ApplicationService.generateNumber("APR")); r.setApplicationId(applicationId);
        r.setStatus(ApprovalStatus.PENDING); r.setCurrentDepartmentId(a.getApplicantDepartmentId());
        r.setCurrentRegionCode(a.getApplicantRegionCode()); r.setSubmittedBy(u.userId());
        r.setSubmittedAt(LocalDateTime.now()); r.setVersion(0); mapper.insert(r);
        logs.insert(ApprovalLog.of(r.getApprovalId(), applicationId, ApprovalAction.SUBMIT, ApplicationStatus.DRAFT.name(), ApplicationStatus.UNDER_REVIEW.name(), u.userId(), null, audit.clientIp(request)));
        audit.recordTransactional(u.userId(), "APPLICATION_SUBMIT", request);
    }

    @Transactional
    public void withdraw(Long id, HttpServletRequest request) {
        BusinessApplication a = applications.require(id); AuthenticatedUser u = CurrentUserContext.requireUser();
        applications.assertOwner(a, u); stateMachine.requireWithdrawable(a.getStatus());
        ApprovalRequest r = mapper.selectByApplicationId(id).orElseThrow(() -> notFound("审批请求不存在"));
        if (r.getStatus() != ApprovalStatus.PENDING) throw conflict("审批已处理，不能撤回");
        if (mapper.decide(r.getApprovalId(), ApprovalStatus.PENDING, ApprovalStatus.CANCELLED, r.getVersion(), u.userId(), "申请人撤回") == 0) ApplicationService.conflict();
        if (applicationMapper.updateStatus(id, a.getStatus(), ApplicationStatus.WITHDRAWN, a.getVersion()) == 0) ApplicationService.conflict();
        notifyStatus(a, ApplicationStatus.WITHDRAWN);
        logs.insert(ApprovalLog.of(r.getApprovalId(), id, ApprovalAction.WITHDRAW, a.getStatus().name(), ApplicationStatus.WITHDRAWN.name(), u.userId(), "申请人撤回", audit.clientIp(request)));
        audit.recordTransactional(u.userId(), "APPLICATION_WITHDRAW", request);
    }

    @Transactional public void approve(Long id, ApprovalDecisionRequest b, HttpServletRequest r) { decide(id,b,ApprovalStatus.APPROVED,ApplicationStatus.APPROVED,ApprovalAction.APPROVE,r); }
    @Transactional public void reject(Long id, ApprovalDecisionRequest b, HttpServletRequest r) { if(b.comment()==null||b.comment().isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST,"驳回意见不能为空"); decide(id,b,ApprovalStatus.REJECTED,ApplicationStatus.REJECTED,ApprovalAction.REJECT,r); }

    private void decide(Long id, ApprovalDecisionRequest body, ApprovalStatus approvalTo, ApplicationStatus appTo, ApprovalAction action, HttpServletRequest request) {
        ApprovalRequest r=require(id); if(r.getStatus()!=ApprovalStatus.PENDING) throw conflict("审批已被处理");
        BusinessApplication a=applications.require(r.getApplicationId()); stateMachine.requireReview(a.getStatus());
        AuthenticatedUser u=CurrentUserContext.requireUser();
        if(u.roleLevel()!=RoleLevel.L3) throw new BusinessException(HttpStatus.FORBIDDEN,"只有 L3 审批人员可以处理审批");
        applications.assertCanView(a,u); if(a.getApplicantUserId().equals(u.userId())) throw new BusinessException(HttpStatus.FORBIDDEN,"审批人不能审批本人申请");
        if(action==ApprovalAction.APPROVE && (materials.countRequired(a.getApplicationId())==0 || materials.countRequiredNotVerified(a.getApplicationId())>0)) throw conflict("必需材料尚未全部核验通过");
        if(mapper.decide(id,ApprovalStatus.PENDING,approvalTo,body.version(),u.userId(),body.comment())==0) ApplicationService.conflict();
        if(applicationMapper.updateStatus(a.getApplicationId(),ApplicationStatus.UNDER_REVIEW,appTo,a.getVersion())==0) ApplicationService.conflict();
        notifyStatus(a,appTo);
        logs.insert(ApprovalLog.of(id,a.getApplicationId(),action,ApplicationStatus.UNDER_REVIEW.name(),appTo.name(),u.userId(),body.comment(),audit.clientIp(request)));
        audit.recordTransactional(u.userId(),"APPROVAL_"+action.name(),request);
    }

    @Transactional(readOnly=true) public List<ApprovalSummary> pending(){return mapper.selectByStatus(true,DataScopeCriteria.current());}
    @Transactional(readOnly=true) public List<ApprovalSummary> processed(){return mapper.selectByStatus(false,DataScopeCriteria.current());}
    @Transactional(readOnly=true) public ApprovalDetailView detail(Long id){ApprovalRequest r=require(id);BusinessApplication a=applications.require(r.getApplicationId());applications.assertCanView(a,CurrentUserContext.requireUser());return new ApprovalDetailView(r,ApplicationView.from(a),materials.selectByApplicationId(a.getApplicationId()).stream().map(MaterialView::from).toList(),logs(a.getApplicationId()));}
    @Transactional(readOnly=true) public List<ApprovalLogView> logs(Long applicationId){BusinessApplication a=applications.require(applicationId);applications.assertCanView(a,CurrentUserContext.requireUser());return logs.selectByApplicationId(applicationId).stream().map(ApprovalLogView::from).toList();}
    private void notifyStatus(BusinessApplication a, ApplicationStatus s){statusListeners.stream().filter(l->l.supports(a.getBusinessType())).forEach(l->l.onStatusChanged(a,s));}
    private ApprovalRequest require(Long id){return mapper.selectById(id).orElseThrow(()->notFound("审批请求不存在"));}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);} private BusinessException notFound(String m){return new BusinessException(HttpStatus.NOT_FOUND,m);}
}
