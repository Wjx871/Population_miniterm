package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.ai.precheck.AiConfirmRequest.ConfirmedField;
import com.wjx871.population.ai.precheck.AiConfirmRequest.MaterialBinding;
import com.wjx871.population.application.ApplicationService;
import com.wjx871.population.application.BusinessApplication;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.material.ApplicationMaterial;
import com.wjx871.population.material.ApplicationMaterialMapper;
import com.wjx871.population.material.BoundStageMaterialStorage;
import com.wjx871.population.material.MaterialView;
import com.wjx871.population.material.MaterialVerifyStatus;
import com.wjx871.population.migration.MigrationCreatedView;
import com.wjx871.population.migration.MigrationInRequest;
import com.wjx871.population.migration.MigrationService;
import com.wjx871.population.migration.MigrationType;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.CurrentUserContext;
import com.wjx871.population.s2.trace.S2TraceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Log4j2
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiConfirmService {
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Pattern PERSON_CANDIDATE_TOKEN = Pattern.compile("pc_[A-Za-z0-9_-]{24,64}");
    private static final Pattern HOUSEHOLD_CANDIDATE_TOKEN = Pattern.compile("hc_[A-Za-z0-9_-]{24,64}");
    private static final Pattern STAGE_MATERIAL_ID = Pattern.compile("stg_[A-Fa-f0-9]{32}");
    private static final Set<String> CONFIRM_FIELD_CODES = Set.of(
            "person.name", "person.idCard", "household.householdNo", "household.address",
            "migration.migrationType", "migration.toRegionCode", "migration.transferBatchNo",
            "migration.fromRegionCode", "migration.fromAddress", "migration.inDate",
            "migration.reason", "migration.title", "migration.remark");
    private static final Set<String> RESOLUTION_FIELD_CODES = Set.of(
            "person.name", "person.idCard", "household.householdNo", "household.address");
    private static final Set<String> MATERIAL_TYPES = Set.of(
            "IDENTITY_PROOF", "HOUSEHOLD_BOOK", "ADDRESS_PROOF", "MIGRATION_PROOF", "APPLICATION_FORM");
    private static final RowMapper<StageMaterialRow> STAGE_MATERIAL_MAPPER = (rs, rowNum) -> new StageMaterialRow(
            rs.getString("stage_material_id"), rs.getLong("owner_user_id"), rs.getString("business_type"),
            rs.getString("document_role_hint"), rs.getString("original_filename"),
            rs.getString("stored_object_key"), rs.getString("content_type"), rs.getLong("file_size"),
            rs.getString("sha256"), rs.getString("status"), rs.getLong("version"),
            rs.getTimestamp("expires_at") == null ? null : rs.getTimestamp("expires_at").toLocalDateTime(),
            rs.getString("active_task_id"), rs.getString("task_role_hint"), rs.getString("task_original_filename"),
            rs.getString("task_content_type"), rs.getLong("task_file_size"), rs.getString("task_sha256"));

    private final JdbcTemplate jdbc;
    private final AiPrecheckTaskService tasks;
    private final AiResolutionService resolutions;
    private final AiResolutionInputCanonicalizer inputCanonicalizer;
    private final MigrationService migrations;
    private final ApplicationService applications;
    private final ApplicationMaterialMapper materialMapper;
    private final BoundStageMaterialStorage stageStorage;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    @Transactional
    public ConfirmResult confirm(AuthenticatedUser user, String taskId, AiConfirmRequest request,
                                 HttpServletRequest httpRequest, String traceId) {
        requireConfirmPermissions(user);
        requireRequestShape(taskId, request);
        Map<String, String> fields = confirmedFields(request);
        IdempotencyContext idempotency = idempotencyDigest(taskId, request, fields);

        AiPrecheckTaskService.TaskRow task = tasks.lockOwnedForResolution(user.userId(), taskId);
        Receipt receipt = findReceiptForUpdate(taskId);
        if (receipt != null) return replayOrReject(user, receipt, idempotency);

        if (!"DOCUMENT_PRECHECK".equals(task.taskType()) || !"MIGRATION_IN".equals(task.businessType())) {
            throw notFound();
        }
        if (!"SUCCEEDED".equals(task.status())) throw conflict("TASK_NOT_READY_FOR_CONFIRMATION");
        if (task.version() != request.taskVersion()) throw conflict("TASK_VERSION_MISMATCH");
        if (request.acknowledgedAlerts() != null && !request.acknowledgedAlerts().isEmpty()) {
            throw conflict("RESOLUTION_NOT_CONFIRMABLE");
        }

        ConfirmedMigration confirmed = confirmedMigration(fields);
        AiConfirmRequest.SelectedCandidates candidates = request.selectedCandidates();
        AiResolutionService.ResolutionSelection selection = resolutions.validateForConfirmation(taskId,
                request.taskVersion(), request.resolutionToken(), candidates.person(), candidates.targetHousehold());
        if (!selection.migrationType().equals(confirmed.migrationType().name())
                || !selection.toRegionCode().equals(confirmed.toRegionCode())
                || !java.util.Objects.equals(selection.transferBatchNo(), confirmed.transferBatchNo())) {
            throw conflict("RESOLUTION_INPUT_CHANGED");
        }

        List<AiResolutionPreviewRequest.Correction> resolutionCorrections = fields.entrySet().stream()
                .filter(entry -> RESOLUTION_FIELD_CODES.contains(entry.getKey()))
                .map(entry -> new AiResolutionPreviewRequest.Correction(entry.getKey(), entry.getValue())).toList();
        AiResolutionInputCanonicalizer.Canonicalized canonicalized = canonicalizeForConfirmation(task,
                confirmed, resolutionCorrections);
        if (!selection.requestDigest().equals(canonicalized.digest())) throw conflict("RESOLUTION_INPUT_CHANGED");

        List<StageMaterialRow> stageRows = lockAndValidateMaterials(user, task, request.materialBindings());
        TypeResolution types = resolveMaterialTypes(stageRows, request.materialBindings(), confirmed.migrationType(),
                request.householdEvidenceType());
        MigrationInRequest migrationRequest = new MigrationInRequest(selection.personId(), confirmed.migrationType(),
                confirmed.fromRegionCode(), confirmed.fromAddress(), selection.toRegionCode(),
                selection.targetHouseholdId(), confirmed.inDate(), confirmed.reason(), confirmed.title(),
                confirmed.remark(), selection.transferBatchNo(), null);
        validateMigrationRequest(migrationRequest);

        MigrationCreatedView created = migrations.createIn(migrationRequest, httpRequest);
        bindMaterials(user, task, created, stageRows, types, traceId);
        List<String> warnings = materialWarnings(types, confirmed.migrationType());
        writeAudit(taskId, user.userId(), traceId, "AI_MIGRATION_DRAFT_CONFIRMED", "SUCCEEDED", "DRAFT", null);
        resolutions.markConsumed(request.resolutionToken());
        insertReceipt(user.userId(), taskId, request.idempotencyKey(), idempotency.digest(),
                created.applicationId(), created.migrationId(), warnings);

        BusinessApplication application = applications.require(created.applicationId());
        return new ConfirmResult(view(taskId, application, created.migrationId(),
                materialMapper.selectByApplicationId(created.applicationId()).stream().map(MaterialView::from).toList(),
                warnings, false), false);
    }

    private void requireConfirmPermissions(AuthenticatedUser user) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        if (user.permissions() == null || !user.permissions().contains("ai:precheck:confirm")
                || !user.permissions().contains("migration:in:create")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "当前账号无迁入确认权限");
        }
        AuthenticatedUser current = CurrentUserContext.requireUser();
        if (!user.userId().equals(current.userId())) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
    }

    private void requireRequestShape(String taskId, AiConfirmRequest request) {
        if (taskId == null || !taskId.matches("task_[A-Fa-f0-9]{32}")) throw notFound();
        if (request == null || !"1.0".equals(request.requestSchemaVersion())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_SCHEMA_VERSION");
        }
        if (request.selectedCandidates() == null || blank(request.selectedCandidates().person())
                || blank(request.selectedCandidates().targetHousehold())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "CANDIDATE_SELECTION_REQUIRED");
        }
        if (!PERSON_CANDIDATE_TOKEN.matcher(request.selectedCandidates().person()).matches()
                || !HOUSEHOLD_CANDIDATE_TOKEN.matcher(request.selectedCandidates().targetHousehold()).matches()) {
            throw unprocessable("INVALID_CANDIDATE_SELECTION");
        }
        if (request.resolutionToken() == null || request.resolutionToken().length() > 80) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_RESOLUTION_TOKEN");
        }
        if (request.idempotencyKey() == null || !request.idempotencyKey().matches("[A-Za-z0-9._-]{1,128}")) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_IDEMPOTENCY_KEY");
        }
        List<MaterialBinding> bindings = request.materialBindings();
        if (bindings != null) {
            if (bindings.size() > 10) throw unprocessable("INVALID_MATERIAL_BINDINGS");
            Set<String> stageIds = new HashSet<>();
            for (MaterialBinding binding : bindings) {
                if (binding == null || blank(binding.stageMaterialId())
                        || !STAGE_MATERIAL_ID.matcher(binding.stageMaterialId()).matches()
                        || binding.materialType() != null && binding.materialType().length() > 50) {
                    throw unprocessable("INVALID_MATERIAL_BINDING");
                }
                if (!stageIds.add(binding.stageMaterialId())) throw unprocessable("DUPLICATE_STAGE_MATERIAL_ID");
            }
        }
    }

    private Map<String, String> confirmedFields(AiConfirmRequest request) {
        Map<String, String> values = new LinkedHashMap<>();
        if (request.confirmedFields() == null) return values;
        for (ConfirmedField field : request.confirmedFields()) {
            if (field == null || field.fieldCode() == null || !CONFIRM_FIELD_CODES.contains(field.fieldCode())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_CONFIRMED_FIELD");
            }
            if (values.containsKey(field.fieldCode())) {
                throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "DUPLICATE_CONFIRMED_FIELD");
            }
            values.put(field.fieldCode(), normalizeField(field.fieldCode(), field.value()));
        }
        return values;
    }

    private ConfirmedMigration confirmedMigration(Map<String, String> fields) {
        String typeValue = required(fields, "migration.migrationType");
        String region = required(fields, "migration.toRegionCode");
        MigrationType migrationType;
        try {
            migrationType = MigrationType.valueOf(typeValue);
        } catch (IllegalArgumentException exception) {
            throw unprocessable("INVALID_MIGRATION_TYPE");
        }
        String batch = optional(fields.get("migration.transferBatchNo"), 40, "migration.transferBatchNo");
        if (migrationType == MigrationType.IN_CITY_CROSS_DISTRICT && batch == null) {
            throw unprocessable("TRANSFER_BATCH_REQUIRED");
        }
        String fromRegion = optional(fields.get("migration.fromRegionCode"), 20, "migration.fromRegionCode");
        String fromAddress = required(fields, "migration.fromAddress");
        String date = required(fields, "migration.inDate");
        LocalDate inDate;
        try {
            inDate = LocalDate.parse(date);
        } catch (DateTimeParseException exception) {
            throw unprocessable("INVALID_IN_DATE");
        }
        String reason = required(fields, "migration.reason");
        String title = required(fields, "migration.title");
        String remark = optional(fields.get("migration.remark"), 500, "migration.remark");
        return new ConfirmedMigration(migrationType, region, batch, fromRegion, fromAddress, inDate,
                reason, title, remark);
    }

    private void validateMigrationRequest(MigrationInRequest request) {
        Set<ConstraintViolation<MigrationInRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) throw unprocessable("INVALID_MIGRATION_FIELDS");
    }

    private AiResolutionInputCanonicalizer.Canonicalized canonicalizeForConfirmation(
            AiPrecheckTaskService.TaskRow task, ConfirmedMigration confirmed,
            List<AiResolutionPreviewRequest.Correction> corrections) {
        try {
            return inputCanonicalizer.canonicalize(task, confirmed.migrationType(), confirmed.toRegionCode(),
                    confirmed.transferBatchNo(), corrections);
        } catch (BusinessException exception) {
            if (exception.getStatus() == HttpStatus.BAD_REQUEST) throw unprocessable(exception.getMessage());
            throw exception;
        }
    }

    private List<StageMaterialRow> lockAndValidateMaterials(AuthenticatedUser user,
                                                             AiPrecheckTaskService.TaskRow task,
                                                             List<MaterialBinding> bindings) {
        List<MaterialBinding> requested = bindings == null ? List.of() : bindings;
        if (requested.stream().map(MaterialBinding::stageMaterialId).distinct().count() != requested.size()) {
            throw unprocessable("DUPLICATE_STAGE_MATERIAL_ID");
        }
        List<MaterialBinding> ordered = requested.stream()
                .sorted(Comparator.comparing(MaterialBinding::stageMaterialId)).toList();
        List<StageMaterialRow> rows = new ArrayList<>(ordered.size());
        for (MaterialBinding binding : ordered) {
            List<StageMaterialRow> found = jdbc.query("SELECT sm.stage_material_id, sm.owner_user_id, sm.business_type, "
                            + "sm.document_role_hint, sm.original_filename, sm.stored_object_key, sm.content_type, "
                            + "sm.file_size, sm.sha256, sm.status, sm.version, sm.expires_at, sm.active_task_id, "
                            + "tm.document_role_hint task_role_hint, tm.original_filename task_original_filename, "
                            + "tm.content_type task_content_type, tm.file_size task_file_size, tm.sha256 task_sha256 "
                            + "FROM ai_stage_material sm JOIN ai_precheck_task_material tm "
                            + "ON tm.stage_material_id=sm.stage_material_id AND tm.task_id=? "
                            + "WHERE sm.stage_material_id=? AND sm.owner_user_id=? AND sm.business_type='MIGRATION_IN' FOR UPDATE",
                    STAGE_MATERIAL_MAPPER, task.taskId(), binding.stageMaterialId(), user.userId());
            if (found.size() != 1) throw notFound();
            StageMaterialRow row = found.get(0);
            if (!"READY".equals(row.status()) || row.expiresAt() == null
                    || !row.expiresAt().isAfter(LocalDateTime.now()) || row.activeTaskId() != null) {
                throw conflict("MATERIAL_NOT_READY");
            }
            if (!row.roleHint().equals(row.taskRoleHint()) || !row.originalFilename().equals(row.taskOriginalFilename())
                    || !row.contentType().equals(row.taskContentType()) || row.fileSize() != row.taskFileSize()
                    || !row.sha256().equalsIgnoreCase(row.taskSha256())) {
                throw conflict("MATERIAL_NOT_READY");
            }
            if (!stageStorage.verify(row.stageMaterialId(), row.objectKey(), row.fileSize(), row.sha256())) {
                throw conflict("MATERIAL_NOT_READY");
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    private TypeResolution resolveMaterialTypes(List<StageMaterialRow> rows, List<MaterialBinding> bindings,
                                               MigrationType migrationType, String householdEvidenceType) {
        Map<String, String> requestedTypes = new LinkedHashMap<>();
        if (bindings != null) {
            for (MaterialBinding binding : bindings) requestedTypes.put(binding.stageMaterialId(), binding.materialType());
        }
        List<ResolvedMaterial> resolved = new ArrayList<>(rows.size());
        for (StageMaterialRow row : rows) {
            String role = row.roleHint();
            String mapped = switch (role) {
                case "IDENTITY_CARD_FRONT", "IDENTITY_CARD_BACK" -> "IDENTITY_PROOF";
                case "HOUSEHOLD_REGISTER_PAGE" -> "HOUSEHOLD_BOOK";
                case "ADDRESS_PROOF" -> "ADDRESS_PROOF";
                case "TRANSFER_CERTIFICATE", "CROSS_DISTRICT_TRANSFER" -> "MIGRATION_PROOF";
                case "APPLICATION_FORM" -> "APPLICATION_FORM";
                case "UNKNOWN" -> null;
                default -> null;
            };
            String suggestion = requestedTypes.get(row.stageMaterialId());
            String type = suggestion == null || suggestion.isBlank() ? mapped : suggestion.trim().toUpperCase(Locale.ROOT);
            if (type == null || !MATERIAL_TYPES.contains(type)) throw unprocessable("MATERIAL_TYPE_REQUIRED_OR_UNSUPPORTED");
            if ("APPLICATION_FORM".equals(role) && !"APPLICATION_FORM".equals(type)) {
                throw unprocessable("APPLICATION_FORM_TYPE_CANNOT_BE_RECLASSIFIED");
            }
            resolved.add(new ResolvedMaterial(row, type, false));
        }
        resolved.sort(Comparator.comparing(item -> item.row().stageMaterialId()));

        LinkedHashSet<String> requiredIds = new LinkedHashSet<>();
        chooseRequired(resolved, "IDENTITY_PROOF", requiredIds);
        List<ResolvedMaterial> householdEvidence = resolved.stream()
                .filter(item -> Set.of("HOUSEHOLD_BOOK", "ADDRESS_PROOF").contains(item.materialType())).toList();
        Set<String> alternativeTypes = householdEvidence.stream().map(ResolvedMaterial::materialType)
                .collect(java.util.stream.Collectors.toSet());
        String chosenAlternative = householdEvidenceType;
        if (alternativeTypes.size() == 2 && chosenAlternative == null) {
            throw unprocessable("HOUSEHOLD_EVIDENCE_CHOICE_REQUIRED");
        }
        if (alternativeTypes.size() == 1) {
            String onlyType = alternativeTypes.iterator().next();
            if (chosenAlternative != null && !chosenAlternative.equals(onlyType)) {
                throw unprocessable("HOUSEHOLD_EVIDENCE_CHOICE_INVALID");
            }
            chosenAlternative = onlyType;
        }
        if (alternativeTypes.isEmpty() && chosenAlternative != null) {
            throw unprocessable("HOUSEHOLD_EVIDENCE_CHOICE_INVALID");
        }
        if (chosenAlternative != null) chooseRequired(resolved, chosenAlternative, requiredIds);
        if (migrationType == MigrationType.IN_CITY_CROSS_DISTRICT) chooseRequired(resolved, "MIGRATION_PROOF", requiredIds);

        List<ResolvedMaterial> flagged = resolved.stream()
                .map(item -> new ResolvedMaterial(item.row(), item.materialType(), requiredIds.contains(item.row().stageMaterialId())))
                .toList();
        Set<String> types = flagged.stream().map(ResolvedMaterial::materialType).collect(java.util.stream.Collectors.toSet());
        return new TypeResolution(flagged, types, chosenAlternative);
    }

    private void chooseRequired(List<ResolvedMaterial> materials, String type, Set<String> requiredIds) {
        materials.stream().filter(item -> type.equals(item.materialType()))
                .map(item -> item.row().stageMaterialId()).min(String::compareTo).ifPresent(requiredIds::add);
    }

    private List<String> materialWarnings(TypeResolution resolution, MigrationType migrationType) {
        List<String> warnings = new ArrayList<>();
        if (!resolution.types().contains("IDENTITY_PROOF")) warnings.add("MISSING_IDENTITY_PROOF");
        if (!resolution.types().contains("HOUSEHOLD_BOOK") && !resolution.types().contains("ADDRESS_PROOF")) {
            warnings.add("MISSING_HOUSEHOLD_BOOK_OR_ADDRESS_PROOF");
        }
        if (migrationType == MigrationType.IN_CITY_CROSS_DISTRICT && !resolution.types().contains("MIGRATION_PROOF")) {
            warnings.add("MISSING_MIGRATION_PROOF");
        }
        return List.copyOf(warnings);
    }

    private void bindMaterials(AuthenticatedUser user, AiPrecheckTaskService.TaskRow task,
                               MigrationCreatedView created, List<StageMaterialRow> rows,
                               TypeResolution types, String traceId) {
        Map<String, ResolvedMaterial> resolvedById = new LinkedHashMap<>();
        for (ResolvedMaterial material : types.materials()) resolvedById.put(material.row().stageMaterialId(), material);
        for (StageMaterialRow row : rows) {
            ResolvedMaterial resolved = resolvedById.get(row.stageMaterialId());
            LocalDateTime now = LocalDateTime.now();
            int stageUpdated = jdbc.update("UPDATE ai_stage_material SET status='BOUND',updated_at=?,version=version+1 "
                            + "WHERE stage_material_id=? AND owner_user_id=? AND status='READY' AND version=? "
                            + "AND expires_at>CURRENT_TIMESTAMP AND active_task_id IS NULL",
                    Timestamp.valueOf(now), row.stageMaterialId(), user.userId(), row.version());
            if (stageUpdated != 1) throw conflict("MATERIAL_ALREADY_BOUND");

            ApplicationMaterial material = new ApplicationMaterial();
            material.setApplicationId(created.applicationId());
            material.setMaterialType(resolved.materialType());
            material.setMaterialName(row.originalFilename());
            material.setOriginalFilename(row.originalFilename());
            material.setStoredFilename(row.stageMaterialId());
            material.setStoragePath("AI_STAGE_REFERENCE");
            material.setStorageKind("AI_STAGE");
            material.setStorageReference(row.stageMaterialId());
            material.setContentType(row.contentType());
            material.setFileSize(row.fileSize());
            material.setFileSha256(row.sha256());
            material.setRequiredFlag(resolved.required());
            material.setVerifyStatus(MaterialVerifyStatus.PENDING);
            material.setUploadedBy(user.userId());
            materialMapper.insert(material);

            jdbc.update("INSERT INTO ai_stage_material_binding(stage_material_id,material_id,task_id,application_id,"
                            + "owner_user_id,status,created_at,updated_at) VALUES(?,?,?,?,?,'ACTIVE',?,?)",
                    row.stageMaterialId(), material.getMaterialId(), task.taskId(), created.applicationId(),
                    user.userId(), Timestamp.valueOf(now), Timestamp.valueOf(now));
            jdbc.update("INSERT INTO ai_precheck_audit(task_id,stage_material_id,actor_user_id,instance_id,trace_id,"
                            + "event_type,from_status,to_status,created_at) VALUES(?,?,?,?,?,'MATERIAL_BOUND_TO_DRAFT','READY','BOUND',?)",
                    task.taskId(), row.stageMaterialId(), user.userId(), S2TraceContext.instanceId(), traceId,
                    Timestamp.valueOf(now));
        }
    }

    private void insertReceipt(long ownerUserId, String taskId, String key, String digest, Long applicationId,
                               Long migrationDetailId, List<String> warnings) {
        try {
            String warningsJson = objectMapper.writeValueAsString(warnings);
            jdbc.update("INSERT INTO ai_confirmation_receipt(owner_user_id,task_id,idempotency_key,request_digest,"
                            + "application_id,migration_detail_id,warnings_json,created_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                    ownerUserId, taskId, key, digest, applicationId, migrationDetailId, warningsJson);
        } catch (DuplicateKeyException exception) {
            throw conflict("TASK_ALREADY_CONSUMED");
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to persist confirmation receipt");
        }
    }

    private Receipt findReceiptForUpdate(String taskId) {
        return jdbc.query("SELECT owner_user_id,task_id,idempotency_key,request_digest,application_id,"
                        + "migration_detail_id,warnings_json FROM ai_confirmation_receipt WHERE task_id=? FOR UPDATE",
                (rs, rowNum) -> new Receipt(rs.getLong("owner_user_id"), rs.getString("task_id"),
                        rs.getString("idempotency_key"), rs.getString("request_digest"),
                        rs.getLong("application_id"), rs.getLong("migration_detail_id"), rs.getString("warnings_json")),
                taskId).stream().findFirst().orElse(null);
    }

    private ConfirmResult replayOrReject(AuthenticatedUser user, Receipt receipt, IdempotencyContext request) {
        if (receipt.ownerUserId() != user.userId()) throw notFound();
        if (receipt.idempotencyKey().equals(request.key())) {
            if (!receipt.requestDigest().equals(request.digest())) throw conflict("IDEMPOTENCY_KEY_REUSED");
            BusinessApplication application = applications.require(receipt.applicationId());
            applications.assertOwner(application, user);
            List<String> warnings = parseWarnings(receipt.warningsJson());
            List<MaterialView> materials = materialMapper.selectByApplicationId(receipt.applicationId())
                    .stream().map(MaterialView::from).toList();
            return new ConfirmResult(view(receipt.taskId(), application, receipt.migrationDetailId(), materials,
                    warnings, true), true);
        }
        throw conflict("TASK_ALREADY_CONSUMED");
    }

    private List<String> parseWarnings(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new IllegalStateException("Persisted confirmation receipt is unreadable");
        }
    }

    private AiConfirmView view(String taskId, BusinessApplication application, Long migrationDetailId,
                               List<MaterialView> materials, List<String> warnings, boolean replayed) {
        return new AiConfirmView(taskId, application.getApplicationId(), application.getApplicationNo(),
                migrationDetailId, "DRAFT", application.getStatus(), replayed, materials, warnings);
    }

    private IdempotencyContext idempotencyDigest(String taskId, AiConfirmRequest request, Map<String, String> fields) {
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("schema", request.requestSchemaVersion());
        canonical.put("taskId", taskId);
        canonical.put("taskVersion", request.taskVersion());
        canonical.put("resolutionToken", request.resolutionToken());
        canonical.put("selectedPerson", request.selectedCandidates().person());
        canonical.put("selectedHousehold", request.selectedCandidates().targetHousehold());
        canonical.put("confirmedFields", new TreeMap<>(fields));
        canonical.put("acknowledgedAlerts", request.acknowledgedAlerts() == null
                ? List.of() : request.acknowledgedAlerts().stream().sorted().toList());
        canonical.put("materialBindings", (request.materialBindings() == null ? List.<MaterialBinding>of()
                : request.materialBindings().stream()
                .sorted(Comparator.comparing(MaterialBinding::stageMaterialId))
                .map(binding -> Map.of("stageMaterialId", binding.stageMaterialId(),
                        "materialType", binding.materialType() == null ? "" : binding.materialType().trim().toUpperCase(Locale.ROOT)))
                .toList()));
        canonical.put("householdEvidenceType", request.householdEvidenceType());
        try {
            return new IdempotencyContext(request.idempotencyKey(), sha256(objectMapper.writeValueAsString(canonical)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to canonicalize confirmation request");
        }
    }

    private String normalizeField(String code, String value) {
        if (value == null) return null;
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFC).trim();
        if (CONTROL.matcher(normalized).find()) throw unprocessable("INVALID_CONFIRMED_FIELD");
        if (RESOLUTION_FIELD_CODES.contains(code)) {
            if ("person.idCard".equals(code)) return normalized.toUpperCase(Locale.ROOT).replace(" ", "");
            return normalized.replaceAll("\\s+", " ");
        }
        if ("migration.migrationType".equals(code)) return normalized.toUpperCase(Locale.ROOT);
        return normalized;
    }

    private String required(Map<String, String> fields, String field) {
        String value = fields.get(field);
        if (value == null || value.isBlank()) throw unprocessable("MISSING_CONFIRMED_FIELD");
        return value;
    }

    private String optional(String value, int max, String field) {
        if (value == null || value.isBlank()) return null;
        if (value.length() > max) throw unprocessable("INVALID_CONFIRMED_FIELD");
        return value;
    }

    private void writeAudit(String taskId, long actorId, String traceId, String event, String from, String to,
                            String safeCode) {
        jdbc.update("INSERT INTO ai_precheck_audit(task_id,actor_user_id,instance_id,trace_id,event_type,from_status,"
                        + "to_status,safe_error_code,created_at) VALUES(?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                taskId, actorId, S2TraceContext.instanceId(), traceId, event, from, to, safeCode);
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private BusinessException unprocessable(String code) {
        return new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, code);
    }

    private BusinessException conflict(String code) { return new BusinessException(HttpStatus.CONFLICT, code); }
    private BusinessException notFound() { return new BusinessException(HttpStatus.NOT_FOUND, "资源不存在或超出授权范围"); }

    public record ConfirmResult(AiConfirmView view, boolean replayed) { }
    private record ConfirmedMigration(MigrationType migrationType, String toRegionCode, String transferBatchNo,
                                      String fromRegionCode, String fromAddress, LocalDate inDate, String reason,
                                      String title, String remark) { }
    private record StageMaterialRow(String stageMaterialId, long ownerUserId, String businessType, String roleHint,
                                    String originalFilename, String objectKey, String contentType, long fileSize,
                                    String sha256, String status, long version, LocalDateTime expiresAt,
                                    String activeTaskId, String taskRoleHint, String taskOriginalFilename,
                                    String taskContentType, long taskFileSize, String taskSha256) { }
    private record ResolvedMaterial(StageMaterialRow row, String materialType, boolean required) { }
    private record TypeResolution(List<ResolvedMaterial> materials, Set<String> types, String householdEvidenceType) { }
    private record Receipt(long ownerUserId, String taskId, String idempotencyKey, String requestDigest,
                           long applicationId, long migrationDetailId, String warningsJson) { }
    private record IdempotencyContext(String key, String digest) { }
}
