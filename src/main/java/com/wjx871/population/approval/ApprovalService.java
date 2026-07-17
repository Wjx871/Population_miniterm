package com.wjx871.population.approval;

import com.wjx871.population.application.*;
import com.wjx871.population.audit.OperationLogService;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.*;
import com.wjx871.population.person.Person;
import com.wjx871.population.person.PersonService;
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
    private final PersonService persons;

    @Transactional
    public void submit(Long applicationId, HttpServletRequest request) {
        BusinessApplication a = applications.require(applicationId);
        AuthenticatedUser u = CurrentUserContext.requireUser();
        applications.assertOwner(a, u);
        stateMachine.requireResubmittable(a.getStatus());
        submissionValidators.stream().filter(v -> v.supports(a.getBusinessType())).forEach(v -> v.validate(a));
        if (materials.countRequired(applicationId) == 0) throw conflict("缺少必需材料，无法提交");
        ApplicationStatus fromStatus = a.getStatus();
        if (applicationMapper.updateStatus(applicationId, fromStatus, ApplicationStatus.UNDER_REVIEW, a.getVersion()) == 0) ApplicationService.conflict();
        notifyStatus(a, ApplicationStatus.UNDER_REVIEW);

        // RETURNED 状态下重新提交：复用已有的 sys_approval_request 行（受 application_id UNIQUE 约束），
        // 将其决策字段清空并重新置为 PENDING；首次提交则新建审批单。
        Long approvalId;
        if (fromStatus == ApplicationStatus.RETURNED) {
            LocalDateTime resubmitAt = LocalDateTime.now();
            int rows = mapper.resetForResubmit(applicationId, u.userId(), resubmitAt);
            if (rows == 0) throw conflict("审批单不可重置，请联系管理员");
            ApprovalRequest r = mapper.selectByApplicationId(applicationId)
                    .orElseThrow(() -> notFound("审批请求不存在"));
            approvalId = r.getApprovalId();
            logs.insert(ApprovalLog.of(r.getApprovalId(), applicationId, ApprovalAction.SUBMIT,
                    fromStatus.name(), ApplicationStatus.UNDER_REVIEW.name(),
                    u.userId(), "申请人补充后重新提交", audit.clientIp(request)));
            audit.recordTransactional(u.userId(), "APPLICATION_RESUBMIT", request);
        } else {
            ApprovalRequest r = new ApprovalRequest();
            r.setApprovalNo(ApplicationService.generateNumber("APR"));
            r.setApplicationId(applicationId);
            r.setStatus(ApprovalStatus.PENDING);
            r.setCurrentDepartmentId(a.getApplicantDepartmentId());
            r.setCurrentRegionCode(a.getApplicantRegionCode());
            r.setSubmittedBy(u.userId());
            r.setSubmittedAt(LocalDateTime.now());
            r.setVersion(0);
            mapper.insert(r);
            approvalId = r.getApprovalId();
            logs.insert(ApprovalLog.of(r.getApprovalId(), applicationId, ApprovalAction.SUBMIT,
                    ApplicationStatus.DRAFT.name(), ApplicationStatus.UNDER_REVIEW.name(),
                    u.userId(), null, audit.clientIp(request)));
            audit.recordTransactional(u.userId(), "APPLICATION_SUBMIT", request);
        }
    }

    @Transactional
    public void withdraw(Long id, HttpServletRequest request) {
        BusinessApplication a = applications.require(id); AuthenticatedUser u = CurrentUserContext.requireUser();
        applications.assertOwner(a, u); stateMachine.requireWithdrawable(a.getStatus());

        // RETURNED 状态来自审批通过后的退回，此时审批单早已不是 PENDING，
        // 不能走「撤回审批单」路径；直接置申请状态为 WITHDRAWN 即可。
        if (a.getStatus() == ApplicationStatus.RETURNED) {
            if (applicationMapper.updateStatus(id, ApplicationStatus.RETURNED, ApplicationStatus.WITHDRAWN, a.getVersion()) == 0) {
                ApplicationService.conflict();
            }
            notifyStatus(a, ApplicationStatus.WITHDRAWN);
            logs.insert(ApprovalLog.of(null, id, ApprovalAction.WITHDRAW,
                    ApplicationStatus.RETURNED.name(), ApplicationStatus.WITHDRAWN.name(),
                    u.userId(), "被退回后申请人放弃", audit.clientIp(request)));
            audit.recordTransactional(u.userId(), "APPLICATION_WITHDRAW_AFTER_RETURN", request);
            return;
        }

        ApprovalRequest r = mapper.selectByApplicationId(id).orElseThrow(() -> notFound("审批请求不存在"));
        if (r.getStatus() != ApprovalStatus.PENDING) throw conflict("审批已处理，不能撤回");
        if (mapper.decide(r.getApprovalId(), ApprovalStatus.PENDING, ApprovalStatus.CANCELLED, r.getVersion(), u.userId(), "申请人撤回") == 0) ApplicationService.conflict();
        if (applicationMapper.updateStatus(id, a.getStatus(), ApplicationStatus.WITHDRAWN, a.getVersion()) == 0) ApplicationService.conflict();
        notifyStatus(a, ApplicationStatus.WITHDRAWN);
        logs.insert(ApprovalLog.of(r.getApprovalId(), id, ApprovalAction.WITHDRAW, a.getStatus().name(), ApplicationStatus.WITHDRAWN.name(), u.userId(), "申请人撤回", audit.clientIp(request)));
        audit.recordTransactional(u.userId(), "APPLICATION_WITHDRAW", request);
    }

    @Transactional public void approve(Long id, ApprovalDecisionRequest b, HttpServletRequest r) { decide(id,b,ApprovalStatus.APPROVED,ApplicationStatus.APPROVED,ApprovalAction.APPROVE,r); }
    @Transactional
    public void approveAndCreatePerson(Long id, ApprovalCreatePersonRequest body, HttpServletRequest request) {
        ApprovalRequest approval = require(id);
        if (approval.getStatus() != ApprovalStatus.PENDING) throw conflict("审批已被处理");
        BusinessApplication application = applications.require(approval.getApplicationId());
        stateMachine.requireReview(application.getStatus());
        if (!isDocumentBasedPersonRegistration(application)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "该申请不是可在审批中心直接建档的恢复登记业务");
        }
        AuthenticatedUser user = CurrentUserContext.requireUser();
        requireApproverCanDecide(application, user);
        if (materials.countRequired(application.getApplicationId()) == 0
                || materials.countRequiredNotVerified(application.getApplicationId()) > 0) {
            throw conflict("必需材料尚未全部核验通过");
        }

        Person person = persons.create(body.person());
        if (mapper.decide(id, ApprovalStatus.PENDING, ApprovalStatus.APPROVED, body.version(), user.userId(), body.comment()) == 0) {
            ApplicationService.conflict();
        }
        if (applicationMapper.completeWithTargetPerson(application.getApplicationId(), application.getVersion(), person.getPersonId()) == 0) {
            ApplicationService.conflict();
        }
        logs.insert(ApprovalLog.of(id, application.getApplicationId(), ApprovalAction.EXECUTE,
                ApplicationStatus.UNDER_REVIEW.name(), ApplicationStatus.COMPLETED.name(), user.userId(),
                body.comment(), audit.clientIp(request)));
        audit.recordTransactional(user.userId(), "APPROVAL_CREATE_PERSON", request);
    }
    @Transactional public void reject(Long id, ApprovalDecisionRequest b, HttpServletRequest r) { if(b.comment()==null||b.comment().isBlank()) throw new BusinessException(HttpStatus.BAD_REQUEST,"驳回意见不能为空"); decide(id,b,ApprovalStatus.REJECTED,ApplicationStatus.REJECTED,ApprovalAction.REJECT,r); }

    private void decide(Long id, ApprovalDecisionRequest body, ApprovalStatus approvalTo, ApplicationStatus appTo, ApprovalAction action, HttpServletRequest request) {
        ApprovalRequest r=require(id); if(r.getStatus()!=ApprovalStatus.PENDING) throw conflict("审批已被处理");
        BusinessApplication a=applications.require(r.getApplicationId()); stateMachine.requireReview(a.getStatus());
        AuthenticatedUser u=CurrentUserContext.requireUser();
        requireApproverCanDecide(a, u);
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
    private void requireApproverCanDecide(BusinessApplication application, AuthenticatedUser user) {
        if(user.roleLevel()!=RoleLevel.L3) throw new BusinessException(HttpStatus.FORBIDDEN,"只有 L3 审批人员可以处理审批");
        applications.assertCanView(application,user);
        if(application.getApplicantUserId().equals(user.userId())) throw new BusinessException(HttpStatus.FORBIDDEN,"审批人不能审批本人申请");
    }
    private boolean isDocumentBasedPersonRegistration(BusinessApplication application) {
        if (application.getBusinessType() != BusinessType.GENERAL_SERVICE || application.getRemark() == null) return false;
        return application.getRemark().contains("登记类型=RELEASED_RESTORE")
                || application.getRemark().contains("登记类型=VETERAN_RESTORE");
    }
    private ApprovalRequest require(Long id){return mapper.selectById(id).orElseThrow(()->notFound("审批请求不存在"));}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);} private BusinessException notFound(String m){return new BusinessException(HttpStatus.NOT_FOUND,m);}
}
