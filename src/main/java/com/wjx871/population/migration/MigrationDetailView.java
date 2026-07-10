package com.wjx871.population.migration;
import com.wjx871.population.application.ApplicationView; import com.wjx871.population.approval.*; import com.wjx871.population.material.MaterialView; import java.util.List;
public record MigrationDetailView(ApplicationView application,MigrationIn migrationIn,MigrationOut migrationOut,Residence currentResidence,HouseholdSnapshot household,List<Long> activeMemberPersonIds,List<MaterialView> materials,ApprovalRequest approval,List<ApprovalLogView> approvalLogs,boolean executable,String executionRestriction) {}
