-- Phase 08 household master data indexes. Legacy residents is intentionally retained but unused.
DROP PROCEDURE IF EXISTS phase08_add_index;
DELIMITER $$
CREATE PROCEDURE phase08_add_index(IN p_table VARCHAR(64), IN p_index VARCHAR(64), IN p_ddl TEXT)
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=p_table AND index_name=p_index) THEN
    SET @phase08_ddl=p_ddl; PREPARE phase08_stmt FROM @phase08_ddl; EXECUTE phase08_stmt; DEALLOCATE PREPARE phase08_stmt;
  END IF;
END$$
DELIMITER ;
CALL phase08_add_index('household','idx_household_region_status','CREATE INDEX idx_household_region_status ON household(region_code,status)');
CALL phase08_add_index('household_member','idx_household_member_household_status','CREATE INDEX idx_household_member_household_status ON household_member(household_id,status)');
CALL phase08_add_index('household_member','idx_household_member_person_status','CREATE INDEX idx_household_member_person_status ON household_member(person_id,status)');
DROP PROCEDURE IF EXISTS phase08_add_index;

INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES
 ('household:view','查看家庭户主数据','HOUSEHOLD','API','ENABLED'),
 ('household:edit','维护家庭户主数据','HOUSEHOLD','API','ENABLED')
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id)
SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code IN('QUERY_VIEWER','POPULATION_MANAGER','APPROVER') AND p.permission_code='household:view')
   OR (r.role_code='HOUSEHOLD_MANAGER' AND p.permission_code IN('household:view','household:edit'))
   OR r.role_code='SYSTEM_ADMIN';
