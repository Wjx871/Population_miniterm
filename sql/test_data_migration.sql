-- =====================================================
-- 业务测试 1：同市跨区联办（批次号 BATCH2026001）回放
-- 期望：
--   - 钱七（person_id=5）从 110101 跨到 110102，旧登记归档到 residence_archive
--   - migration_in / migration_out 同时办结；archive_id 互不冲突
--   - household_member 中钱七在 H110101001 LEFT、H110102002 CURRENT
-- =====================================================

USE population_miniterm;

-- 0. 期望前置：钱七 当前在 H110101001
SELECT '==== 0. 前置：钱七在 H110101001 ====' AS Step;
SELECT person_id, household_id FROM residence_registration WHERE person_id = 5;
SELECT household_id, member_status FROM household_member WHERE person_id = 5;

-- 1. 模拟迁出办结（业务上由 /api/migration-out/{id}/complete 触发）
-- 这里用 SQL 直接模拟：插入 archive 快照 + 删除当前 registration + 置 LEFT
-- 仅供回放演示；真实系统不应绕过 service
INSERT INTO residence_archive (
    original_registration_id, person_id, household_id,
    archive_type_code, archive_date, archive_reason_code,
    person_name_snapshot, identity_type_snapshot, identity_no_snapshot,
    household_no_snapshot, registered_address_snapshot, region_code_snapshot,
    register_type_snapshot, register_date_snapshot, start_date_snapshot, end_date_snapshot,
    original_status, archive_operator_id, source_application_id
)
SELECT
    rr.registration_id, rr.person_id, rr.household_id,
    'MIGRATION_OUT', '2026-01-15', 'HOUSING',
    p.name, rr.identity_type, rr.identity_no,
    h.household_no, rr.registered_address, rr.region_code,
    rr.register_type_code, rr.register_date, rr.start_date, '2026-01-15',
    'ACTIVE', 1, 90003
FROM residence_registration rr
JOIN person p ON rr.person_id = p.person_id
JOIN household h ON rr.household_id = h.household_id
WHERE rr.person_id = 5;

DELETE FROM residence_registration WHERE person_id = 5;
UPDATE household_member SET member_status = 'LEFT', leave_date = '2026-01-15'
WHERE person_id = 5 AND member_status = 'CURRENT';

-- 2. 模拟迁入办结（按 COMPLETE 流程归档 -> 写新登记 -> 写成员）
INSERT INTO residence_registration (person_id, household_id, register_type_code, register_date, registered_address, region_code, start_date, source_application_id)
SELECT 5, 5, 'MIGRATION_IN', '2026-01-15',
       (SELECT registered_address FROM household WHERE household_id = 5),
       (SELECT region_code FROM household WHERE household_id = 5),
       '2026-01-15', 90001;
INSERT INTO household_member (household_id, person_id, relationship_code, join_date, member_status, source_application_id)
VALUES (5, 5, 'OTHER', '2026-01-15', 'CURRENT', 90001);

UPDATE migration_in SET new_registration_id = LAST_INSERT_ID(),
    operator_id = 1, completed_at = NOW() WHERE application_id = 90001;
UPDATE migration_out SET archive_id = (
        SELECT archive_id FROM residence_archive
        WHERE person_id = 5 AND archive_type_code = 'MIGRATION_OUT' LIMIT 1
    ), operator_id = 1, completed_at = NOW() WHERE application_id = 90003;

-- 3. 校验：当前应只剩新登记
SELECT '==== 3. 校验：钱七当前登记应在 H110102002 ====' AS Step;
SELECT rr.person_id, rr.household_id, h.household_no FROM residence_registration rr
LEFT JOIN household h ON rr.household_id = h.household_id WHERE rr.person_id = 5;
SELECT household_id, member_status, leave_date FROM household_member WHERE person_id = 5;

-- 4. 批次号视图
SELECT '==== 4. 批次号视图 ====' AS Step;
SELECT * FROM v_migration_batch_detail WHERE transfer_batch_no = 'BATCH2026001';

-- 5. 人口户籍时间线
SELECT '==== 5. 钱七户籍时间线 ====' AS Step;
SELECT record_kind, start_date, end_date, register_type_name, archive_reason_name, household_no
FROM v_person_residence_history WHERE person_id = 5 ORDER BY COALESCE(start_date, end_date);
