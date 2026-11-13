-- =============================================================================
-- V4_020: Issue #64 - Durable JWT revocation store and fail-closed checkpoint.
--
-- Target: MySQL 8.4, apply after V4_019 on a backed-up database.
-- IMPORTANT:
--   1. This migration intentionally does NOT hardcode `USE population_miniterm;`
--      so the caller/operator must explicitly select the target database in the
--      session (preventing accidental execution against a production database
--      during isolated testing).
--   2. This migration ONLY creates two independent #64 tables (`jwt_revocation`
--      and `jwt_revocation_checkpoint`). It never modifies existing business
--      tables or business rows.
--   3. Initial checkpoint status is strictly `UNINITIALIZED` with NULL cutoff.
--      It NEVER auto-activates as `READY` or `0`. Re-running this script is
--      idempotent and NEVER overwrites an existing `READY` cutoff watermark.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. Preflight database identity & existing-schema compatibility verification.
-- -----------------------------------------------------------------------------
SELECT
    DATABASE() AS target_database,
    @@version AS mysql_version,
    @@hostname AS server_host,
    @@port AS server_port,
    @@read_only AS read_only,
    CURRENT_USER() AS current_db_user;

DROP PROCEDURE IF EXISTS v4_020_preflight_guard;
DELIMITER $$
CREATE PROCEDURE v4_020_preflight_guard()
BEGIN
    DECLARE v_db VARCHAR(128) DEFAULT DATABASE();
    DECLARE v_read_only INT DEFAULT 0;
    DECLARE v_super_read_only INT DEFAULT 0;
    DECLARE v_rev_table_exists INT DEFAULT 0;
    DECLARE v_rev_col_count INT DEFAULT 0;
    DECLARE v_rev_idx_count INT DEFAULT 0;
    DECLARE v_cp_table_exists INT DEFAULT 0;
    DECLARE v_cp_col_count INT DEFAULT 0;
    DECLARE v_cp_chk_count INT DEFAULT 0;

    IF v_db IS NULL OR v_db = '' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_020_ABORT_NO_DATABASE_SELECTED';
    END IF;

    IF LOWER(v_db) IN ('mysql', 'information_schema', 'performance_schema', 'sys') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_020_ABORT_SYSTEM_SCHEMA_FORBIDDEN';
    END IF;

    SELECT @@read_only, @@super_read_only INTO v_read_only, v_super_read_only;
    IF v_read_only <> 0 OR v_super_read_only <> 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V4_020_ABORT_READ_ONLY_INSTANCE';
    END IF;

    -- Verify existing jwt_revocation structure if table already exists
    SELECT COUNT(*) INTO v_rev_table_exists
    FROM information_schema.tables
    WHERE table_schema = v_db AND table_name = 'jwt_revocation';

    IF v_rev_table_exists > 0 THEN
        SELECT COUNT(*) INTO v_rev_col_count
        FROM information_schema.columns
        WHERE table_schema = v_db
          AND table_name = 'jwt_revocation'
          AND (
              (column_name = 'revocation_id' AND data_type = 'bigint' AND is_nullable = 'NO')
              OR (column_name = 'jti_sha256' AND data_type = 'char' AND character_maximum_length = 64 AND is_nullable = 'NO')
              OR (column_name = 'user_id' AND data_type = 'bigint' AND is_nullable = 'YES')
              OR (column_name = 'expires_at_epoch_ms' AND data_type = 'bigint' AND is_nullable = 'NO')
              OR (column_name = 'token_expires_at' AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'NO')
              OR (column_name = 'revoked_at' AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'NO')
              OR (column_name = 'revocation_reason' AND data_type = 'varchar' AND is_nullable = 'NO')
              OR (column_name = 'instance_id' AND data_type = 'varchar')
              OR (column_name = 'trace_id' AND data_type = 'varchar')
              OR (column_name = 'created_at' AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'NO')
          );

        SELECT COUNT(DISTINCT index_name) INTO v_rev_idx_count
        FROM information_schema.statistics
        WHERE table_schema = v_db
          AND table_name = 'jwt_revocation'
          AND (
              (index_name = 'PRIMARY' AND column_name = 'revocation_id')
              OR (index_name = 'uk_jwt_revocation_jti_sha256' AND non_unique = 0 AND column_name = 'jti_sha256')
              OR (index_name = 'idx_jwt_revocation_expires_ms' AND column_name = 'expires_at_epoch_ms')
              OR (index_name = 'idx_jwt_revocation_user_id' AND column_name = 'user_id')
          );

        IF v_rev_col_count <> 10 OR v_rev_idx_count <> 4 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V4_020_ABORT_EXISTING_JWT_REVOCATION_SCHEMA_MISMATCH';
        END IF;
    END IF;

    -- Verify existing jwt_revocation_checkpoint structure if table already exists
    SELECT COUNT(*) INTO v_cp_table_exists
    FROM information_schema.tables
    WHERE table_schema = v_db AND table_name = 'jwt_revocation_checkpoint';

    IF v_cp_table_exists > 0 THEN
        SELECT COUNT(*) INTO v_cp_col_count
        FROM information_schema.columns
        WHERE table_schema = v_db
          AND table_name = 'jwt_revocation_checkpoint'
          AND (
              (column_name = 'checkpoint_key' AND data_type = 'varchar' AND is_nullable = 'NO')
              OR (column_name = 'checkpoint_status' AND data_type = 'varchar' AND is_nullable = 'NO')
              OR (column_name = 'min_valid_iat_epoch_ms' AND data_type = 'bigint' AND is_nullable = 'YES')
              OR (column_name = 'activated_at' AND data_type = 'datetime' AND datetime_precision = 3)
              OR (column_name = 'updated_by' AND data_type = 'varchar')
              OR (column_name = 'updated_at' AND data_type = 'datetime' AND datetime_precision = 3 AND is_nullable = 'NO')
          );

        SELECT COUNT(*) INTO v_cp_chk_count
        FROM information_schema.table_constraints
        WHERE table_schema = v_db
          AND table_name = 'jwt_revocation_checkpoint'
          AND constraint_type = 'CHECK'
          AND constraint_name IN ('chk_jwt_checkpoint_status', 'chk_jwt_checkpoint_ready_cutoff');

        IF v_cp_col_count <> 6 OR v_cp_chk_count <> 2 THEN
            SIGNAL SQLSTATE '45000'
                SET MESSAGE_TEXT = 'V4_020_ABORT_EXISTING_JWT_CHECKPOINT_SCHEMA_MISMATCH';
        END IF;
    END IF;
