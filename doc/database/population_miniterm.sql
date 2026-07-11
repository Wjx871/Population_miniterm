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

CREATE TABLE IF NOT EXISTS business_application (
    application_id BIGINT NOT NULL AUTO_INCREMENT,
    application_no VARCHAR(40) NOT NULL,
    business_type VARCHAR(50) NOT NULL,
    title VARCHAR(200) NOT NULL,
    applicant_user_id BIGINT NOT NULL,
    applicant_department_id BIGINT NULL,
    applicant_region_code VARCHAR(20) NULL,
    target_person_id BIGINT NULL,
    target_household_id BIGINT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    reason TEXT NOT NULL,
    remark VARCHAR(500) NULL,
    submitted_at DATETIME NULL,
    completed_at DATETIME NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (application_id),
    UNIQUE KEY uk_business_application_no (application_no),
    KEY idx_application_status_created (status, created_at),
    KEY idx_application_user_status (applicant_user_id, status),
    KEY idx_application_department_status (applicant_department_id, status),
    KEY idx_application_region_status (applicant_region_code, status),
    KEY idx_application_target_person (target_person_id),
    KEY idx_application_target_household (target_household_id),
    CONSTRAINT fk_application_user FOREIGN KEY (applicant_user_id) REFERENCES sys_user (user_id),
    CONSTRAINT fk_application_department FOREIGN KEY (applicant_department_id) REFERENCES sys_department (department_id),
    CONSTRAINT fk_application_person FOREIGN KEY (target_person_id) REFERENCES person (person_id),
    CONSTRAINT fk_application_household FOREIGN KEY (target_household_id) REFERENCES household (household_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS application_material (
    material_id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    material_type VARCHAR(50) NOT NULL,
    material_name VARCHAR(200) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    storage_path VARCHAR(500) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    file_sha256 CHAR(64) NOT NULL,
    required_flag TINYINT NOT NULL DEFAULT 0,
    verify_status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    verify_user_id BIGINT NULL,
    verify_comment VARCHAR(500) NULL,
    verified_at DATETIME NULL,
    uploaded_by BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (material_id),
    KEY idx_material_application (application_id),
    KEY idx_material_verify_status (verify_status),
    CONSTRAINT fk_material_application FOREIGN KEY (application_id) REFERENCES business_application (application_id),
    CONSTRAINT fk_material_verify_user FOREIGN KEY (verify_user_id) REFERENCES sys_user (user_id),
    CONSTRAINT fk_material_uploaded_by FOREIGN KEY (uploaded_by) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_approval_request (
    approval_id BIGINT NOT NULL AUTO_INCREMENT,
    approval_no VARCHAR(40) NOT NULL,
    application_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    current_approver_id BIGINT NULL,
    current_department_id BIGINT NULL,
    current_region_code VARCHAR(20) NULL,
    submitted_by BIGINT NOT NULL,
    submitted_at DATETIME NOT NULL,
    decided_by BIGINT NULL,
    decided_at DATETIME NULL,
    decision_comment VARCHAR(500) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_approval_no (approval_no),
    UNIQUE KEY uk_approval_application (application_id),
    KEY idx_approval_status_submitted (status, submitted_at),
    CONSTRAINT fk_approval_application FOREIGN KEY (application_id) REFERENCES business_application (application_id),
    CONSTRAINT fk_approval_current_user FOREIGN KEY (current_approver_id) REFERENCES sys_user (user_id),
    CONSTRAINT fk_approval_department FOREIGN KEY (current_department_id) REFERENCES sys_department (department_id),
    CONSTRAINT fk_approval_submitted_by FOREIGN KEY (submitted_by) REFERENCES sys_user (user_id),
    CONSTRAINT fk_approval_decided_by FOREIGN KEY (decided_by) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_approval_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT,
    approval_id BIGINT NULL,
    application_id BIGINT NOT NULL,
    action VARCHAR(30) NOT NULL,
    from_status VARCHAR(30) NULL,
    to_status VARCHAR(30) NOT NULL,
    operator_user_id BIGINT NOT NULL,
    comment VARCHAR(500) NULL,
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50) NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (log_id),
    KEY idx_approval_log_approval (approval_id, operation_time),
    KEY idx_approval_log_application (application_id, operation_time),
    CONSTRAINT fk_approval_log_approval FOREIGN KEY (approval_id) REFERENCES sys_approval_request (approval_id),
    CONSTRAINT fk_approval_log_application FOREIGN KEY (application_id) REFERENCES business_application (application_id),
    CONSTRAINT fk_approval_log_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission (permission_code, permission_name, module_name, permission_type, status)
VALUES ('application:view', '查看申请', 'APPLICATION', 'API', 'ENABLED'),
       ('application:create', '创建申请', 'APPLICATION', 'API', 'ENABLED'),
       ('application:edit', '编辑申请', 'APPLICATION', 'API', 'ENABLED'),
       ('application:submit', '提交申请', 'APPLICATION', 'API', 'ENABLED'),
       ('application:withdraw', '撤回申请', 'APPLICATION', 'API', 'ENABLED'),
       ('material:view', '查看材料', 'MATERIAL', 'API', 'ENABLED'),
       ('material:upload', '上传材料', 'MATERIAL', 'API', 'ENABLED'),
       ('material:delete', '删除材料', 'MATERIAL', 'API', 'ENABLED'),
       ('material:verify', '核验材料', 'MATERIAL', 'API', 'ENABLED'),
       ('approval:view', '查看审批', 'APPROVAL', 'API', 'ENABLED'),
       ('approval:handle', '处理审批', 'APPROVAL', 'API', 'ENABLED')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), module_name = VALUES(module_name),
                        permission_type = VALUES(permission_type), status = VALUES(status);

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code = 'QUERY_VIEWER' AND p.permission_code = 'application:view')
   OR (r.role_code IN ('POPULATION_MANAGER', 'HOUSEHOLD_MANAGER') AND p.permission_code IN
       ('application:view','application:create','application:edit','application:submit','application:withdraw',
        'material:view','material:upload','material:delete'))
   OR (r.role_code = 'APPROVER' AND p.permission_code IN
       ('application:view','material:view','material:verify','approval:view','approval:handle'))
   OR r.role_code = 'SYSTEM_ADMIN';

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

-- Phase 03 final household migration shape (fresh initialization only).
ALTER TABLE person ADD COLUMN current_status_code VARCHAR(30) NOT NULL DEFAULT 'PENDING' AFTER status;
ALTER TABLE household ADD COLUMN region_code VARCHAR(20) NULL AFTER address, ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status;
ALTER TABLE household_member ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status, ADD UNIQUE KEY uk_household_member_pair(household_id,person_id);
UPDATE household SET status='ACTIVE';
UPDATE household_member SET status=CASE WHEN leave_date IS NULL THEN 'ACTIVE' ELSE 'LEFT' END;
ALTER TABLE residence CHANGE residence_type register_type_code VARCHAR(30) NOT NULL,
 ADD COLUMN registered_address VARCHAR(255) NULL AFTER household_id, ADD COLUMN region_code VARCHAR(20) NULL AFTER registered_address,
 ADD COLUMN start_date DATE NULL AFTER register_date, ADD COLUMN created_by BIGINT NULL AFTER status, ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER created_by;
UPDATE residence r JOIN household h ON h.household_id=r.household_id SET r.registered_address=h.address,r.region_code=h.region_code,r.start_date=r.register_date,r.status='ACTIVE';
ALTER TABLE residence MODIFY registered_address VARCHAR(255) NOT NULL,MODIFY region_code VARCHAR(20) NOT NULL,MODIFY start_date DATE NOT NULL,ADD UNIQUE KEY uk_residence_person(person_id);
ALTER TABLE migration_in ADD COLUMN application_id BIGINT NOT NULL AFTER in_id,ADD COLUMN migration_type VARCHAR(40) NOT NULL AFTER person_id,
 ADD COLUMN from_region_code VARCHAR(20) NULL AFTER migration_type,ADD COLUMN to_region_code VARCHAR(20) NOT NULL AFTER from_address,
 ADD COLUMN to_address_snapshot VARCHAR(255) NULL AFTER to_household_id,ADD COLUMN transfer_batch_no VARCHAR(40) NULL AFTER reason,
 ADD COLUMN business_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' AFTER transfer_batch_no,ADD COLUMN executed_at DATETIME NULL AFTER operator_id,
 ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER executed_at,ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 ADD UNIQUE KEY uk_migration_in_application(application_id),ADD UNIQUE KEY uk_migration_in_batch(transfer_batch_no),
 ADD CONSTRAINT fk_migration_in_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),MODIFY reason VARCHAR(500) NULL;
