-- Phase 03 household migration and residence archive. Additive and repeatable; no core table is dropped.
USE population_miniterm;
DELIMITER $$
DROP PROCEDURE IF EXISTS phase03_add_column$$
CREATE PROCEDURE phase03_add_column(IN t VARCHAR(64),IN c VARCHAR(64),IN ddl TEXT)
BEGIN IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN SET @s=ddl;PREPARE x FROM @s;EXECUTE x;DEALLOCATE PREPARE x;END IF;END$$
DROP PROCEDURE IF EXISTS phase03_add_index$$
CREATE PROCEDURE phase03_add_index(IN t VARCHAR(64),IN i VARCHAR(64),IN ddl TEXT)
BEGIN IF NOT EXISTS(SELECT 1 FROM information_schema.statistics WHERE table_schema=DATABASE() AND table_name=t AND index_name=i) THEN SET @s=ddl;PREPARE x FROM @s;EXECUTE x;DEALLOCATE PREPARE x;END IF;END$$
DROP PROCEDURE IF EXISTS phase03_add_constraint$$
CREATE PROCEDURE phase03_add_constraint(IN t VARCHAR(64),IN n VARCHAR(64),IN ddl TEXT)
BEGIN IF NOT EXISTS(SELECT 1 FROM information_schema.table_constraints WHERE constraint_schema=DATABASE() AND table_name=t AND constraint_name=n) THEN SET @s=ddl;PREPARE x FROM @s;EXECUTE x;DEALLOCATE PREPARE x;END IF;END$$
DROP PROCEDURE IF EXISTS phase03_modify_if_column$$
CREATE PROCEDURE phase03_modify_if_column(IN t VARCHAR(64),IN c VARCHAR(64),IN ddl TEXT)
BEGIN IF EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=t AND column_name=c) THEN SET @s=ddl;PREPARE x FROM @s;EXECUTE x;DEALLOCATE PREPARE x;END IF;END$$
DELIMITER ;

CALL phase03_add_column('person','current_status_code','ALTER TABLE person ADD COLUMN current_status_code VARCHAR(30) NOT NULL DEFAULT ''PENDING'' AFTER status');
CALL phase03_add_column('household','region_code','ALTER TABLE household ADD COLUMN region_code VARCHAR(20) NULL AFTER address');
CALL phase03_add_column('household','version','ALTER TABLE household ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status');
CALL phase03_add_column('household_member','version','ALTER TABLE household_member ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status');
UPDATE household SET status='ACTIVE' WHERE status NOT IN ('ACTIVE','PENDING_CANCELLATION');
UPDATE household_member SET status=CASE WHEN leave_date IS NULL THEN 'ACTIVE' ELSE 'LEFT' END WHERE status NOT IN ('ACTIVE','LEFT');
CALL phase03_add_index('household_member','uk_household_member_pair','ALTER TABLE household_member ADD UNIQUE KEY uk_household_member_pair(household_id,person_id)');

CALL phase03_add_column('residence','registered_address','ALTER TABLE residence ADD COLUMN registered_address VARCHAR(255) NULL AFTER household_id');
CALL phase03_add_column('residence','region_code','ALTER TABLE residence ADD COLUMN region_code VARCHAR(20) NULL AFTER registered_address');
CALL phase03_add_column('residence','register_type_code','ALTER TABLE residence ADD COLUMN register_type_code VARCHAR(30) NULL AFTER region_code');
CALL phase03_add_column('residence','start_date','ALTER TABLE residence ADD COLUMN start_date DATE NULL AFTER register_date');
CALL phase03_add_column('residence','created_by','ALTER TABLE residence ADD COLUMN created_by BIGINT NULL AFTER status');
CALL phase03_add_column('residence','version','ALTER TABLE residence ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER created_by');
CALL phase03_modify_if_column('residence','residence_type','ALTER TABLE residence MODIFY residence_type VARCHAR(30) NULL DEFAULT ''LEGACY''');
UPDATE residence r JOIN household h ON h.household_id=r.household_id SET r.registered_address=COALESCE(r.registered_address,h.address),r.region_code=COALESCE(r.region_code,h.region_code),r.register_type_code=COALESCE(r.register_type_code,r.residence_type,'LEGACY'),r.start_date=COALESCE(r.start_date,r.register_date),r.status='ACTIVE';
CALL phase03_add_index('residence','uk_residence_person','ALTER TABLE residence ADD UNIQUE KEY uk_residence_person(person_id)');

