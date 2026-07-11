-- =====================================================
-- Base Data Initialization Script v4.0
-- Based on: 数据库设计v4.0_Cursor详细说明.md
-- =====================================================

USE population_miniterm;

-- Disable foreign key checks for initial data load
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. Administrative Regions (admin_region)
-- Sample data for testing - should be replaced with actual data
-- =====================================================
INSERT INTO admin_region (region_code, region_name, parent_code, region_level_code, city_code, enabled_flag, sort_no) VALUES
-- Province Level
('110000', '北京市', NULL, 'PROVINCE', '110000', 1, 1),
('310000', '上海市', NULL, 'PROVINCE', '310000', 1, 2),
-- City Level
('110100', '北京市直辖', '110000', 'CITY', '110000', 1, 1),
('310100', '上海市直辖', '310000', 'CITY', '310000', 1, 2),
-- District Level
('110101', '东城区', '110100', 'DISTRICT', '110000', 1, 1),
('110102', '西城区', '110100', 'DISTRICT', '110000', 1, 2),
('110105', '朝阳区', '110100', 'DISTRICT', '110000', 1, 3),
('110106', '丰台区', '110100', 'DISTRICT', '110000', 1, 4),
('310101', '黄浦区', '310100', 'DISTRICT', '310000', 1, 1),
('310104', '徐汇区', '310100', 'DISTRICT', '310000', 1, 2),
-- Street Level
('110101001', '东华门街道', '110101', 'STREET', '110000', 1, 1),
('110101002', '景山街道', '110101', 'STREET', '110000', 1, 2),
('110102001', '西长安街街道', '110102', 'STREET', '110000', 1, 1),
('110102002', '金融街街道', '110102', 'STREET', '110000', 1, 2),
-- Community Level
('110101001001', '东华门社区', '110101001', 'COMMUNITY', '110000', 1, 1),
('110101001002', '南池子社区', '110101001', 'COMMUNITY', '110000', 1, 2),
('110102001001', '六铺炕社区', '110102001', 'COMMUNITY', '110000', 1, 1)
ON DUPLICATE KEY UPDATE region_name = VALUES(region_name);

-- =====================================================
-- 2. Departments (sys_department)
-- =====================================================
INSERT INTO sys_department (department_code, department_name, department_type_code, region_code, parent_id, status) VALUES
('D001', '东城区公安分局', 'PUBLIC_SECURITY', '110101', NULL, 'ENABLED'),
('D002', '东华门派出所', 'POLICE_STATION', '110101', 1, 'ENABLED'),
('D003', '西城区公安分局', 'PUBLIC_SECURITY', '110102', NULL, 'ENABLED'),
('D004', '金融街街道办', 'STREET', '110102', NULL, 'ENABLED'),
('D005', '东城区民政局', 'STREET', '110101', NULL, 'ENABLED')
ON DUPLICATE KEY UPDATE department_name = VALUES(department_name);

-- =====================================================
-- 3. Roles (sys_role)
-- Based on: 数据库设计v4.0_Cursor详细说明.md Section 6
-- =====================================================
INSERT INTO sys_role (role_code, role_name, permission_level, data_scope_code, description, status) VALUES
('L1_QUERY', '一级查询用户', 1, 'REGION', '一级查询用户，仅能查询，按数据范围过滤', 'ENABLED'),
('L2_HANDLE', '二级经办用户', 2, 'DEPARTMENT', '二级经办用户，可办理业务，部分需审批', 'ENABLED'),
('L3_APPROVE_ADMIN', '三级审批管理员', 3, 'ALL', '三级审批管理员，可审批重大业务和系统配置', 'ENABLED'),
('ADMIN', '系统管理员', 3, 'ALL', '系统管理员，拥有所有权限', 'ENABLED')
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

