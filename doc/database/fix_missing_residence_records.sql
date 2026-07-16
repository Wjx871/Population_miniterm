-- 修复：为缺少 residence 记录的人员自动创建 residence 记录
-- household_id 不能为 null，需要使用有效的 household_id

-- 1. 先检查是否有可用的 household
SELECT '可用的 household 列表:' AS info;
SELECT household_id, household_no, head_person_id FROM household WHERE status = 'ACTIVE' LIMIT 5;

-- 2. 为缺少 residence 记录的人员创建 residence 记录
-- 使用第一个活跃的 household 作为占位符
INSERT INTO residence (person_id, household_id, registered_address, region_code, register_type_code, register_date, start_date, status, created_by, version)
SELECT
    p.person_id,
    (
        SELECT h.household_id
        FROM household h
        WHERE h.status = 'ACTIVE'
        LIMIT 1
    ) AS household_id,
    COALESCE(p.current_address, '未知地址') AS registered_address,
    '110000' AS region_code,
    'REGISTERED' AS register_type_code,
    CURDATE() AS register_date,
    CURDATE() AS start_date,
    'ACTIVE' AS status,
    1 AS created_by,
    0 AS version
FROM person p
WHERE NOT EXISTS (
    SELECT 1 FROM residence r WHERE r.person_id = p.person_id
);

-- 3. 验证结果
SELECT
    (SELECT COUNT(*) FROM person) AS total_persons,
    (SELECT COUNT(*) FROM residence) AS total_residences,
    (SELECT COUNT(*) FROM person p WHERE NOT EXISTS (SELECT 1 FROM residence r WHERE r.person_id = p.person_id)) AS missing_residence_count;
