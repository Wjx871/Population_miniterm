-- V4_016: S4-A entity-resolution sessions and monotonic person versions.
-- Apply once to an isolated, backed-up MySQL 8 database after V4_015.
USE population_miniterm;

ALTER TABLE person
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '人员实体单调版本';

CREATE TABLE ai_resolution_session (
    resolution_id BIGINT NOT NULL AUTO_INCREMENT,
    resolution_token_sha256 CHAR(64) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    task_id VARCHAR(80) NOT NULL,
    task_version BIGINT NOT NULL,
    request_digest CHAR(64) NOT NULL,
    scope_digest CHAR(64) NOT NULL,
    migration_type VARCHAR(50) NOT NULL,
    to_region_code VARCHAR(20) NOT NULL,
    transfer_batch_no VARCHAR(40) NULL,
    resolution_status VARCHAR(30) NOT NULL,
    confirmable TINYINT(1) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    expires_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (resolution_id),
    UNIQUE KEY uk_ai_resolution_token (resolution_token_sha256),
    KEY idx_ai_resolution_owner_task_status (owner_user_id, task_id, status, expires_at),
    CONSTRAINT fk_ai_resolution_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user(user_id),
    CONSTRAINT fk_ai_resolution_task FOREIGN KEY (task_id) REFERENCES ai_precheck_task(task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE ai_resolution_candidate (
    candidate_id BIGINT NOT NULL AUTO_INCREMENT,
    resolution_id BIGINT NOT NULL,
    candidate_token_sha256 CHAR(64) NOT NULL,
    entity_type VARCHAR(20) NOT NULL,
    entity_id BIGINT NOT NULL,
    entity_version BIGINT NOT NULL,
    entity_status VARCHAR(30) NOT NULL,
    state_digest CHAR(64) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (candidate_id),
    UNIQUE KEY uk_ai_resolution_candidate_token (candidate_token_sha256),
    UNIQUE KEY uk_ai_resolution_candidate_entity (resolution_id, entity_type, entity_id),
    KEY idx_ai_resolution_candidate_parent (resolution_id, entity_type),
    CONSTRAINT fk_ai_resolution_candidate_session FOREIGN KEY (resolution_id)
        REFERENCES ai_resolution_session(resolution_id) ON DELETE CASCADE,
    CONSTRAINT ck_ai_resolution_candidate_type CHECK (entity_type IN ('PERSON', 'HOUSEHOLD'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