-- =====================================================
-- 4. Permissions (sys_permission)
-- Based on: 数据库设计v4.0_Cursor详细说明.md
-- =====================================================
INSERT INTO sys_permission (permission_code, permission_name, module_name, action_code, sensitivity_level, approval_required) VALUES
-- Person permissions
('person:query', '人口查询', 'PERSON', 'QUERY', 1, 0),
('person:create', '人口新增', 'PERSON', 'CREATE', 2, 1),
('person:update', '人口修改', 'PERSON', 'UPDATE', 2, 1),
('person:register', '人口登记', 'PERSON', 'CREATE', 2, 1),
-- Household permissions
('household:query', '户籍查询', 'HOUSEHOLD', 'QUERY', 1, 0),
('household:create', '户籍新增', 'HOUSEHOLD', 'CREATE', 2, 1),
('household:establish', '家庭户立户', 'HOUSEHOLD', 'CREATE', 2, 1),
('household:update', '户籍修改', 'HOUSEHOLD', 'UPDATE', 2, 1),
-- Migration permissions
('migration:query', '迁移查询', 'MIGRATION', 'QUERY', 1, 0),
('migration:in:create', '迁入办理', 'MIGRATION', 'CREATE', 3, 1),
('migration:out:create', '迁出办理', 'MIGRATION', 'CREATE', 3, 1),
-- Floating population permissions
('floating:query', '流动人口查询', 'FLOATING', 'QUERY', 1, 0),
('floating:register', '流动人口登记', 'FLOATING', 'CREATE', 2, 0),
-- Residence permit permissions
('permit:query', '居住凭证查询', 'PERMIT', 'QUERY', 1, 0),
('permit:apply', '居住凭证申领', 'PERMIT', 'CREATE', 2, 1),
-- Key population permissions
('key:query', '重点人口查询', 'KEY_POPULATION', 'QUERY', 3, 0),
('key:register', '重点人口登记', 'KEY_POPULATION', 'CREATE', 3, 1),
('key:release', '解除重点管理', 'KEY_POPULATION', 'UPDATE', 3, 1),
-- Certificate permissions
('certificate:query', '证件查询', 'CERTIFICATE', 'QUERY', 1, 0),
('certificate:manage', '证件管理', 'CERTIFICATE', 'CREATE', 2, 1),
-- Cancellation permissions
('cancellation:person', '人口注销', 'CANCELLATION', 'CREATE', 3, 1),
('cancellation:household', '家庭户销户', 'CANCELLATION', 'CREATE', 3, 1),
-- Approval permissions
('approval:query', '审批查询', 'APPROVAL', 'QUERY', 1, 0),
('approval:handle', '审批办理', 'APPROVAL', 'APPROVE', 3, 0),
-- System permissions
('user:query', '用户查询', 'USER', 'QUERY', 2, 0),
('user:manage', '用户管理', 'USER', 'CONFIG', 3, 0),
('role:query', '角色查询', 'ROLE', 'QUERY', 2, 0),
('role:manage', '角色管理', 'ROLE', 'CONFIG', 3, 0),
('department:query', '部门查询', 'DEPARTMENT', 'QUERY', 2, 0),
('department:manage', '部门管理', 'DEPARTMENT', 'CONFIG', 3, 0),
('dictionary:query', '字典查询', 'DICTIONARY', 'QUERY', 2, 0),
('dictionary:manage', '字典管理', 'DICTIONARY', 'CONFIG', 3, 0),
-- Export permissions
('export:normal', '普通导出', 'EXPORT', 'EXPORT', 1, 0),
('export:sensitive', '敏感导出', 'EXPORT', 'EXPORT', 2, 1),
('export:sensitive:high', '高敏导出', 'EXPORT', 'EXPORT', 3, 1)
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name);

-- =====================================================
-- 5. Role-Permission Relations
-- =====================================================
-- L1_QUERY: Basic query permissions
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'L1_QUERY'
AND p.permission_code IN ('person:query', 'household:query', 'migration:query', 'floating:query', 'permit:query')
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- L2_HANDLE: Basic + handling permissions
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'L2_HANDLE'
AND p.permission_code IN (
    'person:query', 'person:create', 'person:update', 'person:register',
    'household:query', 'household:create', 'household:establish', 'household:update',
    'migration:query',
    'floating:query', 'floating:register',
    'permit:query', 'permit:apply',
    'certificate:query',
    'approval:query',
    'user:query', 'role:query', 'department:query', 'dictionary:query',
    'export:normal'
)
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- L3_APPROVE_ADMIN: All permissions except system admin
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'L3_APPROVE_ADMIN'
AND p.permission_code NOT LIKE '%manage%'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- ADMIN: All permissions
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.role_id, p.permission_id
FROM sys_role r
CROSS JOIN sys_permission p
WHERE r.role_code = 'ADMIN'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

