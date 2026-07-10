package com.wjx871.population.material;

import com.wjx871.population.application.*; import com.wjx871.population.approval.*; import com.wjx871.population.common.BusinessException;
import com.wjx871.population.security.*; import java.util.List; import lombok.RequiredArgsConstructor; import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import org.springframework.web.multipart.MultipartFile;

@Service @RequiredArgsConstructor
public class MaterialService {
    private final ApplicationMaterialMapper mapper; private final ApplicationService applications; private final LocalFileStorageService storage;
    private final ApprovalRequestMapper approvalMapper; private final ApprovalLogMapper logMapper;
    @Transactional public MaterialView upload(Long applicationId,String type,String name,boolean required,MultipartFile file) {
        if(type==null||type.isBlank()||type.length()>50||name==null||name.isBlank()||name.length()>200) throw new BusinessException(HttpStatus.BAD_REQUEST,"材料类型或名称不合法");
        BusinessApplication app=applications.require(applicationId); AuthenticatedUser user=CurrentUserContext.requireUser();
        applications.assertOwner(app,user); if(app.getStatus()!=ApplicationStatus.DRAFT) throw conflict("只有草稿可以上传材料");
        LocalFileStorageService.StoredFile stored=storage.store(file); ApplicationMaterial m=new ApplicationMaterial();
        m.setApplicationId(applicationId);m.setMaterialType(type);m.setMaterialName(name);m.setOriginalFilename(stored.original());m.setStoredFilename(stored.stored());m.setStoragePath(stored.path());m.setContentType(stored.contentType());m.setFileSize(stored.size());m.setFileSha256(stored.sha256());m.setRequiredFlag(required);m.setVerifyStatus(MaterialVerifyStatus.PENDING);m.setUploadedBy(user.userId());
        try { mapper.insert(m); } catch(RuntimeException e){ storage.delete(stored.stored()); throw e; } return MaterialView.from(require(m.getMaterialId()));
    }
    @Transactional(readOnly=true) public List<MaterialView> list(Long applicationId){ BusinessApplication app=applications.require(applicationId);applications.assertCanView(app,CurrentUserContext.requireUser());return mapper.selectByApplicationId(applicationId).stream().map(MaterialView::from).toList();}
    @Transactional(readOnly=true) public Download download(Long id){ApplicationMaterial m=require(id);BusinessApplication a=applications.require(m.getApplicationId());applications.assertCanView(a,CurrentUserContext.requireUser());return new Download(storage.load(m.getStoredFilename()),m.getOriginalFilename(),m.getContentType());}
    @Transactional public void delete(Long id){ApplicationMaterial m=require(id);BusinessApplication a=applications.require(m.getApplicationId());applications.assertOwner(a,CurrentUserContext.requireUser());if(a.getStatus()!=ApplicationStatus.DRAFT)throw conflict("已提交申请不能删除材料");storage.delete(m.getStoredFilename());mapper.deleteById(id);}
    @Transactional public MaterialView verify(Long id,MaterialVerifyRequest request,String ip){if(request.result()==MaterialVerifyStatus.PENDING)throw new BusinessException(HttpStatus.BAD_REQUEST,"核验结果必须为 VERIFIED 或 REJECTED");ApplicationMaterial m=require(id);BusinessApplication a=applications.require(m.getApplicationId());applications.assertCanView(a,CurrentUserContext.requireUser());if(a.getStatus()!=ApplicationStatus.UNDER_REVIEW)throw conflict("申请未处于审核中");AuthenticatedUser u=CurrentUserContext.requireUser();if(u.roleLevel()!=RoleLevel.L3)throw new BusinessException(HttpStatus.FORBIDDEN,"只有 L3 审批人员可以核验材料");if(mapper.verify(id,request.result(),u.userId(),request.comment())==0)throw conflict("材料已被其他用户核验");Long approvalId=approvalMapper.selectByApplicationId(a.getApplicationId()).map(ApprovalRequest::getApprovalId).orElse(null);logMapper.insert(ApprovalLog.of(approvalId,a.getApplicationId(),request.result()==MaterialVerifyStatus.VERIFIED?ApprovalAction.MATERIAL_VERIFY:ApprovalAction.MATERIAL_REJECT,MaterialVerifyStatus.PENDING.name(),request.result().name(),u.userId(),request.comment(),ip));return MaterialView.from(require(id));}
    public long requiredCount(Long id){return mapper.countRequired(id);} public long unverifiedRequiredCount(Long id){return mapper.countRequiredNotVerified(id);}
    private ApplicationMaterial require(Long id){return mapper.selectById(id).orElseThrow(()->new BusinessException(HttpStatus.NOT_FOUND,"材料不存在"));}
    private BusinessException conflict(String m){return new BusinessException(HttpStatus.CONFLICT,m);} public record Download(Resource resource,String filename,String contentType){}
}
