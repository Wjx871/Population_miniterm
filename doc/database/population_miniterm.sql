CREATE DATABASE IF NOT EXISTS population_miniterm
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE population_miniterm;

CREATE TABLE IF NOT EXISTS sys_role (
    role_id BIGINT NOT NULL AUTO_INCREMENT,
    role_name VARCHAR(50) NOT NULL,
    description VARCHAR(255) NULL,
    status VARCHAR(20) NOT NULL DEFAULT '启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (role_id),
    UNIQUE KEY uk_sys_role_role_name (role_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_permission (
    permission_id BIGINT NOT NULL AUTO_INCREMENT,
    permission_code VARCHAR(100) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    module_name VARCHAR(100) NULL,
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
    real_name VARCHAR(50) NULL,
    phone VARCHAR(20) NULL,
    status VARCHAR(20) NOT NULL DEFAULT '启用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id),
    UNIQUE KEY uk_sys_user_username (username),
    KEY idx_sys_user_role_id (role_id),
    CONSTRAINT fk_sys_user_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS sys_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    PRIMARY KEY (role_id, permission_id),
    KEY idx_sys_role_permission_permission_id (permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (role_id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
    target_table VARCHAR(100) NULL,
    target_id VARCHAR(100) NULL,
    operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ip_address VARCHAR(50) NULL,
    operation_result VARCHAR(50) NULL,
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
