package com.wjx871.population.ai.precheck;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wjx871.population.ai.precheck.AiResolutionPreviewRequest.Correction;
import com.wjx871.population.common.BusinessException;
import com.wjx871.population.household.Household;
import com.wjx871.population.household.HouseholdMapper;
import com.wjx871.population.migration.MigrationMapper;
import com.wjx871.population.migration.MigrationOut;
import com.wjx871.population.migration.MigrationType;
import com.wjx871.population.person.IdCardValidator;
import com.wjx871.population.person.Person;
import com.wjx871.population.person.PersonMapper;
import com.wjx871.population.person.PersonStatus;
import com.wjx871.population.security.AuthenticatedUser;
import com.wjx871.population.security.DataScope;
import com.wjx871.population.security.DataScopeCriteria;
import com.wjx871.population.security.SensitiveDataMaskingService;
import com.wjx871.population.s2.trace.S2TraceContext;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Resolves OCR/user-corrected text to scoped candidates and persists opaque, expiring preview tokens. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "population.ai.precheck", name = "enabled", havingValue = "true")
public class AiResolutionService {
    private static final String REQUEST_VERSION = "1.0";
    private static final String TASK_TYPE = "DOCUMENT_PRECHECK";
    private static final String BUSINESS_TYPE = "MIGRATION_IN";
    private static final int MAX_HOUSEHOLD_CANDIDATES = 50;
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}\\p{Cf}]");
    private static final Set<String> CORRECTABLE_FIELDS = Set.of(
            "person.name", "person.idCard", "household.householdNo", "household.address");
    private static final Set<String> RESOLUTION_FIELDS = CORRECTABLE_FIELDS;

    private final AiPrecheckTaskService tasks;
    private final AiPrecheckProperties properties;
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final PersonMapper persons;
    private final HouseholdMapper households;
    private final MigrationMapper migrations;
    private final IdCardValidator idCards;
    private final SensitiveDataMaskingService masking;

    @Transactional
    public AiResolutionPreviewView preview(AuthenticatedUser user, String taskId,
                                          AiResolutionPreviewRequest request, String traceId) {
        long startedNanos = System.nanoTime();
        requirePermissions(user);
        DataScopeCriteria scope = scope(user);
        validateRequestVersion(request);

        AiPrecheckTaskService.TaskRow task = tasks.lockOwnedForResolution(user.userId(), taskId);
        if (!TASK_TYPE.equals(task.taskType()) || !BUSINESS_TYPE.equals(task.businessType())) {
            throw notFound();
        }
        if (!"SUCCEEDED".equals(task.status())) throw conflict("TASK_NOT_READY_FOR_RESOLUTION");
        if (request.taskVersion() != task.version()) throw conflict("TASK_VERSION_MISMATCH");
        if (request.migrationType() == null || !request.migrationType().name().equals(task.migrationType())) {
            throw conflict("MIGRATION_CONTEXT_MISMATCH");
        }

        String toRegionCode = normalizeRequired(request.toRegionCode(), 20, "INVALID_TO_REGION_CODE");
        String batchNo = normalizeOptional(request.transferBatchNo(), 40, "INVALID_TRANSFER_BATCH_NO");
        Map<String, String> corrections = normalizeCorrections(request.corrections());
        ExtractedFields extracted = extractFields(task.resultJson());
        ResolvedInputs inputs = resolveInputs(extracted, corrections);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plus(properties.getResolutionTokenTtl());
        String token = opaqueToken("res_");
        String scopeDigest = digest(scopeCanonical(user));
        String requestDigest = digest(inputCanonical(task, request.migrationType(), toRegionCode, batchNo,
                inputs, extracted.conflictingFields()));

        // The task row lock serializes previews for this owner/task across JVMs.
        int revokedCount = jdbc.update("UPDATE ai_resolution_session SET status='REVOKED', updated_at=CURRENT_TIMESTAMP "
                        + "WHERE owner_user_id=? AND task_id=? AND status='ACTIVE'",
                user.userId(), taskId);
        jdbc.update("INSERT INTO ai_resolution_session(resolution_token_sha256,owner_user_id,task_id,task_version,"
                        + "request_digest,scope_digest,migration_type,to_region_code,transfer_batch_no,"
                        + "resolution_status,confirmable,status,expires_at,created_at,updated_at) "
                        + "VALUES(?,?,?,?,?,?,?,?,?,'NEEDS_CORRECTION',FALSE,'ACTIVE',?,?,?)",
                sha256(token), user.userId(), taskId, task.version(), requestDigest, scopeDigest,
                request.migrationType().name(), toRegionCode, batchNo, Timestamp.valueOf(expiresAt),
                Timestamp.valueOf(now), Timestamp.valueOf(now));
        if (revokedCount > 0) insertAudit(taskId, user.userId(), traceId, "RESOLUTION_PREVIEW_REVOKED", "ACTIVE", "REVOKED",
                (System.nanoTime() - startedNanos) / 1_000_000);

        List<AiResolutionPreviewView.Warning> warnings = new ArrayList<>();
        warnings.addAll(inputs.warnings());
        PersonResolution personResult = resolvePerson(scope, inputs, warnings);
        HouseholdResolution householdResult = resolveHouseholds(scope, inputs, toRegionCode, warnings);
        boolean batchValid = validateTransferBatch(request.migrationType(), batchNo,
                personResult.candidates().size() == 1 ? personResult.candidates().get(0).entity().getPersonId() : null,
                toRegionCode, warnings);

        List<AiResolutionPreviewView.PersonCandidate> personViews = new ArrayList<>();
        for (PersonCandidate candidate : personResult.candidates()) {
            String candidateToken = insertCandidate(token, "PERSON", candidate.entity().getPersonId(),
                    candidate.entity().getVersion(), currentStatus(candidate.entity()), candidate.stateDigest());
            personViews.add(new AiResolutionPreviewView.PersonCandidate(candidateToken,
                    maskName(candidate.entity().getName()), masking.identityAlways(candidate.entity().getIdCard()),
                    candidate.entity().getVersion()));
        }
        List<AiResolutionPreviewView.HouseholdCandidate> householdViews = new ArrayList<>();
        for (HouseholdCandidate candidate : householdResult.candidates()) {
            String candidateToken = insertCandidate(token, "HOUSEHOLD", candidate.entity().getHouseholdId(),
                    candidate.entity().getVersion() == null ? 0L : candidate.entity().getVersion().longValue(),
                    candidate.entity().getStatus(), candidate.stateDigest());
            householdViews.add(new AiResolutionPreviewView.HouseholdCandidate(candidateToken,
                    maskHouseholdNo(candidate.entity().getHouseholdNo()), candidate.entity().getRegionCode(),
                    masking.addressAlways(candidate.entity().getAddress()), candidate.entity().getStatus(),
                    candidate.entity().getVersion() == null ? 0L : candidate.entity().getVersion().longValue()));
        }

        boolean complete = inputs.warnings().isEmpty() && personViews.size() > 0 && householdViews.size() > 0
                && householdResult.truncatedCount() == 0 && batchValid && warnings.isEmpty();
        String status;
        if (!complete) status = "NEEDS_CORRECTION";
        else if (personViews.size() == 1 && householdViews.size() == 1) status = "RESOLVED_SINGLE";
        else status = "SELECTION_REQUIRED";
        boolean confirmable = complete;
        jdbc.update("UPDATE ai_resolution_session SET resolution_status=?,confirmable=? WHERE resolution_token_sha256=?",
                status, confirmable, sha256(token));
        insertAudit(taskId, user.userId(), traceId, "RESOLUTION_PREVIEW_CREATED", null, status,
                (System.nanoTime() - startedNanos) / 1_000_000);

        List<String> requiredSelections = complete
                ? List.of("person", "targetHousehold") : List.of("person", "targetHousehold");
        return new AiResolutionPreviewView(taskId, task.version(), status, token, expiresAt, confirmable,
                requiredSelections, List.copyOf(personViews), List.copyOf(householdViews), deduplicateWarnings(warnings));
    }

    /**
     * S4-B transaction primitive. It is intentionally not exposed as an HTTP endpoint.
     * Callers must keep this transaction open through draft creation and material binding.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ResolutionSelection validateForConfirmation(String taskId, long taskVersion, String resolutionToken,
                                                       String personCandidateToken, String householdCandidateToken) {
        AuthenticatedUser user = com.wjx871.population.security.CurrentUserContext.requireUser();
        requireConfirmationPermissions(user);
        DataScopeCriteria scope = confirmationScope(user);
        if (resolutionToken == null || resolutionToken.length() > 80) throw notFound();
        AiPrecheckTaskService.TaskRow task = tasks.lockOwnedForResolution(user.userId(), taskId);
        if (!TASK_TYPE.equals(task.taskType()) || !BUSINESS_TYPE.equals(task.businessType())
                || !"SUCCEEDED".equals(task.status()) || task.version() != taskVersion) {
            throw conflict("TASK_VERSION_MISMATCH");
        }
        ResolutionSession session = jdbc.query("SELECT resolution_id,owner_user_id,task_id,task_version,request_digest,"
                        + "scope_digest,migration_type,to_region_code,transfer_batch_no,resolution_status,confirmable,status,expires_at "
                        + "FROM ai_resolution_session WHERE resolution_token_sha256=? FOR UPDATE",
                (rs, n) -> new ResolutionSession(rs.getLong("resolution_id"), rs.getLong("owner_user_id"),
                        rs.getString("task_id"), rs.getLong("task_version"), rs.getString("request_digest"),
                        rs.getString("scope_digest"), rs.getString("migration_type"), rs.getString("to_region_code"),
                        rs.getString("transfer_batch_no"), rs.getString("resolution_status"), rs.getBoolean("confirmable"),
                        rs.getString("status"), rs.getTimestamp("expires_at").toLocalDateTime()),
                sha256(resolutionToken)).stream().findFirst().orElseThrow(AiResolutionService::notFound);
        LocalDateTime now = LocalDateTime.now();
        if (session.ownerUserId() != user.userId() || !session.taskId().equals(taskId)) throw notFound();
        if (session.taskVersion() != taskVersion || !"ACTIVE".equals(session.status())
                || !session.expiresAt().isAfter(now)) throw conflict("RESOLUTION_EXPIRED");
        if (!session.migrationType().equals(task.migrationType())) throw conflict("MIGRATION_CONTEXT_MISMATCH");
        if (!session.confirmable() || "NEEDS_CORRECTION".equals(session.resolutionStatus())) {
            throw conflict("RESOLUTION_NOT_CONFIRMABLE");
        }
        if (!session.scopeDigest().equals(digest(scopeCanonical(user)))) throw conflict("RESOLUTION_SCOPE_CHANGED");
        CandidateRow person = lockSelectedCandidate(session.resolutionId(), personCandidateToken, "PERSON");
        CandidateRow household = lockSelectedCandidate(session.resolutionId(), householdCandidateToken, "HOUSEHOLD");

        Person currentPerson = persons.selectScopedById(person.entityId(), scope).orElseThrow(AiResolutionService::notFound);
        lockPerson(currentPerson.getPersonId());
        currentPerson = persons.selectScopedByIdForUpdate(person.entityId(), scope).orElseThrow(AiResolutionService::notFound);
        if (!currentPerson.getVersion().equals(person.entityVersion())
                || !person.stateDigest().equals(personStateDigest(currentPerson.getPersonId(), currentPerson, true))) {
            throw conflict("RESOLUTION_ENTITY_CHANGED");
        }
        if (!personEligible(currentPerson)) throw conflict("PERSON_UNAVAILABLE");

        Household currentHousehold = households.findScoped(household.entityId(), scope)
                .orElseThrow(AiResolutionService::notFound);
        lockHousehold(currentHousehold.getHouseholdId());
        currentHousehold = households.lockScoped(household.entityId(), scope)
                .orElseThrow(AiResolutionService::notFound);
        if (!sameHouseholdSnapshot(household, currentHousehold)
                || !"ACTIVE".equals(currentHousehold.getStatus())
                || !session.toRegionCode().equals(currentHousehold.getRegionCode())) {
            throw conflict("RESOLUTION_ENTITY_CHANGED");
        }
        if (hasActiveResidence(currentPerson.getPersonId()) || hasActiveMembership(currentPerson.getPersonId())) {
            throw conflict("PERSON_CURRENTLY_REGISTERED");
        }
        if (!validateTransferBatchForConfirmation(session, currentPerson.getPersonId())) {
            throw conflict("TRANSFER_BATCH_INVALID_OR_USED");
        }
        return new ResolutionSelection(currentPerson.getPersonId(), currentHousehold.getHouseholdId(),
                currentPerson.getVersion(), currentHousehold.getVersion() == null ? 0L : currentHousehold.getVersion().longValue(), session.migrationType(),
                session.toRegionCode(), session.transferBatchNo(), session.requestDigest(), session.scopeDigest());
    }

    private PersonResolution resolvePerson(DataScopeCriteria scope, ResolvedInputs inputs,
                                           List<AiResolutionPreviewView.Warning> warnings) {
        if (inputs.personName() == null || inputs.personIdCard() == null) return new PersonResolution(List.of());
        Person person = persons.selectScopedByIdCard(inputs.personIdCard(), scope).orElse(null);
        if (person == null) {
            warnings.add(warning("ENTITY_NOT_FOUND", "person.name", "person.idCard"));
            return new PersonResolution(List.of());
        }
        if (!inputs.personName().equals(normalizeName(person.getName()))) {
            warnings.add(warning("PERSON_NAME_MISMATCH", "person.name", "person.idCard"));
            return new PersonResolution(List.of());
        }
        if (!personEligible(person)) {
            warnings.add(warning("PERSON_UNAVAILABLE", "person.name", "person.idCard"));
            return new PersonResolution(List.of());
        }
        if (hasActiveResidence(person.getPersonId()) || hasActiveMembership(person.getPersonId())) {
            warnings.add(warning("PERSON_CURRENTLY_REGISTERED", "person.name", "person.idCard"));
            return new PersonResolution(List.of());
        }
        return new PersonResolution(List.of(new PersonCandidate(person, personStateDigest(person.getPersonId(), person, false))));
    }

    private HouseholdResolution resolveHouseholds(DataScopeCriteria scope, ResolvedInputs inputs, String toRegionCode,
                                                 List<AiResolutionPreviewView.Warning> warnings) {
        if (inputs.householdNo() != null) {
            Household household = households.findScopedByNo(inputs.householdNo(), scope).orElse(null);
            if (household == null) {
                warnings.add(warning("ENTITY_NOT_FOUND", "household.householdNo"));
                return new HouseholdResolution(List.of(), 0);
            }
            if (!"ACTIVE".equals(household.getStatus())) {
                warnings.add(warning("TARGET_HOUSEHOLD_NOT_ACTIVE", "household.householdNo"));
                return new HouseholdResolution(List.of(), 0);
            }
            if (!toRegionCode.equals(household.getRegionCode())) {
                warnings.add(warning("TARGET_REGION_MISMATCH", "household.householdNo"));
                return new HouseholdResolution(List.of(), 0);
            }
            return new HouseholdResolution(List.of(new HouseholdCandidate(household,
                    householdStateDigest(household.getHouseholdId(), household, false))), 0);
        }
        if (inputs.householdAddress() == null) {
            warnings.add(warning("TARGET_HOUSEHOLD_IDENTIFIER_REQUIRED", "household.householdNo", "household.address"));
            return new HouseholdResolution(List.of(), 0);
        }
        List<Household> found = households.search(null, null, inputs.householdAddress(), toRegionCode, null, "ACTIVE",
                scope, MAX_HOUSEHOLD_CANDIDATES + 1, 0, "h.household_id ASC");
        List<HouseholdCandidate> result = new ArrayList<>();
        for (Household household : found.stream().limit(MAX_HOUSEHOLD_CANDIDATES).toList()) {
            result.add(new HouseholdCandidate(household,
                    householdStateDigest(household.getHouseholdId(), household, false)));
        }
        long truncated = Math.max(0, found.size() - MAX_HOUSEHOLD_CANDIDATES);
        if (result.isEmpty()) warnings.add(warning("ENTITY_NOT_FOUND", "household.address"));
        if (truncated > 0) warnings.add(warning("CANDIDATE_SET_TRUNCATED", "household.address"));
        return new HouseholdResolution(List.copyOf(result), truncated);
    }

    private boolean validateTransferBatch(MigrationType type, String batchNo, Long personId, String toRegionCode,
                                          List<AiResolutionPreviewView.Warning> warnings) {
        if (type == MigrationType.OUTSIDE_CITY) {
            if (batchNo != null) {
                warnings.add(warning("TRANSFER_BATCH_NOT_APPLICABLE", "migration.transferBatchNo"));
                return false;
            }
            return true;
        }
        if (batchNo == null || personId == null) {
            warnings.add(warning("TRANSFER_BATCH_INVALID_OR_USED", "migration.transferBatchNo"));
            return false;
        }
        MigrationOut out = migrations.findCompletedBatch(batchNo).orElse(null);
        if (out == null || !personId.equals(out.getPersonId()) || out.getMigrationType() != MigrationType.IN_CITY_CROSS_DISTRICT
                || !toRegionCode.equals(out.getToRegionCode()) || countAnyInBatch(batchNo) > 0) {
            warnings.add(warning("TRANSFER_BATCH_INVALID_OR_USED", "migration.transferBatchNo"));
            return false;
        }
        return true;
    }

    private boolean validateTransferBatchForConfirmation(ResolutionSession session, Long personId) {
        MigrationType type = MigrationType.valueOf(session.migrationType());
        if (type == MigrationType.OUTSIDE_CITY) return session.transferBatchNo() == null;
        if (session.transferBatchNo() == null) return false;
        List<TransferBatchRow> outgoing = jdbc.query("SELECT person_id,migration_type,to_region_code,business_status "
                        + "FROM migration_out WHERE transfer_batch_no=? FOR UPDATE",
                (rs, row) -> new TransferBatchRow(rs.getLong("person_id"), rs.getString("migration_type"),
                        rs.getString("to_region_code"), rs.getString("business_status")), session.transferBatchNo());
        List<Long> incoming = jdbc.queryForList("SELECT in_id FROM migration_in WHERE transfer_batch_no=? FOR UPDATE",
                Long.class, session.transferBatchNo());
        return outgoing.size() == 1 && incoming.isEmpty() && personId.equals(outgoing.get(0).personId())
                && MigrationType.IN_CITY_CROSS_DISTRICT.name().equals(outgoing.get(0).migrationType())
                && "COMPLETED".equals(outgoing.get(0).businessStatus())
                && session.toRegionCode().equals(outgoing.get(0).toRegionCode());
    }

    private CandidateRow lockSelectedCandidate(long resolutionId, String token, String entityType) {
        if (token == null || token.length() > 80) throw notFound();
        return jdbc.query("SELECT candidate_id,resolution_id,entity_type,entity_id,entity_version,entity_status,state_digest "
                        + "FROM ai_resolution_candidate WHERE resolution_id=? AND candidate_token_sha256=? AND entity_type=? FOR UPDATE",
                (rs, n) -> new CandidateRow(rs.getLong("candidate_id"), rs.getLong("resolution_id"),
                        rs.getString("entity_type"), rs.getLong("entity_id"), rs.getLong("entity_version"),
                        rs.getString("entity_status"), rs.getString("state_digest")),
                resolutionId, sha256(token), entityType).stream().findFirst()
                .orElseThrow(AiResolutionService::notFound);
    }

    private String insertCandidate(String resolutionToken, String type, Long entityId, Long entityVersion,
                                   String entityStatus, String stateDigest) {
        String token = opaqueToken("PERSON".equals(type) ? "pc_" : "hc_");
        Long resolutionId = jdbc.queryForObject("SELECT resolution_id FROM ai_resolution_session WHERE resolution_token_sha256=?",
                Long.class, sha256(resolutionToken));
        jdbc.update("INSERT INTO ai_resolution_candidate(resolution_id,candidate_token_sha256,entity_type,entity_id,"
                        + "entity_version,entity_status,state_digest,created_at) VALUES(?,?,?,?,?,?,?,CURRENT_TIMESTAMP)",
                resolutionId, sha256(token), type, entityId, entityVersion == null ? 0L : entityVersion,
                entityStatus, stateDigest);
        return token;
    }

    private boolean personEligible(Person person) {
        if (person == null || !PersonStatus.NORMAL.equals(person.getStatus())) return false;
        String code = person.getCurrentStatusCode();
        return code == null || "PENDING".equals(code) || "MIGRATED_OUT".equals(code);
    }

    private boolean hasActiveResidence(Long personId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM residence WHERE person_id=? AND status='ACTIVE'",
                Integer.class, personId) > 0;
    }

    private boolean hasActiveMembership(Long personId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM household_member WHERE person_id=? AND status='ACTIVE'",
                Integer.class, personId) > 0;
    }

    private int countAnyInBatch(String batchNo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM migration_in WHERE transfer_batch_no=?", Integer.class, batchNo);
    }

    private void insertAudit(String taskId, Long actorId, String traceId, String event, String from, String to,
                             long elapsedMs) {
        jdbc.update("INSERT INTO ai_precheck_audit(task_id,actor_user_id,instance_id,trace_id,event_type,from_status,"
                        + "to_status,attempt_count,safe_error_code,elapsed_ms,created_at) "
                        + "VALUES(?,?,?,?,?,?,?,0,NULL,?,CURRENT_TIMESTAMP)",
                taskId, actorId, S2TraceContext.instanceId(), traceId, event, from, to, elapsedMs);
    }

    private String personStateDigest(Long id, Person person, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        List<String> residences = jdbc.query("SELECT residence_id,household_id,region_code,status,version FROM residence "
                        + "WHERE person_id=? ORDER BY residence_id" + lock,
                (rs, n) -> rs.getLong("residence_id") + ":" + rs.getLong("household_id") + ":"
                        + rs.getString("region_code") + ":" + rs.getString("status") + ":" + rs.getLong("version"), id);
        List<String> memberships = jdbc.query("SELECT member_id,household_id,relationship,status,version FROM household_member "
                        + "WHERE person_id=? ORDER BY member_id" + lock,
                (rs, n) -> rs.getLong("member_id") + ":" + rs.getLong("household_id") + ":"
                        + rs.getString("relationship") + ":" + rs.getString("status") + ":" + rs.getLong("version"), id);
        return digest(person.getVersion() + "\n" + person.getStatus() + "\n" + person.getCurrentStatusCode()
                + "\nR=" + String.join("|", residences) + "\nM=" + String.join("|", memberships));
    }

    private String householdStateDigest(Long id, Household household, boolean forUpdate) {
        String lock = forUpdate ? " FOR UPDATE" : "";
        List<String> members = jdbc.query("SELECT member_id,person_id,relationship,status,version FROM household_member "
                        + "WHERE household_id=? ORDER BY member_id" + lock,
                (rs, n) -> rs.getLong("member_id") + ":" + rs.getLong("person_id") + ":"
                        + rs.getString("relationship") + ":" + rs.getString("status") + ":" + rs.getLong("version"), id);
        List<String> residences = jdbc.query("SELECT residence_id,person_id,region_code,status,version FROM residence "
                        + "WHERE household_id=? ORDER BY residence_id" + lock,
                (rs, n) -> rs.getLong("residence_id") + ":" + rs.getLong("person_id") + ":"
                        + rs.getString("region_code") + ":" + rs.getString("status") + ":" + rs.getLong("version"), id);
        List<String> incoming = jdbc.query("SELECT application_id,person_id,business_status,version FROM migration_in "
                        + "WHERE to_household_id=? AND business_status IN('DRAFT','UNDER_REVIEW','APPROVED') ORDER BY application_id" + lock,
                (rs, n) -> rs.getLong("application_id") + ":" + rs.getLong("person_id") + ":"
                        + rs.getString("business_status") + ":" + rs.getInt("version"), id);
        return digest(household.getVersion() + "\n" + household.getStatus() + "\n" + household.getRegionCode()
                + "\nM=" + String.join("|", members) + "\nR=" + String.join("|", residences)
                + "\nI=" + String.join("|", incoming));
    }

    private void lockPerson(Long personId) {
        jdbc.queryForList("SELECT person_id FROM person WHERE person_id=? FOR UPDATE", Long.class, personId);
        jdbc.queryForList("SELECT residence_id FROM residence WHERE person_id=? FOR UPDATE", Long.class, personId);
        jdbc.queryForList("SELECT member_id FROM household_member WHERE person_id=? FOR UPDATE", Long.class, personId);
    }

    private void lockHousehold(Long householdId) {
        jdbc.queryForList("SELECT household_id FROM household WHERE household_id=? FOR UPDATE", Long.class, householdId);
        jdbc.queryForList("SELECT member_id FROM household_member WHERE household_id=? FOR UPDATE", Long.class, householdId);
        jdbc.queryForList("SELECT residence_id FROM residence WHERE household_id=? FOR UPDATE", Long.class, householdId);
        jdbc.queryForList("SELECT application_id FROM migration_in WHERE to_household_id=? AND business_status IN('DRAFT','UNDER_REVIEW','APPROVED') FOR UPDATE",
                Long.class, householdId);
    }

    private boolean sameHouseholdSnapshot(CandidateRow candidate, Household current) {
        return candidate.entityVersion() == (current.getVersion() == null ? 0L : current.getVersion().longValue())
                && candidate.entityStatus().equals(current.getStatus())
                && candidate.stateDigest().equals(householdStateDigest(current.getHouseholdId(), current, true));
    }

    private String currentStatus(Person person) {
        return person.getCurrentStatusCode() == null ? "UNKNOWN" : person.getCurrentStatusCode();
    }

    private void requirePermissions(AuthenticatedUser user) {
        if (user == null || user.userId() == null) throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        if (user.permissions() == null || !user.permissions().contains("ai:precheck:view")
                || !user.permissions().contains("migration:in:create")) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "无实体解析预览权限");
        }
    }

    private void requireConfirmationPermissions(AuthenticatedUser user) {
        if (user == null || user.userId() == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED");
        }
        if (user.permissions() == null || !user.permissions().contains("ai:precheck:view")
                || !user.permissions().contains("migration:in:create")) {
            throw notFound();
        }
    }

    private DataScopeCriteria confirmationScope(AuthenticatedUser user) {
        if (user.dataScope() == null || (user.dataScope() == DataScope.REGION && user.regionCodes().isEmpty())) {
            throw notFound();
        }
        return new DataScopeCriteria(user.dataScope(), user.userId(), user.departmentId(), user.regionCode(),
                user.regionCodes());
    }

    private DataScopeCriteria scope(AuthenticatedUser user) {
        if (user.dataScope() == null || (user.dataScope() == DataScope.REGION && user.regionCodes().isEmpty())) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "用户数据范围无法验证");
        }
        return new DataScopeCriteria(user.dataScope(), user.userId(), user.departmentId(), user.regionCode(),
                user.regionCodes());
    }

    private String scopeCanonical(AuthenticatedUser user) {
        return user.userId() + "\n" + user.dataScope() + "\n" + user.departmentId() + "\n" + user.regionCode()
                + "\n" + user.regionCodes().stream().sorted().collect(Collectors.joining(","))
                + "\n" + user.permissions().stream().sorted().collect(Collectors.joining(","));
    }

    private void validateRequestVersion(AiResolutionPreviewRequest request) {
        if (request == null || !REQUEST_VERSION.equals(request.requestSchemaVersion())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_SCHEMA_VERSION");
        }
    }

    private Map<String, String> normalizeCorrections(List<Correction> corrections) {
        Map<String, String> values = new LinkedHashMap<>();
        if (corrections == null) return values;
        for (Correction correction : corrections) {
            if (correction == null || correction.fieldCode() == null || !CORRECTABLE_FIELDS.contains(correction.fieldCode())) {
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
                            if (!RESOLUTION_FIELDS.contains(code) || !"PRESENT".equals(field.path("fieldStatus").asText())) continue;
                            JsonNode value = field.get("value");
                            if (value == null || !value.isValueNode() || value.isNull()) continue;
                            String normalized = normalizeField(code, value.asText(), false);
                            if (normalized != null) found.computeIfAbsent(code, ignored -> new TreeSet<>()).add(normalized);
                        }
                    }
                }
            } catch (Exception exception) {
                // Persisted task output is untrusted input here; a malformed result becomes a correction request.
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

    private String normalizeName(String value) {
        return value == null ? null : java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFC)
                .trim().replaceAll("\\s+", " ");
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
        canonical.put("conflicts", conflicts.stream().sorted().toList());
        try {
            return objectMapper.writeValueAsString(canonical);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to canonicalize resolution input");
        }
    }

    private String opaqueToken(String prefix) {
        byte[] random = new byte[32];
        RANDOM.nextBytes(random);
        return prefix + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private static final java.security.SecureRandom RANDOM = new java.security.SecureRandom();

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private String digest(String value) { return sha256(value); }

    private String maskName(String value) {
        if (value == null || value.isEmpty()) return "**";
        return value.substring(0, 1) + "*".repeat(Math.max(1, value.length() - 1));
    }

    private String maskHouseholdNo(String value) {
        if (value == null || value.length() <= 4) return "****";
        return "*".repeat(value.length() - 4) + value.substring(value.length() - 4);
    }

    private AiResolutionPreviewView.Warning warning(String code, String... fields) {
        return new AiResolutionPreviewView.Warning(code, List.of(fields));
    }

    private List<AiResolutionPreviewView.Warning> deduplicateWarnings(Collection<AiResolutionPreviewView.Warning> warnings) {
        Map<String, LinkedHashSet<String>> merged = new LinkedHashMap<>();
        for (AiResolutionPreviewView.Warning warning : warnings) {
            merged.computeIfAbsent(warning.code(), ignored -> new LinkedHashSet<>()).addAll(warning.fieldCodes());
        }
        return merged.entrySet().stream().map(entry -> new AiResolutionPreviewView.Warning(entry.getKey(),
                List.copyOf(entry.getValue()))).toList();
    }

    private BusinessException bad(String code) { return new BusinessException(HttpStatus.BAD_REQUEST, code); }
    private BusinessException conflict(String code) { return new BusinessException(HttpStatus.CONFLICT, code); }
    private static BusinessException notFound() { return new BusinessException(HttpStatus.NOT_FOUND, "实体不存在或超出数据范围"); }

    public record ResolutionSelection(Long personId, Long targetHouseholdId, Long personVersion, Long householdVersion,
                                      String migrationType, String toRegionCode, String transferBatchNo,
                                      String requestDigest, String scopeDigest) { }

    private record ExtractedFields(Map<String, String> values, Set<String> conflictingFields) { }
    private record ResolvedInputs(String personName, String personIdCard, String householdNo, String householdAddress,
                                  List<AiResolutionPreviewView.Warning> warnings) { }
    private record PersonCandidate(Person entity, String stateDigest) { }
    private record PersonResolution(List<PersonCandidate> candidates) { }
    private record HouseholdCandidate(Household entity, String stateDigest) { }
    private record HouseholdResolution(List<HouseholdCandidate> candidates, long truncatedCount) { }
    private record CandidateRow(long candidateId, long resolutionId, String entityType, long entityId,
                                long entityVersion, String entityStatus, String stateDigest) { }
    private record ResolutionSession(long resolutionId, long ownerUserId, String taskId, long taskVersion,
                                     String requestDigest, String scopeDigest, String migrationType,
                                     String toRegionCode, String transferBatchNo, String resolutionStatus,
                                     boolean confirmable, String status, LocalDateTime expiresAt) { }
    private record TransferBatchRow(long personId, String migrationType, String toRegionCode, String businessStatus) { }
}