-- =====================================================
-- 6. System Users (sys_user)
-- Default password: Admin@123 (SHA-256+salt via PasswordEncoder; format = hash:base64salt)
-- =====================================================
INSERT INTO sys_user (username, password_hash, real_name, phone, role_id, department_id, status) VALUES
-- Admin user (role_id=4, department_id=1)
('admin',      '0de25abfb9e2f1d55e7098621b2995fa090dff4c0bf87c7ea45fc50553296dd8:eOJWh/WpoT1ugqlQc5cmUA==', '系统管理员', '13800138000', 4, 1, 'ENABLED'),
-- L1 Query user
('viewer01',   '4ab724fa646f13277f2fa537140af0b42c7e5e5bfc5e22d0da4ef70ee1eeebef:rsM2txc3HMoE+fLIn4zKHA==', '张三(查询)', '13800138001', 1, 2, 'ENABLED'),
-- L2 Handle user
('operator01', 'be05900889d6308f08bf32274702381a2eb9722218a761f42946d1a61c49ce4f:CV875gE90d3iml0uy3hJvg==', '李四(经办)', '13800138002', 2, 2, 'ENABLED'),
-- L3 Approve user
('approver01', '535cdd1377a83f4a0640f813c2fc0ba47e0645ddf87c4a5afe240d8d8ec295f1:iqyjO1MbQY0rbMG8rsZaqQ==', '王五(审批)', '13800138003', 3, 1, 'ENABLED')
ON DUPLICATE KEY UPDATE username = VALUES(username);

-- =====================================================
-- 7. Sample Business Data (for testing)
-- =====================================================

-- Sample person data
-- 覆盖场景：男/女、汉族/少数民族、ACTIVE/已注销、不同年龄段、外来迁入准备人、死亡/出国注销候选人
INSERT INTO person (name, gender_code, identity_type_code, identity_no, birth_date, ethnicity_code, phone, contact_address, record_status_code) VALUES
('张三', 'MALE', 'ID_CARD', '110101199001011234', '1990-01-01', 'HAN', '13800138001', '北京市东城区东华门街道1号', 'ACTIVE'),
('李四', 'FEMALE', 'ID_CARD', '110101199101021234', '1991-01-02', 'HAN', '13800138002', '北京市东城区东华门街道2号', 'ACTIVE'),
('王五', 'MALE', 'ID_CARD', '110102198805031234', '1988-05-03', 'HAN', '13800138003', '北京市西城区金融街街道1号', 'ACTIVE'),
('赵六', 'FEMALE', 'ID_CARD', '110102199203041234', '1992-03-04', 'OTHER', '13800138004', '北京市西城区金融街街道2号', 'ACTIVE'),
('钱七', 'MALE', 'ID_CARD', '110101199506051234', '1995-06-05', 'HAN', '13800138005', '北京市东城区东华门街道3号', 'ACTIVE'),
('孙八', 'FEMALE', 'ID_CARD', '110101199807081234', '1998-07-08', 'HAN', '13800138006', '北京市东城区东华门街道1号', 'ACTIVE'),
('周九', 'MALE', 'ID_CARD', '310101199309091234', '1993-09-09', 'HAN', '13800138007', '上海市黄浦区南京路1号', 'ACTIVE'),
('吴十', 'FEMALE', 'ID_CARD', '310101199410101234', '1994-10-10', 'OTHER', '13800138008', '上海市黄浦区南京路2号', 'ACTIVE'),
('郑十一', 'MALE', 'ID_CARD', '110101195011111234', '1950-11-11', 'HAN', '13800138009', '北京市东城区东华门街道4号', 'ACTIVE'),
('王十二', 'FEMALE', 'ID_CARD', '110102195512121234', '1955-12-12', 'HAN', '13800138010', '北京市西城区金融街街道3号', 'ACTIVE'),
('陈十三', 'MALE', 'ID_CARD', '110101198001131234', '1980-01-13', 'HAN', '13800138011', '北京市东城区东华门街道5号', 'ACTIVE'),
('刘十四', 'FEMALE', 'ID_CARD', '110101199203141234', '1992-03-14', 'HAN', '13800138012', '北京市东城区东华门街道6号', 'ACTIVE'),
('黄十五', 'MALE', 'ID_CARD', '110102198804151234', '1988-04-15', 'HAN', '13800138013', '北京市西城区金融街街道4号', 'ACTIVE'),
('冯九十九', 'MALE', 'ID_CARD', '110101197001011999', '1970-01-01', 'HAN', '13800138999', '北京市东城区历史地址1号', 'CANCELLED')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- Sample household data
-- 含 1 个 COLLECTIVE 集体户、1 个 CANCELLED 已销户（空户）、1 个跨区目标户
INSERT INTO household (household_no, household_type_code, head_person_id, registered_address, region_code, department_id, establish_date, status) VALUES
('H110101001', 'FAMILY', 1, '北京市东城区东华门街道东华门社区1号', '110101', 2, '2020-01-01', 'ACTIVE'),
('H110101002', 'FAMILY', 2, '北京市东城区东华门街道东华门社区2号', '110101', 2, '2020-02-01', 'ACTIVE'),
('H110102001', 'FAMILY', 3, '北京市西城区金融街街道六铺炕社区1号', '110102', 3, '2019-01-01', 'ACTIVE'),
('H110101003', 'COLLECTIVE', 12, '北京市东城区东华门街道集体户挂靠地址', '110101', 2, '2021-03-01', 'ACTIVE'),
('H110102002', 'FAMILY', 4, '北京市西城区金融街街道六铺炕社区2号', '110102', 3, '2022-06-01', 'ACTIVE'),
('H110101999', 'FAMILY', NULL, '北京市东城区已销户地址', '110101', 2, '2018-01-01', 'CANCELLED')
ON DUPLICATE KEY UPDATE household_no = VALUES(household_no);

