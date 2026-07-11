-- Phase 05: floating population and residence permit lifecycle (MySQL 8)
CREATE TABLE IF NOT EXISTS floating_registration_application(
 floating_application_id BIGINT NOT NULL AUTO_INCREMENT,application_id BIGINT NOT NULL,person_id BIGINT NOT NULL,source_region_code VARCHAR(20) NOT NULL,
 source_address VARCHAR(255),current_region_code VARCHAR(20) NOT NULL,current_address VARCHAR(255) NOT NULL,residence_reason_code VARCHAR(30) NOT NULL,
 residence_proof_type VARCHAR(30) NOT NULL,arrival_date DATE NOT NULL,planned_leave_date DATE,applicant_phone VARCHAR(20),business_status VARCHAR(30) NOT NULL,
 executed_floating_id BIGINT,operator_id BIGINT,executed_at DATETIME,version INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(floating_application_id),
 UNIQUE KEY uk_floating_application(application_id),KEY idx_floating_app_person_status(person_id,business_status),
 CONSTRAINT fk_floating_app_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),
 CONSTRAINT fk_floating_app_person FOREIGN KEY(person_id) REFERENCES person(person_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE floating_population
 ADD COLUMN registration_no VARCHAR(40) NULL AFTER floating_id,ADD COLUMN source_application_id BIGINT NULL AFTER registration_no,
 ADD COLUMN source_region_code VARCHAR(20) NULL AFTER person_id,ADD COLUMN source_address VARCHAR(255) NULL AFTER source_region_code,
 ADD COLUMN current_region_code VARCHAR(20) NULL AFTER source_address,ADD COLUMN residence_reason_code VARCHAR(30) NULL AFTER current_address,
 ADD COLUMN residence_proof_type VARCHAR(30) NULL AFTER residence_reason_code,ADD COLUMN arrival_date DATE NULL AFTER residence_proof_type,
 ADD COLUMN planned_leave_date DATE NULL AFTER arrival_date,CHANGE register_date registration_date DATE NOT NULL,
 ADD COLUMN eligible_from_date DATE NULL AFTER registration_date,ADD COLUMN department_id BIGINT NULL AFTER eligible_from_date,
 ADD COLUMN operator_id BIGINT NULL AFTER department_id,ADD COLUMN close_reason_code VARCHAR(30) NULL AFTER status,
 ADD COLUMN closed_at DATETIME NULL AFTER close_reason_code,ADD COLUMN current_flag TINYINT NULL AFTER closed_at,
 ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER current_flag;
UPDATE floating_population SET registration_no=CONCAT('LEGACY-FR-',floating_id),source_region_code='LEGACY',current_region_code='LEGACY',
 residence_reason_code='OTHER_APPROVED',residence_proof_type='OTHER_APPROVED',arrival_date=registration_date,current_flag=IF(status IN('有效','ACTIVE'),1,NULL),
 status=IF(status='有效','ACTIVE',status) WHERE registration_no IS NULL;
ALTER TABLE floating_population MODIFY registration_no VARCHAR(40) NOT NULL,MODIFY source_region_code VARCHAR(20) NOT NULL,
 MODIFY current_region_code VARCHAR(20) NOT NULL,MODIFY residence_reason_code VARCHAR(30) NOT NULL,MODIFY residence_proof_type VARCHAR(30) NOT NULL,
 MODIFY arrival_date DATE NOT NULL,ADD UNIQUE KEY uk_floating_registration_no(registration_no),ADD UNIQUE KEY uk_floating_source_application(source_application_id),
 ADD UNIQUE KEY uk_floating_person_current(person_id,current_flag),ADD KEY idx_floating_region_status(current_region_code,status);

CREATE TABLE IF NOT EXISTS residence_permit(
 permit_id BIGINT NOT NULL AUTO_INCREMENT,permit_no VARCHAR(50) NOT NULL,person_id BIGINT NOT NULL,floating_id BIGINT NOT NULL,source_application_id BIGINT NOT NULL,
 issue_region_code VARCHAR(20) NOT NULL,issuing_department_id BIGINT,issuing_authority VARCHAR(100) NOT NULL,issue_date DATE NOT NULL,valid_from DATE NOT NULL,
 valid_until DATE NOT NULL,last_endorsed_at DATETIME,status VARCHAR(30) NOT NULL,cancellation_reason VARCHAR(500),cancelled_at DATETIME,current_flag TINYINT,
 version INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(permit_id),UNIQUE KEY uk_permit_no(permit_no),UNIQUE KEY uk_permit_source_application(source_application_id),
 UNIQUE KEY uk_permit_person_current(person_id,current_flag),KEY idx_permit_region_status_expiry(issue_region_code,status,valid_until),
 CONSTRAINT fk_permit_person FOREIGN KEY(person_id) REFERENCES person(person_id),CONSTRAINT fk_permit_floating FOREIGN KEY(floating_id) REFERENCES floating_population(floating_id),
 CONSTRAINT fk_permit_application FOREIGN KEY(source_application_id) REFERENCES business_application(application_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS residence_permit_application(
 permit_application_id BIGINT NOT NULL AUTO_INCREMENT,application_id BIGINT NOT NULL,apply_type VARCHAR(30) NOT NULL,floating_id BIGINT NOT NULL,permit_id BIGINT,
 person_id BIGINT NOT NULL,current_region_code VARCHAR(20) NOT NULL,residence_basis_code VARCHAR(30) NOT NULL,requested_valid_from DATE,requested_valid_until DATE,
 business_status VARCHAR(30) NOT NULL,executed_permit_id BIGINT,operator_id BIGINT,executed_at DATETIME,version INT NOT NULL DEFAULT 0,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(permit_application_id),UNIQUE KEY uk_permit_application(application_id),KEY idx_permit_app_person_status(person_id,business_status),
 CONSTRAINT fk_permit_app_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),
 CONSTRAINT fk_permit_app_floating FOREIGN KEY(floating_id) REFERENCES floating_population(floating_id),
 CONSTRAINT fk_permit_app_permit FOREIGN KEY(permit_id) REFERENCES residence_permit(permit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS residence_permit_log(
 permit_log_id BIGINT NOT NULL AUTO_INCREMENT,permit_id BIGINT NOT NULL,application_id BIGINT,action VARCHAR(30) NOT NULL,from_status VARCHAR(30),
 to_status VARCHAR(30) NOT NULL,old_valid_until DATE,new_valid_until DATE,operator_id BIGINT,reason VARCHAR(500),
 operation_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(permit_log_id),
 KEY idx_permit_log_permit_time(permit_id,operation_time),CONSTRAINT fk_permit_log_permit FOREIGN KEY(permit_id) REFERENCES residence_permit(permit_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES
 ('floating:create','创建流动登记','FLOATING','API','ENABLED'),('floating:edit','编辑流动登记','FLOATING','API','ENABLED'),
 ('floating:view','查看流动人口','FLOATING','API','ENABLED'),('floating:execute','执行流动登记','FLOATING','API','ENABLED'),
 ('floating:close','关闭流动登记','FLOATING','API','ENABLED'),('residence-permit:apply','申请居住证','RESIDENCE_PERMIT','API','ENABLED'),
 ('residence-permit:view','查看居住证','RESIDENCE_PERMIT','API','ENABLED'),('residence-permit:issue','签发居住证','RESIDENCE_PERMIT','API','ENABLED'),
 ('residence-permit:endorse','签注居住证','RESIDENCE_PERMIT','API','ENABLED'),('residence-permit:cancel','注销居住证','RESIDENCE_PERMIT','API','ENABLED'),
 ('residence-permit:log:view','查看居住证轨迹','RESIDENCE_PERMIT','API','ENABLED'),('residence-permit:expiry:view','查看到期提醒','RESIDENCE_PERMIT','API','ENABLED')
 ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p WHERE
 (r.role_code='QUERY_VIEWER' AND p.permission_code IN('floating:view','residence-permit:view','residence-permit:log:view','residence-permit:expiry:view')) OR
 (r.role_code='POPULATION_MANAGER' AND p.permission_code IN('floating:create','floating:edit','floating:view','residence-permit:apply','residence-permit:view','residence-permit:log:view','residence-permit:expiry:view')) OR
 (r.role_code='HOUSEHOLD_MANAGER' AND p.module_name IN('FLOATING','RESIDENCE_PERMIT')) OR
 (r.role_code='APPROVER' AND p.permission_code IN('floating:view','residence-permit:view','residence-permit:log:view','residence-permit:expiry:view')) OR r.role_code='SYSTEM_ADMIN';
