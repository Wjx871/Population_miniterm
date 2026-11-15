package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class PolicyRagServiceTest {
    private PolicyRagService service;
    private MockRestServiceServer server;
    private PolicyTaskAgentService.AgentResponse baseline;

    @BeforeEach void setup() {
        service = new PolicyRagService(new ObjectMapper());
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate")).build();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "baseUrl", "http://rag-test");
        ReflectionTestUtils.setField(service, "serviceKey", "test-only-key");
        baseline = new PolicyTaskAgentService.AgentResponse("MIGRATION_IN", "户籍迁入", "迁入", "旧摘要",
                List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(new PolicyTaskAgentService.AllowedAction("查看迁入", "/migrations/in")),
                "SUFFICIENT", false, List.of(), "RETRIEVAL_ONLY", .8, "base-trace");
    }

    @Test void disabledKeepsExistingBehavior() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertThat(service.enhance("迁入", null, baseline)).isSameAs(baseline);
        server.verify();
    }

    @Test void usesGeneratedAnswerAndKeepsOnlyExistingAuthorizedActions() {
        server.expect(requestTo("http://rag-test/internal/policy-query")).andExpect(method(HttpMethod.POST))
                .andExpect(header("X-AI-Service-Key", "test-only-key"))
                .andExpect(content().json("{\"question\":\"迁入\",\"topK\":4,\"history\":[]}"))
                .andRespond(withSuccess("""
                    {"schemaVersion":"1.0","traceId":"rag-trace","mode":"AI_ASSISTED","evidenceStatus":"CANDIDATES_ONLY",
                    "answer":"按页面清单准备材料。[1]","citations":[{"index":1,"logicalPath":"migration-in-guide",
                    "title":"迁入指南","section":"材料","category":"迁入","sourceType":"PROJECT_GUIDE","sourceVersion":"V1","excerpt":"按页面清单准备材料。"}]}
                    """, MediaType.APPLICATION_JSON));
        var result = service.enhance("迁入", null, baseline);
        assertThat(result.answerSummary()).isEqualTo("按页面清单准备材料。[1]");
        assertThat(result.mode()).isEqualTo("AI_ASSISTED");
        assertThat(result.nextActions()).isEqualTo(baseline.nextActions());
        assertThat(result.citations()).hasSize(1);
        assertThat(result.confidence()).isZero();
        server.verify();
    }

    @Test void refusalDoesNotReuseOldCitationsOrActions() {
        server.expect(requestTo("http://rag-test/internal/policy-query")).andRespond(withSuccess("""
            {"schemaVersion":"1.0","traceId":"refusal","mode":"SAFE_REFUSAL","evidenceStatus":"REFUSED",
            "answer":"不允许绕过审批。","citations":[]}
            """, MediaType.APPLICATION_JSON));
        var result = service.enhance("绕过审批", null, baseline);
        assertThat(result.mode()).isEqualTo("SAFE_REFUSAL");
        assertThat(result.answerSummary()).contains("不允许");
        assertThat(result.citations()).isEmpty();
        assertThat(result.nextActions()).isEmpty();
    }

    @Test void unavailableServiceDoesNotHideFailureWithUnscopedOldAnswer() {
        server.expect(requestTo("http://rag-test/internal/policy-query")).andRespond(withServiceUnavailable());
        var result = service.enhance("迁入", null, baseline);
        assertThat(result.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(result.answerSummary()).contains("暂时不可用").doesNotContain("旧摘要");
        assertThat(result.citations()).isEmpty();
    }

    @Test void invalidCitationIsRejected() {
        server.expect(requestTo("http://rag-test/internal/policy-query")).andRespond(withSuccess("""
            {"schemaVersion":"1.0","mode":"AI_ASSISTED","evidenceStatus":"CANDIDATES_ONLY","answer":"无依据回答",
            "citations":[{"index":1,"logicalPath":"../../secrets","excerpt":"伪造证据"}]}
            """, MediaType.APPLICATION_JSON));
        assertThat(service.enhance("迁入", null, baseline).warnings()).contains("RAG_SERVICE_UNAVAILABLE");
    }

    @Test void returnsOfficialDocumentLinkAndEffectiveDate() {
        server.expect(requestTo("http://rag-test/internal/policy-query")).andRespond(withSuccess("""
            {"schemaVersion":"1.0","mode":"RETRIEVAL_ONLY","evidenceStatus":"CANDIDATES_ONLY","answer":"按条件核对 [1]",
            "citations":[{"index":1,"logicalPath":"qingdao-2023","title":"通知","section":"第二条第二项",
            "category":"迁入","sourceType":"OFFICIAL_POLICY","sourceVersion":"青政字〔2023〕32号",
            "effectiveDate":"2023-11-07","sourceUrl":"https://www.qingdao.gov.cn/official","excerpt":"需要办理租赁备案。"}]}
            """, MediaType.APPLICATION_JSON));
        var citation = service.enhance("租房落户", null, baseline).citations().get(0);
        assertThat(citation.sourceUrl()).isEqualTo("https://www.qingdao.gov.cn/official");
        assertThat(citation.effectiveDate()).isEqualTo("2023-11-07");
    }

    @Test void statusEvidenceKeepsTheEligibilityDecisionInsufficient() {
        server.expect(requestTo("http://rag-test/internal/policy-query")).andRespond(withSuccess("""
            {"schemaVersion":"1.0","mode":"RETRIEVAL_ONLY","evidenceStatus":"INSUFFICIENT","answer":"无法确认仍然有效",
            "citations":[],"scopeSource":{"title":"旧通知","sourceVersion":"鲁公通〔2021〕157号",
            "sourceUrl":"https://www.qingdao.gov.cn/official","effectiveDate":"2021-11-01","expiresOn":"2026-10-31"}}
            """, MediaType.APPLICATION_JSON));
        var result = service.enhance("旧通知还有效吗", null, baseline);
        assertThat(result.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(result.citations().get(0).sourceType()).isEqualTo("POLICY_STATUS");
        assertThat(result.citations().get(0).summary()).contains("2026-10-31");
    }
}
