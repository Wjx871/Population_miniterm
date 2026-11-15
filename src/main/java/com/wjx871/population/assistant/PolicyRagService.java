package com.wjx871.population.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/** 可选只读 RAG 适配。业务权限和下一步入口仍由既有 Agent 过滤，不执行写操作。 */
@Slf4j
@Service
public class PolicyRagService {
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    @Value("${policy.rag.enabled:false}") private boolean enabled;
    @Value("${policy.rag.url:http://127.0.0.1:8091}") private String baseUrl;
    @Value("${policy.rag.service-key:}") private String serviceKey;

    public PolicyRagService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(65000);
        this.restTemplate = new RestTemplate(factory);
    }

    boolean isEnabled() { return enabled; }

    public PolicyTaskAgentService.AgentResponse enhance(String question,
            List<PolicyTaskAgentService.ConversationTurn> history, PolicyTaskAgentService.AgentResponse baseline) {
        if (!enabled || "SAFE_REFUSAL".equals(baseline.mode())) return baseline;
        try {
            if (serviceKey == null || serviceKey.isBlank()) throw new IllegalStateException("Missing service key");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("X-AI-Service-Key", serviceKey);
            String request = objectMapper.writeValueAsString(Map.of("question", question, "topK", 4,
                    "history", boundedHistory(history)));
            String raw = restTemplate.postForObject(baseUrl.replaceAll("/$", "") + "/internal/policy-query",
                    new HttpEntity<>(request, headers), String.class);
            if (raw == null || raw.length() > 64000) throw new IllegalStateException("Invalid response size");
            JsonNode data = objectMapper.readTree(raw);
            String answer = data.path("answer").asText();
            String mode = data.path("mode").asText();
            String evidence = data.path("evidenceStatus").asText();
            if (answer.isBlank() || !List.of("RETRIEVAL_ONLY", "SAFE_REFUSAL", "AI_ASSISTED").contains(mode)
                    || !List.of("CANDIDATES_ONLY", "INSUFFICIENT", "REFUSED").contains(evidence)
                    || !"1.0".equals(data.path("schemaVersion").asText()) || !data.path("citations").isArray()) {
                throw new IllegalStateException("Invalid RAG contract");
            }
            List<PolicyAssistantService.Citation> citations = new ArrayList<>();
            if (data.path("citations").size() > 8) throw new IllegalStateException("Too many citations");
            for (JsonNode c : data.path("citations")) {
                String path = c.path("logicalPath").asText();
                if (!path.matches("[A-Za-z0-9_-]{1,120}") || c.path("excerpt").asText().isBlank()
                        || c.path("excerpt").asText().length() > 2000 || c.path("index").asInt() != citations.size() + 1) {
                    throw new IllegalStateException("Invalid citation");
                }
                citations.add(new PolicyAssistantService.Citation(c.path("index").asInt(), c.path("title").asText(),
                        c.path("section").asText(), c.path("category").asText(), c.path("sourceType").asText(),
                        c.path("sourceVersion").asText(), path, c.path("excerpt").asText(),
                        officialUrl(c.path("sourceUrl").asText()), c.path("effectiveDate").asText(null)));
            }
            if (!"CANDIDATES_ONLY".equals(evidence) && !citations.isEmpty()) {
                throw new IllegalStateException("Refusal must not contain citations");
            }
            if ("CANDIDATES_ONLY".equals(evidence) && citations.isEmpty()) {
                throw new IllegalStateException("Candidate response needs evidence");
            }
            JsonNode scope = data.path("scopeSource");
            if ("INSUFFICIENT".equals(evidence) && scope.isObject()) {
                String expires = scope.path("expiresOn").asText("");
                citations.add(new PolicyAssistantService.Citation(1, scope.path("title").asText(),
                        "文件时效", "", "POLICY_STATUS", scope.path("sourceVersion").asText(),
                        "policy-document-status", expires.isBlank() ? "" : "原文有效期截至 " + expires,
                        officialUrl(scope.path("sourceUrl").asText()), scope.path("effectiveDate").asText(null)));
            }
            List<String> warnings = new ArrayList<>();
            if ("CANDIDATES_ONLY".equals(evidence) && !"AI_ASSISTED".equals(mode)) {
                warnings.add("当前展示检索到的资料摘要，请核对所引条款。");
            }
            List<String> trace = List.of("已检查来源适用性", "AI_ASSISTED".equals(mode)
                    ? "已核对回答与引用摘要" : "已保留适用性核查或检索结果");
            return new PolicyTaskAgentService.AgentResponse(baseline.intent(), baseline.intentLabel(),
                    data.path("standaloneQuestion").asText(question), answer, List.of(), List.of(), List.of(), warnings,
                    List.copyOf(citations), "SAFE_REFUSAL".equals(mode) ? List.of() : baseline.nextActions(),
                    "CANDIDATES_ONLY".equals(evidence) ? "PARTIAL" : "INSUFFICIENT", true, trace, mode,
                    "PYTHON_REMOTE_RAG", 0.0, baseline.traceId(), data.path("knowledgeVersion").asText(null),
                    data.path("modelVersions").path("generator").asText(null), data.path("retrievalEngine").asText(null),
                    data.path("modelVersions").path("reranker").asText(null),
                    data.path("timingMs").isNumber() ? Math.round(data.path("timingMs").asDouble()) : null);
        } catch (Exception ex) {
            log.warn("RAG service unavailable or response rejected; no business writes performed: {}", ex.getClass().getSimpleName());
            // 不用无地区/时间约束的旧回答掩盖故障，避免退化为错误的政策确认。
            return new PolicyTaskAgentService.AgentResponse(baseline.intent(), baseline.intentLabel(),
                    baseline.standaloneQuestion(), "智能检索服务暂时不可用，请稍后重试或联系业务人员；普通业务功能仍可使用。",
                    List.of(), List.of(), List.of(), List.of("RAG_SERVICE_UNAVAILABLE"), List.of(), baseline.nextActions(),
                    "INSUFFICIENT", true, List.of("检索服务暂不可用，未生成政策结论"), "RETRIEVAL_ONLY", 0.0, baseline.traceId());
        }
    }

    private String officialUrl(String value) {
        if (value.isBlank() || value.length() > 1000) return null;
        URI uri = URI.create(value);
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getUserInfo() != null || host == null) return null;
        return host.toLowerCase(java.util.Locale.ROOT).endsWith(".gov.cn") ? value : null;
    }

    private List<PolicyTaskAgentService.ConversationTurn> boundedHistory(List<PolicyTaskAgentService.ConversationTurn> history) {
        if (history == null) return List.of();
        return history.stream().filter(t -> t != null && t.content() != null && !t.content().isBlank()
                        && ("user".equals(t.role()) || "assistant".equals(t.role())))
                .skip(Math.max(0, history.size() - 6))
                .map(t -> new PolicyTaskAgentService.ConversationTurn(t.role(), t.content().substring(0, Math.min(500, t.content().length()))))
                .toList();
    }
}
