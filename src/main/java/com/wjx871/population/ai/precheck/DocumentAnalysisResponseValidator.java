package com.wjx871.population.ai.precheck;

import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Alert;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.BoundingBox;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Field;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.MaterialAnalysis;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Request;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Response;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Source;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class DocumentAnalysisResponseValidator {
    private static final Set<String> DISPOSITIONS = Set.of("ANALYZED", "NEEDS_REVIEW", "INSUFFICIENT_INPUT");
    private static final Set<String> FIELD_STATUSES = Set.of("PRESENT", "NOT_FOUND", "UNREADABLE", "NOT_APPLICABLE");
    private static final Set<String> DETECTED_TYPES = Set.of("IDENTITY_CARD_FRONT", "IDENTITY_CARD_BACK",
            "HOUSEHOLD_REGISTER_PAGE", "ADDRESS_PROOF", "TRANSFER_CERTIFICATE", "CROSS_DISTRICT_TRANSFER",
            "HOUSEHOLD_CONSENT", "APPLICATION_FORM", "UNKNOWN");
    private static final Set<String> MATERIAL_TYPES = Set.of("IDENTITY_PROOF", "HOUSEHOLD_BOOK", "ADDRESS_PROOF",
            "MIGRATION_PROOF", "HOUSEHOLD_CONSENT", "APPLICATION_FORM");
    private static final Pattern FIELD_CODE = Pattern.compile("[A-Za-z][A-Za-z0-9_.-]{0,99}");

    public Response validate(Request request, Response response) {
        if (response == null || !"1.0".equals(response.schemaVersion())
                || !request.requestId().equals(response.requestId())
                || !request.taskId().equals(response.taskId()) || !request.traceId().equals(response.traceId())
                || !DISPOSITIONS.contains(response.analysisDisposition()) || response.materials() == null
                || response.alerts() == null || response.elapsedMs() < 0
                || !Set.of("MOCK", "HTTP").contains(response.mode())) {
            throw invalid();
        }
        Set<String> inputRefs = request.materials().stream().map(DocumentAnalysisContracts.MaterialInput::materialRef)
                .collect(java.util.stream.Collectors.toSet());
        Set<String> outputRefs = new HashSet<>();
        for (MaterialAnalysis material : response.materials()) {
            if (material == null || material.materialRef() == null || !inputRefs.contains(material.materialRef())
                    || !outputRefs.add(material.materialRef()) || !DETECTED_TYPES.contains(material.detectedType())
                    || !confidence(material.typeConfidence()) || material.fields() == null) throw invalid();
            for (Field field : material.fields()) validateField(field, material.materialRef(), inputRefs);
        }
        if (!outputRefs.equals(inputRefs)) throw invalid();
        Set<String> allowedRuleIds = request.materialRequirements().stream().map(
                DocumentAnalysisContracts.MaterialRule::ruleId).collect(java.util.stream.Collectors.toSet());
        for (Alert alert : response.alerts()) validateAlert(alert, inputRefs, allowedRuleIds);
        return response;
    }

    private void validateField(Field field, String materialRef, Set<String> inputRefs) {
        if (field == null || field.fieldCode() == null || !FIELD_CODE.matcher(field.fieldCode()).matches()
                || !FIELD_STATUSES.contains(field.fieldStatus()) || !confidence(field.confidence())) throw invalid();
        if (field.source() != null) {
            Source source = field.source();
            if (!materialRef.equals(source.materialRef()) || !inputRefs.contains(source.materialRef())
                    || source.page() == null || source.page() < 1 || source.boundingBox() == null
                    || !validBox(source.boundingBox())) throw invalid();
        }
        if ("NOT_FOUND".equals(field.fieldStatus()) && field.value() != null) throw invalid();
    }

    private void validateAlert(Alert alert, Set<String> inputRefs, Set<String> allowedRuleIds) {
        if (alert == null || alert.code() == null || alert.code().isBlank() || alert.severity() == null
                || alert.safeMessage() == null || alert.safeMessage().length() > 500) throw invalid();
        if (alert.materialRefs() != null && !inputRefs.containsAll(alert.materialRefs())) throw invalid();
        if (alert.ruleId() != null && !allowedRuleIds.contains(alert.ruleId())) throw invalid();
        if (alert.alternatives() != null && alert.alternatives().stream().anyMatch(type -> !MATERIAL_TYPES.contains(type))) {
            throw invalid();
        }
    }

    private boolean confidence(Double value) {
        return value == null || (Double.isFinite(value) && value >= 0d && value <= 1d);
    }

    private boolean validBox(BoundingBox box) {
        if (box.x() == null || box.y() == null || box.width() == null || box.height() == null) return false;
        if (!unit(box.x()) || !unit(box.y()) || !unit(box.width()) || !unit(box.height())) return false;
        return box.x() + box.width() <= 1.0000001 && box.y() + box.height() <= 1.0000001;
    }

    private boolean unit(double value) {
        return Double.isFinite(value) && value >= 0d && value <= 1d;
    }

    private AnalysisFailure invalid() {
        return new AnalysisFailure("AI_INVALID_RESPONSE", false);
    }
}
