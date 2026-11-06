package com.wjx871.population.assistant;

import com.wjx871.population.s2.trace.S2TraceContext;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.stereotype.Service;

/**
 * Bounded, read-only task agent for policy guidance. It intentionally has no
 * repository, mapper, SQL, mutation, or arbitrary URL capability: every step
 * below is a server-owned allowlisted tool.
 */
@Slf4j
@Service
public class PolicyTaskAgentService {
    private static final int MAX_HISTORY_TURNS = 6;
    private final PolicyAssistantService policyService;

    public PolicyTaskAgentService(PolicyAssistantService policyService) {
        this.policyService = policyService;
    }

    public AgentResponse execute(String question, List<ConversationTurn> history) {
        String traceId = S2TraceContext.resolveTraceId(ThreadContext.get("traceId"));
        return execute(question, history, traceId);
    }

    public AgentResponse execute(String question, List<ConversationTurn> history, String traceId) {
        String effectiveTraceId = S2TraceContext.resolveTraceId(
                traceId != null ? traceId : ThreadContext.get("traceId")
        );
        try (S2TraceContext.Scope ignored = S2TraceContext.open(effectiveTraceId)) {
            String raw = question == null ? "" : question.trim();
            Intent intent = classifyIntent(raw, history);
            String standaloneQuestion = rewriteQuestion(raw, history, intent);

            if (policyService.containsSensitiveInput(raw, history) || policyService.isSensitive(standaloneQuestion)) {
                PolicyAssistantService.QueryResponse refusal = policyService.safeRefusalResponse(effectiveTraceId);
                return buildSafeRefusalResponse(intent, standaloneQuestion, refusal, effectiveTraceId);
            }

            List<String> queries = expandQueries(standaloneQuestion, intent);
            String businessType = intent == Intent.APPLICATION ? null : intent.code;
            String regionCode = resolveTrustedRegionCode();

            PolicyAssistantService.QueryResponse primary = policyService.queryPrimary(
                    queries.get(0), history, businessType, regionCode, effectiveTraceId
            );

            if (PolicyAssistantService.MODE_SAFE_REFUSAL.equals(primary.mode())) {
                return buildSafeRefusalResponse(intent, standaloneQuestion, primary, effectiveTraceId);
            }
            if (PolicyAssistantService.MODE_SERVICE_ERROR.equals(primary.mode())) {
                return buildServiceErrorResponse(intent, standaloneQuestion, primary, effectiveTraceId);
            }

            if (PolicyAssistantService.SOURCE_MODE_PYTHON_HYBRID_RAG.equals(primary.sourceMode())) {
                if (PolicyAssistantService.MODE_RETRIEVAL_ONLY.equals(primary.mode())
                        && primary.citations().isEmpty()) {
                    return buildPythonInsufficientResponse(raw, intent, standaloneQuestion, primary, effectiveTraceId);
                }
                OptionalPythonAgentOutcome pyOutcome = tryBuildVerifiedPythonAgentResponse(
                        raw, intent, standaloneQuestion, primary, effectiveTraceId
                );
                if (pyOutcome.response() != null) {
                    return pyOutcome.response();
                }
                log.warn("Policy Python answer discarded after fusion/intent check: traceId={} instanceId={} fallbackReason={}",
                        effectiveTraceId, S2TraceContext.instanceId(), pyOutcome.fallbackReason());
                return executeLocalFallback(raw, intent, standaloneQuestion, queries, effectiveTraceId, pyOutcome.fallbackReason());
            }

            List<PolicyAssistantService.QueryResponse> localResponses = new ArrayList<>(queries.size());
            localResponses.add(primary);
            for (int i = 1; i < queries.size(); i++) {
                PolicyAssistantService.QueryResponse secondary = policyService.queryLocalOnly(
                        queries.get(i), effectiveTraceId, null
                );
                if (!PolicyAssistantService.MODE_SERVICE_ERROR.equals(secondary.mode())) {
                    localResponses.add(secondary);
                }
            }
            return buildLocalAgentResponse(raw, intent, standaloneQuestion, localResponses, effectiveTraceId);
        }
    }

