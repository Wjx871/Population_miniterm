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
