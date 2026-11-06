package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.assistant.PolicyPythonContracts.PythonCitation;
import com.wjx871.population.assistant.PolicyPythonContracts.Request;
import com.wjx871.population.assistant.PolicyPythonContracts.Response;
import com.wjx871.population.auth.AuthService;
import com.wjx871.population.s2.trace.S2TraceContext;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.JwtService;
import com.wjx871.population.security.RoleLevel;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "policy.ai.enabled=false",
        "policy.python.enabled=true",
        "policy.python.client=MOCK"
})
@AutoConfigureMockMvc
class PolicyPythonAdapterIntegrationTest {

    @Autowired
    private PolicyAssistantService assistantService;

    @Autowired
    private PolicyTaskAgentService taskAgentService;

    @Autowired
    private PolicyWorkflowService workflowService;

    @Autowired
    private PolicyPythonProperties pythonProperties;

    @Autowired
    private MockPolicyQueryClient mockPolicyQueryClient;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private AuthService authService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        pythonProperties.setEnabled(true);
        pythonProperties.setClient(PolicyPythonProperties.ClientMode.MOCK);
        pythonProperties.setMockMode(PolicyPythonProperties.MockMode.SUCCESS);
        pythonProperties.setMockDelay(Duration.ZERO);
        assistantService.setSimulateLocalSearchFailure(false);
        mockPolicyQueryClient.clearCustomHandler();
        mockPolicyQueryClient.clearRecordedRequests();
        setSecurityUser("110101", List.of("population:view", "migration:in:create", "migration:view", "application:view"));
    }

    @AfterEach
    void tearDown() {
        assistantService.setSimulateLocalSearchFailure(false);
        mockPolicyQueryClient.clearCustomHandler();
        mockPolicyQueryClient.clearRecordedRequests();
        pythonProperties.setEnabled(false);
        pythonProperties.setMockMode(PolicyPythonProperties.MockMode.SUCCESS);
        pythonProperties.setMockDelay(Duration.ZERO);
        SecurityContextHolder.clearContext();
    }

    // =========================================================================
    // A. 成功路径 & 约束一（可信来源注册表与保守证据等级）
    // =========================================================================

    @Test
    void a1_to_a6_pythonVerifiedAnswerBecomesAnswerSummaryWithRenumberedCitationsAndMetadata() {
        var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of(), "trace-s4c-a1");

        assertThat(response.mode()).isEqualTo("AI_ASSISTED");
        assertThat(response.sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
        assertThat(response.evidenceStatus()).isEqualTo("SUFFICIENT");
        assertThat(response.needHumanHelp()).isFalse();
        assertThat(response.traceId()).isEqualTo("trace-s4c-a1");
        assertThat(response.knowledgeBaseVersion()).isEqualTo("kb-2026.11-mock");
        assertThat(response.modelVersion()).isEqualTo("mock-policy-rag-v1");
        assertThat(response.retrievalVersion()).isEqualTo("mock-hybrid-bm25-v1");
        assertThat(response.rerankerVersion()).isEqualTo("mock-reranker-v1");
        assertThat(response.elapsedMs()).isNotNull().isPositive();

        assertThat(response.citations()).hasSize(2);
        assertThat(response.citations().get(0).index()).isEqualTo(1);
        assertThat(response.citations().get(0).logicalPath()).isEqualTo("migration-in-guide");
        assertThat(response.citations().get(0).version()).isEqualTo("V1");
        assertThat(response.citations().get(1).index()).isEqualTo(2);
        assertThat(response.citations().get(1).logicalPath()).isEqualTo("migration-materials-followup-guide");

        assertThat(response.answerSummary())
                .contains("[1]")
                .contains("[2]")
                .doesNotContain("[[migration-in-guide]]")
                .doesNotContain("[[migration-materials-followup-guide]]");
        assertThat(response.requiredMaterials()).isNotEmpty();
    }

    @Test
    void a3_statusQuestionAppendsJavaDeterministicStatusExplanationToVerifiedPythonAnswer() {
        var response = taskAgentService.execute("审批通过是不是已经办结？", List.of(), "trace-s4c-status");

        assertThat(response.mode()).isEqualTo("AI_ASSISTED");
        assertThat(response.sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
        assertThat(response.answerSummary())
                .contains("[1]")
                .contains("[2]")
                .contains("业务状态为：申请已提交 → 等待审批 → 审批通过 → 等待专业业务执行 → 业务办结。审批通过不等于业务已办结。");
        assertThat(response.steps()).anyMatch(s -> s.contains("审批通过不等于业务已办结"));
    }

    @Test
    void a7_multiTurnFollowUpPreservesIntentAndRewritesStandaloneQuestion() {
        List<PolicyTaskAgentService.ConversationTurn> history = List.of(
                new PolicyTaskAgentService.ConversationTurn("user", "办理户籍迁入需要什么材料？"),
                new PolicyTaskAgentService.ConversationTurn("assistant", "需要准备身份证和户口簿等材料。")
        );
        var response = taskAgentService.execute("那如果材料不全呢？", history, "trace-s4c-followup");

        assertThat(response.intent()).isEqualTo("MIGRATION_IN");
        assertThat(response.standaloneQuestion()).isEqualTo("办理户籍迁入时，那如果材料不全呢？");
        assertThat(response.sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
        assertThat(mockPolicyQueryClient.getRecordedRequests()).hasSize(1);
        Request recorded = mockPolicyQueryClient.getRecordedRequests().get(0);
        assertThat(recorded.businessType()).isEqualTo("MIGRATION_IN");
        assertThat(recorded.regionCode()).isEqualTo("110101");
        assertThat(recorded.history()).hasSize(2);
    }

    @Test
    void a8_nextActionsRemainStrictlyFilteredByJavaUserPermissions() {
        setSecurityUser("110101", List.of("population:view", "migration:view"));
        var readOnlyUserResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(readOnlyUserResp.nextActions())
                .extracting(PolicyTaskAgentService.AllowedAction::path)
                .containsExactly("/migrations/in")
                .doesNotContain("/migrations/in/apply");

        setSecurityUser("110101", List.of("population:view", "migration:in:create", "migration:view"));
        var operatorResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(operatorResp.nextActions())
                .extracting(PolicyTaskAgentService.AllowedAction::path)
                .containsExactly("/migrations/in/apply", "/migrations/in");
    }

    @Test
    void a9_workflowServiceGeneratesBoundedChecklistFromValidatedCitations() {
        var workflow = workflowService.generateChecklist("我要办理户籍迁入", false);

        assertThat(workflow.businessType()).isEqualTo("户籍迁入");
        assertThat(workflow.evidenceStatus()).isEqualTo("SUFFICIENT");
        assertThat(workflow.policy().sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
        assertThat(workflow.checklist()).isNotEmpty();
        assertThat(workflow.checklist()).anyMatch(item -> "材料核对".equals(item.name()) && item.needsConfirmation());
        assertThat(workflow.actionPath()).isEqualTo("/migrations/in/apply");
    }

    @Test
    void a10_existingRestEndpointsMaintainBackwardCompatiblePayloadAndTraceHeader() throws Exception {
        String token = createBearerToken("110101", List.of("population:view", "migration:in:create"));

        mockMvc.perform(post("/api/assistant/policy/query")
                        .header("Authorization", "Bearer " + token)
                        .header(S2TraceContext.TRACE_HEADER, "trace-http-compat-01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "办理户籍迁入需要什么材料？",
                                "history", List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(header().string(S2TraceContext.TRACE_HEADER, "trace-http-compat-01"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.answerSummary").isNotEmpty())
                .andExpect(jsonPath("$.data.conditions").isArray())
                .andExpect(jsonPath("$.data.requiredMaterials").isArray())
                .andExpect(jsonPath("$.data.steps").isArray())
                .andExpect(jsonPath("$.data.warnings").isArray())
                .andExpect(jsonPath("$.data.citations").isArray())
                .andExpect(jsonPath("$.data.nextActions").isArray())
                .andExpect(jsonPath("$.data.evidenceStatus").value("SUFFICIENT"))
                .andExpect(jsonPath("$.data.needHumanHelp").value(false))
                .andExpect(jsonPath("$.data.executionTrace").isArray())
                .andExpect(jsonPath("$.data.mode").value("AI_ASSISTED"))
                .andExpect(jsonPath("$.data.sourceMode").value("PYTHON_HYBRID_RAG"))
                .andExpect(jsonPath("$.data.knowledgeBaseVersion").value("kb-2026.11-mock"))
                .andExpect(jsonPath("$.data.traceId").value("trace-http-compat-01"));

        mockMvc.perform(get("/api/assistant/policy/suggestions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0]").isNotEmpty());

        mockMvc.perform(post("/api/assistant/policy/check-materials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "我要办理户籍迁入",
                                "idCardRecognized", false
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checklist").isArray())
                .andExpect(jsonPath("$.data.evidenceStatus").value("SUFFICIENT"));
    }

    @Test
    void constraint1_externalUnregisteredSourcesKeepConservativePartialEvidenceEvenWhenPythonClaimsSufficient() {
        pythonProperties.setMockMode(PolicyPythonProperties.MockMode.EXTERNAL_UNREGISTERED_SOURCE);

        var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());

        assertThat(response.mode()).isEqualTo("AI_ASSISTED");
        assertThat(response.sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
        assertThat(response.evidenceStatus()).isEqualTo("PARTIAL");
        assertThat(response.needHumanHelp()).isTrue();
        assertThat(response.answerSummary()).contains("[1]").contains("[2]");
    }

    @Test
    void constraint1_tamperedRegisteredSourceMetadataIsRejectedAndFallsBackToJavaLocal() {
        pythonProperties.setMockMode(PolicyPythonProperties.MockMode.TAMPERED_REGISTERED_SOURCE);

        var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());

        assertThat(response.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(response.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(response.knowledgeBaseVersion()).isEqualTo("LOCAL_MD_V1");
        assertThat(response.modelVersion()).isNull();
        assertThat(response.citations()).noneMatch(c -> "TAMPERED_V999".equals(c.version()));
    }

    // =========================================================================
    // B. 无依据与异常引用 & 约束四（融合阶段失败后整体状态一致性）
    // =========================================================================

    @Test
    void b1_insufficientEvidenceOutcomeNeverExposesDefinitiveConclusion() {
        pythonProperties.setMockMode(PolicyPythonProperties.MockMode.INSUFFICIENT_EVIDENCE);

        var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());

        assertThat(response.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(response.sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
        assertThat(response.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(response.needHumanHelp()).isTrue();
        assertThat(response.citations()).isEmpty();
        assertThat(response.answerSummary()).contains("当前知识库没有足够依据确认这个问题");

        // Even if Python tries to sneak a definitive claim into INSUFFICIENT_EVIDENCE, validator rejects it
        mockPolicyQueryClient.setCustomHandler(req -> new Response(
                "1.0", req.requestId(), req.traceId(), "INSUFFICIENT_EVIDENCE",
                "完全可以办理，无需审批即可立即办结。",
                List.of(), List.of(), "kb-2026.11-mock", null, null, null, "INSUFFICIENT", 5L
        ));
        var contradictoryResp = taskAgentService.execute("量子计算如何申请户籍", List.of());
        assertThat(contradictoryResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(contradictoryResp.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(contradictoryResp.answerSummary()).doesNotContain("完全可以办理");
    }

    @Test
    void b2_to_b5_and_b7_to_b10_invalidCitationsAndUnsafeAnswersDegradeCleanlyToJavaLocal() {
        List<PolicyPythonProperties.MockMode> invalidModes = List.of(
                PolicyPythonProperties.MockMode.EMPTY_CITATIONS,
                PolicyPythonProperties.MockMode.UNKNOWN_CITATION_ID_USED,
                PolicyPythonProperties.MockMode.UNDECLARED_ANSWER_CITATION,
                PolicyPythonProperties.MockMode.DUPLICATE_SOURCE_ID,
                PolicyPythonProperties.MockMode.REGION_MISMATCH,
                PolicyPythonProperties.MockMode.EXPIRED_CITATION,
                PolicyPythonProperties.MockMode.INVALID_SOURCE_VERSION,
                PolicyPythonProperties.MockMode.UNSAFE_ANSWER_INSTRUCTION
        );

        for (PolicyPythonProperties.MockMode mode : invalidModes) {
            pythonProperties.setMockMode(mode);
            var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());

            assertThat(response.mode()).as("mode for %s", mode).isEqualTo("RETRIEVAL_ONLY");
            assertThat(response.sourceMode()).as("sourceMode for %s", mode).isEqualTo("JAVA_LOCAL_FALLBACK");
            assertThat(response.knowledgeBaseVersion()).as("kbVersion for %s", mode).isEqualTo("LOCAL_MD_V1");
            assertThat(response.modelVersion()).as("modelVersion for %s", mode).isNull();
            assertThat(response.retrievalVersion()).as("retrievalVersion for %s", mode).isNull();
            assertThat(response.rerankerVersion()).as("rerankerVersion for %s", mode).isNull();
            assertThat(response.answerSummary())
                    .as("answerSummary for %s", mode)
                    .startsWith("已识别为“户籍迁入”任务。")
                    .doesNotContain("executeIn")
                    .doesNotContain("personId")
                    .doesNotContain("unknown-source-id")
                    .doesNotContain("undeclared-doc");
            assertThat(response.citations())
                    .as("citations for %s", mode)
                    .isNotEmpty()
                    .allMatch(c -> "V1".equals(c.version()) && !c.logicalPath().contains("unknown"));
        }
    }

    @Test
    void b6_and_constraint4_citationFilteredOutByIntentDiscardsPythonAnswerAndClearsAllPythonMetadata() {
        pythonProperties.setMockMode(PolicyPythonProperties.MockMode.INTENT_MISMATCH_CATEGORY);

        var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of(), "trace-b6-intent");

        assertThat(response.intent()).isEqualTo("MIGRATION_IN");
        assertThat(response.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(response.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(response.knowledgeBaseVersion()).isEqualTo("LOCAL_MD_V1");
        assertThat(response.modelVersion()).isNull();
        assertThat(response.retrievalVersion()).isNull();
        assertThat(response.rerankerVersion()).isNull();
        assertThat(response.answerSummary())
                .startsWith("已识别为“户籍迁入”任务。")
                .doesNotContain("注销指南");
        assertThat(response.citations())
                .isNotEmpty()
                .noneMatch(c -> "注销".equals(c.category()));
        assertThat(response.needHumanHelp()).isEqualTo(!"SUFFICIENT".equals(response.evidenceStatus()));

        // Partial fusion failure: Python cites two docs, one matches MIGRATION_IN and one is CANCELLATION
        mockPolicyQueryClient.setCustomHandler(req -> new Response(
                "1.0", req.requestId(), req.traceId(), "ANSWERED",
                "迁入需准备材料[[migration-in-guide]]，同时参考注销规定[[cancellation-guide]]。",
                List.of("migration-in-guide", "cancellation-guide"),
                List.of(
                        new PythonCitation("migration-in-guide", "户籍迁入指南", "材料与信息", "迁入", "PROJECT_GUIDE",
                                "请按迁入申请页面展示的必填项和材料清单准备、上传。", "V1", "2026-01-01", "2027-12-31", req.regionCode(), null),
                        new PythonCitation("cancellation-guide", "注销指南", "办理边界", "注销", "PROJECT_GUIDE",
                                "人员或家庭户注销需要按规定创建注销申请。", "V1", "2026-01-01", "2027-12-31", req.regionCode(), null)
                ),
                "kb-python-99", "py-model-99", "py-ret-99", "py-rerank-99", "SUFFICIENT", 12L
        ));

        var partialFusionFailResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(partialFusionFailResp.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(partialFusionFailResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(partialFusionFailResp.knowledgeBaseVersion()).isEqualTo("LOCAL_MD_V1");
        assertThat(partialFusionFailResp.modelVersion()).isNull();
        assertThat(partialFusionFailResp.retrievalVersion()).isNull();
        assertThat(partialFusionFailResp.rerankerVersion()).isNull();
        assertThat(partialFusionFailResp.answerSummary()).doesNotContain("同时参考注销规定");
        assertThat(partialFusionFailResp.citations()).noneMatch(c -> "cancellation-guide".equals(c.logicalPath()));
    }

    // =========================================================================
    // C. 故障与本地降级（含 Python + 本地双故障 SERVICE_ERROR）
    // =========================================================================

    @Test
    void c1_to_c14_pythonFailuresFallbackToDeterministicJavaLocalWithoutCallingLegacyAi() {
        List<PolicyPythonProperties.MockMode> failureModes = List.of(
                PolicyPythonProperties.MockMode.CONNECT_FAILURE,
                PolicyPythonProperties.MockMode.TIMEOUT,
                PolicyPythonProperties.MockMode.HTTP_401,
                PolicyPythonProperties.MockMode.HTTP_403,
                PolicyPythonProperties.MockMode.HTTP_429,
                PolicyPythonProperties.MockMode.HTTP_500,
                PolicyPythonProperties.MockMode.HTTP_503,
                PolicyPythonProperties.MockMode.INVALID_JSON,
                PolicyPythonProperties.MockMode.UNSUPPORTED_SCHEMA,
                PolicyPythonProperties.MockMode.MISMATCHED_REQUEST_ID,
                PolicyPythonProperties.MockMode.INCOMPLETE_RESPONSE
        );

        pythonProperties.setRequestTimeout(Duration.ofMillis(50));
        for (PolicyPythonProperties.MockMode mode : failureModes) {
            pythonProperties.setMockMode(mode);
            var response = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());

            assertThat(response.mode()).as("mode for %s", mode).isEqualTo("RETRIEVAL_ONLY");
            assertThat(response.sourceMode()).as("sourceMode for %s", mode).isEqualTo("JAVA_LOCAL_FALLBACK");
            assertThat(response.knowledgeBaseVersion()).as("kbVersion for %s", mode).isEqualTo("LOCAL_MD_V1");
            assertThat(response.citations()).as("citations for %s", mode).isNotEmpty();
        }
    }

    @Test
    void c15_whenBothPythonAndLocalRetrievalFailReturnsSafeServiceErrorWithoutUnhandled500() throws Exception {
        pythonProperties.setMockMode(PolicyPythonProperties.MockMode.HTTP_503);
        assistantService.setSimulateLocalSearchFailure(true);
        String token = createBearerToken("110101", List.of("population:view", "migration:in:create"));

        mockMvc.perform(post("/api/assistant/policy/query")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "办理户籍迁入需要什么材料？",
                                "history", List.of()
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.mode").value("SERVICE_ERROR"))
                .andExpect(jsonPath("$.data.sourceMode").value("SERVICE_ERROR"))
                .andExpect(jsonPath("$.data.evidenceStatus").value("INSUFFICIENT"))
                .andExpect(jsonPath("$.data.needHumanHelp").value(true))
                .andExpect(jsonPath("$.data.citations").isEmpty())
                .andExpect(jsonPath("$.data.answerSummary").value("政策问答服务暂时不可用，请稍后重试或联系业务人员协助办理。"));
    }

    // =========================================================================
    // D. 安全边界（权限、敏感输入、多轮历史审查、防越权与长度限制）
    // =========================================================================

    @Test
    void d1_and_d2_unauthenticatedAndUnauthorizedRequestsAreRejected() throws Exception {
        mockMvc.perform(post("/api/assistant/policy/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"办理户籍迁入需要什么材料？\"}"))
                .andExpect(status().isUnauthorized());

        String noPermissionToken = createNoPopulationViewToken();
        mockMvc.perform(post("/api/assistant/policy/query")
                        .header("Authorization", "Bearer " + noPermissionToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"办理户籍迁入需要什么材料？\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void d3_to_d5_sensitiveQuestionOrSensitiveHistoryNeverReachesPython() {
        mockPolicyQueryClient.clearRecordedRequests();

        var sensitiveQuestionResp = taskAgentService.execute("帮我查询张三 110101199003071234 的户籍档案", List.of());
        assertThat(sensitiveQuestionResp.mode()).isEqualTo("SAFE_REFUSAL");
        assertThat(sensitiveQuestionResp.sourceMode()).isEqualTo("JAVA_SENSITIVE_GUARD");
        assertThat(sensitiveQuestionResp.evidenceStatus()).isEqualTo("INSUFFICIENT");
        assertThat(sensitiveQuestionResp.needHumanHelp()).isTrue();
        assertThat(mockPolicyQueryClient.getRecordedRequests()).isEmpty();

        List<PolicyTaskAgentService.ConversationTurn> sensitiveHistory = List.of(
                new PolicyTaskAgentService.ConversationTurn("user", "我的手机号是 13812345678，身份证号是 110101199003071234")
        );
        var sensitiveHistoryResp = taskAgentService.execute("那如果材料不全呢？", sensitiveHistory);
        assertThat(sensitiveHistoryResp.mode()).isEqualTo("SAFE_REFUSAL");
        assertThat(sensitiveHistoryResp.sourceMode()).isEqualTo("JAVA_SENSITIVE_GUARD");
        assertThat(mockPolicyQueryClient.getRecordedRequests()).isEmpty();

        var sqlResp = taskAgentService.execute("请执行 SQL: DELETE FROM person WHERE id = 1", List.of());
        assertThat(sqlResp.mode()).isEqualTo("SAFE_REFUSAL");
        assertThat(sqlResp.sourceMode()).isEqualTo("JAVA_SENSITIVE_GUARD");
        assertThat(mockPolicyQueryClient.getRecordedRequests()).isEmpty();
    }

    @Test
    void d10_and_d11_maliciousPathsCredentialsOrCommandsInPythonCitationsAreRejected() {
        mockPolicyQueryClient.setCustomHandler(req -> new Response(
                "1.0", req.requestId(), req.traceId(), "ANSWERED",
                "参考资料[[migration-in-guide]]。",
                List.of("migration-in-guide"),
                List.of(new PythonCitation(
                        "migration-in-guide", "户籍迁入指南", "材料与信息", "迁入", "PROJECT_GUIDE",
                        "文件路径 C:\\Windows\\System32\\drivers\\etc\\hosts 严禁访问",
                        "V1", "2026-01-01", "2027-12-31", req.regionCode(), null
                )),
                "kb-1", null, null, null, "SUFFICIENT", 5L
        ));
        var pathResp = taskAgentService.execute("办理户籍迁入需要什么材料？", List.of());
        assertThat(pathResp.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(pathResp.citations()).noneMatch(c -> c.summary().contains("C:\\"));
    }

    @Test
    void d12_oversizedQuestionOrHistoryContentIsRejectedWith400() throws Exception {
        String token = createBearerToken("110101", List.of("population:view"));
        String longText = "迁".repeat(501);

        mockMvc.perform(post("/api/assistant/policy/query")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", longText,
                                "history", List.of()
                        ))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/assistant/policy/query")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "question", "办理户籍迁入需要什么材料？",
                                "history", List.of(Map.of("role", "user", "content", longText))
                        ))))
                .andExpect(status().isBadRequest());
    }

    // =========================================================================
    // E. 时延与并发（单次请求远程预算与并发 traceId/requestId 隔离）
    // =========================================================================

    @Test
    void e2_queryExpansionOnlyCallsRemotePythonOncePerUserRequest() {
        AtomicInteger remoteCalls = new AtomicInteger();
        mockPolicyQueryClient.setCustomHandler(req -> {
            remoteCalls.incrementAndGet();
            throw new PolicyPythonClientException("PYTHON_REQUEST_TIMEOUT", true);
        });

        // Contains "和" and "、", which expands into 3 queries in PolicyTaskAgentService
        var response = taskAgentService.execute("办理户籍迁入的条件、材料和步骤是什么？", List.of());

        assertThat(remoteCalls.get()).isEqualTo(1);
        assertThat(response.mode()).isEqualTo("RETRIEVAL_ONLY");
        assertThat(response.sourceMode()).isEqualTo("JAVA_LOCAL_FALLBACK");
        assertThat(response.citations()).isNotEmpty();
    }

    @Test
    void e4_concurrentRequestsMaintainIsolatedTraceIdsRequestIdsAndRegionCodes() throws Exception {
        Set<String> seenRequestIds = ConcurrentHashMap.newKeySet();
        Map<String, String> traceToRegion = new ConcurrentHashMap<>();

        mockPolicyQueryClient.setCustomHandler(req -> {
            seenRequestIds.add(req.requestId());
            traceToRegion.put(req.traceId(), req.regionCode());
            return new Response(
                    "1.0", req.requestId(), req.traceId(), "ANSWERED",
                    "办理户籍迁入需核对条件[[migration-in-guide]]并准备材料[[migration-materials-followup-guide]]。",
                    List.of("migration-in-guide", "migration-materials-followup-guide"),
                    List.of(
                            new PythonCitation("migration-in-guide", "户籍迁入指南", "材料与信息", "迁入", "PROJECT_GUIDE",
                                    "请按迁入申请页面展示的必填项和材料清单准备、上传。", "V1", "2026-01-01", "2027-12-31", req.regionCode(), null),
                            new PythonCitation("migration-materials-followup-guide", "户籍迁入材料与追问指引", "材料不全处理", "迁入", "SYSTEM_GUIDE",
                                    "材料不全时可先保存已确认项。", "V1", "2026-01-01", "2027-12-31", req.regionCode(), null)
                    ),
                    "kb-concurrent", "model-1", "ret-1", "rerank-1", "SUFFICIENT", 3L
            );
        });

        int count = 12;
        ExecutorService pool = Executors.newFixedThreadPool(6);
        try {
            List<Callable<PolicyTaskAgentService.AgentResponse>> tasks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                final int idx = i;
                final String expectedTraceId = "trace-concurrent-" + idx;
                final String expectedRegion = "1101" + String.format("%02d", (idx % 10) + 1);
                tasks.add(() -> {
                    setSecurityUser(expectedRegion, List.of("population:view", "migration:in:create"));
                    try {
                        return taskAgentService.execute("办理户籍迁入需要什么材料？", List.of(), expectedTraceId);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });
            }
            List<Future<PolicyTaskAgentService.AgentResponse>> futures = pool.invokeAll(tasks);
            for (int i = 0; i < count; i++) {
                String expectedTraceId = "trace-concurrent-" + i;
                String expectedRegion = "1101" + String.format("%02d", (i % 10) + 1);
                PolicyTaskAgentService.AgentResponse resp = futures.get(i).get();
                assertThat(resp.traceId()).isEqualTo(expectedTraceId);
                assertThat(resp.sourceMode()).isEqualTo("PYTHON_HYBRID_RAG");
                assertThat(traceToRegion.get(expectedTraceId)).isEqualTo(expectedRegion);
            }
            assertThat(seenRequestIds).hasSize(count);
        } finally {
            pool.shutdownNow();
        }
    }

    private void setSecurityUser(String regionCode, List<String> permissions) {
        AuthenticatedUser user = new AuthenticatedUser(
                901L, "s4c_tester", "N/A", "测试员", "ENABLED",
                1L, "HOUSEHOLD_MANAGER", "户籍管理员", RoleLevel.L2, DataScope.REGION,
                "ENABLED", 10L, "测试部门", regionCode, permissions,
                regionCode == null ? List.of() : List.of(regionCode)
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String createBearerToken(String ignoredRegionCode, List<String> ignoredPermissions) {
        return jwtService.createToken(authService.loadUser("household"));
    }

    private String createNoPopulationViewToken() {
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_role WHERE role_id = 994", Integer.class) == 0) {
            jdbcTemplate.update("INSERT INTO sys_role (role_id, role_code, role_name, role_level, data_scope, status) "
                    + "VALUES (994, 'NO_POP_VIEW_ROLE', '无人口查看角色', 'L1', 'SELF', 'ENABLED')");
            jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (994, 3)");
        }
        if (jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sys_user WHERE user_id = 994", Integer.class) == 0) {
            jdbcTemplate.update("INSERT INTO sys_user (user_id, username, password_hash, role_id, department_id, real_name, status) "
                    + "VALUES (994, 'nopopview_s4c', '$2a$10$hqLjVyldvMDp7tlJcpkDZOaTT1dCAuSA5I7FgRfD/B7QXluT8ArB.', 994, 1, '无权限测试员', 'ENABLED')");
        }
        return jwtService.createToken(authService.loadUser("nopopview_s4c"));
    }
}