    private OptionalPythonAgentOutcome tryBuildVerifiedPythonAgentResponse(String raw,
                                                                           Intent intent,
                                                                           String standaloneQuestion,
                                                                           PolicyAssistantService.QueryResponse primary,
                                                                           String traceId) {
        if (primary.rawPythonAnswer() == null || primary.rawPythonAnswer().isBlank()
                || primary.citationIdsUsed() == null || primary.citationIdsUsed().isEmpty()
                || primary.citations() == null || primary.citations().isEmpty()) {
            return new OptionalPythonAgentOutcome(null, "PYTHON_MISSING_ANSWER_OR_CITATIONS");
        }

        List<PolicyAssistantService.Citation> fusedCitations = fusePythonCitations(primary.citations(), intent);
        if (fusedCitations.isEmpty()) {
            return new OptionalPythonAgentOutcome(null, "PYTHON_CITATIONS_FILTERED_BY_INTENT");
        }

        Map<String, Integer> indexBySourceId = new LinkedHashMap<>();
        for (PolicyAssistantService.Citation c : fusedCitations) {
            indexBySourceId.put(c.logicalPath(), c.index());
        }

        if (!indexBySourceId.keySet().containsAll(primary.citationIdsUsed())) {
            return new OptionalPythonAgentOutcome(null, "PYTHON_USED_CITATION_DROPPED_IN_FUSION");
        }

        String renumberedAnswer;
        try {
            Set<String> answerMarkers = PolicyPythonResponseValidator.extractAndValidateAnswerMarkers(primary.rawPythonAnswer());
            if (answerMarkers.isEmpty() || !indexBySourceId.keySet().containsAll(answerMarkers)) {
                return new OptionalPythonAgentOutcome(null, "PYTHON_ANSWER_MARKER_UNMAPPED_AFTER_FUSION");
            }
            renumberedAnswer = PolicyPythonResponseValidator.rewriteCitationMarkers(
                    primary.rawPythonAnswer(), indexBySourceId
            );
        } catch (Exception e) {
            return new OptionalPythonAgentOutcome(null, "PYTHON_ANSWER_REWRITE_FAILED");
        }

        Set<String> distinctSections = new LinkedHashSet<>();
        for (String usedId : primary.citationIdsUsed()) {
            fusedCitations.stream()
                    .filter(c -> usedId.equals(c.logicalPath()))
                    .findFirst()
                    .ifPresent(c -> distinctSections.add(c.section()));
        }
        String evidenceStatus = PolicyPythonResponseValidator.computeConservativeEvidenceStatus(
                primary.evidenceLevel(), primary.allSourcesRegistryVerified(), distinctSections.size()
        );
        if ("INSUFFICIENT".equals(evidenceStatus)) {
            return new OptionalPythonAgentOutcome(null, "PYTHON_EVIDENCE_EVALUATED_INSUFFICIENT");
        }

        List<String> conditions = sectionValues(fusedCitations, "办理说明", "使用范围", "办理边界");
        List<String> materials = sectionValues(fusedCitations, "材料", "信息");
        List<String> steps = sectionValues(fusedCitations, "步骤", "后续处理", "驳回", "到期");
        List<String> warnings = sectionValues(fusedCitations, "注意事项", "办理边界", "无关问题");
        String statusExplanation = null;
        if (asksStatus(raw)) {
            statusExplanation = explainBusinessStatus(raw);
            steps = merge(steps, List.of(statusExplanation));
        }

        String answerSummary = renumberedAnswer;
        if (statusExplanation != null && !answerSummary.contains(statusExplanation)) {
            answerSummary = answerSummary + "\n\n" + statusExplanation;
        }

        List<AllowedAction> nextActions = getAllowedActions(intent, CurrentUserContext.requireUser());
        List<String> trace = new ArrayList<>(List.of(
                "已识别办理事项：" + intent.label,
                "已改写为独立问题",
                "已检索相关业务指南：" + fusedCitations.size() + " 条",
                "已核验回答依据：" + evidenceStatus
        ));
        if (asksStatus(raw)) {
            trace.add("已解释业务状态流转");
        }
        trace.add("已过滤权限内下一步操作");

        double confidence = "SUFFICIENT".equals(evidenceStatus) ? 0.85 : 0.50;
        AgentResponse agentResponse = new AgentResponse(
                intent.code,
                intent.label,
                standaloneQuestion,
                answerSummary,
                conditions,
                materials,
                steps,
                warnings,
                fusedCitations,
                nextActions,
                evidenceStatus,
                !"SUFFICIENT".equals(evidenceStatus),
                List.copyOf(trace),
                PolicyAssistantService.MODE_AI_ASSISTED,
                PolicyAssistantService.SOURCE_MODE_PYTHON_HYBRID_RAG,
                confidence,
                traceId,
                primary.knowledgeBaseVersion(),
                primary.modelVersion(),
                primary.retrievalVersion(),
                primary.rerankerVersion(),
                primary.elapsedMs()
        );
        return new OptionalPythonAgentOutcome(agentResponse, null);
    }

