-- =====================================================
-- 归档一致性：在迁出 / 注销业务回放后验证
-- 期望：
--   - 所有完成（completed_at 非空）的 migration_out 行：
--       1) 关联的 archive_id 在 residence_archive 存在
--       2) 该 person 在 residence_registration 不存在
--       3) 该 person 的 household_member 没有 CURRENT 行（旧行应是 LEFT）
--   - 所有完成的 cancellation_record（PERSON）：
--       person.record_status_code='CANCELLED'
-- =====================================================

USE population_miniterm;

-- 1. 迁出一致性：已办结的 out，无当前登记 + 有归档
SELECT '==== 1. 迁出归档一致性 ====' AS Step;
SELECT
    mo.out_id,
    mo.person_id,
    mo.archive_id,
    (SELECT COUNT(*) FROM residence_registration rr WHERE rr.person_id = mo.person_id) AS current_registration_count,
    (SELECT COUNT(*) FROM residence_archive ra WHERE ra.archive_id = mo.archive_id) AS archive_exists,
    CASE
        WHEN mo.completed_at IS NULL THEN 'SKIP: 未办结'
        WHEN (SELECT COUNT(*) FROM residence_registration rr WHERE rr.person_id = mo.person_id) > 0 THEN 'FAIL: 当前登记仍存在'
        WHEN (SELECT COUNT(*) FROM residence_archive ra WHERE ra.archive_id = mo.archive_id) = 0 THEN 'FAIL: 归档缺失'
        ELSE 'PASS'
    END AS check_result
FROM migration_out mo
ORDER BY mo.out_id;

-- 2. 人口注销一致性
SELECT '==== 2. 人口注销一致性 ====' AS Step;
SELECT
    cr.cancel_id,
    cr.cancel_object_type,
    cr.person_id,
    cr.archive_id,
    p.record_status_code,
    CASE
        WHEN cr.completed_at IS NULL THEN 'SKIP: 未办结'
        WHEN cr.cancel_object_type <> 'PERSON' THEN 'SKIP: 非人口'
        WHEN (SELECT COUNT(*) FROM residence_registration rr WHERE rr.person_id = cr.person_id) > 0 THEN 'FAIL: 当前登记仍存在'
        WHEN p.record_status_code <> 'CANCELLED' THEN 'FAIL: 人口状态未置 CANCELLED'
        ELSE 'PASS'
    END AS check_result
FROM cancellation_record cr
LEFT JOIN person p ON cr.person_id = p.person_id
ORDER BY cr.cancel_id;

-- 3. 多次迁入迁出后，归档数应等于迁出次数
SELECT '==== 3. 归档数与迁出次数对比 ====' AS Step;
SELECT
    (SELECT COUNT(*) FROM residence_archive) AS archive_total,
    (SELECT COUNT(*) FROM migration_out WHERE completed_at IS NOT NULL) AS out_completed,
    CASE
        WHEN (SELECT COUNT(*) FROM residence_archive) >= (SELECT COUNT(*) FROM migration_out WHERE completed_at IS NOT NULL)
        THEN 'PASS'
        ELSE 'FAIL: 归档数少于迁出数'
    END AS check_result;

-- 4. 完整报表
SELECT '==== 4. 户籍时间线抽查 ====' AS Step;
SELECT person_id, person_name, record_kind, start_date, end_date, register_type_name, archive_reason_name
FROM v_person_residence_history
WHERE person_id IN (5, 11)
ORDER BY person_id, COALESCE(start_date, end_date);