ALTER TABLE migration_out ADD COLUMN application_id BIGINT NOT NULL AFTER out_id,ADD COLUMN migration_type VARCHAR(40) NOT NULL AFTER person_id,
 ADD COLUMN from_region_code VARCHAR(20) NULL AFTER migration_type,ADD COLUMN from_address_snapshot VARCHAR(255) NOT NULL AFTER from_household_id,
 ADD COLUMN to_region_code VARCHAR(20) NULL AFTER from_address_snapshot,ADD COLUMN transfer_batch_no VARCHAR(40) NULL AFTER reason,
 ADD COLUMN new_head_person_id BIGINT NULL AFTER transfer_batch_no,ADD COLUMN business_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT' AFTER new_head_person_id,
 ADD COLUMN executed_at DATETIME NULL AFTER operator_id,ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER executed_at,
 ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 ADD UNIQUE KEY uk_migration_out_application(application_id),ADD UNIQUE KEY uk_migration_out_batch(transfer_batch_no),
 ADD CONSTRAINT fk_migration_out_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),MODIFY reason VARCHAR(500) NULL;
CREATE TABLE IF NOT EXISTS residence_archive(archive_id BIGINT NOT NULL AUTO_INCREMENT,original_registration_id BIGINT NOT NULL,person_id BIGINT NOT NULL,
 household_id BIGINT NOT NULL,application_id BIGINT NOT NULL,migration_out_id BIGINT NULL,archive_type VARCHAR(40) NOT NULL,archive_reason VARCHAR(500),
 person_name_snapshot VARCHAR(50) NOT NULL,identity_no_snapshot VARCHAR(50) NOT NULL,household_no_snapshot VARCHAR(30) NOT NULL,
 registered_address_snapshot VARCHAR(255) NOT NULL,region_code_snapshot VARCHAR(20) NOT NULL,register_type_code_snapshot VARCHAR(30),
 register_date_snapshot DATE,start_date_snapshot DATE,end_date_snapshot DATE NOT NULL,original_status VARCHAR(30),archived_by BIGINT NOT NULL,
 archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(archive_id),
 UNIQUE KEY uk_residence_archive_application(application_id),KEY idx_archive_person_time(person_id,archived_at),KEY idx_archive_region_time(region_code_snapshot,archived_at),
 CONSTRAINT fk_archive_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),CONSTRAINT fk_archive_migration_out FOREIGN KEY(migration_out_id) REFERENCES migration_out(out_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES
 ('migration:in:create','创建迁入申请','MIGRATION','API','ENABLED'),('migration:out:create','创建迁出申请','MIGRATION','API','ENABLED'),
 ('migration:view','查看迁移','MIGRATION','API','ENABLED'),('migration:execute','执行迁移','MIGRATION','API','ENABLED'),('migration:archive:view','查看户籍归档','MIGRATION','API','ENABLED')
 ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p WHERE
 (r.role_code='QUERY_VIEWER' AND p.permission_code='migration:view') OR (r.role_code='POPULATION_MANAGER' AND p.permission_code='migration:view') OR
 (r.role_code='HOUSEHOLD_MANAGER' AND p.permission_code IN('migration:in:create','migration:out:create','migration:view','migration:execute','migration:archive:view')) OR
 (r.role_code='APPROVER' AND p.permission_code IN('migration:view','migration:archive:view')) OR r.role_code='SYSTEM_ADMIN';

-- Phase 04 cancellation workflow for fresh initialization.
ALTER TABLE household ADD COLUMN household_type VARCHAR(30) NULL AFTER region_code;
CREATE TABLE IF NOT EXISTS cancellation_record(cancellation_id BIGINT NOT NULL AUTO_INCREMENT,cancellation_no VARCHAR(40) NOT NULL,application_id BIGINT NOT NULL,cancel_object_type VARCHAR(30) NOT NULL,person_id BIGINT NULL,household_id BIGINT NULL,source_household_id BIGINT NULL,cancel_reason_code VARCHAR(50) NOT NULL,cancel_reason_detail VARCHAR(500),event_date DATE NOT NULL,new_head_person_id BIGINT,business_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',person_name_snapshot VARCHAR(50),identity_no_snapshot VARCHAR(50),household_no_snapshot VARCHAR(30),address_snapshot VARCHAR(255),region_code_snapshot VARCHAR(20),operator_id BIGINT,executed_at DATETIME,version INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(cancellation_id),UNIQUE KEY uk_cancellation_no(cancellation_no),UNIQUE KEY uk_cancellation_application(application_id),KEY idx_cancellation_person_status(person_id,business_status),KEY idx_cancellation_household_status(household_id,business_status),CONSTRAINT ck_cancellation_object CHECK((cancel_object_type='PERSON' AND person_id IS NOT NULL AND household_id IS NULL)OR(cancel_object_type='HOUSEHOLD' AND household_id IS NOT NULL AND person_id IS NULL)),CONSTRAINT fk_cancellation_application FOREIGN KEY(application_id) REFERENCES business_application(application_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS household_archive(archive_id BIGINT NOT NULL AUTO_INCREMENT,original_household_id BIGINT NOT NULL,application_id BIGINT NOT NULL,cancellation_id BIGINT NOT NULL,household_no_snapshot VARCHAR(30) NOT NULL,head_person_id_snapshot BIGINT,head_person_name_snapshot VARCHAR(50),registered_address_snapshot VARCHAR(255) NOT NULL,region_code_snapshot VARCHAR(20),household_type_snapshot VARCHAR(30),establish_date_snapshot DATE,original_status VARCHAR(30) NOT NULL,cancellation_reason_code VARCHAR(50) NOT NULL,cancellation_reason_detail VARCHAR(500),archived_by BIGINT NOT NULL,archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(archive_id),UNIQUE KEY uk_household_archive_application(application_id),UNIQUE KEY uk_household_archive_cancellation(cancellation_id),KEY idx_household_archive_region_time(region_code_snapshot,archived_at),CONSTRAINT fk_household_archive_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),CONSTRAINT fk_household_archive_cancellation FOREIGN KEY(cancellation_id) REFERENCES cancellation_record(cancellation_id)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES('cancellation:person:create','创建人员注销申请','CANCELLATION','API','ENABLED'),('cancellation:household:create','创建家庭户销户申请','CANCELLATION','API','ENABLED'),('cancellation:view','查看注销业务','CANCELLATION','API','ENABLED'),('cancellation:execute','执行注销业务','CANCELLATION','API','ENABLED'),('cancellation:archive:view','查看注销归档','CANCELLATION','API','ENABLED') ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p WHERE(r.role_code='QUERY_VIEWER' AND p.permission_code IN('cancellation:view','cancellation:archive:view'))OR(r.role_code='POPULATION_MANAGER' AND p.permission_code IN('cancellation:view','cancellation:person:create'))OR(r.role_code='HOUSEHOLD_MANAGER' AND p.permission_code IN('cancellation:view','cancellation:person:create','cancellation:household:create','cancellation:execute','cancellation:archive:view'))OR(r.role_code='APPROVER' AND p.permission_code IN('cancellation:view','cancellation:archive:view'))OR r.role_code='SYSTEM_ADMIN';
