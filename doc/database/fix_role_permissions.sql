-- ============================================================
-- 修复角色权限分配 - 为所有角色重新分配应有的权限
-- ============================================================

-- ==================== Phase 01: 基础权限 ====================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code = 'QUERY_VIEWER' AND p.permission_code IN ('population:view', 'household:view', 'migration:view', 'statistics:view', 'log:view'))
   OR (r.role_code = 'POPULATION_MANAGER' AND p.permission_code IN ('population:view', 'population:edit', 'household:view', 'migration:view', 'migration:edit', 'statistics:view'))
   OR (r.role_code = 'HOUSEHOLD_MANAGER' AND p.permission_code IN ('population:view', 'household:view', 'household:edit', 'migration:view', 'migration:edit', 'statistics:view'))
   OR (r.role_code = 'APPROVER' AND p.permission_code IN ('population:view', 'household:view', 'migration:view', 'approval:view', 'approval:handle', 'statistics:view', 'data:export'));

-- ==================== Phase 02: 业务申请和审批权限 ====================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code = 'QUERY_VIEWER' AND p.permission_code IN ('application:view'))
   OR (r.role_code = 'POPULATION_MANAGER' AND p.permission_code IN ('application:create', 'application:edit', 'application:view', 'application:submit', 'approval:view'))
   OR (r.role_code = 'HOUSEHOLD_MANAGER' AND p.permission_code IN ('application:create', 'application:edit', 'application:view', 'application:submit', 'approval:view'))
   OR (r.role_code = 'APPROVER' AND p.permission_code IN ('application:view', 'approval:handle', 'approval:view'));

-- ==================== Phase 04: 注销管理权限 ====================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code = 'POPULATION_MANAGER' AND p.permission_code IN ('cancellation:view', 'cancellation:person:create', 'cancellation:household:create', 'cancellation:execute'))
   OR (r.role_code = 'HOUSEHOLD_MANAGER' AND p.permission_code IN ('cancellation:view', 'cancellation:person:create', 'cancellation:household:create', 'cancellation:execute', 'cancellation:archive:view'))
   OR (r.role_code = 'APPROVER' AND p.permission_code IN ('cancellation:view', 'cancellation:execute'));

-- ==================== Phase 05: 流动人口和居住证权限 ====================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code = 'QUERY_VIEWER' AND p.permission_code IN ('floating:view', 'residence-permit:view', 'residence-permit:log:view', 'residence-permit:expiry:view'))
   OR (r.role_code = 'POPULATION_MANAGER' AND p.permission_code IN ('floating:create', 'floating:edit', 'floating:view', 'residence-permit:apply', 'residence-permit:view', 'residence-permit:log:view', 'residence-permit:expiry:view'))
   OR (r.role_code = 'HOUSEHOLD_MANAGER' AND p.module_name IN ('FLOATING', 'RESIDENCE_PERMIT'))
   OR (r.role_code = 'APPROVER' AND p.permission_code IN ('floating:view', 'residence-permit:view', 'residence-permit:log:view', 'residence-permit:expiry:view'));

-- ==================== Phase 06: 导出和证书权限 ====================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE (r.role_code = 'QUERY_VIEWER' AND p.permission_code IN ('data:export:normal', 'data:export:log:view', 'certificate:view'))
   OR (r.role_code = 'POPULATION_MANAGER' AND p.permission_code IN ('data:export:normal', 'data:export:sensitive:apply', 'data:export:log:view', 'certificate:view'))
   OR (r.role_code = 'HOUSEHOLD_MANAGER' AND p.permission_code IN ('sensitive-data:view-full', 'data:export:normal', 'data:export:sensitive:apply', 'data:export:sensitive:execute', 'data:export:sensitive:download', 'data:export:log:view', 'certificate:view'))
   OR (r.role_code = 'APPROVER' AND p.permission_code IN ('data:export:log:view', 'data:export:sensitive:download', 'certificate:view'));

-- ==================== Phase 09: 行政区划和数据字典权限 ====================
INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE p.permission_code = 'region:view' OR (r.role_code = 'SYSTEM_ADMIN' AND p.permission_code = 'region:manage');

INSERT IGNORE INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id FROM sys_role r CROSS JOIN sys_permission p
WHERE p.permission_code = 'dictionary:view' OR (r.role_code = 'SYSTEM_ADMIN' AND p.permission_code = 'dictionary:manage');

-- ==================== 验证：查询各角色权限数量 ====================
SELECT r.role_code, r.role_name, COUNT(rp.permission_id) AS permission_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.role_id = rp.role_id
GROUP BY r.role_id, r.role_code, r.role_name
ORDER BY r.role_id;
