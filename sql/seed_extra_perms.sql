-- 增量：把 sql/data.sql 的新权限码和角色分配跑进已存在的库
-- 不动 schema；幂等（ON DUPLICATE KEY UPDATE）。
-- 由"补 Controller 护栏"任务创建。生产环境建议直接重跑完整 data.sql。

-- 1) 新权限码（13 条；v2 增加综合查询 query:comprehensive，对应 §2.2.9）
INSERT INTO sys_permission (permission_code, permission_name, module_name, action_code, sensitivity_level, approval_required) VALUES
('application:query', '业务申请查询', 'APPLICATION', 'QUERY', 2, 0),
('application:manage', '业务申请办理', 'APPLICATION', 'CREATE', 3, 1),
('registration:query', '户籍登记查询', 'REGISTRATION', 'QUERY', 2, 0),
('registration:manage', '户籍登记办理', 'REGISTRATION', 'CREATE', 3, 1),
('archive:query', '户籍历史查询', 'ARCHIVE', 'QUERY', 1, 0),
('archive:manage', '户籍历史归档', 'ARCHIVE', 'CREATE', 2, 1),
('region:query', '行政区划查询', 'REGION', 'QUERY', 1, 0),
('region:manage', '行政区划管理', 'REGION', 'CONFIG', 3, 0),
('material:query', '申请材料查询', 'MATERIAL', 'QUERY', 2, 0),
('material:manage', '申请材料维护', 'MATERIAL', 'CREATE', 2, 1),
('material:verify', '申请材料核验', 'MATERIAL', 'APPROVE', 3, 0),
('log:query', '审计日志查询', 'LOG', 'QUERY', 2, 0),
('query:comprehensive', '综合查询', 'QUERY', 'QUERY', 1, 0)
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name);

-- 2) L1: 基础数据（region/dictionary）全员可见 + 综合查询（只读）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'L1_QUERY'
  AND p.permission_code IN ('region:query', 'dictionary:query', 'query:comprehensive')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 3) L2: 经办级权限补全
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'L2_HANDLE'
  AND p.permission_code IN (
    'application:query', 'application:manage',
    'registration:query', 'registration:manage',
    'archive:query',
    'material:query', 'material:manage',
    'log:query',
    'region:manage'
  )
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 4) L3: 拿到所有新加的 manage 类权限（补 NOT LIKE '%manage%' 漏掉的）
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r CROSS JOIN sys_permission p
WHERE r.role_code = 'L3_APPROVE_ADMIN'
  AND p.permission_code IN (
    'application:manage', 'registration:manage', 'archive:manage',
    'region:manage', 'material:manage', 'material:verify'
  )
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 5) ADMIN: 全选 (data.sql 原写法是 CROSS JOIN 全选，对已存在的 OK，但新增码要补)
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
    'log:query',
    'query:comprehensive'
  )
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- 验证
SELECT r.role_code, COUNT(*) AS perm_count
FROM sys_role_permission rp
JOIN sys_role r ON rp.role_id = r.role_id
GROUP BY r.role_code
ORDER BY r.role_code;
