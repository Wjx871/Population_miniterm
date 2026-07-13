-- ============================================================
-- Sprint 5 P0: 统计看板 / 数据大屏 API 权限与角色分配
-- ============================================================

-- 1) 新增统计相关权限码
INSERT INTO sys_permission (permission_code, permission_name, module_name, action_code, sensitivity_level, approval_required) VALUES
('stats:query', '统计看板查询', 'STATS', 'QUERY', 1, 0)
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name);

-- 2) L1_QUERY：拿到 stats:query（查询统计人员角色的最小权限集合）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'L1_QUERY'
  AND p.permission_code IN ('stats:query')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 3) ADMIN 角色也补 stats:query
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN ('stats:query')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
