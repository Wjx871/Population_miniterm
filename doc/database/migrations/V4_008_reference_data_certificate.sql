-- Phase 09 reference data and generic certificate support (MySQL 8, repeatable).
CREATE TABLE IF NOT EXISTS admin_region(
 region_id BIGINT NOT NULL AUTO_INCREMENT,region_code VARCHAR(20) NOT NULL,region_name VARCHAR(100) NOT NULL,parent_id BIGINT NULL,
 region_level INT NOT NULL,full_name VARCHAR(255) NOT NULL,sort_no INT NOT NULL DEFAULT 0,status VARCHAR(20) NOT NULL DEFAULT 'ENABLED',
 version INT NOT NULL DEFAULT 0,created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
 PRIMARY KEY(region_id),UNIQUE KEY uk_admin_region_code(region_code),KEY idx_admin_region_parent_status(parent_id,status,sort_no),KEY idx_admin_region_level_status(region_level,status,sort_no),
 CONSTRAINT fk_admin_region_parent FOREIGN KEY(parent_id) REFERENCES admin_region(region_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO admin_region(region_code,region_name,parent_id,region_level,full_name,sort_no,status) VALUES
 ('110000','示例省',NULL,1,'示例省',10,'ENABLED')
ON DUPLICATE KEY UPDATE region_name=VALUES(region_name),full_name=VALUES(full_name);
INSERT INTO admin_region(region_code,region_name,parent_id,region_level,full_name,sort_no,status) SELECT '110100','示例市',region_id,2,'示例省示例市',10,'ENABLED' FROM admin_region WHERE region_code='110000' ON DUPLICATE KEY UPDATE region_name=VALUES(region_name),parent_id=VALUES(parent_id),full_name=VALUES(full_name);
INSERT INTO admin_region(region_code,region_name,parent_id,region_level,full_name,sort_no,status) SELECT '110101','示例东区',region_id,3,'示例省示例市示例东区',10,'ENABLED' FROM admin_region WHERE region_code='110100' ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id);
INSERT INTO admin_region(region_code,region_name,parent_id,region_level,full_name,sort_no,status) SELECT '110105','示例西区',region_id,3,'示例省示例市示例西区',20,'ENABLED' FROM admin_region WHERE region_code='110100' ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id);
INSERT INTO admin_region(region_code,region_name,parent_id,region_level,full_name,sort_no,status) SELECT '110105001','示例街道',region_id,4,'示例省示例市示例西区示例街道',10,'ENABLED' FROM admin_region WHERE region_code='110105' ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id);
INSERT INTO admin_region(region_code,region_name,parent_id,region_level,full_name,sort_no,status) SELECT '110105001001','示例社区',region_id,5,'示例省示例市示例西区示例街道示例社区',10,'ENABLED' FROM admin_region WHERE region_code='110105001' ON DUPLICATE KEY UPDATE parent_id=VALUES(parent_id);
INSERT INTO admin_region(region_code,region_name,parent_id,region_level,full_name,sort_no,status) VALUES('120000','外区示例省',NULL,1,'外区示例省',20,'ENABLED') ON DUPLICATE KEY UPDATE region_name=VALUES(region_name),full_name=VALUES(full_name);

INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES
 ('region:view','查看行政区划','REGION','API','ENABLED'),('region:manage','维护行政区划','REGION','API','ENABLED')
ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p WHERE p.permission_code='region:view' OR (r.role_code='SYSTEM_ADMIN' AND p.permission_code='region:manage');

DROP PROCEDURE IF EXISTS phase09_add_column;
DELIMITER $$
CREATE PROCEDURE phase09_add_column(IN p_table VARCHAR(64),IN p_column VARCHAR(64),IN p_ddl TEXT)
BEGIN IF NOT EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=p_table AND column_name=p_column) THEN SET @phase09_ddl=p_ddl;PREPARE phase09_stmt FROM @phase09_ddl;EXECUTE phase09_stmt;DEALLOCATE PREPARE phase09_stmt;END IF;END$$
DELIMITER ;
CALL phase09_add_column('data_dictionary','version','ALTER TABLE data_dictionary ADD COLUMN version INT NOT NULL DEFAULT 0 AFTER status');
UPDATE data_dictionary SET status=CASE WHEN status IN('启用','ENABLED') THEN 'ENABLED' WHEN status IN('停用','DISABLED') THEN 'DISABLED' ELSE status END;
INSERT INTO data_dictionary(dict_type,dict_code,dict_name,sort_no,status) VALUES
 ('CERTIFICATE_TYPE','PASSPORT','护照',10,'ENABLED'),('CERTIFICATE_TYPE','DRIVER_LICENSE','机动车驾驶证',20,'ENABLED'),('CERTIFICATE_TYPE','OTHER','其他证件',99,'ENABLED'),
 ('ETHNICITY','HAN','汉族',10,'ENABLED'),('HOUSEHOLD_RELATIONSHIP','HEAD','户主',10,'ENABLED'),('HOUSEHOLD_RELATIONSHIP','SPOUSE','配偶',20,'ENABLED'),('HOUSEHOLD_TYPE','FAMILY','家庭户',10,'ENABLED'),('MIGRATION_REASON','WORK','工作迁移',10,'ENABLED'),('CANCELLATION_REASON','DEATH','死亡',10,'ENABLED'),('FLOATING_RESIDENCE_REASON','WORK','务工',10,'ENABLED'),('KEY_POPULATION_TYPE','OTHER','其他',99,'ENABLED')
ON DUPLICATE KEY UPDATE dict_name=VALUES(dict_name),sort_no=VALUES(sort_no);
INSERT INTO sys_permission(permission_code,permission_name,module_name,permission_type,status) VALUES('dictionary:view','查看数据字典','DICTIONARY','API','ENABLED'),('dictionary:manage','维护数据字典','DICTIONARY','API','ENABLED') ON DUPLICATE KEY UPDATE permission_name=VALUES(permission_name),status='ENABLED';
INSERT IGNORE INTO sys_role_permission(role_id,permission_id) SELECT r.role_id,p.permission_id FROM sys_role r CROSS JOIN sys_permission p WHERE p.permission_code='dictionary:view' OR (r.role_code='SYSTEM_ADMIN' AND p.permission_code='dictionary:manage');
DROP PROCEDURE IF EXISTS phase09_add_column;