CALL phase03_add_column('migration_in','application_id','ALTER TABLE migration_in ADD COLUMN application_id BIGINT NULL AFTER in_id');
CALL phase03_add_column('migration_in','migration_type','ALTER TABLE migration_in ADD COLUMN migration_type VARCHAR(40) NULL AFTER person_id');
CALL phase03_add_column('migration_in','from_region_code','ALTER TABLE migration_in ADD COLUMN from_region_code VARCHAR(20) NULL AFTER migration_type');
CALL phase03_add_column('migration_in','to_region_code','ALTER TABLE migration_in ADD COLUMN to_region_code VARCHAR(20) NULL AFTER from_address');
CALL phase03_add_column('migration_in','to_address_snapshot','ALTER TABLE migration_in ADD COLUMN to_address_snapshot VARCHAR(255) NULL AFTER to_household_id');
CALL phase03_add_column('migration_in','transfer_batch_no','ALTER TABLE migration_in ADD COLUMN transfer_batch_no VARCHAR(40) NULL AFTER reason');
CALL phase03_add_column('migration_in','business_status','ALTER TABLE migration_in ADD COLUMN business_status VARCHAR(30) NOT NULL DEFAULT ''DRAFT'' AFTER transfer_batch_no');
CALL phase03_add_column('migration_in','executed_at','ALTER TABLE migration_in ADD COLUMN executed_at DATETIME NULL AFTER operator_id');
CALL phase03_add_column('migration_in','updated_at','ALTER TABLE migration_in ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at');
CALL phase03_add_column('migration_in','version','ALTER TABLE migration_in ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER executed_at');
ALTER TABLE migration_in MODIFY reason VARCHAR(500) NULL;
CALL phase03_add_index('migration_in','uk_migration_in_application','ALTER TABLE migration_in ADD UNIQUE KEY uk_migration_in_application(application_id)');
CALL phase03_add_index('migration_in','uk_migration_in_batch','ALTER TABLE migration_in ADD UNIQUE KEY uk_migration_in_batch(transfer_batch_no)');
CALL phase03_add_constraint('migration_in','fk_migration_in_application','ALTER TABLE migration_in ADD CONSTRAINT fk_migration_in_application FOREIGN KEY(application_id) REFERENCES business_application(application_id)');

