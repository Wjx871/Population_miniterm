package com.wjx871.population.migration;
import com.wjx871.population.application.*; import com.wjx871.population.common.BusinessException; import lombok.RequiredArgsConstructor; import org.springframework.http.HttpStatus; import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class MigrationStatusListener implements ApplicationStatusListener, ApplicationDraftCancellationListener {
 private final MigrationMapper mapper;
 public boolean supports(BusinessType t){return t==BusinessType.MIGRATION_IN||t==BusinessType.MIGRATION_OUT;}
 public void onStatusChanged(BusinessApplication a,ApplicationStatus s){MigrationBusinessStatus to=MigrationBusinessStatus.valueOf(s.name());if(a.getBusinessType()==BusinessType.MIGRATION_IN){MigrationIn v=mapper.findIn(a.getApplicationId()).orElseThrow();if(mapper.updateInStatus(a.getApplicationId(),v.getBusinessStatus(),to,v.getVersion(),null)==0)ApplicationService.conflict();}else{MigrationOut v=mapper.findOut(a.getApplicationId()).orElseThrow();if(mapper.updateOutStatus(a.getApplicationId(),v.getBusinessStatus(),to,v.getVersion(),null,null)==0)ApplicationService.conflict();}}
 public void onDraftCancelled(BusinessApplication a){
  if(a.getBusinessType()==BusinessType.MIGRATION_IN){
   MigrationIn v=mapper.findIn(a.getApplicationId()).orElseThrow(()->new BusinessException(HttpStatus.CONFLICT,"迁入详情不存在，取消未完成"));
   if(v.getBusinessStatus()==MigrationBusinessStatus.CANCELLED)return;
   if(mapper.updateInStatus(a.getApplicationId(),v.getBusinessStatus(),MigrationBusinessStatus.CANCELLED,v.getVersion(),null)==0)ApplicationService.conflict();
  }else if(a.getBusinessType()==BusinessType.MIGRATION_OUT){
   MigrationOut v=mapper.findOut(a.getApplicationId()).orElseThrow(()->new BusinessException(HttpStatus.CONFLICT,"迁出详情不存在，取消未完成"));
   if(v.getBusinessStatus()==MigrationBusinessStatus.CANCELLED)return;
   if(mapper.updateOutStatus(a.getApplicationId(),v.getBusinessStatus(),MigrationBusinessStatus.CANCELLED,v.getVersion(),null,null)==0)ApplicationService.conflict();
  }
 }
}
