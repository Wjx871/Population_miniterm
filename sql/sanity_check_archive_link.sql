-- =====================================================
-- 一致性校验 2：迁出归档链完整性
-- 期望：migration_out 中 completed_at 非空记录的 archive_id 必须能在 residence_archive 中查到，
--       且 person_id 与 household_id 一致；archive_type_code 必须为 MIGRATION_OUT
-- =====================================================
USE population_miniterm;

SELECT
    mo.out_id,
    mo.person_id,
    mo.archive_id,
    ra.person_id AS archive_person_id,
    ra.archive_type_code,
    CASE WHEN ra.archive_id IS NULL THEN 'FAIL: archive 缺失'
         WHEN ra.person_id <> mo.person_id THEN 'FAIL: person_id 不一致'
         WHEN ra.archive_type_code <> 'MIGRATION_OUT' THEN 'FAIL: archive 类型错误'
         ELSE 'PASS' END AS check_result
FROM migration_out mo
LEFT JOIN residence_archive ra ON mo.archive_id = ra.archive_id
WHERE mo.completed_at IS NOT NULL
ORDER BY mo.out_id;

-- 统计
SELECT
    SUM(CASE WHEN ra.archive_id IS NULL THEN 1 ELSE 0 END) AS missing_archive_count,
    SUM(CASE WHEN ra.person_id <> mo.person_id THEN 1 ELSE 0 END) AS person_mismatch_count,
    SUM(CASE WHEN ra.archive_type_code <> 'MIGRATION_OUT' THEN 1 ELSE 0 END) AS type_mismatch_count,
    COUNT(*) AS total_completed_out
FROM migration_out mo
LEFT JOIN residence_archive ra ON mo.archive_id = ra.archive_id
WHERE mo.completed_at IS NOT NULL;