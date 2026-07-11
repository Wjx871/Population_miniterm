-- =====================================================
-- 业务测试 2：人口注销（郑十一，死亡）+ 家庭户销户（H110101999 已为空户）
-- 期望：
--   - 郑十一 当前登记被删除，archive_type_code=PERSON_CANCEL 一条生成
--   - household_member 当前行变 CANCELLED，person.record_status_code='CANCELLED'
--   - H110101999 置 status='CANCELLED'
-- =====================================================

USE population_miniterm;

-- 0. 前置校验
SELECT '==== 0. 前置 ====' AS Step;
SELECT person_id, name, record_status_code FROM person WHERE person_id IN (11, 12);
SELECT person_id, household_id FROM residence_registration WHERE person_id IN (11, 12);
SELECT household_id, household_no, status FROM household WHERE household_id = 6;
SELECT COUNT(*) AS current_member_count FROM household_member
WHERE household_id = 6 AND member_status = 'CURRENT';

-- 1. 人口注销：郑十一（id=11）→ 归档 → 删登记 → 状态更新
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
    'PERSON_CANCEL', '2026-03-01', 'DEATH',
    p.name, rr.identity_type, rr.identity_no,
    h.household_no, rr.registered_address, rr.region_code,
    rr.register_type_code, rr.register_date, rr.start_date, '2026-03-01',
    'ACTIVE', 1, 90010
FROM residence_registration rr
JOIN person p ON rr.person_id = p.person_id
JOIN household h ON rr.household_id = h.household_id
WHERE rr.person_id = 11;

DELETE FROM residence_registration WHERE person_id = 11;
UPDATE household_member SET member_status = 'CANCELLED', leave_date = '2026-03-01'
WHERE person_id = 11 AND member_status = 'CURRENT';
UPDATE person SET record_status_code = 'CANCELLED' WHERE person_id = 11;
UPDATE cancellation_record SET archive_id = (
        SELECT archive_id FROM residence_archive
        WHERE person_id = 11 AND archive_type_code = 'PERSON_CANCEL' LIMIT 1
    ), operator_id = 1, completed_at = NOW() WHERE cancel_id = (
    SELECT cancel_id FROM (SELECT cancel_id FROM cancellation_record WHERE application_id = 90010) t
);

-- 2. 家庭户销户：H110101999（id=6，已空户）
UPDATE household SET status = 'CANCELLED' WHERE household_id = 6;
UPDATE cancellation_record SET operator_id = 1, completed_at = NOW() WHERE application_id = 90011;

-- 3. 校验
SELECT '==== 3. 校验 ====' AS Step;
SELECT person_id, name, record_status_code FROM person WHERE person_id IN (11, 12);
SELECT cancel_object_type, cancel_reason_code, archive_id, completed_at FROM cancellation_record
WHERE application_id IN (90010, 90011);
SELECT household_id, household_no, status FROM household WHERE household_id = 6;
SELECT '==== v_cancellation_detail ====' AS Step;
SELECT * FROM v_cancellation_detail WHERE application_id IN (90010, 90011);
