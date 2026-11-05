package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.ai.precheck.AiResolutionPreviewRequest.Correction;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.migration.MigrationType;
import com.wjx871.population.person.IdCardValidator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Shared normalization and request digest implementation for preview and confirmation. */
@Component
@RequiredArgsConstructor
public class AiResolutionInputCanonicalizer {
    private static final String REQUEST_VERSION = "1.0";
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Set<String> CORRECTABLE_FIELDS = Set.of(
            "person.name", "person.idCard", "household.householdNo", "household.address");

    private final ObjectMapper objectMapper;
    private final IdCardValidator idCards;

    public Canonicalized canonicalize(AiPrecheckTaskService.TaskRow task, MigrationType migrationType,
                                      String rawRegion, String rawBatch, List<Correction> corrections) {
        if (task == null || migrationType == null) throw bad("INVALID_RESOLUTION_INPUT");
        String region = normalizeRequired(rawRegion, 20, "INVALID_TO_REGION_CODE");
        String batch = normalizeOptional(rawBatch, 40, "INVALID_TRANSFER_BATCH_NO");
        Map<String, String> normalizedCorrections = normalizeCorrections(corrections);
        ExtractedFields extracted = extractFields(task.resultJson());
        ResolvedInputs inputs = resolveInputs(extracted, normalizedCorrections);
        String canonical = inputCanonical(task, migrationType, region, batch, inputs, extracted.conflictingFields());
        return new Canonicalized(region, batch, Collections.unmodifiableMap(new LinkedHashMap<>(normalizedCorrections)), inputs,
                extracted.values(), extracted.conflictingFields(), canonical, sha256(canonical));
    }

    private Map<String, String> normalizeCorrections(List<Correction> corrections) {
        Map<String, String> values = new LinkedHashMap<>();
        if (corrections == null) return values;
        for (Correction correction : corrections) {
            if (correction == null || correction.fieldCode() == null
                    || !CORRECTABLE_FIELDS.contains(correction.fieldCode())) {
                throw bad("UNSUPPORTED_CORRECTION_FIELD");
            }
            if (values.containsKey(correction.fieldCode())) throw bad("DUPLICATE_CORRECTION_FIELD");
            values.put(correction.fieldCode(), normalizeField(correction.fieldCode(), correction.value(), true));
        }
        return values;
    }

    private ExtractedFields extractFields(String resultJson) {
        Map<String, Set<String>> found = new LinkedHashMap<>();
        if (resultJson != null) {
            try {
                JsonNode root = objectMapper.readTree(resultJson);
                JsonNode materials = root.path("materials");
                if (materials.isArray()) {
                    for (JsonNode material : materials) {
                        JsonNode fields = material.path("fields");
                        if (!fields.isArray()) continue;
                        for (JsonNode field : fields) {
                            String code = field.path("fieldCode").asText(null);
                            if (!CORRECTABLE_FIELDS.contains(code)
                                    || !"PRESENT".equals(field.path("fieldStatus").asText())) continue;
                            JsonNode value = field.get("value");
                            if (value == null || !value.isValueNode() || value.isNull()) continue;
                            String normalized = normalizeField(code, value.asText(), false);
                            if (normalized != null) found.computeIfAbsent(code, ignored -> new TreeSet<>()).add(normalized);
                        }
                    }
                }
            } catch (Exception exception) {
                // Persisted model output is untrusted. A malformed result remains unconfirmable.
                return new ExtractedFields(Map.of(), Set.of("person.name", "person.idCard", "household.householdNo"));
            }
        }
        Map<String, String> one = new LinkedHashMap<>();
        Set<String> conflicts = new LinkedHashSet<>();
        found.forEach((code, values) -> {
            if (values.size() > 1) conflicts.add(code);
            else if (!values.isEmpty()) one.put(code, values.iterator().next());
        });
        return new ExtractedFields(Map.copyOf(one), Set.copyOf(conflicts));
    }

