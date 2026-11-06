package com.wjx871.population.assistant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Set;

public final class PolicyPythonContracts {
    public static final String SCHEMA_VERSION_V1 = "1.0";
    public static final String INTERNAL_POLICY_QUERY_PATH = "/internal/policy-query";
    public static final String DEFAULT_LOCALE = "zh-CN";

    private PolicyPythonContracts() {
    }

    public record Request(
            String schemaVersion,
            String requestId,
            String traceId,
            String question,
            List<HistoryTurn> history,
            String businessType,
            String regionCode,
            String locale
    ) {
    }

    public record HistoryTurn(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(
            String schemaVersion,
            String requestId,
            String traceId,
            String outcome,
            String answer,
            List<String> citationIdsUsed,
            List<PythonCitation> citations,
            String knowledgeBaseVersion,
            String modelVersion,
            String retrievalVersion,
            String rerankerVersion,
            String evidenceLevel,
            Long elapsedMs
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PythonCitation(
            String sourceId,
            String title,
            String section,
            String category,
            String sourceType,
            String excerpt,
            String sourceVersion,
            String effectiveFrom,
            String effectiveTo,
            String regionCode,
            String retrievedAt
    ) {
    }

    public record RegisteredSourceMeta(
            String logicalPath,
            String title,
            String category,
            String sourceType,
            String version,
            Set<String> sections
    ) {
    }
}
