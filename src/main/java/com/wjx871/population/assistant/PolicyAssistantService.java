package com.wjx871.population.assistant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.assistant.PolicyPythonContracts.HistoryTurn;
import com.wjx871.population.assistant.PolicyPythonContracts.RegisteredSourceMeta;
import com.wjx871.population.s2.trace.S2TraceContext;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.ThreadContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class PolicyAssistantService {
    public static final String SOURCE_MODE_PYTHON_HYBRID_RAG = "PYTHON_HYBRID_RAG";
    public static final String SOURCE_MODE_JAVA_LOCAL_FALLBACK = "JAVA_LOCAL_FALLBACK";
    public static final String SOURCE_MODE_JAVA_LEGACY_AI = "JAVA_LEGACY_AI_ASSISTED";
    public static final String SOURCE_MODE_JAVA_SENSITIVE_GUARD = "JAVA_SENSITIVE_GUARD";
    public static final String SOURCE_MODE_SERVICE_ERROR = "SERVICE_ERROR";

    public static final String MODE_AI_ASSISTED = "AI_ASSISTED";
    public static final String MODE_RETRIEVAL_ONLY = "RETRIEVAL_ONLY";
    public static final String MODE_SAFE_REFUSAL = "SAFE_REFUSAL";
    public static final String MODE_SERVICE_ERROR = "SERVICE_ERROR";

    public static final String LOCAL_KB_VERSION = "LOCAL_MD_V1";

    private static final String NOTICE = "回答依据系统内置业务指南，正式办理要求请以主管部门最新规定为准。";
    static final String REFUSAL = "该助手仅提供办理政策和系统使用指引，不查询或修改人口敏感数据。";
    static final String INSUFFICIENT_GUIDE_MESSAGE = "当前内置业务指南未提供足够依据来确认这个问题。请换用户籍迁入、迁出、居住证、家庭户、注销或审批办理相关的问题咨询。";
    static final String SERVICE_ERROR_MESSAGE = "政策问答服务暂时不可用，请稍后重试或联系业务人员协助办理。";

    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i)(\\b\\d{17}[0-9Xx]\\b|\\b1[3-9]\\d{9}\\b|完整身份证|身份证号|手机号"
                    + "|注销.*人员|绕过审批|修改.*人口"
                    + "|(?:修改|篡改|删除|伪造).*(?:人口|户籍|家庭户|数据|档案|审批)"
                    + "|(?:查询|调取|导出).*(?:个人敏感|敏感档案|他人档案|完整档案|全部身份证)"
                    + "|(?:生成|执行|运行).*sql|\\b(?:select|insert|update|delete|drop|alter|truncate)\\b.*\\b(?:from|into|table|set|where)\\b"
                    + "|系统密钥|bearer\\s+[a-z0-9._-]+|eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,}|api[_-]?key|access[_-]?token|password\\s*[:=])"
    );
    private static final Pattern UNRELATED = Pattern.compile("量子|天气|股票|编程|代码|游戏|旅游|菜谱");

    private final ObjectMapper objectMapper;
    private final PolicyPythonProperties pythonProperties;
    private final HttpPolicyQueryClient httpPolicyQueryClient;
    private final MockPolicyQueryClient mockPolicyQueryClient;
    private final PolicyPythonResponseValidator responseValidator;

    private final HttpClient legacyHttpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    private final List<Chunk> chunks = new ArrayList<>();
    private final Map<String, RegisteredSourceMeta> registeredSources = new LinkedHashMap<>();
    private volatile boolean simulateLocalSearchFailure = false;

    @Value("${policy.ai.enabled:false}")
    private boolean aiEnabled;
    @Value("${policy.ai.base-url:https://api.deepseek.com}")
    private String aiBaseUrl;
    @Value("${policy.ai.api-key:}")
    private String apiKey;
    @Value("${policy.ai.model:deepseek-chat}")
    private String model;
    @Value("${policy.ai.timeout-seconds:30}")
    private int timeoutSeconds;

    @Autowired
    public PolicyAssistantService(ObjectMapper objectMapper,
                                  PolicyPythonProperties pythonProperties,
                                  HttpPolicyQueryClient httpPolicyQueryClient,
                                  MockPolicyQueryClient mockPolicyQueryClient,
                                  PolicyPythonResponseValidator responseValidator) {
        this.objectMapper = objectMapper;
        this.pythonProperties = pythonProperties;
        this.httpPolicyQueryClient = httpPolicyQueryClient;
        this.mockPolicyQueryClient = mockPolicyQueryClient;
        this.responseValidator = responseValidator;
    }

    public PolicyAssistantService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.pythonProperties = new PolicyPythonProperties();
        this.httpPolicyQueryClient = new HttpPolicyQueryClient(this.pythonProperties, objectMapper, null);
        this.mockPolicyQueryClient = new MockPolicyQueryClient(this.pythonProperties);
        this.responseValidator = new PolicyPythonResponseValidator();
    }

    @PostConstruct
    void load() {
        try {
            chunks.clear();
            registeredSources.clear();
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:policy-knowledge/*.md");
            for (Resource resource : resources) {
                parse(new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            }
            log.info("Policy knowledge base loaded: {} chunks across {} registered sources",
                    chunks.size(), registeredSources.size());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to load policy knowledge base", e);
        }
    }

    private void parse(String markdown) {
        Map<String, String> meta = new HashMap<>();
        String[] parts = markdown.split("---", 3);
        if (parts.length >= 3) {
            for (String line : parts[1].split("\\R")) {
                int i = line.indexOf(':');
                if (i > 0) {
                    meta.put(line.substring(0, i).trim(), line.substring(i + 1).trim());
                }
            }
            markdown = parts[2];
        }
        String section = "概述";
        StringBuilder text = new StringBuilder();
        Set<String> sectionsInDoc = new LinkedHashSet<>();
        for (String line : markdown.split("\\R")) {
            if (line.startsWith("## ")) {
                add(meta, section, text, sectionsInDoc);
                section = line.substring(3).trim();
                text.setLength(0);
            } else {
                text.append(line).append('\n');
            }
        }
        add(meta, section, text, sectionsInDoc);

        String title = meta.getOrDefault("title", "业务办理指南");
        String category = meta.getOrDefault("category", "业务办理");
        String sourceType = meta.getOrDefault("sourceType", "PROJECT_GUIDE");
        String version = meta.getOrDefault("version", "V1");
        String logicalPath = meta.getOrDefault("logicalPath", "policy-guide");
        if (!sectionsInDoc.isEmpty()) {
            registeredSources.put(logicalPath, new RegisteredSourceMeta(
                    logicalPath, title, category, sourceType, version, Set.copyOf(sectionsInDoc)
            ));
        }
    }

    private void add(Map<String, String> meta, String section, StringBuilder text, Set<String> sectionsInDoc) {
        String content = text.toString().trim();
        if (!content.isBlank()) {
            chunks.add(new Chunk(
                    meta.getOrDefault("title", "业务办理指南"),
                    section,
                    meta.getOrDefault("category", "业务办理"),
                    meta.getOrDefault("sourceType", "PROJECT_GUIDE"),
                    meta.getOrDefault("version", "V1"),
                    meta.getOrDefault("logicalPath", "policy-guide"),
                    content
            ));
            sectionsInDoc.add(section);
        }
    }

    public boolean isSensitive(String text) {
        return text != null && !text.isBlank() && SENSITIVE.matcher(text).find();
    }

    public boolean containsSensitiveInput(String question, List<PolicyTaskAgentService.ConversationTurn> history) {
        if (isSensitive(question)) {
            return true;
        }
        if (history == null || history.isEmpty()) {
            return false;
        }
        for (PolicyTaskAgentService.ConversationTurn turn : history) {
            if (turn == null) {
                continue;
            }
            if (isSensitive(turn.content()) || isSensitive(turn.role())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isUnrelatedQuestion(String question) {
        return question != null && UNRELATED.matcher(question).find();
    }

    public Map<String, RegisteredSourceMeta> getRegisteredSources() {
        return Collections.unmodifiableMap(registeredSources);
    }

    void setSimulateLocalSearchFailure(boolean simulateLocalSearchFailure) {
        this.simulateLocalSearchFailure = simulateLocalSearchFailure;
    }

    public QueryResponse query(String question) {
        String traceId = S2TraceContext.resolveTraceId(ThreadContext.get("traceId"));
        try (S2TraceContext.Scope ignored = S2TraceContext.open(traceId)) {
            return queryPrimary(question, List.of(), null, resolveTrustedRegionCode(), traceId);
        }
    }

    public QueryResponse queryPrimary(String question,
                                      List<PolicyTaskAgentService.ConversationTurn> history,
                                      String businessType,
                                      String regionCode,
                                      String traceId) {
        String effectiveTraceId = S2TraceContext.resolveTraceId(
                traceId != null ? traceId : ThreadContext.get("traceId")
        );
        String q = question == null ? "" : question.trim();
        if (containsSensitiveInput(q, history)) {
            log.info("Policy query blocked by sensitive guard: traceId={} instanceId={} sourceMode={}",
                    effectiveTraceId, S2TraceContext.instanceId(), SOURCE_MODE_JAVA_SENSITIVE_GUARD);
            return safeRefusalResponse(effectiveTraceId);
        }
        if (q.isBlank() || q.length() > 500) {
            return insufficientLocalResponse(effectiveTraceId, 0.0, SOURCE_MODE_JAVA_LOCAL_FALLBACK);
        }

        if (pythonProperties != null && pythonProperties.isEnabled()) {
            return queryViaPythonWithFallback(q, history, businessType, regionCode, effectiveTraceId);
        }

        return queryLocalInternal(q, effectiveTraceId, true, SOURCE_MODE_JAVA_LOCAL_FALLBACK, null);
    }

    public QueryResponse queryLocalOnly(String question, String traceId, String fallbackReason) {
        String effectiveTraceId = S2TraceContext.resolveTraceId(
                traceId != null ? traceId : ThreadContext.get("traceId")
        );
        String q = question == null ? "" : question.trim();
        if (isSensitive(q)) {
            return safeRefusalResponse(effectiveTraceId);
        }
        return queryLocalInternal(q, effectiveTraceId, false, SOURCE_MODE_JAVA_LOCAL_FALLBACK, fallbackReason);
    }

    public QueryResponse safeRefusalResponse(String traceId) {
        String effectiveTraceId = S2TraceContext.resolveTraceId(traceId);
        return new QueryResponse(
                REFUSAL,
                MODE_SAFE_REFUSAL,
                SOURCE_MODE_JAVA_SENSITIVE_GUARD,
                1.0,
                List.of(),
                List.of(),
                effectiveTraceId,
                null,
                null,
                null,
                null,
                "INSUFFICIENT",
                0L,
                null,
                List.of(),
                false
        );
    }

    public QueryResponse serviceErrorResponse(String traceId, long elapsedMs) {
        String effectiveTraceId = S2TraceContext.resolveTraceId(traceId);
        return new QueryResponse(
                SERVICE_ERROR_MESSAGE,
                MODE_SERVICE_ERROR,
                SOURCE_MODE_SERVICE_ERROR,
                0.0,
                List.of(),
                List.of(),
                effectiveTraceId,
                null,
                null,
                null,
                null,
                "INSUFFICIENT",
                Math.max(0L, elapsedMs),
                null,
                List.of(),
                false
        );
    }

    private QueryResponse queryViaPythonWithFallback(String question,
                                                     List<PolicyTaskAgentService.ConversationTurn> history,
                                                     String businessType,
                                                     String regionCode,
                                                     String traceId) {
        String requestId = UUID.randomUUID().toString();
        long started = System.nanoTime();
        try {
            List<HistoryTurn> sanitizedHistory = sanitizeHistory(history);
            PolicyPythonContracts.Request pyRequest = new PolicyPythonContracts.Request(
                    PolicyPythonContracts.SCHEMA_VERSION_V1,
                    requestId,
                    traceId,
                    question,
                    sanitizedHistory,
                    businessType,
                    regionCode,
                    PolicyPythonContracts.DEFAULT_LOCALE
            );
            PolicyQueryClient client = selectPythonClient();
            PolicyPythonContracts.Response rawResponse = client.query(pyRequest);
            PolicyPythonResponseValidator.ValidatedPolicyResponse validated = responseValidator.validate(
                    pyRequest, rawResponse, registeredSources, configuredSecrets()
            );
            long elapsedMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);

            if ("INSUFFICIENT_EVIDENCE".equals(validated.response().outcome())) {
                log.info("Policy Python query completed with insufficient evidence: traceId={} requestId={} instanceId={} sourceMode={} citationCount=0 elapsedMs={}",
                        traceId, requestId, S2TraceContext.instanceId(), SOURCE_MODE_PYTHON_HYBRID_RAG, elapsedMs);
                return new QueryResponse(
                        INSUFFICIENT_GUIDE_MESSAGE,
                        MODE_RETRIEVAL_ONLY,
                        SOURCE_MODE_PYTHON_HYBRID_RAG,
                        0.0,
                        List.of(),
                        List.of(),
                        traceId,
                        validated.response().knowledgeBaseVersion(),
                        validated.response().modelVersion(),
                        validated.response().retrievalVersion(),
                        validated.response().rerankerVersion(),
                        "INSUFFICIENT",
                        elapsedMs,
                        null,
                        List.of(),
                        false
                );
            }

            List<Citation> citations = validated.citations();
            double confidence = "SUFFICIENT".equals(validated.computedEvidenceStatus()) ? 0.85 : 0.50;
            log.info("Policy Python query succeeded: traceId={} requestId={} instanceId={} sourceMode={} citationCount={} registryVerified={} elapsedMs={}",
                    traceId, requestId, S2TraceContext.instanceId(), SOURCE_MODE_PYTHON_HYBRID_RAG,
                    citations.size(), validated.allSourcesRegistryVerified(), elapsedMs);
            return new QueryResponse(
                    validated.renumberedAnswer(),
                    MODE_AI_ASSISTED,
                    SOURCE_MODE_PYTHON_HYBRID_RAG,
                    confidence,
                    citations,
                    actionsFromCitations(citations),
                    traceId,
                    validated.response().knowledgeBaseVersion(),
                    validated.response().modelVersion(),
                    validated.response().retrievalVersion(),
                    validated.response().rerankerVersion(),
                    validated.computedEvidenceStatus(),
                    elapsedMs,
                    validated.response().answer(),
                    validated.citationIdsUsed(),
                    validated.allSourcesRegistryVerified()
            );
        } catch (Exception e) {
            long elapsedMs = Math.max(1L, (System.nanoTime() - started) / 1_000_000L);
            String errorCode = e instanceof PolicyPythonClientException pce
                    ? pce.getErrorCode()
                    : "PYTHON_UNEXPECTED_ERROR";
            log.warn("Policy Python query degraded to Java local fallback: traceId={} requestId={} instanceId={} sourceMode={} fallbackReason={} elapsedMs={}",
                    traceId, requestId, S2TraceContext.instanceId(), SOURCE_MODE_JAVA_LOCAL_FALLBACK, errorCode, elapsedMs);
            return queryLocalInternal(question, traceId, false, SOURCE_MODE_JAVA_LOCAL_FALLBACK, errorCode);
        }
    }

    private QueryResponse queryLocalInternal(String question,
                                             String traceId,
                                             boolean allowLegacyAi,
                                             String baseSourceMode,
                                             String fallbackReason) {
        long started = System.nanoTime();
        try {
            if (simulateLocalSearchFailure || chunks.isEmpty()) {
                throw new IllegalStateException("Local policy knowledge base unavailable");
            }
            if (UNRELATED.matcher(question).find()) {
                return insufficientLocalResponse(traceId, 0.0, baseSourceMode);
            }
            List<Hit> hits = search(question, 4);
            double confidence = hits.isEmpty() ? 0 : Math.min(.96, hits.get(0).score() / 18);
            if (hits.isEmpty() || confidence < .15) {
                return insufficientLocalResponse(traceId, confidence, baseSourceMode);
            }
            List<Citation> citations = new ArrayList<>();
            for (int i = 0; i < hits.size(); i++) {
                Chunk c = hits.get(i).chunk();
                citations.add(new Citation(
                        i + 1, c.title(), c.section(), c.category(), c.sourceType(),
                        c.version(), c.logicalPath(), summary(c.content())
                ));
            }
            String fallback = composeFallback(citations);
            Optional<String> legacyAnswer = allowLegacyAi ? tryAi(question, citations, traceId) : Optional.empty();
            boolean usedLegacyAi = legacyAnswer.isPresent();
            String finalAnswer = legacyAnswer.orElse(fallback);
            String mode = usedLegacyAi ? MODE_AI_ASSISTED : MODE_RETRIEVAL_ONLY;
            String sourceMode = usedLegacyAi ? SOURCE_MODE_JAVA_LEGACY_AI : baseSourceMode;
            String evidenceLevel = confidence >= .28 && citations.size() >= 2 ? "SUFFICIENT" : "PARTIAL";
            long elapsedMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            return new QueryResponse(
                    finalAnswer,
                    mode,
                    sourceMode,
                    confidence,
                    List.copyOf(citations),
                    actions(hits),
                    traceId,
                    LOCAL_KB_VERSION,
                    usedLegacyAi ? model : null,
                    null,
                    null,
                    evidenceLevel,
                    elapsedMs,
                    null,
                    List.of(),
                    true
            );
        } catch (Exception e) {
            long elapsedMs = Math.max(0L, (System.nanoTime() - started) / 1_000_000L);
            log.error("Policy local retrieval failed: traceId={} instanceId={} sourceMode={} previousFallbackReason={} errorType={}",
                    traceId, S2TraceContext.instanceId(), SOURCE_MODE_SERVICE_ERROR,
                    fallbackReason == null ? "NONE" : fallbackReason, e.getClass().getSimpleName());
            return serviceErrorResponse(traceId, elapsedMs);
        }
    }

    private QueryResponse insufficientLocalResponse(String traceId, double confidence, String sourceMode) {
        return new QueryResponse(
                INSUFFICIENT_GUIDE_MESSAGE,
                MODE_RETRIEVAL_ONLY,
                sourceMode,
                confidence,
                List.of(),
                List.of(),
                traceId,
                LOCAL_KB_VERSION,
                null,
                null,
                null,
                "INSUFFICIENT",
                0L,
                null,
                List.of(),
                true
        );
    }

    private PolicyQueryClient selectPythonClient() {
        PolicyPythonProperties.ClientMode clientMode = pythonProperties.getClient();
        if (clientMode == PolicyPythonProperties.ClientMode.HTTP) {
            return httpPolicyQueryClient;
        }
        return mockPolicyQueryClient;
    }

    private List<HistoryTurn> sanitizeHistory(List<PolicyTaskAgentService.ConversationTurn> history) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, history.size() - 6);
        List<HistoryTurn> sanitized = new ArrayList<>();
        for (int i = start; i < history.size(); i++) {
            PolicyTaskAgentService.ConversationTurn turn = history.get(i);
            if (turn == null || turn.content() == null || turn.content().isBlank()) {
                continue;
            }
            String role = turn.role() == null ? "user" : turn.role().trim().toLowerCase(Locale.ROOT);
            if (!"user".equals(role) && !"assistant".equals(role)) {
                role = "user";
            }
            String content = turn.content().trim();
            if (content.length() > 500) {
                content = content.substring(0, 500);
            }
            sanitized.add(new HistoryTurn(role, content));
        }
        return List.copyOf(sanitized);
    }

    private String resolveTrustedRegionCode() {
        AuthenticatedUser user = CurrentUserContext.getUserOrNull();
        if (user == null || user.regionCode() == null || user.regionCode().isBlank()) {
            return null;
        }
        return user.regionCode().trim();
    }

    private List<String> configuredSecrets() {
        List<String> secrets = new ArrayList<>(2);
        if (pythonProperties != null && pythonProperties.getAuthToken() != null
                && !pythonProperties.getAuthToken().isBlank()) {
            secrets.add(pythonProperties.getAuthToken().trim());
        }
        if (apiKey != null && !apiKey.isBlank()) {
            secrets.add(apiKey.trim());
        }
        return secrets;
    }

    private List<Hit> search(String q, int topK) {
        List<String> tokens = ngrams(normalize(q));
        Map<String, Integer> df = new HashMap<>();
        for (Chunk c : chunks) {
            new HashSet<>(ngrams(normalize(c.content() + c.title() + c.section())))
                    .forEach(t -> df.merge(t, 1, Integer::sum));
        }
        int n = chunks.size();
        return chunks.stream().map(c -> {
            List<String> doc = ngrams(normalize(c.content()));
            double score = 0;
            for (String t : tokens) {
                int f = Collections.frequency(doc, t);
                if (f > 0) {
                    score += Math.log((n - df.getOrDefault(t, 0) + .5) / (df.getOrDefault(t, 0) + .5) + 1)
                            * (f * 2.2 / (f + 1.2));
                }
                if (normalize(c.title() + c.category() + c.section()).contains(t)) {
                    score += 1.2;
                }
            }
            return new Hit(c, score);
        }).filter(h -> h.score() > 0)
                .sorted(Comparator.comparingDouble(Hit::score).reversed())
                .limit(Math.max(1, Math.min(8, topK)))
                .toList();
    }

    private static String normalize(String s) {
        return s.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }

    private static List<String> ngrams(String s) {
        List<String> r = new ArrayList<>();
        for (int i = 0; i < s.length(); i++) {
            r.add(s.substring(i, Math.min(i + 2, s.length())));
        }
        return r;
    }

    private Optional<String> tryAi(String question, List<Citation> citations, String traceId) {
        if (!aiEnabled || apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            String context = citations.stream()
                    .map(c -> "[" + c.index() + "] " + c.title() + "/" + c.section() + "：" + c.summary())
                    .collect(Collectors.joining("\n"));
            String prompt = "仅依据以下资料回答问题，不得编造政策、材料、时限或法规。使用[1]引用。以办理说明、材料、步骤、注意事项、下一步组织；没有依据时明确说明。不要输出路径或技术实现。\n资料：\n"
                    + context + "\n问题：" + question;
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt)),
                    "temperature", 0.1
            ));
            HttpRequest request = HttpRequest.newBuilder(URI.create(aiBaseUrl.replaceAll("/$", "") + "/v1/chat/completions"))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofSeconds(timeoutSeconds))
                    .build();
            JsonNode node = objectMapper.readTree(legacyHttpClient.send(request, HttpResponse.BodyHandlers.ofString()).body());
            String answer = node.path("choices").path(0).path("message").path("content").asText();
            return answer.isBlank() ? Optional.empty() : Optional.of(answer + "\n\n" + NOTICE);
        } catch (Exception e) {
            log.warn("Policy legacy AI unavailable; using retrieval-only fallback: traceId={} errorType={}",
                    traceId, e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String composeFallback(List<Citation> citations) {
        return "根据内置业务指南，建议按以下参考内容核对办理条件和系统入口：\n\n"
                + citations.stream().map(c -> "[" + c.index() + "] " + c.summary()).collect(Collectors.joining("\n\n"))
                + "\n\n" + NOTICE;
    }

    private static String summary(String s) {
        return s.length() > 260 ? s.substring(0, 260) + "…" : s;
    }

    private static List<SuggestedAction> actions(List<Hit> hits) {
        return hits.stream().map(Hit::chunk).map(c -> mapCategoryAction(c.category()))
                .distinct().limit(3).toList();
    }

    private static List<SuggestedAction> actionsFromCitations(List<Citation> citations) {
        return citations.stream().filter(Objects::nonNull).map(c -> mapCategoryAction(c.category()))
                .distinct().limit(3).toList();
    }

    private static SuggestedAction mapCategoryAction(String category) {
        String p = switch (category == null ? "" : category) {
            case "迁入", "迁出" -> "/migrations";
            case "居住证" -> "/residence-permits";
            case "审批" -> "/approvals";
            case "家庭户" -> "/households";
            default -> "/applications";
        };
        return new SuggestedAction((category == null ? "业务办理" : category) + "相关功能", p);
    }

    private static String defaultSourceMode(String mode) {
        if (MODE_SAFE_REFUSAL.equals(mode)) {
            return SOURCE_MODE_JAVA_SENSITIVE_GUARD;
        }
        if (MODE_SERVICE_ERROR.equals(mode)) {
            return SOURCE_MODE_SERVICE_ERROR;
        }
        if (MODE_AI_ASSISTED.equals(mode)) {
            return SOURCE_MODE_JAVA_LEGACY_AI;
        }
        return SOURCE_MODE_JAVA_LOCAL_FALLBACK;
    }

    public List<String> suggestions() {
        return List.of(
                "办理户籍迁入需要什么材料？",
                "那如果材料不全呢？",
                "户籍迁出的流程是什么？",
                "居住证到期后怎么办？",
                "在哪里提交居住证申请？",
                "家庭户新增成员需要什么条件？",
                "申请被驳回后怎么办？",
                "补正后在哪里重新提交？",
                "审批通过是不是已经办结？",
                "没有权限时该怎么处理？"
        );
    }

    record Chunk(String title, String section, String category, String sourceType,
                 String version, String logicalPath, String content) {
    }

    record Hit(Chunk chunk, double score) {
    }

    public record Citation(
            int index,
            String title,
            String section,
            String category,
            String sourceType,
            String version,
            String logicalPath,
            String summary,
            String effectiveFrom,
            String effectiveTo,
            String regionCode,
            String retrievedAt
    ) {
        public Citation(int index, String title, String section, String category,
                        String sourceType, String version, String logicalPath, String summary) {
            this(index, title, section, category, sourceType, version, logicalPath, summary, null, null, null, null);
        }
    }

    public record SuggestedAction(String label, String path) {
    }

    public record QueryResponse(
            String answer,
            String mode,
            String sourceMode,
            double confidence,
            List<Citation> citations,
            List<SuggestedAction> suggestedActions,
            String traceId,
            String knowledgeBaseVersion,
            String modelVersion,
            String retrievalVersion,
            String rerankerVersion,
            String evidenceLevel,
            Long elapsedMs,
            @JsonIgnore String rawPythonAnswer,
            @JsonIgnore List<String> citationIdsUsed,
            @JsonIgnore boolean allSourcesRegistryVerified
    ) {
        public QueryResponse(String answer, String mode, double confidence,
                             List<Citation> citations, List<SuggestedAction> suggestedActions, String traceId) {
            this(answer, mode, defaultSourceMode(mode), confidence, citations, suggestedActions,
                    traceId, LOCAL_KB_VERSION, null, null, null, null, 0L, null, List.of(), true);
        }
    }
}
