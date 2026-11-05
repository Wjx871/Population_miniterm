package com.wjx871.population.material;

import com.wjx871.population.application.ApplicationService;
import com.wjx871.population.application.ApplicationStatus;
import com.wjx871.population.application.BusinessApplication;
import com.wjx871.population.approval.ApprovalAction;
import com.wjx871.population.approval.ApprovalLog;
import com.wjx871.population.approval.ApprovalLogMapper;
import com.wjx871.population.approval.ApprovalRequest;
import com.wjx871.population.approval.ApprovalRequestMapper;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.security.RoleLevel;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MaterialService {
    private final ApplicationMaterialMapper mapper;
    private final ApplicationService applications;
    private final LocalFileStorageService storage;
    private final ApplicationMaterialStorageResolver storageResolver;
    private final AiBoundMaterialCleanupService boundMaterialCleanup;
    private final ApprovalRequestMapper approvalMapper;
    private final ApprovalLogMapper logMapper;

    @Transactional
    public MaterialView upload(Long applicationId, String type, String name, boolean required, MultipartFile file) {
        if (type == null || type.isBlank() || type.length() > 50 || name == null || name.isBlank() || name.length() > 200) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "材料类型或名称不合法");
        }
        BusinessApplication app = applications.requireForUpdate(applicationId);
        AuthenticatedUser user = CurrentUserContext.requireUser();
        applications.assertOwner(app, user);
        if (!materialEditable(app.getStatus())) throw conflict("只有草稿或退回补正申请可以上传材料");
        LocalFileStorageService.StoredFile stored = storage.store(file);
        ApplicationMaterial material = new ApplicationMaterial();
        material.setApplicationId(applicationId);
        material.setMaterialType(type);
        material.setMaterialName(name);
        material.setOriginalFilename(stored.original());
        material.setStoredFilename(stored.stored());
        material.setStoragePath(stored.path());
        material.setStorageKind("LOCAL");
        material.setStorageReference(null);
        material.setContentType(stored.contentType());
        material.setFileSize(stored.size());
        material.setFileSha256(stored.sha256());
        material.setRequiredFlag(required);
        material.setVerifyStatus(MaterialVerifyStatus.PENDING);
        material.setUploadedBy(user.userId());
        try {
            mapper.insert(material);
        } catch (RuntimeException exception) {
            storage.delete(stored.stored());
            throw exception;
        }
        return MaterialView.from(require(material.getMaterialId()));
    }

    @Transactional(readOnly = true)
    public List<MaterialView> list(Long applicationId) {
        BusinessApplication app = applications.require(applicationId);
        applications.assertCanView(app, CurrentUserContext.requireUser());
        return mapper.selectByApplicationId(applicationId).stream().map(MaterialView::from).toList();
    }

    @Transactional(readOnly = true)
    public Download download(Long id) {
        ApplicationMaterial material = require(id);
        BusinessApplication app = applications.require(material.getApplicationId());
        applications.assertCanView(app, CurrentUserContext.requireUser());
        return new Download(storageResolver.load(material), material.getOriginalFilename(), material.getContentType());
    }

    @Transactional
    public void delete(Long id) {
        ApplicationMaterial material = require(id);
        BusinessApplication app = applications.requireForUpdate(material.getApplicationId());
        AuthenticatedUser user = CurrentUserContext.requireUser();
        applications.assertOwner(app, user);
        if (!materialEditable(app.getStatus())) throw conflict("仅草稿或退回补正申请可以删除材料");

        if ("AI_STAGE".equals(material.getStorageKind())) {
            String stageId = boundMaterialCleanup.releaseBeforeDelete(material.getMaterialId(),
                    material.getStorageReference(), user.userId());
            if (mapper.deleteById(id) != 1) throw conflict("材料状态已变化，请刷新后重试");
            if (!TransactionSynchronizationManager.isSynchronizationActive()) {
                throw new IllegalStateException("Bound material deletion requires a transaction synchronization");
            }
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    boundMaterialCleanup.cleanupAfterCommit(stageId);
                }
            });
            return;
        }

        if (!"LOCAL".equals(material.getStorageKind())) throw conflict("材料存储来源不可用");
        // Preserve the established local-file deletion behavior for manually uploaded materials.
        storage.delete(material.getStoredFilename());
        mapper.deleteById(id);
    }

    @Transactional
    public MaterialView verify(Long id, MaterialVerifyRequest request, String ip) {
        if (request.result() == MaterialVerifyStatus.PENDING) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "核验结果必须为 VERIFIED 或 REJECTED");
        }
        ApplicationMaterial material = require(id);
        BusinessApplication app = applications.require(material.getApplicationId());
        AuthenticatedUser user = CurrentUserContext.requireUser();
        applications.assertCanView(app, user);
        if (app.getStatus() != ApplicationStatus.UNDER_REVIEW) throw conflict("申请未处于审核中");
        if (user.roleLevel() != RoleLevel.L3) throw new BusinessException(HttpStatus.FORBIDDEN, "只有 L3 审批人员可以核验材料");
        if (app.getApplicantUserId().equals(user.userId()) || user.userId().equals(material.getUploadedBy())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "申请人或材料上传人不能核验本人材料");
        }
        if (mapper.verify(id, request.result(), user.userId(), request.comment()) == 0) {
            throw conflict("材料已被其他用户核验");
        }
        Long approvalId = approvalMapper.selectByApplicationId(app.getApplicationId())
                .map(ApprovalRequest::getApprovalId).orElse(null);
        logMapper.insert(ApprovalLog.of(null, app.getApplicationId(),
                request.result() == MaterialVerifyStatus.VERIFIED ? ApprovalAction.MATERIAL_VERIFY : ApprovalAction.MATERIAL_REJECT,
                MaterialVerifyStatus.PENDING.name(), request.result().name(), user.userId(), request.comment(), ip));
        return MaterialView.from(require(id));
    }

    public long requiredCount(Long id) { return mapper.countRequired(id); }
    public long unverifiedRequiredCount(Long id) { return mapper.countRequiredNotVerified(id); }

    private boolean materialEditable(ApplicationStatus status) {
        return status == ApplicationStatus.DRAFT || status == ApplicationStatus.RETURNED;
    }

    private ApplicationMaterial require(Long id) {
        return mapper.selectById(id).orElseThrow(() -> new BusinessException(HttpStatus.NOT_FOUND, "材料不存在"));
    }

    private BusinessException conflict(String message) { return new BusinessException(HttpStatus.CONFLICT, message); }

    public record Download(Resource resource, String filename, String contentType) { }
}
