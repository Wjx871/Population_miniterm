-- =====================================================
-- 完整性断言：期望以下语句全部 FAIL（即各自抛约束错误）
-- 用于手测 CHECK / UNIQUE / FK 约束生效。
-- =====================================================

USE population_miniterm;

SELECT '==== TEST 1: 重复身份证号应冲突 ====' AS Test;
-- 期望：ERROR 1062 Duplicate entry
INSERT INTO person (name, gender_code, identity_type_code, identity_no, record_status_code)
VALUES ('张三副本', 'MALE', 'ID_CARD', '110101199001011234', 'ACTIVE');

SELECT '==== TEST 2: 同一 person 重复当前登记应冲突（uk_registration_person）====' AS Test;
-- 期望：ERROR 1062 Duplicate entry '5' for key 'uk_registration_person'
INSERT INTO residence_registration (person_id, household_id, register_type_code, register_date, registered_address, region_code, start_date)
SELECT 5, 1, 'MIGRATION_IN', CURDATE(), '北京市东城区', '110101', CURDATE()
WHERE EXISTS (SELECT 1 FROM residence_registration WHERE person_id = 5);

SELECT '==== TEST 3: cancellation_record 同时填 person 与 household 应被 chk_cancel_object 拒绝 ====' AS Test;
-- 期望：ERROR 3819 Check constraint 'chk_cancel_object' violated
INSERT INTO cancellation_record (
    cancellation_no, application_id, cancel_object_type,
    person_id, household_id, cancel_reason_code, cancel_date
) VALUES ('CX_DUP_TEST', 99001, 'PERSON',
    1, 1, 'DEATH', CURDATE());

SELECT '==== TEST 4: leave_date < join_date 应被 chk_leave_date 拒绝 ====' AS Test;
-- 期望：ERROR 3819
INSERT INTO household_member (household_id, person_id, relationship_code, join_date, leave_date, member_status)
VALUES (1, 9, 'OTHER', '2026-04-01', '2026-01-01', 'LEFT');

SELECT '==== TEST 5: 身份证号非法（仅 17 位）应被 chk_person_identity_no 拒绝 ====' AS Test;
-- 期望：ERROR 3819
INSERT INTO person (name, gender_code, identity_type_code, identity_no, record_status_code)
VALUES ('测试人', 'MALE', 'ID_CARD', '11010119900101123', 'ACTIVE');

SELECT '==== TEST 6: 手机号非法应被 chk_person_phone 拒绝 ====' AS Test;
-- 期望：ERROR 3819
INSERT INTO person (name, gender_code, identity_type_code, identity_no, phone, record_status_code)
VALUES ('测试人2', 'MALE', 'ID_CARD', '110101197001011998', '12345', 'ACTIVE');

SELECT '==== TEST 7: 删除被引用的 admin_region 应被 FK 拒绝 ====' AS Test;
-- 期望：ERROR 1451 Cannot delete or update a parent row
DELETE FROM admin_region WHERE region_code = '110101';

SELECT '==== TEST 8: 同一户重复 CURRENT 成员（一人一户一条）应被 uk_member_current_dedup 拒绝 ====' AS Test;
-- 期望：ERROR 1062 Duplicate entry for key 'uk_member_current_dedup'
INSERT INTO household_member (household_id, person_id, relationship_code, join_date, member_status)
SELECT 1, 1, 'OTHER', CURDATE(), 'CURRENT'
WHERE EXISTS (SELECT 1 FROM household_member WHERE household_id = 1 AND person_id = 1 AND member_status = 'CURRENT');

SELECT '==== TESTS COMPLETE (期望上面每个测试都报错) ====' AS Result;
