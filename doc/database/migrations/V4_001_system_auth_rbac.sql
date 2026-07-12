-- Phase 01 authentication and RBAC upgrade for existing MySQL 8 databases.
-- The script preserves existing business data and can be executed repeatedly.
USE population_miniterm;

CREATE TABLE IF NOT EXISTS sys_department (
    department_id BIGINT NOT NULL AUTO_INCREMENT,
    department_code VARCHAR(50) NOT NULL,
    department_name VARCHAR(100) NOT NULL,
    parent_id BIGINT NULL,
    region_code VARCHAR(50) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    sort_no INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (department_id),
    UNIQUE KEY uk_sys_department_code (department_code),
    KEY idx_sys_department_parent_id (parent_id),
    KEY idx_sys_department_region_code (region_code),
    CONSTRAINT fk_sys_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

DELIMITER $$
DROP PROCEDURE IF EXISTS phase01_add_column$$
CREATE PROCEDURE phase01_add_column(IN table_name_value VARCHAR(64), IN column_name_value VARCHAR(64), IN ddl_value TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = table_name_value AND column_name = column_name_value
    ) THEN
        SET @ddl = ddl_value;
        PREPARE statement_value FROM @ddl;
        EXECUTE statement_value;
        DEALLOCATE PREPARE statement_value;
    END IF;
END$$

DROP PROCEDURE IF EXISTS phase01_add_index$$
CREATE PROCEDURE phase01_add_index(IN table_name_value VARCHAR(64), IN index_name_value VARCHAR(64), IN ddl_value TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = table_name_value AND index_name = index_name_value
    ) THEN
        SET @ddl = ddl_value;
        PREPARE statement_value FROM @ddl;
        EXECUTE statement_value;
        DEALLOCATE PREPARE statement_value;
    END IF;
END$$

DROP PROCEDURE IF EXISTS phase01_add_constraint$$
CREATE PROCEDURE phase01_add_constraint(IN table_name_value VARCHAR(64), IN constraint_name_value VARCHAR(64), IN ddl_value TEXT)
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.table_constraints
        WHERE constraint_schema = DATABASE() AND table_name = table_name_value
          AND constraint_name = constraint_name_value
    ) THEN
        SET @ddl = ddl_value;
        PREPARE statement_value FROM @ddl;
        EXECUTE statement_value;
        DEALLOCATE PREPARE statement_value;
    END IF;
END$$
DELIMITER ;

CALL phase01_add_column('sys_role', 'role_code',
    'ALTER TABLE sys_role ADD COLUMN role_code VARCHAR(50) NULL AFTER role_id');
CALL phase01_add_column('sys_role', 'role_level',
    'ALTER TABLE sys_role ADD COLUMN role_level VARCHAR(10) NULL AFTER role_name');
CALL phase01_add_column('sys_role', 'data_scope',
    'ALTER TABLE sys_role ADD COLUMN data_scope VARCHAR(20) NULL AFTER role_level');
CALL phase01_add_column('sys_permission', 'permission_type',
    'ALTER TABLE sys_permission ADD COLUMN permission_type VARCHAR(20) NOT NULL DEFAULT ''API'' AFTER module_name');
CALL phase01_add_column('sys_permission', 'status',
    'ALTER TABLE sys_permission ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT ''ENABLED'' AFTER permission_type');
CALL phase01_add_column('sys_user', 'department_id',
    'ALTER TABLE sys_user ADD COLUMN department_id BIGINT NULL AFTER role_id');
CALL phase01_add_column('sys_user', 'last_login_time',
    'ALTER TABLE sys_user ADD COLUMN last_login_time DATETIME NULL AFTER status');
CALL phase01_add_column('sys_user', 'last_login_ip',
    'ALTER TABLE sys_user ADD COLUMN last_login_ip VARCHAR(50) NULL AFTER last_login_time');
CALL phase01_add_column('operation_log', 'module_name',
    'ALTER TABLE operation_log ADD COLUMN module_name VARCHAR(100) NULL AFTER operation_type');
