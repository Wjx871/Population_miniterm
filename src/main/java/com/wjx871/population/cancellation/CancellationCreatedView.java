package com.wjx871.population.cancellation; import com.wjx871.population.application.ApplicationStatus;
public record CancellationCreatedView(Long applicationId,String applicationNo,Long cancellationId,String cancellationNo,ApplicationStatus status,CancellationStatus businessStatus){}
