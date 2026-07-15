-- Phase 12 application return-after-approve workflow (MySQL 8)
-- Adds the application:return permission and grants it to the executor role.
USE population_miniterm;

INSERT INTO sys_permission(permission_code, permission_name, module_name, permission_type, status)
VALUES ('application:return', '退回已批准申请', 'APPLICATION', 'API', 'ENABLED')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name),
                        module_name = VALUES(module_name),
                        permission_type = VALUES(permission_type),
                        status = 'ENABLED';

-- HOUSEHOLD_MANAGER 既是日常业务执行人，也是审批通过后的执行/复核人员，
-- 因此授予 application:return。其他角色保持原职责边界：
--   - APPROVAL 角色只能批/不批，不能退回（退回会改写审批结论）。
--   - POPULATION_MANAGER / QUERY_VIEWER 不参与业务落地。
--   - SYSTEM_ADMIN 默认全权。
INSERT IGNORE INTO sys_role_permission(role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code IN ('HOUSEHOLD_MANAGER', 'SYSTEM_ADMIN')
  AND p.permission_code = 'application:return';