-- Sample household member data
-- 涵盖多种关系，特别准备：户主+配偶；同户非户主(孙八，可作换户主候选人)；
-- 同市跨区候选(钱七，H110101001→H110102002)；集体户成员；注销目标
INSERT INTO household_member (household_id, person_id, relationship_code, join_date, member_status) VALUES
(1, 1, 'HEAD', '2020-01-01', 'CURRENT'),
(1, 2, 'SPOUSE', '2020-01-01', 'CURRENT'),
(1, 8, 'CHILD', '2020-07-08', 'CURRENT'),
(1, 5, 'CHILD', '2020-06-05', 'CURRENT'),
(1, 11, 'PARENT', '1980-01-13', 'CURRENT'),
(2, 3, 'HEAD', '2020-02-01', 'CURRENT'),
(3, 4, 'HEAD', '2019-01-01', 'CURRENT'),
(3, 10, 'PARENT', '2019-01-01', 'CURRENT'),
(4, 12, 'HEAD', '2021-03-01', 'CURRENT'),
(4, 13, 'OTHER', '2021-03-01', 'CURRENT'),
(5, 9, 'HEAD', '2022-06-01', 'CURRENT')
ON DUPLICATE KEY UPDATE household_id = VALUES(household_id);

-- Sample residence registration data
-- 每个 ACTIVE person 一条（除已注销和待迁入新人）
INSERT INTO residence_registration (person_id, household_id, register_type_code, register_date, registered_address, region_code, start_date) VALUES
(1, 1, 'INITIAL', '2020-01-01', '北京市东城区东华门街道东华门社区1号', '110101', '2020-01-01'),
(2, 1, 'MIGRATION_IN', '2020-01-01', '北京市东城区东华门街道东华门社区1号', '110101', '2020-01-01'),
(3, 2, 'INITIAL', '2020-02-01', '北京市东城区东华门街道东华门社区2号', '110101', '2020-02-01'),
(4, 3, 'INITIAL', '2019-01-01', '北京市西城区金融街街道六铺炕社区1号', '110102', '2019-01-01'),
(5, 1, 'BIRTH', '2020-06-05', '北京市东城区东华门街道东华门社区1号', '110101', '2020-06-05'),
(8, 1, 'BIRTH', '2020-07-08', '北京市东城区东华门街道东华门社区1号', '110101', '2020-07-08'),
(9, 5, 'INITIAL', '2022-06-01', '北京市西城区金融街街道六铺炕社区2号', '110102', '2022-06-01'),
(10, 3, 'INITIAL', '2019-01-01', '北京市西城区金融街街道六铺炕社区1号', '110102', '2019-01-01'),
(11, 1, 'INITIAL', '1980-01-13', '北京市东城区东华门街道东华门社区5号', '110101', '1980-01-13'),
(12, 4, 'INITIAL', '2021-03-01', '北京市东城区东华门街道集体户挂靠地址', '110101', '2021-03-01'),
(13, 4, 'INITIAL', '2021-03-01', '北京市东城区东华门街道集体户挂靠地址', '110101', '2021-03-01')
ON DUPLICATE KEY UPDATE person_id = VALUES(person_id);

