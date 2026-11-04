-- S3: isolated AI staging, task, outbox and audit data. Existing business tables are untouched.
CREATE TABLE ai_stage_material (
    stage_material_id VARCHAR(80) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_department_id BIGINT NULL,
    owner_region_code VARCHAR(20) NULL,
    owner_data_scope VARCHAR(20) NOT NULL,
    business_type VARCHAR(40) NOT NULL,
    document_role_hint VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_object_key VARCHAR(120) NOT NULL,
    temp_object_key VARCHAR(120) NULL,
    content_type VARCHAR(100) NOT NULL,
    file_extension VARCHAR(10) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    idempotency_endpoint VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    status VARCHAR(20) NOT NULL,
    active_task_id VARCHAR(80) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    uploaded_at DATETIME NULL,
    expires_at DATETIME NULL,
    deleted_at DATETIME NULL,
    file_deleted_at DATETIME NULL,
    version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (stage_material_id),
    UNIQUE KEY uk_ai_stage_object (stored_object_key),
    UNIQUE KEY uk_ai_stage_idempotency (owner_user_id, idempotency_endpoint, idempotency_key),
    KEY idx_ai_stage_owner_status_expiry (owner_user_id, status, expires_at),
    KEY idx_ai_stage_status_updated (status, updated_at),
    KEY idx_ai_stage_active_task (active_task_id),
    CONSTRAINT fk_ai_stage_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_precheck_task (
    task_id VARCHAR(80) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    owner_department_id BIGINT NULL,
    owner_region_code VARCHAR(20) NULL,
    owner_data_scope VARCHAR(20) NOT NULL,
    task_type VARCHAR(40) NOT NULL,
    business_type VARCHAR(40) NOT NULL,
    migration_type VARCHAR(50) NOT NULL,
    request_schema_version VARCHAR(20) NOT NULL,
    result_schema_version VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempt_count INT NOT NULL DEFAULT 0,
    delivery_recovery_count INT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    trace_id VARCHAR(64) NOT NULL,
    request_digest CHAR(64) NOT NULL,
    idempotency_endpoint VARCHAR(80) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    started_at DATETIME NULL,
    completed_at DATETIME NULL,
    next_attempt_at DATETIME NULL,
    lease_owner VARCHAR(100) NULL,
    lease_expires_at DATETIME NULL,
    last_enqueued_at DATETIME NULL,
    result_json MEDIUMTEXT NULL,
    failure_code VARCHAR(80) NULL,
    PRIMARY KEY (task_id),
    UNIQUE KEY uk_ai_task_idempotency (owner_user_id, task_type, idempotency_endpoint, idempotency_key),
    KEY idx_ai_task_owner_created (owner_user_id, created_at),
    KEY idx_ai_task_status_next (status, next_attempt_at),
    KEY idx_ai_task_status_lease (status, lease_expires_at),
    KEY idx_ai_task_status_enqueued (status, last_enqueued_at),
    CONSTRAINT fk_ai_task_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_precheck_task_material (
    task_id VARCHAR(80) NOT NULL,
    stage_material_id VARCHAR(80) NOT NULL,
    material_order INT NOT NULL,
    document_role_hint VARCHAR(50) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    sha256 CHAR(64) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (task_id, stage_material_id),
    KEY idx_ai_task_material_ref (stage_material_id, task_id),
    CONSTRAINT fk_ai_task_material_task FOREIGN KEY (task_id) REFERENCES ai_precheck_task(task_id),
    CONSTRAINT fk_ai_task_material_stage FOREIGN KEY (stage_material_id) REFERENCES ai_stage_material(stage_material_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_precheck_capacity (
    capacity_key VARCHAR(40) NOT NULL,
    outstanding_count INT NOT NULL DEFAULT 0,
    max_outstanding INT NOT NULL DEFAULT 1000,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (capacity_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO ai_precheck_capacity (capacity_key, outstanding_count, max_outstanding)
VALUES ('DOCUMENT_PRECHECK', 0, 1000)
ON DUPLICATE KEY UPDATE capacity_key = VALUES(capacity_key);

CREATE TABLE ai_precheck_outbox (
    outbox_id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(80) NOT NULL,
    task_id VARCHAR(80) NOT NULL,
    event_type VARCHAR(80) NOT NULL,
    schema_version VARCHAR(20) NOT NULL,
    destination VARCHAR(20) NOT NULL DEFAULT 'MAIN',
    payload_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    available_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    attempt_count INT NOT NULL DEFAULT 0,
    lease_owner VARCHAR(100) NULL,
    lease_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    published_at DATETIME NULL,
    last_error_code VARCHAR(80) NULL,
    PRIMARY KEY (outbox_id),
    UNIQUE KEY uk_ai_outbox_event (event_id),
    KEY idx_ai_outbox_due (status, available_at, outbox_id),
    KEY idx_ai_outbox_task (task_id, created_at),
    CONSTRAINT fk_ai_outbox_task FOREIGN KEY (task_id) REFERENCES ai_precheck_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_precheck_inbox (
    event_id VARCHAR(80) NOT NULL,
    task_id VARCHAR(80) NULL,
    trace_id VARCHAR(64) NULL,
    outcome VARCHAR(40) NOT NULL,
    safe_error_code VARCHAR(80) NULL,
    processed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (event_id),
    KEY idx_ai_inbox_task_time (task_id, processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_precheck_audit (
    audit_id BIGINT NOT NULL AUTO_INCREMENT,
    task_id VARCHAR(80) NULL,
    stage_material_id VARCHAR(80) NULL,
    event_id VARCHAR(80) NULL,
    actor_user_id BIGINT NULL,
    instance_id VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    event_type VARCHAR(50) NOT NULL,
    from_status VARCHAR(20) NULL,
    to_status VARCHAR(20) NULL,
    attempt_count INT NULL,
    safe_error_code VARCHAR(80) NULL,
    elapsed_ms BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (audit_id),
    KEY idx_ai_audit_task_time (task_id, created_at),
    KEY idx_ai_audit_stage_time (stage_material_id, created_at),
    KEY idx_ai_audit_event (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (permission_code, permission_name, module_name, permission_type, status)
VALUES ('ai:precheck:create', '创建材料预审任务', 'AI_PRECHECK', 'API', 'ENABLED'),
       ('ai:precheck:view', '查看本人预审任务与暂存材料', 'AI_PRECHECK', 'API', 'ENABLED')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), module_name = VALUES(module_name), status = 'ENABLED';

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('HOUSEHOLD_MANAGER', 'SYSTEM_ADMIN')
  AND p.permission_code IN ('ai:precheck:create', 'ai:precheck:view');
