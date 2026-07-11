-- =====================================================
-- 一致性断言 5：家庭户户主一致性
-- 期望：ACTIVE 户必有 CURRENT HEAD 行；head_person_id 与 HEAD 行 person_id 一致
-- =====================================================

USE population_miniterm;

-- ACTIVE 户必须有 CURRENT HEAD 成员行
SELECT
    h.household_id,
    h.household_no,
    CASE WHEN EXISTS (
        SELECT 1 FROM household_member hm
        WHERE hm.household_id = h.household_id
          AND hm.relationship_code = 'HEAD'
          AND hm.member_status = 'CURRENT'
    ) THEN 'PASS' ELSE 'FAIL: 缺 HEAD 成员' END AS head_member_check
FROM household h
WHERE h.status = 'ACTIVE'
ORDER BY h.household_id;

-- household.head_person_id 与 HEAD 成员行一致性
SELECT
    h.household_id,
    h.household_no,
    h.head_person_id AS household_head_id,
    hm.person_id AS member_head_id,
    CASE WHEN h.head_person_id IS NULL THEN 'SKIP: 未设户主'
         WHEN h.head_person_id = hm.person_id THEN 'PASS'
         ELSE 'FAIL: head_person_id 与 HEAD 成员不一致' END AS consistency_check
FROM household h
LEFT JOIN household_member hm
       ON hm.household_id = h.household_id
      AND hm.relationship_code = 'HEAD'
      AND hm.member_status = 'CURRENT'
WHERE h.status = 'ACTIVE';

-- 一户至多一条 CURRENT HEAD 关系（HEAD 行数量 = 1）
SELECT household_id, COUNT(*) AS head_row_count, 'FAIL if > 1'
FROM household_member
WHERE relationship_code = 'HEAD' AND member_status = 'CURRENT'
GROUP BY household_id
HAVING COUNT(*) > 1;
