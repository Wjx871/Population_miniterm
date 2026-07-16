-- Phase 14: 新增人口-身份证影印本必传，配合 PaddleOCR 自动录入（可跳过）。
-- 决策：
--   * OCR 三态 SUCCESS / FAILED / SKIPPED 全部入 person_idcard_image，不再单独建 idcard_ocr_log。
--   * 反面识别不做（side 恒为 front），保留扩展点。
--   * draft_uuid 允许"先上传、后提交"，与 person 解耦；commit 后写回 person_id。
--   * 同 SHA 复用，不重复落盘。
--   * 权限码 person:create-with-idcard 仅授予 POPULATION_MANAGER / SYSTEM_ADMIN；
--     HOUSEHOLD_MANAGER 不需要（户口经理不参与新增人口）。
-- Safe to execute repeatedly on MySQL 8.

USE population_miniterm;

-- 1) 权限
INSERT INTO sys_permission (permission_code, permission_name, module_name, permission_type, status)
VALUES ('person:create-with-idcard', '新增人口并附身份证影印本', 'PERSON', 'API', 'ENABLED')
ON DUPLICATE KEY UPDATE
    permission_name = VALUES(permission_name),
    status          = VALUES(status);

-- 2) 角色绑定（仅人口经理 / 系统管理员）
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE p.permission_code = 'person:create-with-idcard'
  AND r.role_code IN ('POPULATION_MANAGER','SYSTEM_ADMIN');

-- 3) 主表
CREATE TABLE IF NOT EXISTS person_idcard_image (
  image_id              BIGINT NOT NULL AUTO_INCREMENT,
  person_id             BIGINT NULL,
  draft_uuid            VARCHAR(36) NOT NULL,
  user_id               BIGINT NOT NULL,
  original_filename     VARCHAR(255) NOT NULL,
  stored_filename       VARCHAR(255) NOT NULL,
  storage_path          VARCHAR(512) NOT NULL,
  content_type          VARCHAR(100) NOT NULL,
  file_size             BIGINT NOT NULL,
  file_sha256           CHAR(64) NOT NULL,
  ocr_status            VARCHAR(20) NOT NULL DEFAULT 'SKIPPED',
  ocr_provider          VARCHAR(20) NULL,
  ocr_engine_version    VARCHAR(40) NULL,
  ocr_elapsed_ms        INT NULL,
  ocr_confidence        DECIMAL(5,4) NULL,
  ocr_idcard_full       VARCHAR(18) NULL,
  ocr_idcard_masked     VARCHAR(20) NULL,
  ocr_name              VARCHAR(50) NULL,
  ocr_birth_date        DATE NULL,
  ocr_gender            VARCHAR(1) NULL,
  ocr_ethnicity         VARCHAR(20) NULL,
  ocr_address           VARCHAR(255) NULL,
  ocr_error             VARCHAR(255) NULL,
  ocr_raw_json          TEXT NULL,
  created_at            DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (image_id),
  UNIQUE KEY uk_person_idcard_image_sha (file_sha256),
  KEY idx_person_idcard_image_person (person_id),
  KEY idx_person_idcard_image_draft (draft_uuid),
  KEY idx_person_idcard_image_user_date (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;