CREATE DATABASE IF NOT EXISTS population_miniterm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS sys_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_code VARCHAR(50) NOT NULL,
    role_name VARCHAR(50) NOT NULL,
    role_level VARCHAR(10) NOT NULL,
    data_scope VARCHAR(20) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_sys_role_role_code (role_code),
    UNIQUE KEY uk_sys_role_role_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_permission (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    module_name VARCHAR(100) NULL,
    permission_type VARCHAR(20) NOT NULL DEFAULT 'API',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_sys_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role_id BIGINT NOT NULL,
    department_id BIGINT NULL,
    real_name VARCHAR(50) NULL,
    phone VARCHAR(20) NULL,
    last_login_time DATETIME NULL,
    last_login_ip VARCHAR(50) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_role_id (role_id),
    KEY idx_sys_user_department_id (department_id),
    CONSTRAINT fk_sys_user_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id),
    CONSTRAINT fk_sys_user_department FOREIGN KEY (department_id) REFERENCES sys_department (department_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission_id (permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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

CREATE TABLE IF NOT EXISTS person (
    person_id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    gender CHAR(1) NOT NULL,
    id_card VARCHAR(18) NOT NULL,
    birth_date DATE NULL,
    ethnicity VARCHAR(30) NULL,
    phone VARCHAR(20) NULL,
    current_address VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT '正常',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (person_id),
    UNIQUE KEY uk_person_id_card (id_card)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS household (
    household_id BIGINT NOT NULL AUTO_INCREMENT,
    household_no VARCHAR(30) NOT NULL,
    head_person_id BIGINT NULL,
    address VARCHAR(255) NOT NULL,
    establish_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT '正常',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (household_id),
    UNIQUE KEY uk_household_no (household_no),
    KEY idx_household_head_person_id (head_person_id),
    CONSTRAINT fk_household_head_person FOREIGN KEY (head_person_id) REFERENCES person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS household_member (
    member_id BIGINT NOT NULL AUTO_INCREMENT,
    household_id BIGINT NOT NULL,
    person_id BIGINT NOT NULL,
    relationship VARCHAR(30) NOT NULL,
    join_date DATE NOT NULL,
    leave_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT '有效',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id),
    KEY idx_household_member_household_id (household_id),
    KEY idx_household_member_person_id (person_id),
    CONSTRAINT fk_household_member_household FOREIGN KEY (household_id) REFERENCES household (household_id),
    CONSTRAINT fk_household_member_person FOREIGN KEY (person_id) REFERENCES person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS residence (
    residence_id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    household_id BIGINT NOT NULL,
    residence_type VARCHAR(30) NOT NULL,
    register_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT '有效',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (residence_id),
    KEY idx_residence_person_id (person_id),
    KEY idx_residence_household_id (household_id),
    CONSTRAINT fk_residence_person FOREIGN KEY (person_id) REFERENCES person (person_id),
    CONSTRAINT fk_residence_household FOREIGN KEY (household_id) REFERENCES household (household_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS migration_in (
    in_id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    from_address VARCHAR(255) NOT NULL,
    to_household_id BIGINT NOT NULL,
    in_date DATE NOT NULL,
    reason VARCHAR(255) NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (in_id),
    KEY idx_migration_in_person_id (person_id),
    KEY idx_migration_in_to_household_id (to_household_id),
    KEY idx_migration_in_operator_id (operator_id),
    CONSTRAINT fk_migration_in_person FOREIGN KEY (person_id) REFERENCES person (person_id),
    CONSTRAINT fk_migration_in_household FOREIGN KEY (to_household_id) REFERENCES household (household_id),
    CONSTRAINT fk_migration_in_operator FOREIGN KEY (operator_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS migration_out (
    out_id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    from_household_id BIGINT NOT NULL,
    to_address VARCHAR(255) NOT NULL,
    out_date DATE NOT NULL,
    reason VARCHAR(255) NULL,
    operator_id BIGINT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (out_id),
    KEY idx_migration_out_person_id (person_id),
    KEY idx_migration_out_from_household_id (from_household_id),
    KEY idx_migration_out_operator_id (operator_id),
    CONSTRAINT fk_migration_out_person FOREIGN KEY (person_id) REFERENCES person (person_id),
    CONSTRAINT fk_migration_out_household FOREIGN KEY (from_household_id) REFERENCES household (household_id),
    CONSTRAINT fk_migration_out_operator FOREIGN KEY (operator_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS floating_population (
    floating_id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    source_place VARCHAR(255) NULL,
    current_address VARCHAR(255) NOT NULL,
    register_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT '有效',
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (floating_id),
    KEY idx_floating_population_person_id (person_id),
    CONSTRAINT fk_floating_population_person FOREIGN KEY (person_id) REFERENCES person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS key_population (
    key_id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    key_type VARCHAR(50) NOT NULL,
    management_level VARCHAR(20) NULL,
    register_date DATE NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT '有效',
    remark VARCHAR(255) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (key_id),
    KEY idx_key_population_person_id (person_id),
    CONSTRAINT fk_key_population_person FOREIGN KEY (person_id) REFERENCES person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS certificate (
    certificate_id BIGINT NOT NULL AUTO_INCREMENT,
    person_id BIGINT NOT NULL,
    certificate_type VARCHAR(30) NOT NULL,
    certificate_no VARCHAR(50) NOT NULL,
    issue_date DATE NULL,
    expire_date DATE NULL,
    status VARCHAR(20) NOT NULL DEFAULT '有效',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (certificate_id),
    UNIQUE KEY uk_certificate_no (certificate_no),
    KEY idx_certificate_person_id (person_id),
    CONSTRAINT fk_certificate_person FOREIGN KEY (person_id) REFERENCES person (person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS operation_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NULL,
    operation_type VARCHAR(50) NOT NULL,
    module_name VARCHAR(100) NULL,
    request_path VARCHAR(255) NULL,
    request_method VARCHAR(20) NULL,
    target_table VARCHAR(100) NULL,
    target_id VARCHAR(100) NULL,
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50) NULL,
    operation_result VARCHAR(50) NULL,
    error_message VARCHAR(500) NULL,
    user_agent VARCHAR(500) NULL,
    detail TEXT NULL,
    PRIMARY KEY (log_id),
    KEY idx_operation_log_user_id (user_id),
    CONSTRAINT fk_operation_log_user FOREIGN KEY (user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS data_dictionary (
    dict_id BIGINT NOT NULL AUTO_INCREMENT,
    dict_type VARCHAR(50) NOT NULL,
    dict_code VARCHAR(50) NOT NULL,
    dict_name VARCHAR(100) NOT NULL,
    sort_no INT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT '启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (dict_id),
    UNIQUE KEY uk_data_dictionary_type_code (dict_type, dict_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS residents (
    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    gender VARCHAR(20) NOT NULL,
    birth_date DATE NOT NULL,
    id_card_number VARCHAR(18) NOT NULL,
    phone_number VARCHAR(30) NULL,
    province VARCHAR(100) NULL,
    city VARCHAR(100) NULL,
    district VARCHAR(100) NULL,
    address VARCHAR(255) NULL,
    active BIT NOT NULL DEFAULT b'1',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_residents_id_card_number (id_card_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO residents (
    name,
    gender,
    birth_date,
    id_card_number,
    phone_number,
    province,
    city,
    district,
    address,
    active
) VALUES (
    '张三',
    'MALE',
    '1999-01-01',
    '110101199901010011',
    '13800138000',
    '北京市',
    '北京市',
    '东城区',
    '示例地址',
    b'1'
) ON DUPLICATE KEY UPDATE
    name = VALUES(name),
    phone_number = VALUES(phone_number),
    updated_at = CURRENT_TIMESTAMP(6);
