package com.wjx871.population.ai.precheck;

import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Condition;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.MaterialInput;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.MaterialRequirement;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.MaterialRule;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Request;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class DocumentAnalysisRequestFactory {
    public Request build(AiPrecheckTaskService.WorkContext context) {
        AiPrecheckTaskService.TaskRow task = context.task();
        List<MaterialRule> rules = new ArrayList<>();
        rules.add(new MaterialRule("MIGRATION_IDENTITY",
                List.of(new MaterialRequirement("IDENTITY_PROOF", 1)), null, null));
        rules.add(new MaterialRule("MIGRATION_BOOK_OR_ADDRESS", null,
                List.of(new MaterialRequirement("HOUSEHOLD_BOOK", 1),
                        new MaterialRequirement("ADDRESS_PROOF", 1)), null));
        if ("IN_CITY_CROSS_DISTRICT".equals(task.migrationType())) {
            rules.add(new MaterialRule("MIGRATION_TRANSFER_PROOF",
                    List.of(new MaterialRequirement("MIGRATION_PROOF", 1)), null,
                    new Condition("IN_CITY_CROSS_DISTRICT")));
        }
        List<MaterialInput> materials = context.materials().stream()
                .map(material -> new MaterialInput(material.stageMaterialId(), material.contentType(),
                        material.fileSize(), material.documentRoleHint()))
                .toList();
        return new Request("1.0", "req_" + java.util.UUID.randomUUID().toString().replace("-", ""),
                task.taskId(), task.traceId(), task.businessType(), task.migrationType(), rules, materials);
    }
}
