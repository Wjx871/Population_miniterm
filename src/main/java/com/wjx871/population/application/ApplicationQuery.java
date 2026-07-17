package com.wjx871.population.application;

import com.wjx871.population.security.DataScopeCriteria;
import java.time.LocalDateTime;

public record ApplicationQuery(
        String applicationNo, BusinessType businessType, ApplicationStatus status, String applicantName,
        LocalDateTime createdFrom, LocalDateTime createdTo, DataScopeCriteria scope,
        boolean scopedViewer, int limit, long offset
) {
}
