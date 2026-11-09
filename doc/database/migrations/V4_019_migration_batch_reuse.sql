-- =============================================================================
-- V4_019: Issue #19 - cancelled MIGRATION_IN drafts must release their batch.
--
-- Target: MySQL 8.4, apply after V4_018 on a backed-up isolated database.
-- This migration is additive: it never deletes migration_in/business_application
-- rows and never rewrites the historical transfer_batch_no value.
--
-- Structure changes:
--   1. migration_in.active_transfer_batch_no (STORED generated column):
--        business_status = 'CANCELLED' -> NULL
--        otherwise                      -> transfer_batch_no
--   2. UNIQUE INDEX uk_migration_in_active_batch(active_transfer_batch_no)
--      replaces the unconditional uk_migration_in_batch(transfer_batch_no).
--   3. Exact historical state alignment for rows whose generic application is
--      already CANCELLED but whose migration_in detail is still DRAFT. The
--      original values are backed up before the UPDATE; transfer_batch_no is
--      never changed.
--
-- H2 parity note: src/test/resources/schema.sql uses the same CASE expression
-- as a computed column without the STORED keyword because H2 2.3.232 rejects
-- STORED; MySQL 8.4 uses STORED. Semantics are identical: CANCELLED -> NULL,
-- every other status keeps the original transfer_batch_no for unique indexing.
--
-- Preconditions / stop conditions (the preflight procedure below enforces them):
--   * No duplicate active (non-CANCELLED) transfer_batch_no values.
--   * No MIGRATION_IN where application.status='CANCELLED' but detail status is
--     outside ('DRAFT','CANCELLED').
--   * No MIGRATION_IN where application.status<>'CANCELLED' but detail status is
--     already CANCELLED.
-- If any check signals SQLSTATE 45000, stop and reconcile manually. Do not
-- bypass a check by editing data in this script.
--
-- Rollback / failure handling:
--   * DDL steps are guarded and repeatable; if a step fails before the old index
--     is dropped, the old uk_migration_in_batch remains in place and data stays
--     protected (business still rejects reuse, safe but unfixed).
--   * The backfill is a single UPDATE and can be rolled back while it runs. The
--     backup table retains original business_status/transfer_batch_no/version so
--     the state can be audited or restored manually if needed.
--   * If the old index drop fails, keep the migration open for repair; do NOT
--     manually delete rows or set transfer_batch_no=NULL.
--   * If validation after upgrade fails, stop application writes, inspect
--     migration_in_issue19_backup, and rebuild the old unique index (the backfill
--     only changes business_status, so historical batch values are unchanged).
-- =============================================================================
USE population_miniterm;

-- -----------------------------------------------------------------------------
-- 0. Preflight read-only statistics (record the output before executing DDL).
-- -----------------------------------------------------------------------------
-- Q1: exact target rows to be state-aligned (application CANCELLED, detail DRAFT)
SELECT a.application_id, a.application_no, a.status AS application_status,
       m.in_id, m.business_status, m.transfer_batch_no,
       m.person_id, m.to_region_code, m.to_household_id,
       m.version, m.created_at, m.updated_at
FROM business_application a
JOIN migration_in m ON m.application_id = a.application_id
WHERE a.business_type = 'MIGRATION_IN'
  AND a.status = 'CANCELLED'
  AND m.business_status = 'DRAFT'
ORDER BY a.application_id;

-- Q2: target statistics
SELECT COUNT(*) AS affected_rows,
       SUM(CASE WHEN m.transfer_batch_no IS NOT NULL THEN 1 ELSE 0 END) AS with_batch,
       SUM(CASE WHEN m.transfer_batch_no IS NULL THEN 1 ELSE 0 END) AS without_batch,
       COUNT(DISTINCT m.transfer_batch_no) AS distinct_batches,
       COALESCE(MAX(m.in_id), 0) AS max_in_id,
       COALESCE(SUM(m.in_id), 0) AS in_id_checksum
FROM business_application a
JOIN migration_in m ON m.application_id = a.application_id
WHERE a.business_type = 'MIGRATION_IN'
  AND a.status = 'CANCELLED'
  AND m.business_status = 'DRAFT';

-- Q3: adjacent anomalies (must be zero; do not auto-repair)
SELECT a.application_id, a.application_no, a.status AS application_status,
       m.in_id, m.business_status, m.transfer_batch_no
FROM business_application a
JOIN migration_in m ON m.application_id = a.application_id
WHERE a.business_type = 'MIGRATION_IN'
  AND ((a.status = 'CANCELLED' AND m.business_status NOT IN ('DRAFT','CANCELLED'))
    OR (a.status <> 'CANCELLED' AND m.business_status = 'CANCELLED'))
