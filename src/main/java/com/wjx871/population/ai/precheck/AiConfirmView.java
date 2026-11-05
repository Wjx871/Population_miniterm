package com.wjx871.population.ai.precheck;

import com.wjx871.population.application.ApplicationStatus;
import com.wjx871.population.material.MaterialView;
import java.util.List;

public record AiConfirmView(String taskId, Long applicationId, String applicationNo, Long migrationDetailId,
                            String creationStatus, ApplicationStatus currentStatus, boolean replayed,
                            List<MaterialView> materials, List<String> warnings) { }
