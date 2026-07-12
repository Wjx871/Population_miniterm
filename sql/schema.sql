-- =====================================================
-- Population Database Management System - Database Schema v4.0
-- Based on: 数据库设计v4.0_Cursor详细说明.md
-- Generated for: MySQL 8.x / InnoDB
-- Character Set: utf8mb4
-- =====================================================

-- Drop existing database and create new one
DROP DATABASE IF EXISTS population_miniterm;
CREATE DATABASE population_miniterm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE population_miniterm;

-- =====================================================
-- Section 1: Foundation Tables (Admin Regions & Dictionary)
-- =====================================================

-- Table 3.1: admin_region - Administrative Regions
CREATE TABLE admin_region (
    region_code VARCHAR(20) NOT NULL COMMENT '行政区划编码',
    region_name VARCHAR(100) NOT NULL COMMENT '行政区划名称',
    parent_code VARCHAR(20) NULL COMMENT '父级区划编码',
    region_level_code VARCHAR(20) NOT NULL COMMENT '区划层级编码：REGION_LEVEL字典',
    city_code VARCHAR(20) NULL COMMENT '所属城市编码（用于同市跨区判断）',
    enabled_flag TINYINT NOT NULL DEFAULT 1 COMMENT '启用标记：1启用，0停用',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    PRIMARY KEY (region_code),
    KEY idx_admin_region_parent (parent_code),
    KEY idx_admin_region_city (city_code),
    CONSTRAINT fk_admin_region_parent FOREIGN KEY (parent_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT chk_enabled_flag CHECK (enabled_flag IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='行政区划表';

-- Table 3.23: data_dictionary - System Data Dictionary
CREATE TABLE data_dictionary (
    dict_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '字典ID',
    dict_type VARCHAR(80) NOT NULL COMMENT '字典类型',
    dict_code VARCHAR(80) NOT NULL COMMENT '字典编码',
    dict_label VARCHAR(100) NOT NULL COMMENT '字典名称',
    sort_no INT NOT NULL DEFAULT 0 COMMENT '排序号',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ACCOUNT_STATUS字典',
    remark VARCHAR(255) NULL COMMENT '备注',
    PRIMARY KEY (dict_id),
    UNIQUE KEY uk_dictionary_type_code (dict_type, dict_code),
    KEY idx_dictionary_type_status (dict_type, status, sort_no),
    KEY idx_dictionary_label (dict_label)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统数据字典';

-- =====================================================
-- Section 2: Organization & Permission Tables
-- =====================================================

-- Table 3.2: sys_department - Department/Organization
CREATE TABLE sys_department (
    department_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '部门ID',
    department_code VARCHAR(50) NOT NULL COMMENT '部门编码',
    department_name VARCHAR(100) NOT NULL COMMENT '部门名称',
    department_type_code VARCHAR(30) NOT NULL COMMENT '部门类型编码：DEPARTMENT_TYPE字典',
    region_code VARCHAR(20) NOT NULL COMMENT '所属区划编码',
    parent_id BIGINT NULL COMMENT '上级部门ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '部门状态：ACCOUNT_STATUS字典',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (department_id),
    UNIQUE KEY uk_department_code (department_code),
    KEY idx_department_region (region_code),
    KEY idx_department_parent (parent_id),
    CONSTRAINT fk_department_region FOREIGN KEY (region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_department_parent FOREIGN KEY (parent_id) REFERENCES sys_department(department_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门机构表';

-- Table 3.3: sys_role - System Role
CREATE TABLE sys_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    permission_level TINYINT NOT NULL DEFAULT 2 COMMENT '权限等级：1查询级，2经办级，3审批/管理级',
    data_scope_code VARCHAR(20) NOT NULL DEFAULT 'DEPARTMENT' COMMENT '数据范围编码：DATA_SCOPE字典',
    description VARCHAR(255) NULL COMMENT '角色说明',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '角色状态：ACCOUNT_STATUS字典',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_role_code (role_code),
    CONSTRAINT chk_permission_level CHECK (permission_level BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

-- Table 3.4: sys_permission - Permission Point
CREATE TABLE sys_permission (
    permission_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限ID',
    permission_code VARCHAR(100) NOT NULL COMMENT '权限编码：如person:query',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    module_name VARCHAR(100) NOT NULL COMMENT '所属模块',
    action_code VARCHAR(30) NOT NULL COMMENT '操作动作编码：ACTION_CODE字典',
    sensitivity_level TINYINT NOT NULL DEFAULT 1 COMMENT '敏感等级：1普通，2敏感，3重大',
    approval_required TINYINT NOT NULL DEFAULT 0 COMMENT '是否需审批：0否，1是',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (permission_id),
    UNIQUE KEY uk_permission_code (permission_code),
    KEY idx_permission_module (module_name),
    CONSTRAINT chk_sensitivity_level CHECK (sensitivity_level BETWEEN 1 AND 3),
    CONSTRAINT chk_approval_required CHECK (approval_required IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点表';

-- Table 3.5: sys_role_permission - Role-Permission Association
CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '关联ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role(role_id) ON DELETE CASCADE,
    CONSTRAINT fk_role_permission_perm FOREIGN KEY (permission_id) REFERENCES sys_permission(permission_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';

-- Table 3.6: sys_user - System User
CREATE TABLE sys_user (
    user_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名（登录账号）',
    password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希（BCrypt/Argon2/PBKDF2）',
    real_name VARCHAR(50) NOT NULL COMMENT '真实姓名',
    phone VARCHAR(20) NULL COMMENT '手机号',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    department_id BIGINT NOT NULL COMMENT '所属部门ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ACCOUNT_STATUS字典',
    last_login_at DATETIME NULL COMMENT '最后登录时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记：0未删除，1已删除',
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_user_username (username),
    KEY idx_user_role (role_id),
    KEY idx_user_department (department_id),
    CONSTRAINT fk_user_role FOREIGN KEY (role_id) REFERENCES sys_role(role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_department FOREIGN KEY (department_id) REFERENCES sys_department(department_id) ON DELETE RESTRICT,
    CONSTRAINT chk_user_deleted CHECK (is_deleted IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- =====================================================
-- Section 3: Person & Household Master Tables
-- =====================================================

-- Table 3.7: person - Person Base Identity Archive
CREATE TABLE person (
    person_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '人口ID',
    name VARCHAR(50) NOT NULL COMMENT '姓名',
    gender_code VARCHAR(20) NOT NULL DEFAULT 'UNKNOWN' COMMENT '性别编码：GENDER字典',
    identity_type_code VARCHAR(30) NOT NULL DEFAULT 'ID_CARD' COMMENT '主身份凭证类型：IDENTITY_TYPE字典',
    identity_no VARCHAR(80) NOT NULL COMMENT '主身份凭证号码',
    birth_date DATE NULL COMMENT '出生日期',
    ethnicity_code VARCHAR(20) NULL COMMENT '民族编码：ETHNICITY字典',
    phone VARCHAR(20) NULL COMMENT '手机号',
    contact_address VARCHAR(255) NULL COMMENT '联系地址（不代表户籍或现居住地址）',
    record_status_code VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '档案状态：PERSON_RECORD_STATUS字典',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除标记',
    PRIMARY KEY (person_id),
    UNIQUE KEY uk_person_identity (identity_type_code, identity_no),
    KEY idx_person_name (name),
    KEY idx_person_phone (phone),
    KEY idx_person_record_status (record_status_code),
    CONSTRAINT chk_person_deleted CHECK (is_deleted IN (0, 1)),
    CONSTRAINT chk_person_identity_no CHECK (
        identity_type_code <> 'ID_CARD' OR
        (CHAR_LENGTH(identity_no) = 18 AND identity_no REGEXP '^[0-9]{17}[0-9Xx]$') OR
        (CHAR_LENGTH(identity_no) = 15 AND identity_no REGEXP '^[0-9]{15}$')
    ),
    CONSTRAINT chk_person_phone CHECK (phone IS NULL OR phone REGEXP '^1[3-9][0-9]{9}$'),
    CONSTRAINT chk_person_birth_date CHECK (birth_date IS NULL OR birth_date <= CURDATE())
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人口基础身份档案表';

-- Table 3.8: household - Family Household Archive
CREATE TABLE household (
    household_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '家庭户ID',
    household_no VARCHAR(30) NOT NULL COMMENT '户号',
    household_type_code VARCHAR(30) NOT NULL DEFAULT 'FAMILY' COMMENT '户类型编码：HOUSEHOLD_TYPE字典',
    head_person_id BIGINT NULL COMMENT '户主人口ID',
    registered_address VARCHAR(255) NOT NULL COMMENT '户籍地址',
    region_code VARCHAR(20) NOT NULL COMMENT '所属区划编码',
    department_id BIGINT NOT NULL COMMENT '管理部门ID',
    establish_date DATE NOT NULL COMMENT '立户日期',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '户状态：HOUSEHOLD_STATUS字典',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (household_id),
    UNIQUE KEY uk_household_no (household_no),
    KEY idx_household_region (region_code),
    KEY idx_household_department (department_id),
    KEY idx_household_head (head_person_id),
    CONSTRAINT fk_household_head FOREIGN KEY (head_person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_household_region FOREIGN KEY (region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_household_department FOREIGN KEY (department_id) REFERENCES sys_department(department_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='家庭户档案表';

-- =====================================================
-- Section 4: Business Application & Approval Tables
-- =====================================================

-- Table 3.9: business_application - Business Application Master
CREATE TABLE business_application (
    application_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '申请ID',
    application_no VARCHAR(40) NOT NULL COMMENT '申请单号',
    business_type_code VARCHAR(50) NOT NULL COMMENT '业务类型编码：BUSINESS_TYPE字典',
    applicant_name VARCHAR(50) NOT NULL COMMENT '申请人姓名',
    applicant_identity_type VARCHAR(30) NOT NULL DEFAULT 'ID_CARD' COMMENT '申请人证件类型：IDENTITY_TYPE字典',
    applicant_identity_no VARCHAR(80) NOT NULL COMMENT '申请人证件号码',
    applicant_phone VARCHAR(20) NULL COMMENT '申请人电话',
    target_person_id BIGINT NULL COMMENT '目标人口ID',
    target_household_id BIGINT NULL COMMENT '目标家庭户ID',
    handling_department_id BIGINT NOT NULL COMMENT '受理部门ID',
    submit_user_id BIGINT NOT NULL COMMENT '提交用户ID（经办人）',
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT' COMMENT '申请状态：APPLICATION_STATUS字典',
    current_step VARCHAR(50) NULL COMMENT '当前环节',
    submitted_at DATETIME NULL COMMENT '提交时间',
    completed_at DATETIME NULL COMMENT '办结时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (application_id),
    UNIQUE KEY uk_application_no (application_no),
    KEY idx_application_status_time (status, created_at),
    KEY idx_application_submit_user (submit_user_id),
    KEY idx_application_department (handling_department_id, status),
    KEY idx_application_target_person (target_person_id),
    KEY idx_application_target_household (target_household_id),
    CONSTRAINT fk_application_person FOREIGN KEY (target_person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_household FOREIGN KEY (target_household_id) REFERENCES household(household_id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_department FOREIGN KEY (handling_department_id) REFERENCES sys_department(department_id) ON DELETE RESTRICT,
    CONSTRAINT fk_application_user FOREIGN KEY (submit_user_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_completed_at CHECK (completed_at IS NULL OR submitted_at IS NULL OR completed_at >= submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务申请主单表';

-- Table 3.10: application_material - Application Material Metadata
CREATE TABLE application_material (
    material_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '材料ID',
    application_id BIGINT NOT NULL COMMENT '申请ID',
    material_type_code VARCHAR(50) NOT NULL COMMENT '材料类型编码：MATERIAL_TYPE字典',
    material_name VARCHAR(150) NOT NULL COMMENT '材料名称',
    material_no VARCHAR(80) NULL COMMENT '材料编号（如证明书编号）',
    file_name VARCHAR(200) NULL COMMENT '文件名',
    storage_uri VARCHAR(500) NULL COMMENT '存储地址（文件URI，不存二进制）',
    file_hash VARCHAR(128) NULL COMMENT '文件哈希（完整性校验和去重）',
    required_flag TINYINT NOT NULL DEFAULT 1 COMMENT '必需标记：1必需，0可选',
    verify_status VARCHAR(20) NOT NULL DEFAULT 'UNVERIFIED' COMMENT '核验状态：MATERIAL_VERIFY_STATUS字典',
    verified_by BIGINT NULL COMMENT '核验人ID',
    verified_at DATETIME NULL COMMENT '核验时间',
    uploaded_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
    uploader_user_id BIGINT NULL COMMENT '上传用户ID',
    PRIMARY KEY (material_id),
    KEY idx_material_application (application_id),
    KEY idx_material_hash (file_hash),
    CONSTRAINT fk_material_application FOREIGN KEY (application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_material_verifier FOREIGN KEY (verified_by) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_material_uploader FOREIGN KEY (uploader_user_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_required_flag CHECK (required_flag IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='申请材料元数据表';

-- Table 3.11: sys_approval_request - Approval Request Master
CREATE TABLE sys_approval_request (
    approval_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批ID',
    approval_no VARCHAR(40) NOT NULL COMMENT '审批单号',
    application_id BIGINT NOT NULL COMMENT '业务申请ID',
    required_level TINYINT NOT NULL DEFAULT 3 COMMENT '最低审批等级：1-3',
    current_approver_id BIGINT NULL COMMENT '当前审批人ID',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态：APPROVAL_STATUS字典',
    apply_reason VARCHAR(500) NULL COMMENT '申请原因',
    submitted_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    finished_at DATETIME NULL COMMENT '办结时间',
    PRIMARY KEY (approval_id),
    UNIQUE KEY uk_approval_no (approval_no),
    UNIQUE KEY uk_approval_application (application_id),
    KEY idx_approval_status_user (status, current_approver_id),
    CONSTRAINT fk_approval_application FOREIGN KEY (application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_approver FOREIGN KEY (current_approver_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_required_level CHECK (required_level BETWEEN 1 AND 3),
    CONSTRAINT chk_finished_at CHECK (finished_at IS NULL OR finished_at >= submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批主单表';

-- Table 3.12: sys_approval_log - Approval Process Log
CREATE TABLE sys_approval_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审批日志ID',
    approval_id BIGINT NOT NULL COMMENT '审批ID',
    step_no INT NOT NULL DEFAULT 1 COMMENT '审批步骤号',
    approver_user_id BIGINT NOT NULL COMMENT '审批人ID',
    action_code VARCHAR(20) NOT NULL COMMENT '审批动作编码：APPROVE_ACTION字典',
    comment VARCHAR(500) NULL COMMENT '审批意见',
    approved_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '审批时间',
    PRIMARY KEY (log_id),
    UNIQUE KEY uk_approval_step (approval_id, step_no),
    CONSTRAINT fk_approval_log_approval FOREIGN KEY (approval_id) REFERENCES sys_approval_request(approval_id) ON DELETE RESTRICT,
    CONSTRAINT fk_approval_log_user FOREIGN KEY (approver_user_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批过程日志表';

-- =====================================================
-- Section 5: Household Relations & Residence Tables
-- =====================================================

-- Table 3.13: household_member - Household-Person Historical Relation
CREATE TABLE household_member (
    member_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '成员关系ID',
    household_id BIGINT NOT NULL COMMENT '家庭户ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    relationship_code VARCHAR(30) NOT NULL COMMENT '与户主关系编码：RELATIONSHIP字典',
    join_date DATE NOT NULL COMMENT '加入日期',
    leave_date DATE NULL COMMENT '离开日期',
    member_status VARCHAR(20) NOT NULL DEFAULT 'CURRENT' COMMENT '成员状态：MEMBER_STATUS字典',
    source_application_id BIGINT NULL COMMENT '来源申请ID',
    -- 生成列：仅当状态为 CURRENT 时才有值，作为一人一户一CURRENT的去重键
    current_dedup_key VARCHAR(40) GENERATED ALWAYS AS (
        CASE WHEN member_status = 'CURRENT'
             THEN CONCAT(household_id, '-', person_id)
             ELSE NULL
        END
    ) VIRTUAL COMMENT 'CURRENT 唯一性去重键（生成列）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (member_id),
    UNIQUE KEY uk_member_current_dedup (current_dedup_key),
    KEY idx_member_household_status (household_id, member_status),
    KEY idx_member_person_status (person_id, member_status),
    CONSTRAINT fk_member_household FOREIGN KEY (household_id) REFERENCES household(household_id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_member_application FOREIGN KEY (source_application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT chk_leave_date CHECK (leave_date IS NULL OR leave_date >= join_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='户与人口历史关系表';

-- Table 3.14: residence_registration - Current Valid Residence Registration
CREATE TABLE residence_registration (
    registration_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '当前户籍登记ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    household_id BIGINT NOT NULL COMMENT '家庭户ID',
    register_type_code VARCHAR(40) NOT NULL COMMENT '登记类型编码：REGISTER_TYPE字典',
    register_date DATE NOT NULL COMMENT '登记日期',
    registered_address VARCHAR(255) NOT NULL COMMENT '登记地址（当前有效登记地址）',
    region_code VARCHAR(20) NOT NULL COMMENT '所属区划编码',
    start_date DATE NOT NULL COMMENT '生效日期',
    source_application_id BIGINT NULL COMMENT '来源申请ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (registration_id),
    UNIQUE KEY uk_registration_person (person_id),
    KEY idx_registration_household (household_id),
    KEY idx_registration_region (region_code),
    CONSTRAINT fk_registration_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_registration_household FOREIGN KEY (household_id) REFERENCES household(household_id) ON DELETE RESTRICT,
    CONSTRAINT fk_registration_region FOREIGN KEY (region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_registration_application FOREIGN KEY (source_application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='当前有效户籍登记表';

-- Table 3.15: residence_archive - Residence History Snapshot
CREATE TABLE residence_archive (
    archive_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '归档ID',
    original_registration_id BIGINT NOT NULL COMMENT '原登记ID（仅保存数值，不建强外键）',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    household_id BIGINT NOT NULL COMMENT '原家庭户ID',
    archive_type_code VARCHAR(30) NOT NULL COMMENT '归档类型编码：ARCHIVE_TYPE字典',
    archive_date DATE NOT NULL COMMENT '归档日期',
    archive_reason_code VARCHAR(50) NULL COMMENT '归档原因编码：MIGRATION_REASON/CANCEL_REASON字典',
    person_name_snapshot VARCHAR(50) NOT NULL COMMENT '姓名快照',
    identity_type_snapshot VARCHAR(30) NOT NULL COMMENT '证件类型快照',
    identity_no_snapshot VARCHAR(80) NOT NULL COMMENT '证件号码快照',
    household_no_snapshot VARCHAR(30) NOT NULL COMMENT '原户号快照',
    registered_address_snapshot VARCHAR(255) NOT NULL COMMENT '原户籍地址快照',
    region_code_snapshot VARCHAR(20) NOT NULL COMMENT '原区划快照',
    register_type_snapshot VARCHAR(40) NOT NULL COMMENT '原登记类型快照',
    register_date_snapshot DATE NOT NULL COMMENT '原登记日期快照',
    start_date_snapshot DATE NOT NULL COMMENT '原生效日期快照',
    end_date_snapshot DATE NOT NULL COMMENT '结束日期快照',
    original_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '原状态快照',
    archive_operator_id BIGINT NULL COMMENT '归档操作人ID',
    source_application_id BIGINT NULL COMMENT '来源申请ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (archive_id),
    KEY idx_archive_original_registration (original_registration_id),
    KEY idx_archive_person_date (person_id, archive_date),
    KEY idx_archive_household_date (household_id, archive_date),
    KEY idx_archive_region_date (region_code_snapshot, archive_date),
    KEY idx_archive_type_date (archive_type_code, archive_date),
    CONSTRAINT fk_archive_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_archive_household FOREIGN KEY (household_id) REFERENCES household(household_id) ON DELETE RESTRICT,
    CONSTRAINT fk_archive_operator FOREIGN KEY (archive_operator_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_archive_application FOREIGN KEY (source_application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT chk_end_date_snapshot CHECK (end_date_snapshot >= start_date_snapshot)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='户籍历史快照表';

-- =====================================================
-- Section 6: Business Tables
-- =====================================================

-- Table 3.16: migration_in - Migration In Record
CREATE TABLE migration_in (
    in_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '迁入记录ID',
    application_id BIGINT NOT NULL COMMENT '申请ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    in_type_code VARCHAR(40) NOT NULL COMMENT '迁入类型编码：IN_TYPE字典',
    transfer_batch_no VARCHAR(40) NULL COMMENT '联办批次号（同市跨区使用）',
    source_registration_id BIGINT NULL COMMENT '原登记ID（仅保存数值，不建强外键）',
    from_region_code VARCHAR(20) NULL COMMENT '来源区划编码',
    from_address VARCHAR(255) NOT NULL COMMENT '来源地址',
    from_household_no VARCHAR(30) NULL COMMENT '原户号',
    to_household_id BIGINT NOT NULL COMMENT '迁入家庭户ID',
    to_region_code VARCHAR(20) NOT NULL COMMENT '目标区划编码',
    in_date DATE NOT NULL COMMENT '迁入日期',
    reason_code VARCHAR(50) NULL COMMENT '迁入原因编码：MIGRATION_REASON字典',
    new_registration_id BIGINT NULL COMMENT '新当前登记ID（业务办结后回填）',
    operator_id BIGINT NULL COMMENT '经办人ID',
    completed_at DATETIME NULL COMMENT '办结时间',
    PRIMARY KEY (in_id),
    UNIQUE KEY uk_migration_in_application (application_id),
    KEY idx_migration_in_person (person_id),
    KEY idx_migration_in_region_date (to_region_code, in_date),
    KEY idx_migration_in_batch (transfer_batch_no),
    KEY idx_migration_in_batch_date (transfer_batch_no, in_date),
    CONSTRAINT fk_migration_in_application FOREIGN KEY (application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_in_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_in_from_region FOREIGN KEY (from_region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_in_to_household FOREIGN KEY (to_household_id) REFERENCES household(household_id) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_in_to_region FOREIGN KEY (to_region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_in_new_reg FOREIGN KEY (new_registration_id) REFERENCES residence_registration(registration_id) ON DELETE SET NULL,
    CONSTRAINT fk_migration_in_operator FOREIGN KEY (operator_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁入业务记录表';

-- Table 3.17: migration_out - Migration Out Record
CREATE TABLE migration_out (
    out_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '迁出记录ID',
    application_id BIGINT NOT NULL COMMENT '申请ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    out_type_code VARCHAR(40) NOT NULL COMMENT '迁出类型编码：OUT_TYPE字典',
    transfer_batch_no VARCHAR(40) NULL COMMENT '联办批次号',
    from_household_id BIGINT NOT NULL COMMENT '原家庭户ID',
    from_region_code VARCHAR(20) NOT NULL COMMENT '原区划编码',
    to_region_code VARCHAR(20) NULL COMMENT '迁往区划编码',
    to_address VARCHAR(255) NOT NULL COMMENT '迁往地址',
    out_date DATE NOT NULL COMMENT '迁出日期',
    reason_code VARCHAR(50) NULL COMMENT '迁出原因编码：MIGRATION_REASON字典',
    archive_id BIGINT NOT NULL COMMENT '归档ID（迁出后必须生成快照归档）',
    operator_id BIGINT NULL COMMENT '经办人ID',
    completed_at DATETIME NULL COMMENT '办结时间',
    PRIMARY KEY (out_id),
    UNIQUE KEY uk_migration_out_application (application_id),
    KEY idx_migration_out_person (person_id),
    KEY idx_migration_out_region_date (from_region_code, out_date),
    KEY idx_migration_out_batch (transfer_batch_no),
    KEY idx_migration_out_batch_date (transfer_batch_no, out_date),
    CONSTRAINT fk_migration_out_application FOREIGN KEY (application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_out_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_out_household FOREIGN KEY (from_household_id) REFERENCES household(household_id) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_out_from_region FOREIGN KEY (from_region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_out_to_region FOREIGN KEY (to_region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_out_archive FOREIGN KEY (archive_id) REFERENCES residence_archive(archive_id) ON DELETE RESTRICT,
    CONSTRAINT fk_migration_out_operator FOREIGN KEY (operator_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='迁出业务记录表';

-- Table 3.18: floating_population - Floating Population Registration
CREATE TABLE floating_population (
    floating_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '流动人口登记ID',
    application_id BIGINT NULL COMMENT '申请ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    source_region_code VARCHAR(20) NULL COMMENT '来源区划编码',
    source_address VARCHAR(255) NULL COMMENT '来源地址',
    current_region_code VARCHAR(20) NOT NULL COMMENT '现住区划编码',
    current_address VARCHAR(255) NOT NULL COMMENT '现居住地址',
    arrival_date DATE NOT NULL COMMENT '到达日期',
    register_date DATE NOT NULL COMMENT '登记日期',
    planned_leave_date DATE NULL COMMENT '预计离开日期',
    actual_leave_date DATE NULL COMMENT '实际离开日期',
    residence_reason_code VARCHAR(50) NULL COMMENT '居住事由编码：RESIDENCE_REASON字典',
    employment_school VARCHAR(150) NULL COMMENT '工作或就读单位',
    landlord_name VARCHAR(50) NULL COMMENT '房东或联系人姓名',
    landlord_phone VARCHAR(20) NULL COMMENT '房东或联系人电话',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '登记状态：FLOATING_STATUS字典',
    handling_department_id BIGINT NOT NULL COMMENT '登记部门ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (floating_id),
    KEY idx_floating_person_status (person_id, status),
    KEY idx_floating_region_status (current_region_code, status),
    KEY idx_floating_leave_date (planned_leave_date),
    CONSTRAINT fk_floating_application FOREIGN KEY (application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_floating_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_floating_source_region FOREIGN KEY (source_region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_floating_current_region FOREIGN KEY (current_region_code) REFERENCES admin_region(region_code) ON DELETE RESTRICT,
    CONSTRAINT fk_floating_department FOREIGN KEY (handling_department_id) REFERENCES sys_department(department_id) ON DELETE RESTRICT,
    CONSTRAINT chk_register_date CHECK (register_date >= arrival_date),
    CONSTRAINT chk_planned_leave_date CHECK (planned_leave_date IS NULL OR planned_leave_date >= arrival_date),
    CONSTRAINT chk_actual_leave_date CHECK (actual_leave_date IS NULL OR actual_leave_date >= arrival_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='流动人口居住登记表';

-- Table 3.19: residence_permit - Residence Permit and Temporary Registration Voucher
CREATE TABLE residence_permit (
    permit_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '居住凭证ID',
    application_id BIGINT NULL COMMENT '申请ID',
    floating_id BIGINT NULL COMMENT '流动人口登记ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    permit_type_code VARCHAR(40) NOT NULL COMMENT '凭证类型编码：PERMIT_TYPE字典',
    permit_no VARCHAR(60) NOT NULL COMMENT '凭证编号',
    issue_authority VARCHAR(100) NULL COMMENT '签发机关',
    issue_date DATE NULL COMMENT '签发日期',
    valid_from DATE NULL COMMENT '有效开始日期',
    valid_until DATE NULL COMMENT '有效截止日期',
    permit_status VARCHAR(20) NOT NULL DEFAULT 'VALID' COMMENT '凭证状态：CERT_STATUS字典',
    cancel_date DATE NULL COMMENT '注销日期',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (permit_id),
    UNIQUE KEY uk_permit_no (permit_no),
    KEY idx_permit_person_status (person_id, permit_status),
    KEY idx_permit_valid_until (valid_until),
    CONSTRAINT fk_permit_application FOREIGN KEY (application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permit_floating FOREIGN KEY (floating_id) REFERENCES floating_population(floating_id) ON DELETE RESTRICT,
    CONSTRAINT fk_permit_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT chk_valid_until CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='临时登记凭证和居住证表';

-- Table 3.20: key_population - Key Population Management Record
CREATE TABLE key_population (
    key_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '重点人口记录ID',
    register_application_id BIGINT NOT NULL COMMENT '登记申请ID',
    release_application_id BIGINT NULL COMMENT '解除申请ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    key_type_code VARCHAR(50) NOT NULL COMMENT '重点类型编码：KEY_TYPE字典',
    management_level_code VARCHAR(20) NULL DEFAULT 'NORMAL' COMMENT '管理等级编码：KEY_LEVEL字典',
    register_date DATE NOT NULL COMMENT '登记日期',
    manage_start_date DATE NULL COMMENT '管理开始日期',
    manage_end_date DATE NULL COMMENT '管理结束日期',
    source_basis_summary VARCHAR(255) NULL COMMENT '登记依据摘要（具体凭证存申请材料表）',
    responsible_department_id BIGINT NULL COMMENT '责任部门ID',
    responsible_user_id BIGINT NULL COMMENT '责任人ID',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '管理状态：KEY_STATUS字典',
    remark VARCHAR(500) NULL COMMENT '备注（敏感内容按权限显示）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (key_id),
    UNIQUE KEY uk_key_register_application (register_application_id),
    UNIQUE KEY uk_key_release_application (release_application_id),
    KEY idx_key_person_status (person_id, status),
    KEY idx_key_department_status (responsible_department_id, status),
    CONSTRAINT fk_key_register_application FOREIGN KEY (register_application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_key_release_application FOREIGN KEY (release_application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_key_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_key_department FOREIGN KEY (responsible_department_id) REFERENCES sys_department(department_id) ON DELETE RESTRICT,
    CONSTRAINT fk_key_user FOREIGN KEY (responsible_user_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_manage_end_date CHECK (manage_end_date IS NULL OR manage_start_date IS NULL OR manage_end_date >= manage_start_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='重点人口管理记录表';

-- Table 3.21: certificate - Universal Person Certificate
CREATE TABLE certificate (
    certificate_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '证件ID',
    person_id BIGINT NOT NULL COMMENT '人口ID',
    certificate_type_code VARCHAR(40) NOT NULL COMMENT '证件类型编码：CERT_TYPE字典（不包含居住证）',
    certificate_no VARCHAR(80) NOT NULL COMMENT '证件号码',
    issue_authority VARCHAR(100) NULL COMMENT '签发机关',
    issue_date DATE NULL COMMENT '签发日期',
    valid_from DATE NULL COMMENT '有效开始日期',
    valid_until DATE NULL COMMENT '有效截止日期',
    certificate_status VARCHAR(20) NOT NULL DEFAULT 'VALID' COMMENT '证件状态：CERT_STATUS字典',
    material_id BIGINT NULL COMMENT '关联材料ID',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (certificate_id),
    UNIQUE KEY uk_certificate_type_no (certificate_type_code, certificate_no),
    KEY idx_certificate_person_status (person_id, certificate_status),
    KEY idx_certificate_valid_until (valid_until),
    CONSTRAINT fk_certificate_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_certificate_material FOREIGN KEY (material_id) REFERENCES application_material(material_id) ON DELETE SET NULL,
    CONSTRAINT chk_cert_valid_until CHECK (valid_until IS NULL OR valid_from IS NULL OR valid_until >= valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通用人员证件表';

-- Table 3.22: cancellation_record - Person or Household Cancellation Record
CREATE TABLE cancellation_record (
    cancel_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '注销记录ID',
    cancellation_no VARCHAR(40) NOT NULL COMMENT '注销业务号',
    application_id BIGINT NOT NULL COMMENT '申请ID',
    cancel_object_type VARCHAR(20) NOT NULL COMMENT '注销对象类型：CANCEL_OBJECT_TYPE字典（PERSON/HOUSEHOLD）',
    person_id BIGINT NULL COMMENT '人口ID（人口注销时填写）',
    household_id BIGINT NULL COMMENT '家庭户ID（家庭户销户时填写）',
    cancel_reason_code VARCHAR(50) NOT NULL COMMENT '注销原因编码：CANCEL_REASON字典',
    cancel_date DATE NOT NULL COMMENT '注销日期',
    archive_id BIGINT NULL COMMENT '归档ID（人口注销后回填）',
    operator_id BIGINT NULL COMMENT '经办人ID',
    completed_at DATETIME NULL COMMENT '办结时间',
    PRIMARY KEY (cancel_id),
    UNIQUE KEY uk_cancellation_no (cancellation_no),
    UNIQUE KEY uk_cancellation_application (application_id),
    KEY idx_cancel_person (person_id),
    KEY idx_cancel_household (household_id),
    KEY idx_cancel_object_household (cancel_object_type, household_id, cancel_date),
    CONSTRAINT fk_cancel_application FOREIGN KEY (application_id) REFERENCES business_application(application_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cancel_person FOREIGN KEY (person_id) REFERENCES person(person_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cancel_household FOREIGN KEY (household_id) REFERENCES household(household_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cancel_archive FOREIGN KEY (archive_id) REFERENCES residence_archive(archive_id) ON DELETE RESTRICT,
    CONSTRAINT fk_cancel_operator FOREIGN KEY (operator_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT chk_cancel_object CHECK (
        (cancel_object_type = 'PERSON' AND person_id IS NOT NULL AND household_id IS NULL) OR
        (cancel_object_type = 'HOUSEHOLD' AND person_id IS NULL AND household_id IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人口注销或家庭户销户表';

-- =====================================================
-- Section 7: Audit Tables
-- =====================================================

-- Table 3.24: operation_log - Key Operation Log
CREATE TABLE operation_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    user_id BIGINT NULL COMMENT '操作用户ID',
    department_id BIGINT NULL COMMENT '操作部门ID',
    operation_type_code VARCHAR(30) NOT NULL COMMENT '操作类型编码：OPERATION_TYPE字典',
    module_name VARCHAR(80) NULL COMMENT '模块名称',
    target_table VARCHAR(80) NULL COMMENT '目标表',
    target_id BIGINT NULL COMMENT '目标记录ID',
    request_method VARCHAR(10) NULL COMMENT '请求方式：GET/POST/PUT/DELETE',
    request_uri VARCHAR(255) NULL COMMENT '请求地址',
    operation_result_code VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果编码：OPERATION_RESULT字典',
    before_json_masked TEXT NULL COMMENT '变更前数据（脱敏后）',
    after_json_masked TEXT NULL COMMENT '变更后数据（脱敏后）',
    ip_address VARCHAR(50) NULL COMMENT 'IP地址',
    trace_id VARCHAR(64) NULL COMMENT '链路追踪ID',
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    PRIMARY KEY (log_id),
    KEY idx_log_user_time (user_id, operation_time),
    KEY idx_log_target (target_table, target_id),
    KEY idx_log_time (operation_time),
    KEY idx_log_trace (trace_id),
    CONSTRAINT fk_log_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE SET NULL,
    CONSTRAINT fk_log_department FOREIGN KEY (department_id) REFERENCES sys_department(department_id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='关键操作日志表';

-- Table 3.25: data_export_log - Data Export Audit Log
CREATE TABLE data_export_log (
    export_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '导出日志ID',
    export_no VARCHAR(40) NOT NULL COMMENT '导出单号',
    user_id BIGINT NOT NULL COMMENT '导出用户ID',
    department_id BIGINT NOT NULL COMMENT '导出部门ID',
    export_type_code VARCHAR(40) NOT NULL COMMENT '导出类型编码：EXPORT_TYPE字典',
    query_condition_summary VARCHAR(1000) NULL COMMENT '查询条件摘要（脱敏）',
    exported_rows INT NOT NULL DEFAULT 0 COMMENT '导出行数',
    sensitivity_level TINYINT NOT NULL DEFAULT 1 COMMENT '敏感等级：1-3',
    approval_id BIGINT NULL COMMENT '审批ID（三级敏感导出必填）',
    file_name VARCHAR(200) NULL COMMENT '导出文件名',
    file_hash VARCHAR(128) NULL COMMENT '文件哈希',
    result_code VARCHAR(20) NOT NULL DEFAULT 'SUCCESS' COMMENT '导出结果编码：OPERATION_RESULT字典',
    exported_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导出时间',
    PRIMARY KEY (export_id),
    UNIQUE KEY uk_export_no (export_no),
    KEY idx_export_user_time (user_id, exported_at),
    KEY idx_export_department_time (department_id, exported_at),
    CONSTRAINT fk_export_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE RESTRICT,
    CONSTRAINT fk_export_department FOREIGN KEY (department_id) REFERENCES sys_department(department_id) ON DELETE RESTRICT,
    CONSTRAINT fk_export_approval FOREIGN KEY (approval_id) REFERENCES sys_approval_request(approval_id) ON DELETE RESTRICT,
    CONSTRAINT chk_exported_rows CHECK (exported_rows >= 0),
    CONSTRAINT chk_export_sensitivity CHECK (sensitivity_level BETWEEN 1 AND 3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据导出审计表';

-- =====================================================
-- Table 3.26: login_log - Login Audit Log
-- =====================================================
CREATE TABLE login_log (
    log_id BIGINT NOT NULL AUTO_INCREMENT COMMENT '登录日志ID',
    username VARCHAR(50) NOT NULL COMMENT '登录用户名（明文，按需脱敏展示）',
    user_id BIGINT NULL COMMENT '登录成功时关联的用户ID（失败时为 NULL）',
    login_status VARCHAR(20) NOT NULL COMMENT '登录状态：LOGIN_STATUS 字典（SUCCESS/FAILED/LOCKED）',
    failure_reason VARCHAR(255) NULL COMMENT '失败原因（密码错误/账号停用/账号不存在/令牌过期等）',
    login_ip VARCHAR(50) NULL COMMENT '客户端 IP',
    user_agent VARCHAR(500) NULL COMMENT '浏览器/客户端 UA',
    device_fingerprint VARCHAR(128) NULL COMMENT '设备指纹（可选）',
    login_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
    PRIMARY KEY (log_id),
    KEY idx_login_username_time (username, login_time),
    KEY idx_login_user_time (user_id, login_time),
    KEY idx_login_ip_time (login_ip, login_time),
    KEY idx_login_status_time (login_status, login_time),
    KEY idx_login_time (login_time),
    CONSTRAINT fk_login_log_user FOREIGN KEY (user_id) REFERENCES sys_user(user_id) ON DELETE SET NULL,
    CONSTRAINT chk_login_status CHECK (login_status IN ('SUCCESS', 'FAILED', 'LOCKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- =====================================================
-- Print completion message
-- =====================================================
SELECT 'Database population_miniterm created successfully with 26 tables!' AS Result;
