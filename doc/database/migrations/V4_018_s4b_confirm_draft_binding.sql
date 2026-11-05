-- V4_018: S4-B explicit confirmation receipts and durable staged-material bindings.
-- Apply after V4_017 on an isolated, backed-up MySQL 8 database.
USE population_miniterm;

INSERT INTO sys_permission (permission_code, permission_name, module_name, permission_type, status)
VALUES ('ai:precheck:confirm', '确认预审并创建迁入草稿', 'AI_PRECHECK', 'API', 'ENABLED')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), module_name = VALUES(module_name),
                        permission_type = VALUES(permission_type), status = 'ENABLED';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('HOUSEHOLD_MANAGER', 'SYSTEM_ADMIN')
  AND p.permission_code = 'ai:precheck:confirm';

ALTER TABLE application_material
    ADD COLUMN storage_kind VARCHAR(20) NOT NULL DEFAULT 'LOCAL' COMMENT '受控存储来源',
    ADD COLUMN storage_reference VARCHAR(80) NULL COMMENT '服务端解析的稳定存储引用',
    ADD UNIQUE KEY uk_application_material_storage_ref (storage_kind, storage_reference);

CREATE TABLE ai_confirmation_receipt (
    receipt_id BIGINT NOT NULL AUTO_INCREMENT,
    owner_user_id BIGINT NOT NULL,
    task_id VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    application_id BIGINT NOT NULL,
    migration_detail_id BIGINT NOT NULL,
    warnings_json TEXT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (receipt_id),
    UNIQUE KEY uk_ai_confirmation_task (task_id),
    UNIQUE KEY uk_ai_confirmation_user_task_key (owner_user_id, task_id, idempotency_key),
    UNIQUE KEY uk_ai_confirmation_application (application_id),
    KEY idx_ai_confirmation_owner_created (owner_user_id, created_at),
    CONSTRAINT fk_ai_confirmation_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_ai_confirmation_task FOREIGN KEY (task_id) REFERENCES ai_precheck_task(task_id),
    CONSTRAINT fk_ai_confirmation_application FOREIGN KEY (application_id) REFERENCES business_application(application_id),
    CONSTRAINT fk_ai_confirmation_migration FOREIGN KEY (migration_detail_id) REFERENCES migration_in(in_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_stage_material_binding (
    binding_id BIGINT NOT NULL AUTO_INCREMENT,
    stage_material_id VARCHAR(80) NOT NULL,
    material_id BIGINT NOT NULL,
    task_id VARCHAR(80) NOT NULL,
    application_id BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    released_at DATETIME NULL,
    PRIMARY KEY (binding_id),
    UNIQUE KEY uk_ai_stage_binding_stage (stage_material_id),
    UNIQUE KEY uk_ai_stage_binding_material (material_id),
    KEY idx_ai_stage_binding_cleanup (status, updated_at, binding_id),
    KEY idx_ai_stage_binding_application (application_id, status),
    CONSTRAINT fk_ai_stage_binding_stage FOREIGN KEY (stage_material_id) REFERENCES ai_stage_material(stage_material_id),
    CONSTRAINT fk_ai_stage_binding_task FOREIGN KEY (task_id) REFERENCES ai_precheck_task(task_id),
    CONSTRAINT fk_ai_stage_binding_application FOREIGN KEY (application_id) REFERENCES business_application(application_id),
    CONSTRAINT fk_ai_stage_binding_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(user_id),
    CONSTRAINT ck_ai_stage_binding_status CHECK (status IN ('ACTIVE', 'RELEASE_PENDING', 'RELEASED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
