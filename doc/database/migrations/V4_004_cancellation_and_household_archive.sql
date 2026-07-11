-- Phase 04 cancellation records and household snapshots. Additive and repeatable.
USE population_miniterm;
DELIMITER $$
DROP PROCEDURE IF EXISTS phase04_add_column$$ CREATE PROCEDURE phase04_add_column(IN t VARCHAR(64),IN c VARCHAR(64),IN ddl TEXT) BEGIN IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN SET @s=ddl;PREPARE x FROM @s;EXECUTE x;DEALLOCATE PREPARE x;END IF;END$$
DELIMITER ;
CALL phase04_add_column('household','household_type','ALTER TABLE household ADD COLUMN household_type VARCHAR(30) NULL AFTER region_code');
CREATE TABLE IF NOT EXISTS cancellation_record(
 cancellation_id BIGINT NOT NULL AUTO_INCREMENT,cancellation_no VARCHAR(40) NOT NULL,application_id BIGINT NOT NULL,cancel_object_type VARCHAR(30) NOT NULL,
 person_id BIGINT NULL,household_id BIGINT NULL,source_household_id BIGINT NULL,cancel_reason_code VARCHAR(50) NOT NULL,cancel_reason_detail VARCHAR(500) NULL,
 event_date DATE NOT NULL,new_head_person_id BIGINT NULL,business_status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',person_name_snapshot VARCHAR(50) NULL,
 identity_no_snapshot VARCHAR(50) NULL,household_no_snapshot VARCHAR(30) NULL,address_snapshot VARCHAR(255) NULL,region_code_snapshot VARCHAR(20) NULL,
 operator_id BIGINT NULL,executed_at DATETIME NULL,version INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,PRIMARY KEY(cancellation_id),UNIQUE KEY uk_cancellation_no(cancellation_no),
 UNIQUE KEY uk_cancellation_application(application_id),KEY idx_cancellation_person_status(person_id,business_status),KEY idx_cancellation_household_status(household_id,business_status),
 KEY idx_cancellation_region_created(region_code_snapshot,created_at),CONSTRAINT ck_cancellation_object CHECK((cancel_object_type='PERSON' AND person_id IS NOT NULL AND household_id IS NULL) OR(cancel_object_type='HOUSEHOLD' AND household_id IS NOT NULL AND person_id IS NULL)),
 CONSTRAINT fk_cancellation_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),CONSTRAINT fk_cancellation_person FOREIGN KEY(person_id) REFERENCES person(person_id),CONSTRAINT fk_cancellation_household FOREIGN KEY(household_id) REFERENCES household(household_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE TABLE IF NOT EXISTS household_archive(
 archive_id BIGINT NOT NULL AUTO_INCREMENT,original_household_id BIGINT NOT NULL,application_id BIGINT NOT NULL,cancellation_id BIGINT NOT NULL,
 household_no_snapshot VARCHAR(30) NOT NULL,head_person_id_snapshot BIGINT NULL,head_person_name_snapshot VARCHAR(50) NULL,registered_address_snapshot VARCHAR(255) NOT NULL,
 region_code_snapshot VARCHAR(20) NULL,household_type_snapshot VARCHAR(30) NULL,establish_date_snapshot DATE NULL,original_status VARCHAR(30) NOT NULL,
 cancellation_reason_code VARCHAR(50) NOT NULL,cancellation_reason_detail VARCHAR(500) NULL,archived_by BIGINT NOT NULL,archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(archive_id),UNIQUE KEY uk_household_archive_application(application_id),UNIQUE KEY uk_household_archive_cancellation(cancellation_id),
 KEY idx_household_archive_no(household_no_snapshot),KEY idx_household_archive_region_time(region_code_snapshot,archived_at),
 CONSTRAINT fk_household_archive_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),CONSTRAINT fk_household_archive_cancellation FOREIGN KEY(cancellation_id) REFERENCES cancellation_record(cancellation_id),CONSTRAINT fk_household_archive_original FOREIGN KEY(original_household_id) REFERENCES household(household_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES
('cancellation:person:create','创建人员注销申请','CANCELLATION','API','ENABLED'),('cancellation:household:create','创建家庭户销户申请','CANCELLATION','API','ENABLED'),('cancellation:view','查看注销业务','CANCELLATION','API','ENABLED'),('cancellation:execute','执行注销业务','CANCELLATION','API','ENABLED'),('cancellation:archive:view','查看注销归档','CANCELLATION','API','ENABLED') ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p WHERE
(r.role_code='QUERY_VIEWER' AND p.permission_code IN('cancellation:view','cancellation:archive:view')) OR(r.role_code='POPULATION_MANAGER' AND p.permission_code IN('cancellation:view','cancellation:person:create')) OR(r.role_code='HOUSEHOLD_MANAGER' AND p.permission_code IN('cancellation:view','cancellation:person:create','cancellation:household:create','cancellation:execute','cancellation:archive:view')) OR(r.role_code='APPROVER' AND p.permission_code IN('cancellation:view','cancellation:archive:view')) OR r.role_code='SYSTEM_ADMIN';
DROP PROCEDURE phase04_add_column;