ORDER BY a.application_id;

-- Q4: duplicate active batch claims (must be zero)
SELECT m.transfer_batch_no, COUNT(*) AS active_rows
FROM migration_in m
WHERE m.transfer_batch_no IS NOT NULL
  AND m.business_status <> 'CANCELLED'
GROUP BY m.transfer_batch_no
HAVING COUNT(*) > 1;

-- Preflight guard procedure: fail fast instead of silently proceeding on dirty data.
DROP PROCEDURE IF EXISTS issue19_preflight;
DELIMITER $$
CREATE PROCEDURE issue19_preflight()
BEGIN
  DECLARE duplicate_active INT DEFAULT 0;
  DECLARE cancelled_anomaly INT DEFAULT 0;
  DECLARE unexpected_cancelled_detail INT DEFAULT 0;

  SELECT COUNT(*) INTO duplicate_active FROM (
    SELECT transfer_batch_no
    FROM migration_in
    WHERE transfer_batch_no IS NOT NULL AND business_status <> 'CANCELLED'
    GROUP BY transfer_batch_no
    HAVING COUNT(*) > 1
  ) duplicates;
  IF duplicate_active > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE19_PRECHECK_DUPLICATE_ACTIVE_BATCH';
  END IF;

  SELECT COUNT(*) INTO cancelled_anomaly
  FROM business_application a
  JOIN migration_in m ON m.application_id = a.application_id
  WHERE a.business_type = 'MIGRATION_IN'
    AND a.status = 'CANCELLED'
    AND m.business_status NOT IN ('DRAFT','CANCELLED');
  IF cancelled_anomaly > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE19_PRECHECK_CANCELLED_DETAIL_STATE_ANOMALY';
  END IF;

  SELECT COUNT(*) INTO unexpected_cancelled_detail
  FROM business_application a
  JOIN migration_in m ON m.application_id = a.application_id
  WHERE a.business_type = 'MIGRATION_IN'
    AND a.status <> 'CANCELLED'
    AND m.business_status = 'CANCELLED';
  IF unexpected_cancelled_detail > 0 THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE19_PRECHECK_UNEXPECTED_CANCELLED_DETAIL';
  END IF;
END$$
DELIMITER ;
CALL issue19_preflight();
DROP PROCEDURE IF EXISTS issue19_preflight;

-- -----------------------------------------------------------------------------
-- 1. Backup exact target rows. Re-running uses INSERT IGNORE so the first
--    snapshot (original DRAFT/original batch/original version) is preserved.
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS migration_in_issue19_backup (
  backup_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  in_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  original_business_status VARCHAR(30) NOT NULL,
  original_transfer_batch_no VARCHAR(40) NULL,
  original_version INT NOT NULL,
  application_status VARCHAR(30) NOT NULL,
  application_version INT NOT NULL,
  backup_run VARCHAR(64) NOT NULL,
  backup_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_issue19_backup_in (in_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT IGNORE INTO migration_in_issue19_backup
  (in_id, application_id, original_business_status, original_transfer_batch_no,
   original_version, application_status, application_version, backup_run)
SELECT m.in_id, m.application_id, m.business_status, m.transfer_batch_no,
       m.version, a.status, a.version,
       CONCAT('issue19-', DATE_FORMAT(NOW(), '%Y%m%d%H%i%s'))
FROM business_application a
JOIN migration_in m ON m.application_id = a.application_id
WHERE a.business_type = 'MIGRATION_IN'
  AND a.status = 'CANCELLED'
  AND m.business_status = 'DRAFT';

-- -----------------------------------------------------------------------------
-- 2. Add the STORED generated column (guarded / repeatable).
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS issue19_add_active_column;
DELIMITER $$
CREATE PROCEDURE issue19_add_active_column()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'migration_in'
      AND column_name = 'active_transfer_batch_no'
  ) THEN
    ALTER TABLE migration_in
      ADD COLUMN active_transfer_batch_no VARCHAR(40)
        GENERATED ALWAYS AS (CASE WHEN business_status = 'CANCELLED' THEN NULL ELSE transfer_batch_no END) STORED
        COMMENT 'Issue19: active batch claim; NULL when the migration detail is CANCELLED';
  END IF;
END$$
DELIMITER ;
CALL issue19_add_active_column();
DROP PROCEDURE IF EXISTS issue19_add_active_column;

-- -----------------------------------------------------------------------------
-- 3. Create the conditional unique index while the old unconditional unique
--    index still exists (no window without any unique protection).
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS issue19_add_active_index;
DELIMITER $$
CREATE PROCEDURE issue19_add_active_index()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'migration_in'
      AND index_name = 'uk_migration_in_active_batch'
  ) THEN
    ALTER TABLE migration_in
      ADD UNIQUE INDEX uk_migration_in_active_batch (active_transfer_batch_no);
  END IF;
