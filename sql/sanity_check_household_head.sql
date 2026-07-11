-- =====================================================
-- 一致性校验 3：家庭户户主一致性
-- 期望：
--   ACTIVE 户必有 CURRENT 状态的 HEAD 成员；
--   household.head_person_id 必须与该 HEAD 成员行 person_id 一致；
--   同一户只能有 1 个 CURRENT HEAD（由 household_member + uk_member_current_dedup 间接保证）
-- =====================================================
USE population_miniterm;

-- 3.1 ACTIVE 户必须有 CURRENT HEAD
SELECT
    h.household_id,
    h.household_no,
    h.head_person_id,
    CASE WHEN EXISTS (
        SELECT 1 FROM household_member hm
        WHERE hm.household_id = h.household_id
          AND hm.relationship_code = 'HEAD'
          AND hm.member_status = 'CURRENT'
    ) THEN 'PASS' ELSE 'FAIL: 缺 HEAD 成员' END AS head_member_check
FROM household h
WHERE h.status = 'ACTIVE'
ORDER BY h.household_id;

-- 3.2 head_person_id 与 HEAD 成员行 person_id 一致
SELECT
    h.household_id,
    h.household_no,
    h.head_person_id AS household_head,
    hm.person_id AS member_head,
    CASE WHEN h.head_person_id IS NOT NULL
              AND h.head_person_id = hm.person_id THEN 'PASS'
         ELSE 'FAIL: head_person_id 与 HEAD 成员不一致' END AS consistency_check
FROM household h
LEFT JOIN household_member hm
       ON hm.household_id = h.household_id
      AND hm.relationship_code = 'HEAD'
      AND hm.member_status = 'CURRENT'
WHERE h.status = 'ACTIVE';

-- 3.3 ACTIVE 户不应有 CURRENT 状态重复（一人一户一条 CURRENT）
SELECT
    household_id,
    person_id,
    COUNT(*) AS cnt
FROM household_member
WHERE member_status = 'CURRENT'
GROUP BY household_id, person_id
HAVING cnt > 1;