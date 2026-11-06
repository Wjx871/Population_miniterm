package com.wjx871.population.assistant;

import com.wjx871.population.assistant.PolicyPythonContracts.PythonCitation;
import com.wjx871.population.assistant.PolicyPythonContracts.RegisteredSourceMeta;
import com.wjx871.population.assistant.PolicyPythonContracts.Request;
import com.wjx871.population.assistant.PolicyPythonContracts.Response;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class PolicyPythonResponseValidator {
    private static final ZoneId APP_ZONE = ZoneId.of("Asia/Shanghai");
    private static final Set<String> ALLOWED_OUTCOMES = Set.of("ANSWERED", "INSUFFICIENT_EVIDENCE");
    private static final Set<String> ALLOWED_EVIDENCE_LEVELS = Set.of("SUFFICIENT", "PARTIAL", "INSUFFICIENT");
    private static final Set<String> ALLOWED_CATEGORIES = Set.of(
            "迁入", "迁出", "居住证", "家庭户", "注销", "审批", "人口查询", "人口", "常见问题", "系统操作", "业务办理"
    );

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern SAFE_VERSION_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");
    private static final Pattern INCOMPATIBLE_VERSION_PATTERN = Pattern.compile(
            "(?i)^(v0([._-].*)?|0(\\.0+)?([._-].*)?|incompatible.*|unsupported.*|invalid.*|unknown.*|draft-0.*)$"
    );
    private static final Pattern SAFE_SOURCE_TYPE_PATTERN = Pattern.compile("^[A-Za-z0-9_.-]{1,64}$");
    private static final Pattern SAFE_REGION_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{2,32}$");
    private static final Pattern CITATION_MARKER_PATTERN = Pattern.compile("\\[\\[([^\\[\\]]+)\\]\\]");

    private static final Pattern PHYSICAL_PATH_PATTERN = Pattern.compile(
            "(?i)([a-z]:\\\\|[a-z]:/|\\\\\\\\[a-z0-9_.-]+|/etc/|/var/|/usr/|/home/|/opt/|/proc/|/root/|/tmp/|file://|classpath:|\\.\\./|\\.\\.\\\\)"
    );
    private static final Pattern CREDENTIAL_OR_SECRET_PATTERN = Pattern.compile(
            "(?i)(bearer\\s+[a-z0-9._-]+|authorization\\s*:|api[_-]?key\\s*[:=]|secret\\s*[:=]|password\\s*[:=]|auth[_-]?token\\s*[:=]|sk-[a-z0-9]{10,}|eyJ[a-zA-Z0-9_-]{10,}\\.[a-zA-Z0-9_-]{10,})"
    );
    private static final Pattern UNSAFE_COMMAND_OR_ACTION_PATTERN = Pattern.compile(
            "(?i)(https?://|/internal/|\\b(?:personId|toHouseholdId|applicationId|userId|stageMaterialId)\\s*[:=]|\\bexecuteIn\\b|绕过审批|直接落户|自动审批|已为您(?:直接|自动)?(?:审批|办理|执行|修改|注销)|\\b(?:select|insert|update|delete|drop|alter|truncate)\\b.*\\b(?:from|into|table|set|where)\\b|<script|javascript:|cmd\\.exe|powershell|/bin/sh|\\$\\(|`)"
    );
    private static final Pattern DEFINITIVE_CLAIM_IN_INSUFFICIENT_PATTERN = Pattern.compile(
            "(?i)(\\[\\[[^\\[\\]]+\\]\\]|完全可以办理|立即办结|无需审批|直接通过|必定通过|肯定可以)"
    );

    public ValidatedPolicyResponse validate(Request request,
                                            Response response,
                                            Map<String, RegisteredSourceMeta> registeredSources,
                                            List<String> configuredSecrets) {
        if (request == null || response == null) {
            throw invalid("PYTHON_NULL_RESPONSE");
        }
        if (!PolicyPythonContracts.SCHEMA_VERSION_V1.equals(response.schemaVersion())) {
            throw invalid("PYTHON_SCHEMA_UNSUPPORTED");
        }
        if (response.requestId() == null || !request.requestId().equals(response.requestId())) {
            throw invalid("PYTHON_REQUEST_ID_MISMATCH");
        }
        if (response.traceId() == null || !request.traceId().equals(response.traceId())
                || !SAFE_ID_PATTERN.matcher(response.traceId()).matches()) {
            throw invalid("PYTHON_TRACE_ID_MISMATCH");
        }
        if (response.outcome() == null || !ALLOWED_OUTCOMES.contains(response.outcome())) {
            throw invalid("PYTHON_INVALID_OUTCOME");
        }
        if (response.elapsedMs() == null || response.elapsedMs() < 0) {
            throw invalid("PYTHON_INVALID_ELAPSED_MS");
        }
        if (response.evidenceLevel() != null && !ALLOWED_EVIDENCE_LEVELS.contains(response.evidenceLevel())) {
            throw invalid("PYTHON_INVALID_EVIDENCE_LEVEL");
        }

        validateOptionalVersion(response.knowledgeBaseVersion(), "PYTHON_INVALID_KB_VERSION", configuredSecrets);
        validateOptionalVersion(response.modelVersion(), "PYTHON_INVALID_MODEL_VERSION", configuredSecrets);
        validateOptionalVersion(response.retrievalVersion(), "PYTHON_INVALID_RETRIEVAL_VERSION", configuredSecrets);
        validateOptionalVersion(response.rerankerVersion(), "PYTHON_INVALID_RERANKER_VERSION", configuredSecrets);

        if ("INSUFFICIENT_EVIDENCE".equals(response.outcome())) {
            if (response.citationIdsUsed() != null && !response.citationIdsUsed().isEmpty()) {
                throw invalid("PYTHON_INSUFFICIENT_WITH_CITATIONS_USED");
            }
            if (response.citations() != null && !response.citations().isEmpty()) {
                throw invalid("PYTHON_INSUFFICIENT_WITH_CITATIONS");
            }
            if (response.answer() != null && !response.answer().isBlank()) {
                validateSafeText(response.answer(), "PYTHON_UNSAFE_ANSWER", 2000, configuredSecrets);
                if (DEFINITIVE_CLAIM_IN_INSUFFICIENT_PATTERN.matcher(response.answer()).find()) {
                    throw invalid("PYTHON_CONTRADICTORY_INSUFFICIENT_ANSWER");
                }
            }
            return new ValidatedPolicyResponse(
                    response,
                    null,
                    List.of(),
                    List.of(),
                    false,
                    "INSUFFICIENT"
            );
        }

        // outcome == ANSWERED
        if ("INSUFFICIENT".equals(response.evidenceLevel())) {
            throw invalid("PYTHON_CONTRADICTORY_EVIDENCE_LEVEL");
        }
        String rawAnswer = response.answer();
        if (rawAnswer == null || rawAnswer.isBlank()) {
            throw invalid("PYTHON_EMPTY_ANSWER");
        }
        validateSafeText(rawAnswer, "PYTHON_UNSAFE_ANSWER", 2000, configuredSecrets);
        if (UNSAFE_COMMAND_OR_ACTION_PATTERN.matcher(rawAnswer).find()) {
            throw invalid("PYTHON_UNSAFE_ANSWER_INSTRUCTION");
        }

        List<PythonCitation> pyCitations = response.citations();
        List<String> idsUsed = response.citationIdsUsed();
        if (pyCitations == null || pyCitations.isEmpty() || pyCitations.size() > 8) {
            throw invalid("PYTHON_EMPTY_OR_EXCESSIVE_CITATIONS");
        }
        if (idsUsed == null || idsUsed.isEmpty() || idsUsed.size() > 8) {
            throw invalid("PYTHON_EMPTY_OR_EXCESSIVE_CITATION_IDS_USED");
        }

        LocalDate today = LocalDate.now(APP_ZONE);
        Map<String, PythonCitation> bySourceId = new LinkedHashMap<>();
        Map<String, Boolean> sourceVerifiedMap = new LinkedHashMap<>();

        for (PythonCitation c : pyCitations) {
            if (c == null) {
                throw invalid("PYTHON_NULL_CITATION");
            }
            String sourceId = trimOrNull(c.sourceId());
            if (sourceId == null || !SAFE_ID_PATTERN.matcher(sourceId).matches() || sourceId.contains("..")) {
                throw invalid("PYTHON_INVALID_SOURCE_ID");
            }
            if (bySourceId.containsKey(sourceId)) {
                throw invalid("PYTHON_DUPLICATE_SOURCE_ID");
            }

            String title = requireSafeField(c.title(), 120, "PYTHON_INVALID_CITATION_TITLE", configuredSecrets);
            String section = requireSafeField(c.section(), 120, "PYTHON_INVALID_CITATION_SECTION", configuredSecrets);
            String category = requireSafeField(c.category(), 64, "PYTHON_INVALID_CITATION_CATEGORY", configuredSecrets);
            if (!ALLOWED_CATEGORIES.contains(category)) {
                throw invalid("PYTHON_UNSUPPORTED_CITATION_CATEGORY");
            }
            String sourceType = requireSafeField(c.sourceType(), 64, "PYTHON_INVALID_CITATION_SOURCE_TYPE", configuredSecrets);
            if (!SAFE_SOURCE_TYPE_PATTERN.matcher(sourceType).matches()) {
                throw invalid("PYTHON_INVALID_CITATION_SOURCE_TYPE");
            }
            String excerpt = requireSafeField(c.excerpt(), 500, "PYTHON_INVALID_CITATION_EXCERPT", configuredSecrets);
            if (UNSAFE_COMMAND_OR_ACTION_PATTERN.matcher(excerpt).find()) {
                throw invalid("PYTHON_UNSAFE_CITATION_EXCERPT");
            }
            String sourceVersion = trimOrNull(c.sourceVersion());
            if (sourceVersion == null || !SAFE_VERSION_PATTERN.matcher(sourceVersion).matches()
                    || INCOMPATIBLE_VERSION_PATTERN.matcher(sourceVersion).matches()) {
                throw invalid("PYTHON_INVALID_SOURCE_VERSION");
            }
            validateSafeText(sourceVersion, "PYTHON_INVALID_SOURCE_VERSION", 64, configuredSecrets);

            validateEffectiveDates(c.effectiveFrom(), c.effectiveTo(), today);
            validateRetrievedAt(c.retrievedAt());
            validateRegionCode(request.regionCode(), c.regionCode());

            boolean registryVerified = verifyAgainstTrustedRegistry(
                    sourceId, title, section, category, sourceType, sourceVersion, registeredSources
            );
            bySourceId.put(sourceId, new PythonCitation(
                    sourceId, title, section, category, sourceType, excerpt,
                    sourceVersion, trimOrNull(c.effectiveFrom()), trimOrNull(c.effectiveTo()),
                    trimOrNull(c.regionCode()), trimOrNull(c.retrievedAt())
            ));
            sourceVerifiedMap.put(sourceId, registryVerified);
        }

        Set<String> normalizedIdsUsed = new LinkedHashSet<>();
        for (String id : idsUsed) {
            String trimmed = trimOrNull(id);
            if (trimmed == null || !bySourceId.containsKey(trimmed)) {
                throw invalid("PYTHON_UNKNOWN_CITATION_ID_USED");
            }
            normalizedIdsUsed.add(trimmed);
        }

        Set<String> markersInAnswer = extractAndValidateAnswerMarkers(rawAnswer);
        if (markersInAnswer.isEmpty()) {
            throw invalid("PYTHON_ANSWER_MISSING_CITATION_MARKERS");
        }
        if (!normalizedIdsUsed.containsAll(markersInAnswer) || !markersInAnswer.containsAll(normalizedIdsUsed)) {
            throw invalid("PYTHON_ANSWER_CITATION_MISMATCH");
        }

        List<PolicyAssistantService.Citation> mappedCitations = new ArrayList<>();
        Map<String, Integer> indexBySourceId = new LinkedHashMap<>();
        boolean allVerified = true;
        int idx = 1;
        for (Map.Entry<String, PythonCitation> entry : bySourceId.entrySet()) {
            String sourceId = entry.getKey();
            PythonCitation pc = entry.getValue();
            if (!Boolean.TRUE.equals(sourceVerifiedMap.get(sourceId))) {
                allVerified = false;
            }
            mappedCitations.add(new PolicyAssistantService.Citation(
                    idx,
                    pc.title(),
                    pc.section(),
                    pc.category(),
                    pc.sourceType(),
                    pc.sourceVersion(),
                    pc.sourceId(),
                    pc.excerpt(),
                    pc.effectiveFrom(),
                    pc.effectiveTo(),
                    pc.regionCode(),
                    pc.retrievedAt()
            ));
            indexBySourceId.put(sourceId, idx);
            idx++;
        }

        for (String usedId : normalizedIdsUsed) {
            if (!Boolean.TRUE.equals(sourceVerifiedMap.get(usedId))) {
                allVerified = false;
            }
        }

        String renumberedAnswer = rewriteCitationMarkers(rawAnswer, indexBySourceId);
        Set<String> distinctSections = new LinkedHashSet<>();
        for (String usedId : normalizedIdsUsed) {
            distinctSections.add(bySourceId.get(usedId).section());
        }
        String computedEvidenceStatus = computeConservativeEvidenceStatus(
                response.evidenceLevel(), allVerified, distinctSections.size()
        );

        return new ValidatedPolicyResponse(
                response,
                renumberedAnswer,
                List.copyOf(mappedCitations),
                List.copyOf(normalizedIdsUsed),
                allVerified,
                computedEvidenceStatus
        );
    }

    public static Set<String> extractAndValidateAnswerMarkers(String rawAnswer) {
        if (rawAnswer == null || rawAnswer.isBlank()) {
            throw invalid("PYTHON_EMPTY_ANSWER");
        }
        Set<String> markers = new LinkedHashSet<>();
        Matcher matcher = CITATION_MARKER_PATTERN.matcher(rawAnswer);
        StringBuffer stripped = new StringBuffer();
        while (matcher.find()) {
            String sourceId = matcher.group(1) == null ? "" : matcher.group(1).trim();
            if (sourceId.isEmpty() || !SAFE_ID_PATTERN.matcher(sourceId).matches() || sourceId.contains("..")) {
                throw invalid("PYTHON_INVALID_ANSWER_MARKER");
            }
            markers.add(sourceId);
            matcher.appendReplacement(stripped, "");
        }
        matcher.appendTail(stripped);
        String remaining = stripped.toString();
        if (remaining.contains("[[") || remaining.contains("]]")) {
            throw invalid("PYTHON_MALFORMED_ANSWER_MARKER");
        }
        return markers;
    }

    public static String rewriteCitationMarkers(String rawAnswer, Map<String, Integer> indexBySourceId) {
        Set<String> markers = extractAndValidateAnswerMarkers(rawAnswer);
        if (markers.isEmpty() || indexBySourceId == null || !indexBySourceId.keySet().containsAll(markers)) {
            throw invalid("PYTHON_ANSWER_CITATION_UNMAPPED");
        }
        Matcher matcher = CITATION_MARKER_PATTERN.matcher(rawAnswer);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String sourceId = matcher.group(1).trim();
            Integer index = indexBySourceId.get(sourceId);
            if (index == null || index < 1) {
                throw invalid("PYTHON_ANSWER_CITATION_UNMAPPED");
            }
            matcher.appendReplacement(sb, "[" + index + "]");
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static String computeConservativeEvidenceStatus(String pythonEvidenceLevel,
                                                           boolean allSourcesRegistryVerified,
                                                           int distinctSectionCount) {
        if ("INSUFFICIENT".equals(pythonEvidenceLevel)) {
            return "INSUFFICIENT";
        }
        if (!allSourcesRegistryVerified) {
            return "PARTIAL";
        }
        if ("PARTIAL".equals(pythonEvidenceLevel)) {
            return "PARTIAL";
        }
        if ("SUFFICIENT".equals(pythonEvidenceLevel)) {
            return "SUFFICIENT";
        }
        return distinctSectionCount >= 2 ? "SUFFICIENT" : "PARTIAL";
    }

    private boolean verifyAgainstTrustedRegistry(String sourceId,
                                                 String title,
                                                 String section,
                                                 String category,
                                                 String sourceType,
                                                 String sourceVersion,
                                                 Map<String, RegisteredSourceMeta> registeredSources) {
        if (registeredSources == null || registeredSources.isEmpty()) {
            return false;
        }
        RegisteredSourceMeta matched = registeredSources.get(sourceId);
        if (matched == null) {
            for (RegisteredSourceMeta candidate : registeredSources.values()) {
                String lp = candidate.logicalPath();
                if (sourceId.startsWith(lp + "-") || sourceId.startsWith(lp + ".")) {
                    matched = candidate;
                    break;
                }
            }
        }
        if (matched == null) {
            return false;
        }
        if (!matched.title().equals(title)
                || !matched.category().equals(category)
                || !matched.sourceType().equals(sourceType)
                || !matched.version().equals(sourceVersion)
                || !matched.sections().contains(section)) {
            throw invalid("PYTHON_CITATION_REGISTRY_MISMATCH");
        }
        return true;
    }

    private void validateEffectiveDates(String effectiveFromRaw, String effectiveToRaw, LocalDate today) {
        LocalDate from = parseFlexibleDate(effectiveFromRaw, "PYTHON_INVALID_EFFECTIVE_FROM");
        LocalDate to = parseFlexibleDate(effectiveToRaw, "PYTHON_INVALID_EFFECTIVE_TO");
        if (from != null && to != null && to.isBefore(from)) {
            throw invalid("PYTHON_INVALID_EFFECTIVE_RANGE");
        }
        if (from != null && from.isAfter(today)) {
            throw invalid("PYTHON_CITATION_NOT_YET_EFFECTIVE");
        }
        if (to != null && to.isBefore(today)) {
            throw invalid("PYTHON_CITATION_EXPIRED");
        }
    }

    private LocalDate parseFlexibleDate(String raw, String errorCode) {
        String trimmed = trimOrNull(raw);
        if (trimmed == null) {
            return null;
        }
        try {
            if (trimmed.length() == 10) {
                return LocalDate.parse(trimmed);
            }
            return OffsetDateTime.parse(trimmed).atZoneSameInstant(APP_ZONE).toLocalDate();
        } catch (Exception e1) {
            try {
                return Instant.parse(trimmed).atZone(APP_ZONE).toLocalDate();
            } catch (Exception e2) {
                throw invalid(errorCode);
            }
        }
    }

    private void validateRetrievedAt(String retrievedAtRaw) {
        String trimmed = trimOrNull(retrievedAtRaw);
        if (trimmed == null) {
            return;
        }
        try {
            OffsetDateTime.parse(trimmed);
        } catch (Exception e1) {
            try {
                Instant.parse(trimmed);
            } catch (Exception e2) {
                try {
                    LocalDateTime.parse(trimmed);
                } catch (Exception e3) {
                    throw invalid("PYTHON_INVALID_RETRIEVED_AT");
                }
            }
        }
    }

    private void validateRegionCode(String requestRegionCode, String citationRegionCode) {
        String cRegion = trimOrNull(citationRegionCode);
        if (cRegion == null) {
            return;
        }
        if (!SAFE_REGION_PATTERN.matcher(cRegion).matches()) {
            throw invalid("PYTHON_INVALID_CITATION_REGION");
        }
        String upper = cRegion.toUpperCase(Locale.ROOT);
        if ("ALL".equals(upper) || "NATIONAL".equals(upper) || "DEFAULT".equals(upper)) {
            return;
        }
        String reqRegion = trimOrNull(requestRegionCode);
        if (reqRegion == null || !reqRegion.equals(cRegion)) {
            throw invalid("PYTHON_CITATION_REGION_MISMATCH");
        }
    }

    private void validateOptionalVersion(String value, String errorCode, List<String> configuredSecrets) {
        String trimmed = trimOrNull(value);
        if (trimmed == null) {
            return;
        }
        if (!SAFE_VERSION_PATTERN.matcher(trimmed).matches()
                || INCOMPATIBLE_VERSION_PATTERN.matcher(trimmed).matches()) {
            throw invalid(errorCode);
        }
        validateSafeText(trimmed, errorCode, 64, configuredSecrets);
    }

    private String requireSafeField(String raw, int maxLen, String errorCode, List<String> configuredSecrets) {
        String trimmed = trimOrNull(raw);
        if (trimmed == null) {
            throw invalid(errorCode);
        }
        validateSafeText(trimmed, errorCode, maxLen, configuredSecrets);
        return trimmed;
    }

    private void validateSafeText(String value, String errorCode, int maxLen, List<String> configuredSecrets) {
        if (value == null || value.length() > maxLen) {
            throw invalid(errorCode);
        }
        if (PHYSICAL_PATH_PATTERN.matcher(value).find() || CREDENTIAL_OR_SECRET_PATTERN.matcher(value).find()) {
            throw invalid(errorCode);
        }
        if (configuredSecrets != null) {
            for (String secret : configuredSecrets) {
                if (secret != null && secret.trim().length() >= 4 && value.contains(secret.trim())) {
                    throw invalid(errorCode);
                }
            }
        }
    }

    private static String trimOrNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static PolicyPythonClientException invalid(String code) {
        return new PolicyPythonClientException(code, false);
    }

    public record ValidatedPolicyResponse(
            Response response,
            String renumberedAnswer,
            List<PolicyAssistantService.Citation> citations,
            List<String> citationIdsUsed,
            boolean allSourcesRegistryVerified,
            String computedEvidenceStatus
    ) {
    }
}
