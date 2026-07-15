package com.wjx871.population.floating;
import com.wjx871.population.application.*;import com.wjx871.population.common.BusinessException;import org.springframework.http.HttpStatus;import org.springframework.stereotype.Component;
@Component class FloatingResidenceSubmissionValidator implements ApplicationSubmissionValidator {private final FloatingResidenceMapper mapper;private final FloatingRegistrationMaterialRequirementService floating;private final ResidencePermitMaterialRequirementService permit;FloatingResidenceSubmissionValidator(FloatingResidenceMapper m,FloatingRegistrationMaterialRequirementService f,ResidencePermitMaterialRequirementService p){mapper=m;floating=f;permit=p;}public boolean supports(BusinessType t){return t==BusinessType.FLOATING_REGISTRATION||t.name().startsWith("RESIDENCE_PERMIT_");}public void validate(BusinessApplication a){if(a.getBusinessType()==BusinessType.FLOATING_REGISTRATION){FloatingApplication v=mapper.findFloatingApplication(a.getApplicationId()).orElseThrow(()->bad("流动登记专业详情不存在"));floating.validate(v);}else{PermitApplication v=mapper.findPermitApplication(a.getApplicationId()).orElseThrow(()->bad("居住证专业详情不存在"));FloatingPopulation f=mapper.findFloating(v.getFloatingId()).orElseThrow(()->bad("流动登记不存在"));permit.validate(v,f);}}private BusinessException bad(String m){return new BusinessException(HttpStatus.CONFLICT,m);}}
@Component class FloatingResidenceStatusListener implements ApplicationStatusListener {private final FloatingResidenceMapper mapper;FloatingResidenceStatusListener(FloatingResidenceMapper m){mapper=m;}public boolean supports(BusinessType t){return t==BusinessType.FLOATING_REGISTRATION||t.name().startsWith("RESIDENCE_PERMIT_");}// 以专业表自身的当前状态作为 `from`，而不是从申请对象反推：
//   - 申请状态（APPROVED）可能与专业状态（RETURNED 时仍是 APPROVED）短暂不同步，
//     用反推会出现 WHERE 不匹配 0 行更新并抛 409；
//   - 与 MigrationStatusListener / CancellationStatusListener 保持一致写法。
public void onStatusChanged(BusinessApplication a, ApplicationStatus s) {
    String to = s.name();
    String from;
    if (a.getBusinessType() == BusinessType.FLOATING_REGISTRATION) {
        FloatingApplication v = mapper.findFloatingApplication(a.getApplicationId()).orElse(null);
        if (v == null) return;
        from = v.getBusinessStatus();
    } else {
        PermitApplication v = mapper.findPermitApplication(a.getApplicationId()).orElse(null);
        if (v == null) return;
        from = v.getBusinessStatus();
    }
    int n = a.getBusinessType() == BusinessType.FLOATING_REGISTRATION
        ? mapper.updateFloatingApplicationStatus(a.getApplicationId(), from, to, null, null)
        : mapper.updatePermitApplicationStatus(a.getApplicationId(), from, to, null, null);
    if (n == 0) throw new BusinessException(HttpStatus.CONFLICT, "专业申请状态已变化，请刷新后重试");
}}
