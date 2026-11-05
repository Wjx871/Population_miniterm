package com.wjx871.population.material;

import com.wjx871.population.common.BusinessException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** Restart-safe cleanup for AI files released by deletion of a formal draft attachment. */
@Service
@RequiredArgsConstructor
@Log4j2
public class AiBoundMaterialCleanupService {
    private final JdbcTemplate jdbc;
    private final BoundStageMaterialStorage storage;
    private final PlatformTransactionManager transactionManager;

    @Transactional(propagation = Propagation.MANDATORY)
    public String releaseBeforeDelete(Long materialId, String expectedStageId, Long ownerUserId) {
        List<ReleaseCandidate> candidates = jdbc.query("SELECT b.stage_material_id, b.task_id, sm.version "
                        + "FROM ai_stage_material_binding b JOIN ai_stage_material sm "
                        + "ON sm.stage_material_id=b.stage_material_id "
                        + "WHERE b.material_id=? AND b.owner_user_id=? AND b.status='ACTIVE' "
                        + "AND sm.owner_user_id=? AND sm.status='BOUND' FOR UPDATE",
                (rs, rowNum) -> new ReleaseCandidate(rs.getString("stage_material_id"),
                        rs.getString("task_id"), rs.getLong("version")), materialId, ownerUserId, ownerUserId);
        if (candidates.size() != 1 || !candidates.get(0).stageMaterialId().equals(expectedStageId)) {
            throw new BusinessException(HttpStatus.CONFLICT, "MATERIAL_BINDING_UNAVAILABLE");
        }
        ReleaseCandidate candidate = candidates.get(0);
        LocalDateTime now = LocalDateTime.now();
        int stageUpdated = jdbc.update("UPDATE ai_stage_material SET status='CANCELLED',active_task_id=NULL,deleted_at=?,"
                        + "expires_at=?,updated_at=?,version=version+1 WHERE stage_material_id=? AND owner_user_id=? "
                        + "AND status='BOUND' AND version=?",
                Timestamp.valueOf(now), Timestamp.valueOf(now), Timestamp.valueOf(now),
                candidate.stageMaterialId(), ownerUserId, candidate.version());
        if (stageUpdated != 1) throw new BusinessException(HttpStatus.CONFLICT, "MATERIAL_BINDING_CHANGED");
        int bindingUpdated = jdbc.update("UPDATE ai_stage_material_binding SET status='RELEASE_PENDING',updated_at=? "
                        + "WHERE material_id=? AND stage_material_id=? AND status='ACTIVE'",
                Timestamp.valueOf(now), materialId, candidate.stageMaterialId());
        if (bindingUpdated != 1) throw new BusinessException(HttpStatus.CONFLICT, "MATERIAL_BINDING_CHANGED");
        jdbc.update("INSERT INTO ai_precheck_audit(task_id,stage_material_id,actor_user_id,instance_id,event_type,"
                        + "from_status,to_status,created_at) VALUES(?,?,?,?,?,?,?,?)",
                candidate.taskId(), candidate.stageMaterialId(), ownerUserId,
                com.wjx871.population.s2.trace.S2TraceContext.instanceId(),
                "BOUND_MATERIAL_RELEASE_PENDING", "BOUND", "CANCELLED", Timestamp.valueOf(now));
        return candidate.stageMaterialId();
    }

    @Scheduled(fixedDelayString = "${AI_PRECHECK_CLEANUP_POLL_MS:60000}")
    public void scheduledCleanup() {
        try {
            cleanupBatch();
        } catch (RuntimeException exception) {
            log.warn("Bound material cleanup batch deferred exceptionType={}", exception.getClass().getSimpleName());
        }
    }

    public void cleanupAfterCommit(String stageMaterialId) {
        try {
            cleanupOne(stageMaterialId);
        } catch (Exception exception) {
            log.warn("Bound material cleanup deferred stageMaterialId={} exceptionType={}", stageMaterialId,
                    exception.getClass().getSimpleName());
        }
    }

    public int cleanupBatch() {
        List<Pending> pending = jdbc.query("SELECT b.stage_material_id, sm.stored_object_key "
                        + "FROM ai_stage_material_binding b JOIN ai_stage_material sm "
                        + "ON sm.stage_material_id=b.stage_material_id "
                        + "WHERE b.status='RELEASE_PENDING' AND sm.status='CANCELLED' "
                        + "ORDER BY b.updated_at, b.binding_id LIMIT 100",
                (rs, rowNum) -> new Pending(rs.getString("stage_material_id"), rs.getString("stored_object_key")));
        int cleaned = 0;
        for (Pending item : pending) {
            if (cleanupOne(item.stageMaterialId())) cleaned++;
        }
        return cleaned;
    }

    private boolean cleanupOne(String stageMaterialId) {
        Pending pending = jdbc.query("SELECT b.stage_material_id, sm.stored_object_key "
                        + "FROM ai_stage_material_binding b JOIN ai_stage_material sm "
                        + "ON sm.stage_material_id=b.stage_material_id "
                        + "WHERE b.stage_material_id=? AND b.status='RELEASE_PENDING' "
                        + "AND sm.status='CANCELLED'",
                (rs, rowNum) -> new Pending(rs.getString("stage_material_id"), rs.getString("stored_object_key")),
                stageMaterialId).stream().findFirst().orElse(null);
        if (pending == null) return false;

        try {
            storage.delete(pending.stageMaterialId(), pending.objectKey());
        } catch (java.io.IOException | RuntimeException exception) {
            log.warn("Bound material physical cleanup deferred stageMaterialId={} exceptionType={}",
                    stageMaterialId, exception.getClass().getSimpleName());
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        Integer updated = transaction.execute(status -> {
            int released = jdbc.update("UPDATE ai_stage_material_binding SET status='RELEASED',released_at=?,updated_at=? "
                            + "WHERE stage_material_id=? AND status='RELEASE_PENDING'",
                    Timestamp.valueOf(now), Timestamp.valueOf(now), stageMaterialId);
            if (released == 1) {
                jdbc.update("UPDATE ai_stage_material SET file_deleted_at=?,updated_at=? "
                                + "WHERE stage_material_id=? AND status='CANCELLED' AND file_deleted_at IS NULL",
                        Timestamp.valueOf(now), Timestamp.valueOf(now), stageMaterialId);
            }
            return released;
        });
        return updated != null && updated == 1;
    }

    private record Pending(String stageMaterialId, String objectKey) { }
    private record ReleaseCandidate(String stageMaterialId, String taskId, long version) { }
}