CALL phase01_add_column('operation_log', 'request_path',
    'ALTER TABLE operation_log ADD COLUMN request_path VARCHAR(255) NULL AFTER module_name');
CALL phase01_add_column('operation_log', 'request_method',
    'ALTER TABLE operation_log ADD COLUMN request_method VARCHAR(20) NULL AFTER request_path');
CALL phase01_add_column('operation_log', 'error_message',
    'ALTER TABLE operation_log ADD COLUMN error_message VARCHAR(500) NULL AFTER operation_result');
CALL phase01_add_column('operation_log', 'user_agent',
    'ALTER TABLE operation_log ADD COLUMN user_agent VARCHAR(500) NULL AFTER error_message');

UPDATE sys_role SET role_code = CONCAT('LEGACY_ROLE_', role_id) WHERE role_code IS NULL OR role_code = '';
UPDATE sys_role SET role_level = 'L1' WHERE role_level IS NULL OR role_level = '';
UPDATE sys_role SET data_scope = 'SELF' WHERE data_scope IS NULL OR data_scope = '';
UPDATE sys_role SET status = 'ENABLED' WHERE status = '启用';
UPDATE sys_role SET status = 'DISABLED' WHERE status = '停用';
UPDATE sys_user SET status = 'ENABLED' WHERE status = '启用';
UPDATE sys_user SET status = 'DISABLED' WHERE status = '停用';
ALTER TABLE sys_role MODIFY COLUMN role_code VARCHAR(50) NOT NULL;
ALTER TABLE sys_role MODIFY COLUMN role_level VARCHAR(10) NOT NULL;
ALTER TABLE sys_role MODIFY COLUMN data_scope VARCHAR(20) NOT NULL;

CALL phase01_add_index('sys_role', 'uk_sys_role_role_code',
    'ALTER TABLE sys_role ADD UNIQUE KEY uk_sys_role_role_code (role_code)');
CALL phase01_add_index('sys_user', 'idx_sys_user_department_id',
    'ALTER TABLE sys_user ADD KEY idx_sys_user_department_id (department_id)');
CALL phase01_add_constraint('sys_user', 'fk_sys_user_department',
    'ALTER TABLE sys_user ADD CONSTRAINT fk_sys_user_department FOREIGN KEY (department_id) REFERENCES sys_department (department_id)');

INSERT INTO sys_department (department_code, department_name, parent_id, region_code, status, sort_no)
VALUES ('SYSTEM', '系统管理部门', NULL, '110000', 'ENABLED', 10),
       ('QUERY', '查询统计部门', NULL, '110000', 'ENABLED', 20),
       ('POPULATION', '人口管理部门', NULL, '110000', 'ENABLED', 30),
       ('HOUSEHOLD', '户籍管理部门', NULL, '110000', 'ENABLED', 40),
       ('APPROVAL', '审批部门', NULL, '110000', 'ENABLED', 50)
ON DUPLICATE KEY UPDATE department_name = VALUES(department_name), region_code = VALUES(region_code),
                        status = VALUES(status), sort_no = VALUES(sort_no);

INSERT INTO sys_role (role_code, role_name, role_level, data_scope, description, status)
VALUES ('QUERY_VIEWER', '查询统计人员', 'L1', 'DEPARTMENT', '查询与统计', 'ENABLED'),
       ('POPULATION_MANAGER', '人口信息管理人员', 'L2', 'REGION', '人口信息经办', 'ENABLED'),
       ('HOUSEHOLD_MANAGER', '户籍管理人员', 'L2', 'REGION', '户籍信息经办', 'ENABLED'),
       ('APPROVER', '审批人员', 'L3', 'REGION', '业务审批', 'ENABLED'),
       ('SYSTEM_ADMIN', '系统管理员', 'L3', 'ALL', '系统管理', 'ENABLED')
