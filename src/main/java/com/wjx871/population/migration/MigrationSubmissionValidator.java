package com.wjx871.population.migration;
import com.wjx871.population.application.*; import com.wjx871.population.common.BusinessException; import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class MigrationSubmissionValidator implements ApplicationSubmissionValidator {
 private final MigrationMapper mapper; private final MigrationMaterialRequirementService rules;
 public boolean supports(BusinessType t){return t==BusinessType.MIGRATION_IN||t==BusinessType.MIGRATION_OUT;}
 public void validate(BusinessApplication a){if(a.getBusinessType()==BusinessType.MIGRATION_IN){MigrationIn v=mapper.findIn(a.getApplicationId()).orElseThrow(()->bad("缺少迁入专业详情"));rules.validateIn(v);}else{MigrationOut v=mapper.findOut(a.getApplicationId()).orElseThrow(()->bad("缺少迁出专业详情"));HouseholdSnapshot h=mapper.findHousehold(v.getFromHouseholdId()).orElseThrow(()->bad("原家庭户不存在"));rules.validateOut(v,v.getPersonId().equals(h.getHeadPersonId())&&h.getActiveMemberCount()>1);}}
 private BusinessException bad(String m){return new BusinessException(HttpStatus.CONFLICT,m);}
}