END$$
DELIMITER ;
CALL issue19_add_active_index();
DROP PROCEDURE IF EXISTS issue19_add_active_index;

-- -----------------------------------------------------------------------------
-- 4. Controlled historical state alignment. Only the exact target set is
--    touched: business_application already CANCELLED + migration_in DRAFT.
--    transfer_batch_no is never modified; the generated column becomes NULL.
-- -----------------------------------------------------------------------------
SET @issue19_backfill_expected = (
  SELECT COUNT(*)
  FROM business_application a
  JOIN migration_in m ON m.application_id = a.application_id
  WHERE a.business_type = 'MIGRATION_IN'
    AND a.status = 'CANCELLED'
    AND m.business_status = 'DRAFT'
);

UPDATE migration_in m
JOIN business_application a ON a.application_id = m.application_id
SET m.business_status = 'CANCELLED',
    m.version = m.version + 1,
    m.updated_at = CURRENT_TIMESTAMP
WHERE a.business_type = 'MIGRATION_IN'
  AND a.status = 'CANCELLED'
  AND m.business_status = 'DRAFT';

SET @issue19_backfill_actual = ROW_COUNT();
SELECT @issue19_backfill_expected AS expected_rows, @issue19_backfill_actual AS updated_rows,
       (SELECT COUNT(*) FROM migration_in_issue19_backup) AS backup_rows;

DROP PROCEDURE IF EXISTS issue19_assert_backfill;
DELIMITER $$
CREATE PROCEDURE issue19_assert_backfill()
BEGIN
  IF @issue19_backfill_expected <> @issue19_backfill_actual THEN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ISSUE19_BACKFILL_ROW_COUNT_MISMATCH';
  END IF;
END$$
DELIMITER ;
CALL issue19_assert_backfill();
DROP PROCEDURE IF EXISTS issue19_assert_backfill;

-- -----------------------------------------------------------------------------
-- 5. Drop the old unconditional unique index only after the new conditional
--    unique index exists and the historical rows have been state-aligned.
-- -----------------------------------------------------------------------------
DROP PROCEDURE IF EXISTS issue19_drop_legacy_batch_index;
DELIMITER $$
CREATE PROCEDURE issue19_drop_legacy_batch_index()
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.statistics
    WHERE table_schema = DATABASE() AND table_name = 'migration_in'
      AND index_name = 'uk_migration_in_batch'
  ) THEN
    ALTER TABLE migration_in DROP INDEX uk_migration_in_batch;
  END IF;
END$$
DELIMITER ;
CALL issue19_drop_legacy_batch_index();
DROP PROCEDURE IF EXISTS issue19_drop_legacy_batch_index;

-- -----------------------------------------------------------------------------
-- 6. Post-upgrade verification (all checks must return zero / expected values).
-- -----------------------------------------------------------------------------
-- 6.1 No remaining target-state mismatch.
SELECT COUNT(*) AS remaining_cancelled_draft
FROM business_application a
JOIN migration_in m ON m.application_id = a.application_id
WHERE a.business_type = 'MIGRATION_IN'
  AND a.status = 'CANCELLED'
  AND m.business_status = 'DRAFT';

-- 6.2 At most one active row per batch.
SELECT m.transfer_batch_no, COUNT(*) AS active_rows
FROM migration_in m
WHERE m.active_transfer_batch_no IS NOT NULL
GROUP BY m.transfer_batch_no
HAVING COUNT(*) > 1;

-- 6.3 Cancelled rows keep the original batch value but do not occupy the active key.
SELECT in_id, application_id, business_status, transfer_batch_no, active_transfer_batch_no
FROM migration_in
WHERE business_status = 'CANCELLED'
ORDER BY in_id;

-- 6.4 Expected structure: only the conditional unique index remains.
SELECT index_name, non_unique, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'migration_in'
  AND index_name IN ('uk_migration_in_batch', 'uk_migration_in_active_batch')
ORDER BY index_name, seq_in_index;

-- 6.5 Row counts are unchanged (no DELETE happened).
SELECT
  (SELECT COUNT(*) FROM migration_in) AS migration_in_rows,
  (SELECT COUNT(*) FROM business_application) AS business_application_rows,
  (SELECT COUNT(*) FROM migration_in_issue19_backup) AS backup_rows;