    private ResolvedInputs resolveInputs(ExtractedFields extracted, Map<String, String> corrections) {
        Map<String, String> values = new LinkedHashMap<>(extracted.values());
        values.putAll(corrections);
        List<AiResolutionPreviewView.Warning> warnings = new ArrayList<>();
        for (String conflictField : extracted.conflictingFields()) {
            if (!corrections.containsKey(conflictField)) warnings.add(warning("OCR_FIELD_CONFLICT", conflictField));
        }
        String name = values.get("person.name");
        String identity = values.get("person.idCard");
        String householdNo = values.get("household.householdNo");
        String address = values.get("household.address");
        if (name == null) warnings.add(warning("PERSON_NAME_REQUIRED", "person.name"));
        if (identity == null) warnings.add(warning("PERSON_IDENTITY_REQUIRED", "person.idCard"));
        if (householdNo == null && address == null) {
            warnings.add(warning("TARGET_HOUSEHOLD_IDENTIFIER_REQUIRED", "household.householdNo", "household.address"));
        }
        return new ResolvedInputs(name, identity, householdNo, address, deduplicateWarnings(warnings));
    }

    private String normalizeField(String code, String value, boolean correction) {
        if (value == null) return null;
        String normalized = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC).trim();
        if (normalized.isEmpty()) return null;
        if (CONTROL.matcher(normalized).find()) {
            if (correction) throw bad("INVALID_CORRECTION_VALUE");
            return null;
        }
        normalized = normalized.replaceAll("\\s+", " ");
        switch (code) {
            case "person.name" -> {
                if (normalized.length() > 50) return invalidValue(correction);
                return normalized;
            }
            case "person.idCard" -> {
                String identity = normalized.toUpperCase(Locale.ROOT).replace(" ", "");
                try {
                    return idCards.parse(identity).normalized();
                } catch (BusinessException exception) {
                    return invalidValue(correction);
                }
            }
            case "household.householdNo" -> {
                if (normalized.length() > 30) return invalidValue(correction);
                return normalized;
            }
            case "household.address" -> {
                if (normalized.length() > 255 || normalized.indexOf('%') >= 0 || normalized.indexOf('_') >= 0) {
                    return invalidValue(correction);
                }
                return normalized;
            }
            default -> {
                return invalidValue(correction);
            }
        }
    }

    private String invalidValue(boolean correction) {
        if (correction) throw bad("INVALID_CORRECTION_VALUE");
        return null;
    }

    private String normalizeRequired(String value, int max, String code) {
        String normalized = value == null ? null : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC).trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > max || CONTROL.matcher(normalized).find()) {
            throw bad(code);
        }
        return normalized;
    }

    private String normalizeOptional(String value, int max, String code) {
        if (value == null || value.isBlank()) return null;
        return normalizeRequired(value, max, code);
    }

    private String inputCanonical(AiPrecheckTaskService.TaskRow task, MigrationType migrationType,
                                  String region, String batch, ResolvedInputs inputs, Set<String> conflicts) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("schema", REQUEST_VERSION);
        canonical.put("taskId", task.taskId());
        canonical.put("taskVersion", task.version());
        canonical.put("migrationType", migrationType.name());
        canonical.put("toRegionCode", region);
        canonical.put("transferBatchNo", batch);
        canonical.put("person.name", inputs.personName());
        canonical.put("person.idCard", inputs.personIdCard());
        canonical.put("household.householdNo", inputs.householdNo());
        canonical.put("household.address", inputs.householdAddress());
        canonical.put("ocrResultSha256", sha256(task.resultJson() == null ? "" : task.resultJson()));
        canonical.put("conflicts", conflicts.stream().sorted().toList());
        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to canonicalize resolution input");
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private List<AiResolutionPreviewView.Warning> deduplicateWarnings(List<AiResolutionPreviewView.Warning> warnings) {
        Map<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        for (AiResolutionPreviewView.Warning warning : warnings) {
            merged.computeIfAbsent(warning.code(), ignored -> new LinkedHashSet<>()).addAll(warning.fieldCodes());
        }
        return merged.entrySet().stream().map(entry -> new AiResolutionPreviewView.Warning(entry.getKey(),
                List.copyOf(entry.getValue()))).toList();
    }

    private AiResolutionPreviewView.Warning warning(String code, String... fields) {
        return new AiResolutionPreviewView.Warning(code, List.of(fields));
    }

    private BusinessException bad(String code) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code);
    }

    private record ExtractedFields(Map<String, String> values, Set<String> conflictingFields) { }

    public record ResolvedInputs(String personName, String personIdCard, String householdNo, String householdAddress,
                                 List<AiResolutionPreviewView.Warning> warnings) { }

    public record Canonicalized(String toRegionCode, String transferBatchNo, Map<String, String> corrections,
                                ResolvedInputs inputs, Map<String, String> extractedValues,
                                Set<String> conflicts, String canonicalInput, String digest) { }
}
