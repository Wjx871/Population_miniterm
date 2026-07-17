-- Phase 15: direct person creation must carry a data-scope ownership anchor.
-- Execute this migration in the target database before deploying the matching backend.
-- MySQL 8; safe to run repeatedly.

DROP PROCEDURE IF EXISTS phase15_add_column;
DELIMITER $$
CREATE PROCEDURE phase15_add_column(IN p_column VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'person' AND column_name = p_column
    ) THEN
        SET @phase15_ddl = p_ddl;
        PREPARE phase15_stmt FROM @phase15_ddl;
        EXECUTE phase15_stmt;
        DEALLOCATE PREPARE phase15_stmt;
    END IF;
END$$
DELIMITER ;
CALL phase15_add_column('created_by_user_id', 'ALTER TABLE person ADD COLUMN created_by_user_id BIGINT NULL AFTER current_status_code');
CALL phase15_add_column('created_department_id', 'ALTER TABLE person ADD COLUMN created_department_id BIGINT NULL AFTER created_by_user_id');
CALL phase15_add_column('created_region_code', 'ALTER TABLE person ADD COLUMN created_region_code VARCHAR(20) NULL AFTER created_department_id');
DROP PROCEDURE phase15_add_column;

DROP PROCEDURE IF EXISTS phase15_add_index;
DELIMITER $$
CREATE PROCEDURE phase15_add_index(IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'person' AND index_name = p_index
    ) THEN
        SET @phase15_ddl = p_ddl;
        PREPARE phase15_stmt FROM @phase15_ddl;
        EXECUTE phase15_stmt;
        DEALLOCATE PREPARE phase15_stmt;
    END IF;
END$$
DELIMITER ;
CALL phase15_add_index('idx_person_created_by_user', 'CREATE INDEX idx_person_created_by_user ON person(created_by_user_id)');
CALL phase15_add_index('idx_person_created_department', 'CREATE INDEX idx_person_created_department ON person(created_department_id)');
CALL phase15_add_index('idx_person_created_region', 'CREATE INDEX idx_person_created_region ON person(created_region_code)');
DROP PROCEDURE phase15_add_index;

-- Backfill only records that can be attributed unambiguously to exactly one
-- PERSON_CREATE audit event in the same second. Unattributable legacy rows are
-- intentionally left NULL rather than assigning them to an arbitrary region.
UPDATE person p
JOIN (
    SELECT operation_time, MIN(user_id) AS user_id
    FROM operation_log
    WHERE operation_type = 'PERSON_CREATE'
    GROUP BY operation_time
    HAVING COUNT(*) = 1
) l ON l.operation_time = p.created_at
JOIN sys_user u ON u.user_id = l.user_id
LEFT JOIN sys_department d ON d.department_id = u.department_id
SET p.created_by_user_id = u.user_id,
    p.created_department_id = u.department_id,
    p.created_region_code = d.region_code
WHERE p.created_by_user_id IS NULL
  AND NOT EXISTS (SELECT 1 FROM residence r WHERE r.person_id = p.person_id)
  AND NOT EXISTS (SELECT 1 FROM business_application a WHERE a.target_person_id = p.person_id);
