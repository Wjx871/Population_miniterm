package com.wjx871.population.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "policy.ai.enabled=false")
class PolicyWorkflowServiceTest {
    @Autowired PolicyWorkflowService workflowService;

    @Test void producesBoundedChecklistFromPolicyEvidence() {
        var response = workflowService.generateChecklist("\u6211\u8981\u529e\u7406\u6237\u7c4d\u8fc1\u5165", true);
        assertThat(response.usedTools()).contains("classify_intent", "search_policy", "get_process_guide", "check_evidence", "generate_checklist");
        assertThat(response.checklist()).isNotEmpty();
        assertThat(response.policy().citations()).isNotEmpty();
        assertThat(response.actionPath()).isEqualTo("/migrations/in/apply");
    }
}