    private AgentResponse executeLocalFallback(String raw,
                                               Intent intent,
                                               String standaloneQuestion,
                                               List<String> queries,
                                               String traceId,
                                               String fallbackReason) {
        List<PolicyAssistantService.QueryResponse> localResponses = new ArrayList<>(queries.size());
        for (int i = 0; i < queries.size(); i++) {
            PolicyAssistantService.QueryResponse resp = policyService.queryLocalOnly(
                    queries.get(i), traceId, i == 0 ? fallbackReason : null
            );
            if (i == 0 && PolicyAssistantService.MODE_SERVICE_ERROR.equals(resp.mode())) {
                return buildServiceErrorResponse(intent, standaloneQuestion, resp, traceId);
            }
            if (!PolicyAssistantService.MODE_SERVICE_ERROR.equals(resp.mode())) {
                localResponses.add(resp);
            }
        }
        return buildLocalAgentResponse(raw, intent, standaloneQuestion, localResponses, traceId);
    }

    private AgentResponse buildLocalAgentResponse(String raw,
                                                  Intent intent,
                                                  String standaloneQuestion,
                                                  List<PolicyAssistantService.QueryResponse> responses,
                                                  String traceId) {
        PolicyAssistantService.QueryResponse primary = responses.get(0);
        List<PolicyAssistantService.Citation> citations = fuseLocalCitations(responses, intent);
        Evidence evidence = checkEvidence(primary, citations);
        List<String> conditions = sectionValues(citations, "办理说明", "使用范围", "办理边界");
        List<String> materials = sectionValues(citations, "材料", "信息");
        List<String> steps = sectionValues(citations, "步骤", "后续处理", "驳回", "到期");
        List<String> warnings = sectionValues(citations, "注意事项", "办理边界", "无关问题");
        if (asksStatus(raw)) {
            steps = merge(steps, List.of(explainBusinessStatus(raw)));
        }
        List<AllowedAction> nextActions = getAllowedActions(intent, CurrentUserContext.requireUser());
        String answerSummary = buildSummary(intent, evidence.status, conditions, materials, steps, warnings);
        List<String> trace = new ArrayList<>(List.of(
                "已识别办理事项：" + intent.label,
                "已改写为独立问题",
                "已检索相关业务指南：" + citations.size() + " 条",
                "已核验回答依据：" + evidence.status
        ));
        if (asksStatus(raw)) {
            trace.add("已解释业务状态流转");
        }
        trace.add("已过滤权限内下一步操作");
        return new AgentResponse(
                intent.code,
                intent.label,
                standaloneQuestion,
                answerSummary,
                conditions,
                materials,
                steps,
                warnings,
                citations,
                nextActions,
                evidence.status,
                !"SUFFICIENT".equals(evidence.status),
                List.copyOf(trace),
                primary.mode(),
                primary.sourceMode(),
                primary.confidence(),
                traceId,
                PolicyAssistantService.LOCAL_KB_VERSION,
                PolicyAssistantService.SOURCE_MODE_JAVA_LEGACY_AI.equals(primary.sourceMode()) ? primary.modelVersion() : null,
                null,
                null,
                primary.elapsedMs()
        );
    }