-- Sample migration_in records（同市跨区 + 外来迁入，配对批次号）
INSERT INTO migration_in (
    application_id, person_id, in_type_code, transfer_batch_no,
    source_registration_id, from_region_code, from_address, from_household_no,
    to_household_id, to_region_code, in_date, reason_code
) VALUES
(90001, 5, 'CROSS_DISTRICT', 'BATCH2026001',
 5, '110101', '北京市东城区东华门街道东华门社区1号', 'H110101001',
 5, '110102', '2026-01-15', 'HOUSING'),
(90002, 7, 'EXTERNAL', NULL,
 NULL, '310101', '上海市黄浦区南京路1号', NULL,
 4, '110101', '2026-02-01', 'EMPLOYMENT')
ON DUPLICATE KEY UPDATE application_id = VALUES(application_id);

-- Sample migration_out records（与上面 in 配对）
INSERT INTO migration_out (
    application_id, person_id, out_type_code, transfer_batch_no,
    from_household_id, from_region_code, to_region_code, to_address,
    out_date, reason_code
) VALUES
(90003, 5, 'CROSS_DISTRICT', 'BATCH2026001',
 1, '110101', '110102', '北京市西城区金融街街道六铺炕社区2号',
 '2026-01-15', 'HOUSING')
ON DUPLICATE KEY UPDATE application_id = VALUES(application_id);

-- Sample cancellation records（人口注销 + 家庭户销户）
INSERT INTO cancellation_record (
    cancellation_no, application_id, cancel_object_type,
    person_id, household_id, cancel_reason_code, cancel_date
) VALUES
('CX20260001', 90010, 'PERSON', 11, NULL, 'DEATH', '2026-03-01'),
('CX20260002', 90011, 'HOUSEHOLD', NULL, 6, 'HOUSEHOLD_EMPTY', '2026-03-15')
ON DUPLICATE KEY UPDATE cancellation_no = VALUES(cancellation_no);

-- Sample floating population data
INSERT INTO floating_population (person_id, source_region_code, source_address, current_region_code, current_address, arrival_date, register_date, residence_reason_code, status, handling_department_id) VALUES
(1, '310000', '上海市徐汇区', '110101', '北京市东城区东华门街道', '2023-01-01', '2023-01-05', 'EMPLOYMENT', 'ACTIVE', 2)
ON DUPLICATE KEY UPDATE person_id = VALUES(person_id);

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Verification queries
SELECT '=== Base Data Initialization Complete ===' AS Result;

SELECT 'Users:' AS Info;
SELECT user_id, username, real_name, role_id, department_id, status FROM sys_user;

SELECT 'Roles:' AS Info;
SELECT role_id, role_code, role_name, permission_level, data_scope_code, status FROM sys_role;

SELECT 'Permissions Count:' AS Info;
SELECT COUNT(*) AS total_permissions FROM sys_permission;

SELECT 'Role-Permission Count:' AS Info;
SELECT r.role_name, COUNT(rp.permission_id) AS permission_count
FROM sys_role r
LEFT JOIN sys_role_permission rp ON r.role_id = rp.role_id
GROUP BY r.role_id, r.role_name;

SELECT 'Sample Data:' AS Info;
SELECT 'Persons:', COUNT(*) AS count FROM person;
SELECT 'Households:', COUNT(*) AS count FROM household;
SELECT 'Household Members:', COUNT(*) AS count FROM household_member;
SELECT 'Residence Registrations:', COUNT(*) AS count FROM residence_registration;
SELECT 'Floating Population:', COUNT(*) AS count FROM floating_population;
SELECT 'Migration In:', COUNT(*) AS count FROM migration_in;
SELECT 'Migration Out:', COUNT(*) AS count FROM migration_out;
SELECT 'Cancellation Records:', COUNT(*) AS count FROM cancellation_record;
