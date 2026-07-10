-- Phase 02 reusable application, material and single-step approval workflow.
-- Additive and repeatable: no existing business table is dropped or rebuilt.
USE population_miniterm;

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