    private AgentResponse buildPythonInsufficientResponse(String raw,
                                                          Intent intent,
                                                          String standaloneQuestion,
                                                          PolicyAssistantService.QueryResponse primary,
                                                          String traceId) {
        List<String> steps = asksStatus(raw) ? List.of(explainBusinessStatus(raw)) : List.of();
        List<AllowedAction> nextActions = getAllowedActions(intent, CurrentUserContext.requireUser());
        String answerSummary = buildSummary(intent, "INSUFFICIENT", List.of(), List.of(), steps, List.of());
        List<String> trace = new ArrayList<>(List.of(
                "已识别办理事项：" + intent.label,
                "已改写为独立问题",
                "已检索相关业务指南：0 条",
                "已核验回答依据：INSUFFICIENT"
        ));
        if (asksStatus(raw)) {
            trace.add("已解释业务状态流转");
        }
        trace.add("已过滤权限内下一步操作");
        return new AgentResponse(
                intent.code,
                intent.label,
                standaloneQuestion,
                answerSummary,
                List.of(),
                List.of(),
                steps,
                List.of(),
                List.of(),
                nextActions,
                "INSUFFICIENT",
                true,
                List.copyOf(trace),
                PolicyAssistantService.MODE_RETRIEVAL_ONLY,
                PolicyAssistantService.SOURCE_MODE_PYTHON_HYBRID_RAG,
                0.0,
                traceId,
                primary.knowledgeBaseVersion(),
                primary.modelVersion(),
                primary.retrievalVersion(),
                primary.rerankerVersion(),
                primary.elapsedMs()
        );
    }

