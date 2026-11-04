package com.wjx871.population.ai.precheck;

import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Alert;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.MaterialAnalysis;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Request;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Response;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "analysis-client", havingValue = "MOCK", matchIfMissing = true)
public class MockDocumentAnalysisClient implements DocumentAnalysisClient {
    private final AiPrecheckProperties properties;
    private final ConcurrentHashMap<String, AtomicInteger> callsByTask = new ConcurrentHashMap<>();

    @Override
    public Response analyze(Request request) {
        long started = System.nanoTime();
        boolean forcedTimeout = properties.getMockMode() == AiPrecheckProperties.MockMode.TIMEOUT;
        Duration delay = properties.getMockDelay();
        if (forcedTimeout && delay.isZero()) delay = properties.getAnalysisReadTimeout();
        if (delay.compareTo(properties.getProcessingTimeout()) >= 0) {
            sleep(properties.getProcessingTimeout());
            throw new AnalysisFailure("AI_TIMEOUT", true);
        }
        sleep(delay);
        if (forcedTimeout) throw new AnalysisFailure("AI_TIMEOUT", true);
        if (properties.getMockMode() == AiPrecheckProperties.MockMode.PERMANENT_FAILURE) {
            throw new AnalysisFailure("AI_MOCK_PERMANENT_FAILURE", false);
        }
        int call = callsByTask.computeIfAbsent(request.taskId(), ignored -> new AtomicInteger()).incrementAndGet();
        if (call <= properties.getMockFailuresBeforeSuccess()) {
            throw new AnalysisFailure("AI_TEMPORARY_UNAVAILABLE", true);
        }
        if (properties.getMockMode() == AiPrecheckProperties.MockMode.INVALID_RESPONSE) {
            callsByTask.remove(request.taskId());
            return new Response("99.0", request.requestId(), request.taskId(), request.traceId(), "ANALYZED",
                    List.of(), List.of(), "MOCK", null, "MOCK_RULES", 0, "MOCK");
        }

        List<MaterialAnalysis> materials = request.materials().stream()
                .map(input -> new MaterialAnalysis(input.materialRef(), "UNKNOWN", null, List.of()))
                .toList();
        List<Alert> alerts = new ArrayList<>();
        for (var rule : request.materialRequirements()) {
            List<String> alternatives = rule.anyOf() == null ? List.of()
                    : rule.anyOf().stream().map(DocumentAnalysisContracts.MaterialRequirement::materialType).toList();
            List<String> refs = request.materials().stream()
                    .map(DocumentAnalysisContracts.MaterialInput::materialRef).toList();
            alerts.add(new Alert("MATERIAL_REVIEW_REQUIRED", "INFO", List.of(), refs,
                    "模拟分析未读取或识别材料内容，请由用户人工核对。", rule.ruleId(), alternatives));
        }
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;
        callsByTask.remove(request.taskId());
        return new Response("1.0", request.requestId(), request.taskId(), request.traceId(),
                "INSUFFICIENT_INPUT", materials, alerts, "MOCK", null, "MOCK_RULES", elapsedMs, "MOCK");
    }

    private void sleep(Duration delay) {
        if (delay == null || delay.isZero() || delay.isNegative()) return;
        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AnalysisFailure("AI_INTERRUPTED", true);
        }
    }
}
