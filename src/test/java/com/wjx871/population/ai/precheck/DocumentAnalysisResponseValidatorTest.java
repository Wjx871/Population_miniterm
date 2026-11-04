package com.wjx871.population.ai.precheck;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Alert;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.BoundingBox;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Field;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.MaterialAnalysis;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Request;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Response;
import com.wjx871.population.ai.precheck.DocumentAnalysisContracts.Source;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentAnalysisResponseValidatorTest {
    private final DocumentAnalysisResponseValidator validator = new DocumentAnalysisResponseValidator();

    @Test
    void requestSeparatesRuleIdsAndAnyOfMaterialTypes() {
        AiPrecheckTaskService.TaskRow task = new AiPrecheckTaskService.TaskRow("task_0123456789abcdef0123456789abcdef",
                6, "DOCUMENT_PRECHECK", "MIGRATION_IN", "IN_CITY_CROSS_DISTRICT", "RUNNING", 1, 0, 2,
                "trace-demo", "digest", null, null, LocalDateTime.now(), LocalDateTime.now(), null, null,
                "instance-demo", LocalDateTime.now().plusMinutes(3), null);
        var materials = List.of(
                new AiPrecheckTaskService.TaskMaterial("stg_0123456789abcdef0123456789abcdef",
                        "IDENTITY_CARD_FRONT", "image/jpeg", 120L, "a".repeat(64), "synthetic.jpg", "obj_0123456789abcdef0123456789abcdef.jpg", 0),
                new AiPrecheckTaskService.TaskMaterial("stg_fedcba9876543210fedcba9876543210",
                        "HOUSEHOLD_REGISTER_PAGE", "application/pdf", 150L, "b".repeat(64), "synthetic.pdf", "obj_fedcba9876543210fedcba9876543210.pdf", 1));
        Request request = new DocumentAnalysisRequestFactory().build(new AiPrecheckTaskService.WorkContext(task, materials));
        var combined = request.materialRequirements().stream()
                .filter(rule -> "MIGRATION_BOOK_OR_ADDRESS".equals(rule.ruleId())).findFirst().orElseThrow();
        var transfer = request.materialRequirements().stream()
                .filter(rule -> "MIGRATION_TRANSFER_PROOF".equals(rule.ruleId())).findFirst().orElseThrow();
        assertThat(combined.allOf()).isNull();
        assertThat(combined.anyOf()).extracting("materialType")
                .containsExactly("HOUSEHOLD_BOOK", "ADDRESS_PROOF");
        assertThat(transfer.ruleId()).isEqualTo("MIGRATION_TRANSFER_PROOF");
        assertThat(transfer.allOf()).extracting("materialType").containsExactly("MIGRATION_PROOF");
        assertThat(request.materials()).extracting("materialRef")
                .containsExactly("stg_0123456789abcdef0123456789abcdef", "stg_fedcba9876543210fedcba9876543210");
    }

    @Test
    void validSourceAndConfidenceAreAcceptedButForeignReferencesAndBadConfidenceFail() {
        Request request = request();
        Response valid = response("stg_0123456789abcdef0123456789abcdef", 0.7d,
                new Source("stg_0123456789abcdef0123456789abcdef", 1,
                        new BoundingBox(0.1d, 0.2d, 0.3d, 0.4d)));
        assertThat(validator.validate(request, valid).mode()).isEqualTo("MOCK");

        Response foreignSource = response("stg_0123456789abcdef0123456789abcdef", 0.7d,
                new Source("stg_fedcba9876543210fedcba9876543210", 1,
                        new BoundingBox(0.1d, 0.2d, 0.3d, 0.4d)));
        assertThatThrownBy(() -> validator.validate(request, foreignSource))
                .isInstanceOf(AnalysisFailure.class)
                .extracting("safeCode").isEqualTo("AI_INVALID_RESPONSE");

        Response invalidConfidence = response("stg_0123456789abcdef0123456789abcdef", 1.1d, null);
        assertThatThrownBy(() -> validator.validate(request, invalidConfidence))
                .isInstanceOf(AnalysisFailure.class)
                .extracting("safeCode").isEqualTo("AI_INVALID_RESPONSE");
    }

    private Request request() {
        return new Request("1.0", "req-test", "task-test", "trace-test", "MIGRATION_IN", "OUTSIDE_CITY",
                List.of(), List.of(new DocumentAnalysisContracts.MaterialInput(
                        "stg_0123456789abcdef0123456789abcdef", "image/jpeg", 123L, "IDENTITY_CARD_FRONT")));
    }

    private Response response(String sourceRef, double confidence, Source source) {
        return new Response("1.0", "req-test", "task-test", "trace-test", "NEEDS_REVIEW",
                List.of(new MaterialAnalysis("stg_0123456789abcdef0123456789abcdef", "IDENTITY_CARD_FRONT", 0.9d,
                        List.of(new Field("person.name", null, "NOT_FOUND", confidence, source)))),
                List.of(new Alert("MATERIAL_REVIEW_REQUIRED", "INFO", List.of("person.name"),
                        List.of("stg_0123456789abcdef0123456789abcdef"), "Synthetic validation result", null, List.of())),
                "MOCK", null, "MOCK_RULES", 1, "MOCK");
    }
}