    private AgentResponse buildSafeRefusalResponse(Intent intent,
                                                   String standaloneQuestion,
                                                   PolicyAssistantService.QueryResponse refusal,
                                                   String traceId) {
        List<String> trace = List.of(
                "已识别办理事项：" + intent.label,
                "已触发敏感信息安全拦截",
                "已核验回答依据：INSUFFICIENT"
        );
        return new AgentResponse(
                intent.code,
                intent.label,
                standaloneQuestion,
                refusal.answer(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "INSUFFICIENT",
                true,
                trace,
                PolicyAssistantService.MODE_SAFE_REFUSAL,
                PolicyAssistantService.SOURCE_MODE_JAVA_SENSITIVE_GUARD,
                1.0,
                traceId,
                null,
                null,
                null,
                null,
                0L
        );
    }

    private AgentResponse buildServiceErrorResponse(Intent intent,
                                                    String standaloneQuestion,
                                                    PolicyAssistantService.QueryResponse errorResp,
                                                    String traceId) {
        List<AllowedAction> nextActions = getAllowedActions(intent, CurrentUserContext.requireUser());
        List<String> trace = List.of(
                "已识别办理事项：" + intent.label,
                "政策检索服务暂时不可用",
                "已核验回答依据：INSUFFICIENT",
                "已过滤权限内下一步操作"
        );
        return new AgentResponse(
                intent.code,
                intent.label,
                standaloneQuestion,
                errorResp.answer(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                nextActions,
                "INSUFFICIENT",
                true,
                trace,
                PolicyAssistantService.MODE_SERVICE_ERROR,
                PolicyAssistantService.SOURCE_MODE_SERVICE_ERROR,
                0.0,
                traceId,
                null,
                null,
                null,
                null,
                errorResp.elapsedMs()
        );
    }

    private Intent classifyIntent(String question, List<ConversationTurn> history) {
        Intent direct = classify(normalize(question));
        if (direct != Intent.APPLICATION) {
            return direct;
        }
        return classify(normalize(recentHistory(history)));
    }

    private Intent classify(String text) {
        if (text.contains("迁入")) return Intent.MIGRATION_IN;
        if (text.contains("迁出")) return Intent.MIGRATION_OUT;
        if (text.contains("居住证") || text.contains("签注")) return Intent.RESIDENCE_PERMIT;
        if (text.contains("家庭户") || text.contains("新增成员")) return Intent.HOUSEHOLD;
        if (text.contains("注销")) return Intent.CANCELLATION;
        if (text.contains("审批") || text.contains("驳回") || text.contains("退回")) return Intent.APPROVAL;
        if (text.contains("人口") || text.contains("户籍")) return Intent.POPULATION;
        return Intent.APPLICATION;
    }

    private String rewriteQuestion(String raw, List<ConversationTurn> history, Intent intent) {
        String context = recentHistory(history);
        boolean followUp = raw.startsWith("那") || raw.startsWith("如果") || raw.contains("材料不全")
                || raw.contains("哪里提交") || raw.contains("怎么办");
        if (!followUp || context.isBlank()) {
            return raw;
        }
        return "办理" + intent.label + "时，" + raw;
    }

    private List<String> expandQueries(String standalone, Intent intent) {
        List<String> queries = new ArrayList<>();
        queries.add(standalone);
        if (standalone.contains("和") || standalone.contains("、") || standalone.contains("同时")) {
            queries.add("办理" + intent.label + "需要的条件和材料");
            queries.add("办理" + intent.label + "的步骤和注意事项");
        }
        return queries.stream().filter(q -> !q.isBlank()).distinct().limit(3).toList();
    }

    private List<PolicyAssistantService.Citation> fusePythonCitations(List<PolicyAssistantService.Citation> pyCitations,
                                                                      Intent intent) {
        Map<String, WeightedCitation> selected = new LinkedHashMap<>();
        for (PolicyAssistantService.Citation citation : pyCitations) {
            if (!matchesIntent(citation, intent)) {
                continue;
            }
            String key = citation.logicalPath();
            double score = 9.0 - citation.index();
            WeightedCitation old = selected.get(key);
            selected.put(key, old == null || score > old.score ? new WeightedCitation(citation, score) : old);
        }
        if (selected.isEmpty()) {
            return List.of();
        }
        return renumberCitations(selected.values().stream()
                .sorted(Comparator.comparingDouble(WeightedCitation::score).reversed())
                .limit(4)
                .map(WeightedCitation::citation)
                .toList());
    }

    private List<PolicyAssistantService.Citation> fuseLocalCitations(List<PolicyAssistantService.QueryResponse> responses,
                                                                     Intent intent) {
        Map<String, WeightedCitation> selected = new LinkedHashMap<>();
        for (int queryIndex = 0; queryIndex < responses.size(); queryIndex++) {
            double queryWeight = queryIndex == 0 ? 1.0 : 0.8;
            for (PolicyAssistantService.Citation citation : responses.get(queryIndex).citations()) {
                if (!matchesIntent(citation, intent)) {
                    continue;
                }
                String key = citation.logicalPath() + "#" + citation.section();
                double score = queryWeight * (5 - citation.index());
                WeightedCitation old = selected.get(key);
                selected.put(key, old == null || score > old.score ? new WeightedCitation(citation, score) : old);
            }
        }
        if (selected.isEmpty()) {
            responses.stream().flatMap(response -> response.citations().stream()).limit(4)
                    .forEach(citation -> selected.put(
                            citation.logicalPath() + "#" + citation.section(),
                            new WeightedCitation(citation, 0)
                    ));
        }
        return renumberCitations(selected.values().stream()
                .sorted(Comparator.comparingDouble(WeightedCitation::score).reversed())
                .limit(4)
                .map(WeightedCitation::citation)
                .toList());
    }

    private List<PolicyAssistantService.Citation> renumberCitations(List<PolicyAssistantService.Citation> rawList) {
        List<PolicyAssistantService.Citation> renumbered = new ArrayList<>(rawList.size());
        for (int i = 0; i < rawList.size(); i++) {
            PolicyAssistantService.Citation c = rawList.get(i);
            renumbered.add(new PolicyAssistantService.Citation(
                    i + 1, c.title(), c.section(), c.category(), c.sourceType(),
                    c.version(), c.logicalPath(), c.summary(),
                    c.effectiveFrom(), c.effectiveTo(), c.regionCode(), c.retrievedAt()
            ));
        }
        return List.copyOf(renumbered);
    }

    private boolean matchesIntent(PolicyAssistantService.Citation citation, Intent intent) {
        String category = citation.category() == null ? "" : citation.category();
        return switch (intent) {
            case MIGRATION_IN -> category.contains("迁入") || category.contains("常见问题") || category.contains("系统操作");
            case MIGRATION_OUT -> category.contains("迁出") || category.contains("常见问题") || category.contains("系统操作");
            case RESIDENCE_PERMIT -> category.contains("居住证") || category.contains("常见问题") || category.contains("系统操作");
            case HOUSEHOLD -> category.contains("家庭户") || category.contains("常见问题") || category.contains("系统操作");
            case CANCELLATION -> category.contains("注销") || category.contains("常见问题") || category.contains("系统操作");
            case APPROVAL -> category.contains("审批") || category.contains("常见问题") || category.contains("系统操作");
            case POPULATION -> category.contains("人口") || category.contains("常见问题") || category.contains("系统操作");
            default -> true;
        };
    }

    private String buildSummary(Intent intent, String evidenceStatus,
                                List<String> conditions, List<String> materials,
                                List<String> steps, List<String> warnings) {
        if ("INSUFFICIENT".equals(evidenceStatus)) {
            return "当前知识库没有足够依据确认这个问题，请联系业务人员或查阅最新规定。";
        }
        String detail = java.util.stream.Stream.of(materials, conditions, steps, warnings)
                .flatMap(Collection::stream)
                .findFirst()
                .orElse("请参考下方政策依据。");
        int sentenceEnd = detail.indexOf('。');
        if (sentenceEnd >= 0) {
            detail = detail.substring(0, sentenceEnd + 1);
        }
        if (detail.length() > 120) {
            detail = detail.substring(0, 120) + "…";
        }
        String prefix = "已识别为“" + intent.label + "”任务。";
        return "PARTIAL".equals(evidenceStatus)
                ? prefix + detail + "其余内容请以主管部门最新要求为准。"
                : prefix + detail;
    }

    private Evidence checkEvidence(PolicyAssistantService.QueryResponse primary,
                                   List<PolicyAssistantService.Citation> citations) {
        if (citations.isEmpty() || primary.confidence() < .15) {
            return new Evidence("INSUFFICIENT");
        }
        Set<String> sections = citations.stream()
                .map(PolicyAssistantService.Citation::section)
                .collect(Collectors.toSet());
        return new Evidence(primary.confidence() >= .28 && sections.size() >= 2 ? "SUFFICIENT" : "PARTIAL");
    }

    private List<String> sectionValues(List<PolicyAssistantService.Citation> citations, String... sectionNames) {
        return citations.stream()
                .filter(c -> Arrays.stream(sectionNames).anyMatch(s -> c.section().contains(s)))
                .map(PolicyAssistantService.Citation::summary)
                .distinct()
                .limit(3)
                .toList();
    }

    private List<AllowedAction> getAllowedActions(Intent intent, AuthenticatedUser user) {
        return actionsFor(intent).stream()
                .filter(action -> user.permissions().contains(action.permission))
                .map(action -> new AllowedAction(action.label, action.path))
                .toList();
    }

    private List<ActionDefinition> actionsFor(Intent intent) {
        return switch (intent) {
            case MIGRATION_IN -> List.of(
                    new ActionDefinition("发起迁入申请", "/migrations/in/apply", "migration:in:create"),
                    new ActionDefinition("查看迁入业务", "/migrations/in", "migration:view")
            );
            case MIGRATION_OUT -> List.of(
                    new ActionDefinition("发起迁出申请", "/migrations/out/apply", "migration:out:create"),
                    new ActionDefinition("查看迁出业务", "/migrations/out", "migration:view")
            );
            case RESIDENCE_PERMIT -> List.of(
                    new ActionDefinition("查看居住证", "/residence-permits", "residence-permit:view"),
                    new ActionDefinition("查看到期提醒", "/residence-permits/expiring", "residence-permit:expiry:view")
            );
            case HOUSEHOLD -> List.of(
                    new ActionDefinition("进入家庭户管理", "/households", "household:view")
            );
            case CANCELLATION -> List.of(
                    new ActionDefinition("进入注销管理", "/cancellations", "cancellation:view")
            );
            case APPROVAL -> List.of(
                    new ActionDefinition("查看我的申请", "/applications", "application:view"),
                    new ActionDefinition("进入待审批列表", "/approvals", "approval:view")
            );
            case POPULATION -> List.of(
                    new ActionDefinition("人口综合查询", "/queries/comprehensive", "population:view")
            );
            default -> List.of(
                    new ActionDefinition("查看我的申请", "/applications", "application:view")
            );
        };
    }

    private boolean asksStatus(String question) {
        return question.contains("审批") || question.contains("驳回")
                || question.contains("办结") || question.contains("状态");
    }

    private String explainBusinessStatus(String question) {
        if (question.contains("驳回") || question.contains("退回")) {
            return "申请被驳回后：先查看审批意见，补充或修正材料后再按流程提交。";
        }
        return "业务状态为：申请已提交 → 等待审批 → 审批通过 → 等待专业业务执行 → 业务办结。审批通过不等于业务已办结。";
    }

    private String resolveTrustedRegionCode() {
        AuthenticatedUser user = CurrentUserContext.getUserOrNull();
        if (user == null || user.regionCode() == null || user.regionCode().isBlank()) {
            return null;
        }
        return user.regionCode().trim();
    }

    private static List<String> merge(List<String> left, List<String> right) {
        return java.util.stream.Stream.concat(left.stream(), right.stream()).distinct().toList();
    }

    private static String recentHistory(List<ConversationTurn> history) {
        if (history == null) {
            return "";
        }
        return history.stream()
                .filter(Objects::nonNull)
                .filter(t -> "user".equals(t.role))
                .map(ConversationTurn::content)
                .filter(Objects::nonNull)
                .skip(Math.max(0, history.size() - MAX_HISTORY_TURNS))
                .collect(Collectors.joining(" "));
    }

    private static String normalize(String value) {
        return value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private enum Intent {
        MIGRATION_IN("MIGRATION_IN", "户籍迁入"),
        MIGRATION_OUT("MIGRATION_OUT", "户籍迁出"),
        RESIDENCE_PERMIT("RESIDENCE_PERMIT", "居住证办理"),
        HOUSEHOLD("HOUSEHOLD", "家庭户办理"),
        CANCELLATION("CANCELLATION", "注销业务"),
        APPROVAL("APPROVAL", "申请审批"),
        POPULATION("POPULATION", "人口户籍"),
        APPLICATION("APPLICATION", "业务办理咨询");

        final String code;
        final String label;

        Intent(String code, String label) {
            this.code = code;
            this.label = label;
        }
    }

    private record OptionalPythonAgentOutcome(AgentResponse response, String fallbackReason) {
    }

    private record WeightedCitation(PolicyAssistantService.Citation citation, double score) {
    }

    private record Evidence(String status) {
    }

    private record ActionDefinition(String label, String path, String permission) {
    }

    public record ConversationTurn(String role, String content) {
    }

    public record AllowedAction(String label, String path) {
    }

    public record AgentResponse(
            String intent,
            String intentLabel,
            String standaloneQuestion,
            String answerSummary,
            List<String> conditions,
            List<String> requiredMaterials,
            List<String> steps,
            List<String> warnings,
            List<PolicyAssistantService.Citation> citations,
            List<AllowedAction> nextActions,
            String evidenceStatus,
            boolean needHumanHelp,
            List<String> executionTrace,
            String mode,
            String sourceMode,
            double confidence,
            String traceId,
            String knowledgeBaseVersion,
            String modelVersion,
            String retrievalVersion,
            String rerankerVersion,
            Long elapsedMs
    ) {
        public AgentResponse(String intent, String intentLabel, String standaloneQuestion, String answerSummary,
                             List<String> conditions, List<String> requiredMaterials, List<String> steps,
                             List<String> warnings, List<PolicyAssistantService.Citation> citations,
                             List<AllowedAction> nextActions, String evidenceStatus, boolean needHumanHelp,
                             List<String> executionTrace, String mode, double confidence, String traceId) {
            this(intent, intentLabel, standaloneQuestion, answerSummary, conditions, requiredMaterials,
                    steps, warnings, citations, nextActions, evidenceStatus, needHumanHelp, executionTrace,
                    mode, PolicyAssistantService.SOURCE_MODE_JAVA_LOCAL_FALLBACK, confidence, traceId,
                    PolicyAssistantService.LOCAL_KB_VERSION, null, null, null, 0L);
        }

        public String answer() {
            return answerSummary;
        }
    }
}
