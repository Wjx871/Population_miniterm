-- ========================================
-- 撤销脚本：恢复 residence 数据到修复前的状态
-- ========================================

-- 步骤1: 先查看当前有哪些 residence 记录是刚才新增的
--       这些记录的 household_id 来自最初插入时选择的 household

-- 查看最近插入的 residence 记录（基于 household_id 判断）
SELECT '最近插入的 residence 记录（可能是需要删除的）:' AS info;
SELECT r.residence_id, r.person_id, p.name, r.household_id, r.region_code, r.registered_address, r.created_at
FROM residence r
JOIN person p ON p.person_id = r.person_id
WHERE r.created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR)
ORDER BY r.created_at DESC;

-- 步骤2: 确认要删除的记录后，执行删除
-- 注意：请先确认上面查询显示的记录是正确要删除的

-- 备份要删除的记录（可选，用于恢复）
-- CREATE TEMPORARY TABLE IF NOT EXISTS backup_residence_rollback AS
-- SELECT * FROM residence WHERE ...;

-- 步骤3: 执行删除
-- 方案A: 如果能确定具体的 person_id，直接按 person_id 删除
-- DELETE FROM residence WHERE person_id IN (xxx, yyy, zzz);

-- 方案B: 如果不确定 person_id，可以按 household_id 和 created_at 删除
-- DELETE FROM residence
-- WHERE household_id = 700001  -- 替换为实际的 household_id
-- AND person_id NOT IN (SELECT person_id FROM household_member WHERE household_id = 700001)
-- AND created_at >= DATE_SUB(NOW(), INTERVAL 1 HOUR);

SELECT '请根据实际情况执行删除操作' AS instruction;
