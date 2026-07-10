package com.wjx871.population.approval; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size;
public record ApprovalDecisionRequest(@Size(max=500) String comment,@NotNull Integer version){}
