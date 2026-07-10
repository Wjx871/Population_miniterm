package com.wjx871.population.migration;
import com.wjx871.population.application.ApplicationStatus;
public record MigrationCreatedView(Long applicationId,String applicationNo,Long migrationId,ApplicationStatus status,MigrationBusinessStatus businessStatus) {}
