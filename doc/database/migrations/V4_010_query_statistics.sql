-- Phase 11 query-only indexes. Safe to execute repeatedly on MySQL 8.
DROP PROCEDURE IF EXISTS phase11_add_index;
DELIMITER $$
CREATE PROCEDURE phase11_add_index(IN table_name_value VARCHAR(64), IN index_name_value VARCHAR(64), IN ddl_value TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE()
      AND table_name=table_name_value AND index_name=index_name_value) THEN
    SET @ddl=ddl_value; PREPARE stmt FROM @ddl; EXECUTE stmt; DEALLOCATE PREPARE stmt;
  END IF;
END$$
DELIMITER ;
CALL phase11_add_index('person','idx_person_query','CREATE INDEX idx_person_query ON person(current_status_code,gender,birth_date)');
CALL phase11_add_index('operation_log','idx_operation_log_query','CREATE INDEX idx_operation_log_query ON operation_log(operation_time,operation_type,module_name,operation_result,user_id)');
CALL phase11_add_index('migration_in','idx_migration_in_query','CREATE INDEX idx_migration_in_query ON migration_in(in_date,business_status,migration_type,person_id)');
CALL phase11_add_index('migration_out','idx_migration_out_query','CREATE INDEX idx_migration_out_query ON migration_out(out_date,business_status,migration_type,person_id)');
DROP PROCEDURE IF EXISTS phase11_add_index;
