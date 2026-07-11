-- =====================================================
-- 一致性校验 1：一人一条当前户籍登记
-- 期望：active_duplicate_count = 0（uk_registration_person 已生效）
-- =====================================================
USE population_miniterm;

SELECT
    COUNT(*) AS active_duplicate_count,
    CASE WHEN COUNT(*) = 0 THEN 'PASS: 一人一条当前登记约束生效'
         ELSE CONCAT('FAIL: 发现 ', COUNT(*), ' 人有多条当前登记') END AS check_result
FROM (
    SELECT person_id, COUNT(*) AS cnt
    FROM residence_registration
    GROUP BY person_id
    HAVING cnt > 1
) t;

-- 详细信息（若失败）
SELECT person_id, COUNT(*) AS cnt
FROM residence_registration
GROUP BY person_id
HAVING cnt > 1;