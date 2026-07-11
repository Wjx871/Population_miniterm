INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN'
  AND p.permission_code IN (
    'application:query','application:manage',
    'registration:query','registration:manage',
    'archive:query','archive:manage',
    'region:query','region:manage',
    'material:query','material:manage','material:verify',
    'log:query'
  )
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
SELECT 'ADMIN_DONE' AS s, COUNT(*) AS cnt
FROM sys_role_permission rp JOIN sys_role r ON rp.role_id = r.role_id
WHERE r.role_code = 'ADMIN';