END$$
DELIMITER ;
CALL v4_020_preflight_guard();
DROP PROCEDURE IF EXISTS v4_020_preflight_guard;

-- -----------------------------------------------------------------------------
-- 1. Authoritative revoked-JTI table (never stores raw JWT or raw JTI).
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS jwt_revocation (
    revocation_id BIGINT NOT NULL AUTO_INCREMENT,
    jti_sha256 CHAR(64) NOT NULL COMMENT 'Lowercase 64-char SHA-256 hex digest of JWT jti claim',
    user_id BIGINT NULL COMMENT 'Optional sys_user.user_id for security audit',
    expires_at_epoch_ms BIGINT NOT NULL COMMENT 'JWT exp claim in UTC epoch milliseconds (authoritative expiration comparison)',
    token_expires_at DATETIME(3) NOT NULL COMMENT 'Human-readable UTC expiration timestamp (for DBA inspection only)',
    revoked_at DATETIME(3) NOT NULL COMMENT 'Authoritative revocation commit timestamp (UTC)',
    revocation_reason VARCHAR(32) NOT NULL DEFAULT 'LOGOUT' COMMENT 'LOGOUT | ADMIN_REVOKE',
    instance_id VARCHAR(64) NULL COMMENT 'App instance id from S2TraceContext',
    trace_id VARCHAR(64) NULL COMMENT 'Request trace id from S2TraceContext',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (revocation_id),
    UNIQUE KEY uk_jwt_revocation_jti_sha256 (jti_sha256),
    KEY idx_jwt_revocation_expires_ms (expires_at_epoch_ms),
    KEY idx_jwt_revocation_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 2. Fail-closed global cutover checkpoint table.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS jwt_revocation_checkpoint (
    checkpoint_key VARCHAR(64) NOT NULL,
    checkpoint_status VARCHAR(20) NOT NULL DEFAULT 'UNINITIALIZED' COMMENT 'UNINITIALIZED | READY | INVALID',
    min_valid_iat_epoch_ms BIGINT NULL COMMENT 'Must be non-null >= 0 when READY; reject tokens with iat_ms < cutoff',
    activated_at DATETIME(3) NULL COMMENT 'First cutover activation timestamp (UTC)',
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (checkpoint_key),
    CONSTRAINT chk_jwt_checkpoint_status CHECK (checkpoint_status IN ('UNINITIALIZED', 'READY', 'INVALID')),
    CONSTRAINT chk_jwt_checkpoint_ready_cutoff CHECK (
        checkpoint_status <> 'READY' OR (min_valid_iat_epoch_ms IS NOT NULL AND min_valid_iat_epoch_ms >= 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Idempotent non-overwriting placeholder initialization.
--    Inserts GLOBAL_JWT_CUTOFF strictly as UNINITIALIZED + NULL only when absent.
--    NEVER auto-activates to READY and NEVER overwrites an existing READY cutoff.
-- -----------------------------------------------------------------------------
INSERT INTO jwt_revocation_checkpoint (
    checkpoint_key, checkpoint_status, min_valid_iat_epoch_ms, updated_by
)
SELECT 'GLOBAL_JWT_CUTOFF', 'UNINITIALIZED', NULL, 'V4_020_DDL_INIT'
WHERE NOT EXISTS (
    SELECT 1 FROM jwt_revocation_checkpoint WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF'
);

-- -----------------------------------------------------------------------------
-- 4. Post-migration read-only verification snapshot.
-- -----------------------------------------------------------------------------
SELECT
    checkpoint_key,
    checkpoint_status,
    min_valid_iat_epoch_ms,
    activated_at,
    updated_by,
    updated_at
FROM jwt_revocation_checkpoint
WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF';
