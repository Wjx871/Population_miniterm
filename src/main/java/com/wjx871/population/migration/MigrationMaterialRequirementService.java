package com.wjx871.population.migration;
import com.wjx871.population.common.BusinessException; import com.wjx871.population.material.*; import java.util.*; import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Service;
@Service @RequiredArgsConstructor
public class MigrationMaterialRequirementService {
 private final ApplicationMaterialMapper materials;
 public void validateIn(MigrationIn v){Set<String> t=types(v.getApplicationId());require(t,"IDENTITY_PROOF");if(!t.contains("HOUSEHOLD_BOOK")&&!t.contains("ADDRESS_PROOF"))bad("迁入需要户口簿或地址证明");if(v.getMigrationType()==MigrationType.IN_CITY_CROSS_DISTRICT)require(t,"MIGRATION_PROOF");}
 public void validateOut(MigrationOut v,boolean headWithOthers){Set<String> t=types(v.getApplicationId());require(t,"IDENTITY_PROOF");require(t,"HOUSEHOLD_BOOK");if(headWithOthers&&v.getNewHeadPersonId()==null&&!t.contains("HOUSEHOLD_CONSENT"))bad("户主迁出需指定新户主或提供家庭成员同意材料");}
 public void validateVerified(Long id){if(materials.countRequired(id)==0||materials.countRequiredNotVerified(id)>0)bad("必需材料尚未全部核验通过");}
 private Set<String> types(Long id){Set<String>s=new HashSet<>();materials.selectByApplicationId(id).stream().filter(m->Boolean.TRUE.equals(m.getRequiredFlag())).forEach(m->s.add(m.getMaterialType()));return s;}
 private void require(Set<String>s,String t){if(!s.contains(t))bad("缺少必需材料："+t);} private void bad(String m){throw new BusinessException(HttpStatus.CONFLICT,m);}
}