ON DUPLICATE KEY UPDATE role_code = VALUES(role_code), role_name = VALUES(role_name), role_level = VALUES(role_level),
                        data_scope = VALUES(data_scope), description = VALUES(description), status = VALUES(status);

INSERT INTO sys_permission (permission_code, permission_name, module_name, permission_type, status)
VALUES ('system:user:view', '查看用户', 'SYSTEM', 'API', 'ENABLED'),
       ('system:user:manage', '管理用户', 'SYSTEM', 'API', 'ENABLED'),
       ('system:role:view', '查看角色', 'SYSTEM', 'API', 'ENABLED'),
       ('system:role:manage', '管理角色', 'SYSTEM', 'API', 'ENABLED'),
       ('population:view', '查看人口', 'POPULATION', 'API', 'ENABLED'),
       ('population:edit', '编辑人口', 'POPULATION', 'API', 'ENABLED'),
       ('household:view', '查看户籍', 'HOUSEHOLD', 'API', 'ENABLED'),
       ('household:edit', '编辑户籍', 'HOUSEHOLD', 'API', 'ENABLED'),
       ('migration:view', '查看迁移', 'MIGRATION', 'API', 'ENABLED'),
       ('migration:edit', '编辑迁移', 'MIGRATION', 'API', 'ENABLED'),
       ('approval:view', '查看审批', 'APPROVAL', 'API', 'ENABLED'),
       ('approval:handle', '处理审批', 'APPROVAL', 'API', 'ENABLED'),
       ('statistics:view', '查看统计', 'STATISTICS', 'API', 'ENABLED'),
       ('data:export', '导出数据', 'DATA', 'API', 'ENABLED'),
       ('log:view', '查看日志', 'LOG', 'API', 'ENABLED')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), module_name = VALUES(module_name),
                        permission_type = VALUES(permission_type), status = VALUES(status);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code = 'QUERY_VIEWER' AND p.permission_code IN ('population:view','household:view','migration:view','statistics:view','log:view'))
   OR (r.role_code = 'POPULATION_MANAGER' AND p.permission_code IN ('population:view','population:edit','household:view','migration:view','migration:edit','statistics:view'))
   OR (r.role_code = 'HOUSEHOLD_MANAGER' AND p.permission_code IN ('population:view','household:view','household:edit','migration:view','migration:edit','statistics:view'))
   OR (r.role_code = 'APPROVER' AND p.permission_code IN ('population:view','household:view','migration:view','approval:view','approval:handle','statistics:view','data:export'))
   OR r.role_code = 'SYSTEM_ADMIN';

INSERT INTO sys_user (username, password_hash, role_id, department_id, real_name, status)
SELECT seed.username, '$2a$10$hqLjVyldvMDp7tlJcpkDZOaTT1dCAuSA5I7FgRfD/B7QXluT8ArB.', r.role_id, d.department_id, seed.real_name, 'ENABLED'
FROM (
    SELECT 'viewer' username, 'QUERY_VIEWER' role_code, 'QUERY' department_code, '查询统计人员' real_name
    UNION ALL SELECT 'population', 'POPULATION_MANAGER', 'POPULATION', '人口信息管理人员'
    UNION ALL SELECT 'household', 'HOUSEHOLD_MANAGER', 'HOUSEHOLD', '户籍管理人员'
    UNION ALL SELECT 'approver', 'APPROVER', 'APPROVAL', '审批人员'
    UNION ALL SELECT 'admin', 'SYSTEM_ADMIN', 'SYSTEM', '系统管理员'
) seed
JOIN sys_role r ON r.role_code = seed.role_code
JOIN sys_department d ON d.department_code = seed.department_code
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id), department_id = VALUES(department_id),
                        real_name = VALUES(real_name), status = VALUES(status);

DROP PROCEDURE IF EXISTS phase01_add_constraint;
DROP PROCEDURE IF EXISTS phase01_add_index;
DROP PROCEDURE IF EXISTS phase01_add_column;