CALL phase03_add_column('migration_out','application_id','ALTER TABLE migration_out ADD COLUMN application_id BIGINT NULL AFTER out_id');
CALL phase03_add_column('migration_out','migration_type','ALTER TABLE migration_out ADD COLUMN migration_type VARCHAR(40) NULL AFTER person_id');
CALL phase03_add_column('migration_out','from_region_code','ALTER TABLE migration_out ADD COLUMN from_region_code VARCHAR(20) NULL AFTER migration_type');
CALL phase03_add_column('migration_out','from_address_snapshot','ALTER TABLE migration_out ADD COLUMN from_address_snapshot VARCHAR(255) NULL AFTER from_household_id');
CALL phase03_add_column('migration_out','to_region_code','ALTER TABLE migration_out ADD COLUMN to_region_code VARCHAR(20) NULL AFTER from_address_snapshot');
CALL phase03_add_column('migration_out','transfer_batch_no','ALTER TABLE migration_out ADD COLUMN transfer_batch_no VARCHAR(40) NULL AFTER reason');
CALL phase03_add_column('migration_out','new_head_person_id','ALTER TABLE migration_out ADD COLUMN new_head_person_id BIGINT NULL AFTER transfer_batch_no');
CALL phase03_add_column('migration_out','business_status','ALTER TABLE migration_out ADD COLUMN business_status VARCHAR(30) NOT NULL DEFAULT ''DRAFT'' AFTER new_head_person_id');
CALL phase03_add_column('migration_out','executed_at','ALTER TABLE migration_out ADD COLUMN executed_at DATETIME NULL AFTER operator_id');
CALL phase03_add_column('migration_out','updated_at','ALTER TABLE migration_out ADD COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER created_at');
CALL phase03_add_column('migration_out','version','ALTER TABLE migration_out ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER executed_at');
ALTER TABLE migration_out MODIFY reason VARCHAR(500) NULL;
CALL phase03_add_index('migration_out','uk_migration_out_application','ALTER TABLE migration_out ADD UNIQUE KEY uk_migration_out_application(application_id)');
CALL phase03_add_index('migration_out','uk_migration_out_batch','ALTER TABLE migration_out ADD UNIQUE KEY uk_migration_out_batch(transfer_batch_no)');
CALL phase03_add_constraint('migration_out','fk_migration_out_application','ALTER TABLE migration_out ADD CONSTRAINT fk_migration_out_application FOREIGN KEY(application_id) REFERENCES business_application(application_id)');

CREATE TABLE IF NOT EXISTS residence_archive(
 archive_id BIGINT NOT NULL AUTO_INCREMENT,original_registration_id BIGINT NOT NULL,person_id BIGINT NOT NULL,household_id BIGINT NOT NULL,
 application_id BIGINT NOT NULL,migration_out_id BIGINT NULL,archive_type VARCHAR(40) NOT NULL,archive_reason VARCHAR(500) NULL,
 person_name_snapshot VARCHAR(50) NOT NULL,identity_no_snapshot VARCHAR(50) NOT NULL,household_no_snapshot VARCHAR(30) NOT NULL,
 registered_address_snapshot VARCHAR(255) NOT NULL,region_code_snapshot VARCHAR(20) NOT NULL,register_type_code_snapshot VARCHAR(30) NULL,
 register_date_snapshot DATE NULL,start_date_snapshot DATE NULL,end_date_snapshot DATE NOT NULL,original_status VARCHAR(30) NULL,
 archived_by BIGINT NOT NULL,archived_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
 PRIMARY KEY(archive_id),UNIQUE KEY uk_residence_archive_application(application_id),KEY idx_archive_person_time(person_id,archived_at),
 KEY idx_archive_region_time(region_code_snapshot,archived_at),CONSTRAINT fk_archive_application FOREIGN KEY(application_id) REFERENCES business_application(application_id),
 CONSTRAINT fk_archive_migration_out FOREIGN KEY(migration_out_id) REFERENCES migration_out(out_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES
('migration:in:create','创建迁入申请','MIGRATION','API','ENABLED'),('migration:out:create','创建迁出申请','MIGRATION','API','ENABLED'),
('migration:view','查看迁移','MIGRATION','API','ENABLED'),('migration:execute','执行迁移','MIGRATION','API','ENABLED'),
('migration:archive:view','查看户籍归档','MIGRATION','API','ENABLED') ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p WHERE
(r.role_code='QUERY_VIEWER' AND p.permission_code='migration:view') OR (r.role_code='POPULATION_MANAGER' AND p.permission_code='migration:view') OR
(r.role_code='HOUSEHOLD_MANAGER' AND p.permission_code IN('migration:in:create','migration:out:create','migration:view','migration:execute','migration:archive:view')) OR
(r.role_code='APPROVER' AND p.permission_code IN('migration:view','migration:archive:view')) OR r.role_code='SYSTEM_ADMIN';

DROP PROCEDURE phase03_add_column; DROP PROCEDURE phase03_add_index; DROP PROCEDURE phase03_add_constraint; DROP PROCEDURE phase03_modify_if_column;
