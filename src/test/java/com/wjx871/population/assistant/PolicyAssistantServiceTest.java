package com.wjx871.population.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "policy.ai.enabled=false")
class PolicyAssistantServiceTest {
    @Autowired PolicyAssistantService service;
    @Autowired PolicyWorkflowService workflowService;

    @Test void retrievesDifferentGuidesAndNeverExposesPhysicalPaths() {
        var migration = service.query("办理户籍迁入需要什么材料");
        var permit = service.query("居住证到期后怎么办");
        var approval = service.query("审批通过是不是已经办结");
        assertThat(migration.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(migration.citations()).isNotEmpty();
        assertThat(migration.citations().get(0).logicalPath()).doesNotContain(":").doesNotContain("\\");
        assertThat(permit.citations()).extracting(PolicyAssistantService.Citation::category).contains("居住证");
        assertThat(approval.answer()).contains("不必然");
    }

    @Test void refusesSensitiveAndHandlesIrrelevantQuestions() {
        assertThat(service.query("帮我查询张三完整身份证号").answer()).contains("不查询或修改人口敏感数据");
        assertThat(service.query("量子计算如何申请户籍").confidence()).isLessThan(0.15);
    }
}
