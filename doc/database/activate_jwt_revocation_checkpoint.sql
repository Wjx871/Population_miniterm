-- =============================================================================
-- Issue #64: Explicit Checkpoint Activation & Monotonic Cutoff Advance Script
--
-- STANDALONE OPS TEMPLATE ONLY — NOT AUTO-EXECUTED BY STARTUP OR MIGRATION.
-- DO NOT run against production in Step 1. Verify only on isolated test DBs.
--
-- Operational Contract (ADR Section 4.1.1, 5.2, 6.2):
--   1. Confirm maintenance window has closed old-version (v_old) entrypoints
--      and V4_020_jwt_revocation.sql has already created GLOBAL_JWT_CUTOFF.
--   2. Explicitly transition checkpoint_status from UNINITIALIZED -> READY
--      (or monotonically advance an existing READY cutoff; NEVER resets
--      INVALID, NEVER inserts/initializes missing rows, NEVER lowers cutoff).
--   3. Set next whole UTC second epoch ms watermark strictly via:
--        (UNIX_TIMESTAMP() + 1) * 1000
--      STRICTLY FORBIDDEN: UNIX_TIMESTAMP(UTC_TIMESTAMP(...)) which causes
--      double timezone interpretation under non-UTC session time_zone values.
--   4. Never lower an existing min_valid_iat_epoch_ms watermark.
--   5. Wait until actual UTC time reaches min_valid_iat_epoch_ms
--      (UNIX_TIMESTAMP() * 1000 >= min_valid_iat_epoch_ms).
--   6. Verify checkpoint_status = 'READY' and valid cutoff before resuming
--      JWT issuance in the next deployment step.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Step 0: Verify target database identity and primary RW role before mutation.
-- -----------------------------------------------------------------------------
SELECT
    DATABASE() AS target_database,
    @@version AS mysql_version,
    @@session.time_zone AS session_time_zone,
    @@read_only AS read_only,
    CURRENT_USER() AS current_db_user;

DROP PROCEDURE IF EXISTS issue64_ops_activate_guard;
DELIMITER $$
CREATE PROCEDURE issue64_ops_activate_guard()
BEGIN
    DECLARE v_db VARCHAR(128) DEFAULT DATABASE();
    DECLARE v_read_only INT DEFAULT 0;
    DECLARE v_status VARCHAR(20) DEFAULT NULL;

    IF v_db IS NULL OR v_db = '' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE64_OPS_ABORT_NO_DATABASE_SELECTED';
    END IF;
    IF LOWER(v_db) IN ('mysql', 'information_schema', 'performance_schema', 'sys') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE64_OPS_ABORT_SYSTEM_SCHEMA';
    END IF;
    SELECT @@read_only INTO v_read_only;
    IF v_read_only <> 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE64_OPS_ABORT_READ_ONLY';
    END IF;

    SELECT checkpoint_status INTO v_status
    FROM jwt_revocation_checkpoint
    WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF';

    IF v_status IS NULL THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE64_OPS_ABORT_MISSING_CHECKPOINT_ROW';
    END IF;
    IF v_status NOT IN ('UNINITIALIZED', 'READY') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE64_OPS_ABORT_CHECKPOINT_INVALID';
    END IF;
END$$
DELIMITER ;
CALL issue64_ops_activate_guard();
DROP PROCEDURE IF EXISTS issue64_ops_activate_guard;

-- -----------------------------------------------------------------------------
-- Step 2-4: Explicit UNINITIALIZED -> READY activation or monotonic advance.
-- Uses parameterless (UNIX_TIMESTAMP() + 1) * 1000 — invariant to session TZ.
-- -----------------------------------------------------------------------------
UPDATE jwt_revocation_checkpoint
SET
    checkpoint_status = 'READY',
    min_valid_iat_epoch_ms = GREATEST(
        COALESCE(min_valid_iat_epoch_ms, 0),
        (UNIX_TIMESTAMP() + 1) * 1000
    ),
    activated_at = COALESCE(activated_at, UTC_TIMESTAMP(3)),
    updated_by = 'OPS_EXPLICIT_CUTOVER',
    updated_at = UTC_TIMESTAMP(3)
WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF'
  AND checkpoint_status IN ('UNINITIALIZED', 'READY');

-- -----------------------------------------------------------------------------
-- Step 5: Wait until real UTC epoch reaches min_valid_iat_epoch_ms (<= 1.1s).
-- -----------------------------------------------------------------------------
SELECT SLEEP(1.1) AS waited_for_whole_utc_second_boundary;

-- -----------------------------------------------------------------------------
-- Step 6: Verify checkpoint is READY, cutoff >= 0, and real UTC >= cutoff.
-- Proceed to resume JWT issuance ONLY when readiness_verified = 1.
-- -----------------------------------------------------------------------------
SELECT
    checkpoint_key,
    checkpoint_status,
    min_valid_iat_epoch_ms,
    UNIX_TIMESTAMP() * 1000 AS current_utc_epoch_ms,
    (
        checkpoint_status = 'READY'
        AND min_valid_iat_epoch_ms IS NOT NULL
        AND min_valid_iat_epoch_ms >= 0
        AND (UNIX_TIMESTAMP() * 1000) >= min_valid_iat_epoch_ms
    ) AS readiness_verified,
    activated_at,
    updated_by,
    updated_at
FROM jwt_revocation_checkpoint
WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF';
