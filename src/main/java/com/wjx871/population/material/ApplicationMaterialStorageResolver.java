package com.wjx871.population.material;

import com.wjx871.population.common.BusinessException;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Dispatches formal material reads through persisted storage metadata, independent of the AI feature flag. */
@Service
public class ApplicationMaterialStorageResolver {
    private final LocalFileStorageService localStorage;
    private final BoundStageMaterialStorage stageStorage;
    private final JdbcTemplate jdbc;

    public ApplicationMaterialStorageResolver(LocalFileStorageService localStorage,
                                               BoundStageMaterialStorage stageStorage,
                                               JdbcTemplate jdbc) {
        this.localStorage = localStorage;
        this.stageStorage = stageStorage;
        this.jdbc = jdbc;
    }

    public Resource load(ApplicationMaterial material) {
        if (material == null || material.getStorageKind() == null) throw unavailable();
        if ("LOCAL".equals(material.getStorageKind())) return localStorage.load(material.getStoredFilename());
        if (!"AI_STAGE".equals(material.getStorageKind())) throw unavailable();

        List<BoundObject> rows = jdbc.query("SELECT b.stage_material_id, sm.stored_object_key, sm.file_size, sm.sha256 "
                        + "FROM ai_stage_material_binding b JOIN ai_stage_material sm "
                        + "ON sm.stage_material_id=b.stage_material_id "
                        + "WHERE b.material_id=? AND b.status='ACTIVE' AND sm.status='BOUND' "
                        + "AND sm.stage_material_id=? AND sm.file_deleted_at IS NULL",
                (rs, rowNum) -> new BoundObject(rs.getString("stage_material_id"),
                        rs.getString("stored_object_key"), rs.getLong("file_size"), rs.getString("sha256")),
                material.getMaterialId(), material.getStorageReference());
        if (rows.size() != 1) throw unavailable();
        BoundObject bound = rows.get(0);
        if (material.getFileSize() == null || material.getFileSha256() == null
                || material.getFileSize() != bound.fileSize()
                || !material.getFileSha256().equalsIgnoreCase(bound.sha256())) throw unavailable();
        return stageStorage.load(bound.stageMaterialId(), bound.objectKey(), bound.fileSize(), bound.sha256());
    }

    private BusinessException unavailable() {
        return new BusinessException(HttpStatus.NOT_FOUND, "材料文件不存在或不可用");
    }

    private record BoundObject(String stageMaterialId, String objectKey, long fileSize, String sha256) { }
}
