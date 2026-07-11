-- =====================================================
-- 一致性断言 4：一人一条当前户籍登记
-- 期望：重复数 = 0（uk_registration_person 生效）
-- 通过：SELECT 'PASS' WHERE 0 = SELECT COUNT(*) FROM (SELECT person_id FROM residence_registration GROUP BY person_id HAVING COUNT(*) > 1) t;
-- =====================================================

USE population_miniterm;

SELECT
    COUNT(*) AS duplicate_person_count,
    CASE WHEN COUNT(*) = 0 THEN 'PASS: 一人一条当前登记约束生效'
         ELSE CONCAT('FAIL: 发现 ', COUNT(*), ' 人有多条当前登记') END AS check_result
FROM (
    SELECT person_id FROM residence_registration
    GROUP BY person_id HAVING COUNT(*) > 1
) t;

-- 详情（若 FAIL 时）
SELECT person_id, COUNT(*) AS cnt
FROM residence_registration
GROUP BY person_id
HAVING cnt > 1;
