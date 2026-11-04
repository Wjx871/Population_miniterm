package com.wjx871.population.ai.precheck;

import java.util.List;

public final class DocumentAnalysisContracts {
    private DocumentAnalysisContracts() {
    }

    public record Request(
            String schemaVersion,
            String requestId,
            String taskId,
            String traceId,
            String businessType,
            String migrationType,
            List<MaterialRule> materialRequirements,
            List<MaterialInput> materials
    ) {
    }

    public record MaterialRule(String ruleId, List<MaterialRequirement> allOf,
                               List<MaterialRequirement> anyOf, Condition when) {
    }

    public record MaterialRequirement(String materialType, int minCount) {
    }

    public record Condition(String migrationType) {
    }

    public record MaterialInput(String materialRef, String contentType, long sizeBytes, String sourceRoleHint) {
    }

    public record Response(
            String schemaVersion,
            String requestId,
            String taskId,
            String traceId,
            String analysisDisposition,
            List<MaterialAnalysis> materials,
            List<Alert> alerts,
            String modelVersion,
            String ocrEngineVersion,
            String ruleSetVersion,
            long elapsedMs,
            String mode
    ) {
    }

    public record MaterialAnalysis(String materialRef, String detectedType, Double typeConfidence,
                                   List<Field> fields) {
    }

    public record Field(String fieldCode, Object value, String fieldStatus, Double confidence, Source source) {
    }

    public record Source(String materialRef, Integer page, BoundingBox boundingBox) {
    }

    public record BoundingBox(Double x, Double y, Double width, Double height) {
    }

    public record Alert(String code, String severity, List<String> fieldCodes, List<String> materialRefs,
                        String safeMessage, String ruleId, List<String> alternatives) {
    }
}
