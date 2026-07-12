-- =====================================================
-- Login Log Table v4.0.1
-- 用于记录每一次登录尝试（成功 + 失败），便于审计、追溯异常登录。
-- 本表为增量 DDL；新建库请先跑 schema.sql 再跑本文件（已存在的库直接执行即可）。
-- =====================================================

USE population_miniterm;

DROP TABLE IF EXISTS login_log;
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
-- 字典项：LOGIN_STATUS / LOGIN_FAILURE_REASON（与 OperationLog 共用 OPERATION_TYPE/LOGIN）
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('LOGIN_STATUS', 'SUCCESS', '登录成功', 1, 'ENABLED', '登录成功'),
('LOGIN_STATUS', 'FAILED',  '登录失败', 2, 'ENABLED', '用户名或密码错误、账号停用等'),
('LOGIN_STATUS', 'LOCKED',  '账号已停用', 3, 'ENABLED', '账号被管理员停用')
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- 字典项：导出全部字典后做一次统计确认
SELECT dict_type, COUNT(*) AS cnt
FROM data_dictionary
WHERE dict_type IN ('LOGIN_STATUS')
GROUP BY dict_type
ORDER BY dict_type;

SELECT 'login_log table + LOGIN_STATUS dictionary installed.' AS Result;