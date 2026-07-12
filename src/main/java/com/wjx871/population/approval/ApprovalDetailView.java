package com.wjx871.population.approval; import com.wjx871.population.application.ApplicationView; import com.wjx871.population.material.MaterialView; import java.util.List;
public record ApprovalDetailView(ApprovalRequest approval,ApplicationView application,List<MaterialView> materials,List<ApprovalLogView> logs){}
