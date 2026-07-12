package com.wjx871.population.migration;
import com.wjx871.population.application.*; import lombok.RequiredArgsConstructor; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class MigrationStatusListener implements ApplicationStatusListener {
 private final MigrationMapper mapper;
 public boolean supports(BusinessType t){return t==BusinessType.MIGRATION_IN||t==BusinessType.MIGRATION_OUT;}
 public void onStatusChanged(BusinessApplication a,ApplicationStatus s){MigrationBusinessStatus to=MigrationBusinessStatus.valueOf(s.name());if(a.getBusinessType()==BusinessType.MIGRATION_IN){MigrationIn v=mapper.findIn(a.getApplicationId()).orElseThrow();if(mapper.updateInStatus(a.getApplicationId(),v.getBusinessStatus(),to,v.getVersion(),null)==0)ApplicationService.conflict();}else{MigrationOut v=mapper.findOut(a.getApplicationId()).orElseThrow();if(mapper.updateOutStatus(a.getApplicationId(),v.getBusinessStatus(),to,v.getVersion(),null,null)==0)ApplicationService.conflict();}}
}
