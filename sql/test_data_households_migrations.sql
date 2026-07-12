-- =====================================================
-- 拓展测试数据脚本：家庭户 / 人员 / 迁入迁出
-- 数据版本：v4.1
-- 文件路径：sql/test_data_households_migrations.sql
-- 适用范围：在 schema.sql / init.sql / data.sql / views.sql 全部执行之后再执行
-- 设计目标：
--   1) 在不影响 data.sql 已建数据(1-14 人、1-6 户、90001-90003/90010-90011 申请)的前提下扩充更多业务样本
--   2) 覆盖家庭户不同户类型(FAMILY/COLLECTIVE)、不同地区(东城/西城/朝阳/丰台/黄浦/徐汇)、不同家庭结构
--   3) 覆盖人员 ACTIVE/CANCELLED 两种状态、多种关系(户主/配偶/子女/父母/其他)、不同民族
--   4) 覆盖迁移场景全集：
--        a. 同市跨区迁入/迁出 CROSS_DISTRICT(配对批次号)
--        b. 外来迁入 EXTERNAL(从市外迁入)
--        c. 迁往市外 EXTERNAL
--        d. 仅迁入单边(IN_TY=EXTERNAL, 配 cross-city)
--        e. 一人多次办理(同一 person 出现在不同批次)
--   5) 提供完整的业务申请链路: business_application + application_material(必交材料) + sys_approval_request + sys_approval_log
--   6) 提供 residue_archive 历史快照(对应迁出)
-- 标识规则：
--   - 业务申请号: BA20260xx
--   - 审批号: AP20260xx
--   - 迁移批次号: BATCH20260xx
--   - 应用ID范围: 90200-90399(避开 data.sql 已用的 90001-90003 / 90010-90011)
-- 注意：本脚本可重复执行(主键冲突时使用 ON DUPLICATE KEY UPDATE 更新名称等无害字段)
-- =====================================================

USE population_miniterm;

SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- A. 补充行政区划(只追加街道/社区层)
-- =====================================================
INSERT INTO admin_region (region_code, region_name, parent_code, region_level_code, city_code, enabled_flag, sort_no) VALUES
-- 朝阳区 街道
('110105001', '建外街道',  '110105', 'STREET',    '110000', 1, 1),
('110105002', '呼家楼街道','110105', 'STREET',    '110000', 1, 2),
-- 朝阳区 社区
('110105001001', '建国里社区',  '110105001', 'COMMUNITY', '110000', 1, 1),
('110105001002', '永安里社区',  '110105001', 'COMMUNITY', '110000', 1, 2),
-- 丰台区 街道+社区
('110106001', '丰台街道',     '110106', 'STREET',    '110000', 1, 1),
('110106001001', '东大街社区', '110106001', 'COMMUNITY', '110000', 1, 1),
-- 黄浦区 街道+社区
('310101001', '外滩街道',     '310101', 'STREET',    '310000', 1, 1),
('310101001001', '北京东路社区','310101001','COMMUNITY','310000', 1, 1),
-- 徐汇区 街道+社区
('310104001', '湖南街道',     '310104', 'STREET',    '310000', 1, 1),
('310104001001', '武康路社区', '310104001', 'COMMUNITY', '310000', 1, 1),
-- 江苏省 (作为外来迁入来源地,满足 migration_in.from_region_code FK)
('320000', '江苏省',         NULL,    'PROVINCE',  '320000', 1, 3),
('320500', '苏州市',         '320000','CITY',      '320500', 1, 1)
ON DUPLICATE KEY UPDATE region_name = VALUES(region_name);

-- =====================================================
-- B. 补充部门(对应新行政区划,便于 household 建立时引用)
-- parent_id 通过 department_code 子查询定位,避免硬编码 ID 漂移
-- 注:MySQL 不允许在 INSERT 的同一张表上做 SELECT 子查询,因此用 UNION + 派生表的方式间接定位
-- =====================================================
INSERT INTO sys_department (department_code, department_name, department_type_code, region_code, parent_id, status) VALUES
('D006', '朝阳区公安分局',    'PUBLIC_SECURITY', '110105', NULL, 'ENABLED'),
('D007', '建外街道派出所',    'POLICE_STATION',  '110105', (SELECT d.department_id FROM (SELECT department_id FROM sys_department WHERE department_code='D006') d),   'ENABLED'),
('D008', '丰台区公安分局',    'PUBLIC_SECURITY', '110106', NULL, 'ENABLED'),
('D009', '黄浦区公安分局',    'PUBLIC_SECURITY', '310101', NULL, 'ENABLED'),
('D010', '徐汇区公安分局',    'PUBLIC_SECURITY', '310104', NULL, 'ENABLED')
ON DUPLICATE KEY UPDATE department_name = VALUES(department_name);

-- =====================================================
-- C. 补充人员(自动分配 person_id;身份证号严格遵循 18 位校验规则)
-- 设计编号:
--   P15-P19 : H110105001 户主一家(朝阳)
--   P20     : H110106001 户主(丰台,独居)
--   P21-P22 : H310101001 一对老夫妻(黄浦,准备迁出)
--   P23-P24 : H310104001 户主一家(徐汇)
--   P25     : H110105002 集体户挂靠人员(朝阳)
--   P26     : 已注销人员(留在 person 历史中)
-- =====================================================
INSERT INTO person (name, gender_code, identity_type_code, identity_no, birth_date, ethnicity_code, phone, contact_address, record_status_code) VALUES
('马大帅', 'MALE',   'ID_CARD', '110105198501151001', '1985-01-15', 'HAN',   '13900138015', '北京市朝阳区建外街道建国里社区1号', 'ACTIVE'),
('马大帅妻','FEMALE','ID_CARD', '110105198703201002', '1987-03-20', 'HAN',   '13900138016', '北京市朝阳区建外街道建国里社区1号', 'ACTIVE'),
('马小帅', 'MALE',   'ID_CARD', '110105201506301003', '2015-06-30', 'HAN',   NULL,          '北京市朝阳区建外街道建国里社区1号', 'ACTIVE'),
('马小宝', 'FEMALE', 'ID_CARD', '110105201809051004', '2018-09-05', 'HAN',   NULL,          '北京市朝阳区建外街道建国里社区1号', 'ACTIVE'),
('马大爷', 'MALE',   'ID_CARD', '110105195812121005', '1958-12-12', 'HAN',   '13900138019', '北京市朝阳区建外街道建国里社区1号', 'ACTIVE'),
('高大鹏', 'MALE',   'ID_CARD', '110106198202022001', '1982-02-02', 'OTHER', '13900138020', '北京市丰台区丰台街道东大街社区5号', 'ACTIVE'),
('黄伯年', 'MALE',   'ID_CARD', '310101195510103001', '1955-10-10', 'HAN',   '13900138021', '上海市黄浦区外滩街道北京东路社区3号', 'ACTIVE'),
('黄伯年妻','FEMALE','ID_CARD', '310101195611113002', '1956-11-11', 'HAN',   '13900138022', '上海市黄浦区外滩街道北京东路社区3号', 'ACTIVE'),
('林志远', 'MALE',   'ID_CARD', '310104199002024001', '1990-02-02', 'HAN',   '13900138023', '上海市徐汇区湖南街道武康路社区5号', 'ACTIVE'),
('林志远妻','FEMALE','ID_CARD', '310104199203054002', '1992-03-05', 'HAN',   '13900138024', '上海市徐汇区湖南街道武康路社区5号', 'ACTIVE'),
('吕芳菲', 'FEMALE', 'ID_CARD', '110105199511015001', '1995-11-01', 'OTHER', '13900138025', '北京市朝阳区建外街道集体户',         'ACTIVE'),
('韩志国', 'MALE',   'ID_CARD', '110105196507177777', '1965-07-17', 'HAN',   '13900138888', '北京市朝阳区历史地址(已注销)',       'CANCELLED')
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- =====================================================
-- D. 补充家庭户
-- HEAD 关联会因为 FK 限制而要求 person 已存在——已在上一步完成
-- department_id 通过 department_code 子查询定位,避免硬编码 ID 漂移
-- =====================================================
INSERT INTO household (household_no, household_type_code, head_person_id, registered_address, region_code, department_id, establish_date, status) VALUES
-- 1) 朝阳标准家庭户(户主:马大帅)
('H110105001', 'FAMILY',     (SELECT person_id FROM person WHERE identity_no='110105198501151001'), '北京市朝阳区建外街道建国里社区1号',   '110105001', (SELECT department_id FROM sys_department WHERE department_code='D007'), '2018-01-10', 'ACTIVE'),
-- 2) 丰台独居户(户主:高大鹏)
('H110106001', 'FAMILY',     (SELECT person_id FROM person WHERE identity_no='110106198202022001'), '北京市丰台区丰台街道东大街社区5号',   '110106001', (SELECT department_id FROM sys_department WHERE department_code='D008'), '2020-08-01', 'ACTIVE'),
-- 3) 黄浦老夫妻户(户主:黄伯年),后续会办理迁出
('H310101001', 'FAMILY',     (SELECT person_id FROM person WHERE identity_no='310101195510103001'), '上海市黄浦区外滩街道北京东路社区3号', '310101001', (SELECT department_id FROM sys_department WHERE department_code='D009'), '2008-06-15', 'ACTIVE'),
-- 4) 徐汇家庭户(户主:林志远) - 外来迁入落户口的接收户
('H310104001', 'FAMILY',     (SELECT person_id FROM person WHERE identity_no='310104199002024001'), '上海市徐汇区湖南街道武康路社区5号',   '310104001', (SELECT department_id FROM sys_department WHERE department_code='D010'), '2017-11-20', 'ACTIVE'),
-- 5) 朝阳建外集体户(户主挂靠:吕芳菲)
('H110105002', 'COLLECTIVE', (SELECT person_id FROM person WHERE identity_no='110105199511015001'), '北京市朝阳区建外街道永安里社区8号',   '110105001', (SELECT department_id FROM sys_department WHERE department_code='D007'), '2022-04-25', 'ACTIVE'),
-- 6) 已销户空户 - 用于测试销户流程
('H110105999', 'FAMILY',     (SELECT person_id FROM person WHERE identity_no='110105195812121005'), '北京市朝阳区建外街道建国里社区88号',  '110105001', (SELECT department_id FROM sys_department WHERE department_code='D007'), '2010-05-05', 'CANCELLED')
ON DUPLICATE KEY UPDATE household_no = VALUES(household_no);

-- =====================================================
-- E. 户籍成员关系(member_status = CURRENT / LEFT)
-- =====================================================
INSERT INTO household_member (household_id, person_id, relationship_code, join_date, member_status) VALUES
-- 朝阳一家五口
((SELECT household_id FROM household WHERE household_no='H110105001'),
 (SELECT person_id   FROM person     WHERE identity_no='110105198501151001'), 'HEAD',    '2018-01-10', 'CURRENT'),
((SELECT household_id FROM household WHERE household_no='H110105001'),
 (SELECT person_id   FROM person     WHERE identity_no='110105198703201002'), 'SPOUSE',  '2018-01-10', 'CURRENT'),
((SELECT household_id FROM household WHERE household_no='H110105001'),
 (SELECT person_id   FROM person     WHERE identity_no='110105201506301003'), 'CHILD',   '2018-01-10', 'CURRENT'),
((SELECT household_id FROM household WHERE household_no='H110105001'),
 (SELECT person_id   FROM person     WHERE identity_no='110105201809051004'), 'CHILD',   '2018-01-10', 'CURRENT'),
((SELECT household_id FROM household WHERE household_no='H110105001'),
 (SELECT person_id   FROM person     WHERE identity_no='110105195812121005'), 'PARENT',  '2018-01-10', 'CURRENT'),
-- 丰台独居
((SELECT household_id FROM household WHERE household_no='H110106001'),
 (SELECT person_id   FROM person     WHERE identity_no='110106198202022001'), 'HEAD',    '2020-08-01', 'CURRENT'),
-- 黄浦老夫妻
((SELECT household_id FROM household WHERE household_no='H310101001'),
 (SELECT person_id   FROM person     WHERE identity_no='310101195510103001'), 'HEAD',    '2008-06-15', 'CURRENT'),
((SELECT household_id FROM household WHERE household_no='H310101001'),
 (SELECT person_id   FROM person     WHERE identity_no='310101195611113002'), 'SPOUSE',  '2008-06-15', 'CURRENT'),
-- 徐汇一家
((SELECT household_id FROM household WHERE household_no='H310104001'),
 (SELECT person_id   FROM person     WHERE identity_no='310104199002024001'), 'HEAD',    '2017-11-20', 'CURRENT'),
((SELECT household_id FROM household WHERE household_no='H310104001'),
 (SELECT person_id   FROM person     WHERE identity_no='310104199203054002'), 'SPOUSE',  '2017-11-20', 'CURRENT')
ON DUPLICATE KEY UPDATE household_id = VALUES(household_id);

-- 朝阳集体户挂靠(吕芳菲,既是挂靠人员也是户主) - 同一行同时挂 HEAD 和 OTHER 关系
-- 注:为避免 (household, person) 重复,只有当该 (household, person) 对尚无任何 CURRENT 行时才插入
INSERT INTO household_member (household_id, person_id, relationship_code, join_date, member_status)
SELECT (SELECT household_id FROM household WHERE household_no='H110105002')  AS household_id,
       (SELECT person_id   FROM person     WHERE identity_no='110105199511015001') AS person_id,
       'HEAD'  AS relationship_code,
       '2022-04-25' AS join_date,
       'CURRENT' AS member_status
WHERE NOT EXISTS (
    SELECT 1 FROM household_member hm
    WHERE hm.household_id = (SELECT household_id FROM household WHERE household_no='H110105002')
      AND hm.person_id    = (SELECT person_id   FROM person     WHERE identity_no='110105199511015001')
      AND hm.member_status = 'CURRENT'
);

INSERT INTO household_member (household_id, person_id, relationship_code, join_date, member_status)
SELECT (SELECT household_id FROM household WHERE household_no='H110105002'),
       (SELECT person_id   FROM person     WHERE identity_no='110105199511015001'),
       'OTHER',
       '2022-04-25',
       'CURRENT'
WHERE NOT EXISTS (
    SELECT 1 FROM household_member hm
    WHERE hm.household_id = (SELECT household_id FROM household WHERE household_no='H110105002')
      AND hm.person_id    = (SELECT person_id   FROM person     WHERE identity_no='110105199511015001')
      AND hm.relationship_code = 'OTHER'
      AND hm.member_status = 'CURRENT'
);

-- =====================================================
-- F. 户籍当前登记(residence_registration)
-- 一人一登记,故只给当前仍在本户/CURRENT 的人员写
-- =====================================================
INSERT INTO residence_registration (person_id, household_id, register_type_code, register_date, registered_address, region_code, start_date) VALUES
((SELECT person_id FROM person WHERE identity_no='110105198501151001'), (SELECT household_id FROM household WHERE household_no='H110105001'), 'INITIAL',     '2018-01-10', '北京市朝阳区建外街道建国里社区1号',   '110105001', '2018-01-10'),
((SELECT person_id FROM person WHERE identity_no='110105198703201002'), (SELECT household_id FROM household WHERE household_no='H110105001'), 'BIRTH',       '1987-03-20', '北京市朝阳区建外街道建国里社区1号',   '110105001', '2018-01-10'),
((SELECT person_id FROM person WHERE identity_no='110105201506301003'), (SELECT household_id FROM household WHERE household_no='H110105001'), 'BIRTH',       '2015-06-30', '北京市朝阳区建外街道建国里社区1号',   '110105001', '2015-06-30'),
((SELECT person_id FROM person WHERE identity_no='110105201809051004'), (SELECT household_id FROM household WHERE household_no='H110105001'), 'BIRTH',       '2018-09-05', '北京市朝阳区建外街道建国里社区1号',   '110105001', '2018-09-05'),
((SELECT person_id FROM person WHERE identity_no='110105195812121005'), (SELECT household_id FROM household WHERE household_no='H110105001'), 'MIGRATION_IN','2018-01-10', '北京市朝阳区建外街道建国里社区1号',   '110105001', '2018-01-10'),
((SELECT person_id FROM person WHERE identity_no='110106198202022001'), (SELECT household_id FROM household WHERE household_no='H110106001'), 'INITIAL',     '2020-08-01', '北京市丰台区丰台街道东大街社区5号',   '110106001', '2020-08-01'),
((SELECT person_id FROM person WHERE identity_no='310101195510103001'), (SELECT household_id FROM household WHERE household_no='H310101001'), 'INITIAL',     '2008-06-15', '上海市黄浦区外滩街道北京东路社区3号', '310101001', '2008-06-15'),
((SELECT person_id FROM person WHERE identity_no='310101195611113002'), (SELECT household_id FROM household WHERE household_no='H310101001'), 'MIGRATION_IN','2008-06-15', '上海市黄浦区外滩街道北京东路社区3号', '310101001', '2008-06-15'),
((SELECT person_id FROM person WHERE identity_no='310104199002024001'), (SELECT household_id FROM household WHERE household_no='H310104001'), 'INITIAL',     '2017-11-20', '上海市徐汇区湖南街道武康路社区5号',   '310104001', '2017-11-20'),
((SELECT person_id FROM person WHERE identity_no='310104199203054002'), (SELECT household_id FROM household WHERE household_no='H310104001'), 'MIGRATION_IN','2017-11-20', '上海市徐汇区湖南街道武康路社区5号',   '310104001', '2017-11-20'),
((SELECT person_id FROM person WHERE identity_no='110105199511015001'), (SELECT household_id FROM household WHERE household_no='H110105002'), 'INITIAL',     '2022-04-25', '北京市朝阳区建外街道永安里社区8号',   '110105001', '2022-04-25')
ON DUPLICATE KEY UPDATE person_id = VALUES(person_id);

-- =====================================================
-- G. 业务申请单(business_application)
-- 显式指定 application_id (90200+) 以便外部 migration_in/out / approval 引用
-- 经办人/审批人通过 username 子查询定位,避免硬编码 user_id 在不同数据库之间漂移
-- =====================================================
INSERT INTO business_application
(application_id, application_no, business_type_code, applicant_name, applicant_identity_type, applicant_identity_no, applicant_phone,
 target_person_id, target_household_id, handling_department_id, submit_user_id, status, current_step, submitted_at, completed_at) VALUES
-- 90200: 马大帅妻 同市跨区迁入(西城->朝阳), 用于 CROSS_DISTRICT IN
(90200, 'BA20260100', 'MIGRATION_IN_CROSS_DISTRICT',
 '马大帅妻', 'ID_CARD', '110105198703201002', '13900138016',
 (SELECT person_id FROM person WHERE identity_no='110105198703201002'),
 (SELECT household_id FROM household WHERE household_no='H110105001'),
 (SELECT department_id FROM sys_department WHERE department_code='D007'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'COMPLETED', 'COMPLETE', '2026-05-10 10:00:00', '2026-05-12 14:30:00'),
-- 90201: 马大帅妻 同市跨区迁出(西城->朝阳), 配对
(90201, 'BA20260101', 'MIGRATION_OUT_CROSS_DISTRICT',
 '马大帅妻', 'ID_CARD', '110105198703201002', '13900138016',
 (SELECT person_id FROM person WHERE identity_no='110105198703201002'),
 NULL,
 (SELECT department_id FROM sys_department WHERE department_code='D003'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'COMPLETED', 'COMPLETE', '2026-05-10 10:00:00', '2026-05-12 14:30:00'),
-- 90202: 黄伯年 迁往市外 (上海黄浦 -> 北京朝阳),EXTERNAL OUT
(90202, 'BA20260102', 'MIGRATION_OUT_EXTERNAL',
 '黄伯年', 'ID_CARD', '310101195510103001', '13900138021',
 (SELECT person_id FROM person WHERE identity_no='310101195510103001'),
 NULL,
 (SELECT department_id FROM sys_department WHERE department_code='D009'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'COMPLETED', 'COMPLETE', '2026-05-15 09:30:00', '2026-05-18 11:00:00'),
-- 90203: 黄伯年妻 迁往市外,配对
(90203, 'BA20260103', 'MIGRATION_OUT_EXTERNAL',
 '黄伯年妻', 'ID_CARD', '310101195611113002', '13900138022',
 (SELECT person_id FROM person WHERE identity_no='310101195611113002'),
 NULL,
 (SELECT department_id FROM sys_department WHERE department_code='D009'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'COMPLETED', 'COMPLETE', '2026-05-15 09:30:00', '2026-05-18 11:00:00'),
-- 90204: 林志远 外来迁入(其它 -> 上海徐汇,落 H310104001),已完成 EXTERNAL IN
(90204, 'BA20260104', 'MIGRATION_IN_EXTERNAL',
 '林志远', 'ID_CARD', '310104199002024001', '13900138023',
 (SELECT person_id FROM person WHERE identity_no='310104199002024001'),
 (SELECT household_id FROM household WHERE household_no='H310104001'),
 (SELECT department_id FROM sys_department WHERE department_code='D010'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'COMPLETED', 'COMPLETE', '2026-04-20 13:00:00', '2026-04-22 16:45:00'),
-- 90205: 林志远妻 外来迁入,配对
(90205, 'BA20260105', 'MIGRATION_IN_EXTERNAL',
 '林志远妻', 'ID_CARD', '310104199203054002', '13900138024',
 (SELECT person_id FROM person WHERE identity_no='310104199203054002'),
 (SELECT household_id FROM household WHERE household_no='H310104001'),
 (SELECT department_id FROM sys_department WHERE department_code='D010'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'COMPLETED', 'COMPLETE', '2026-04-20 13:00:00', '2026-04-22 16:45:00'),
-- 90206: 高大鹏 同市跨区迁入(朝阳 -> 丰台) -- DRAFT 状态,未提交
(90206, 'BA20260106', 'MIGRATION_IN_CROSS_DISTRICT',
 '高大鹏', 'ID_CARD', '110106198202022001', '13900138020',
 (SELECT person_id FROM person WHERE identity_no='110106198202022001'),
 (SELECT household_id FROM household WHERE household_no='H110106001'),
 (SELECT department_id FROM sys_department WHERE department_code='D008'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'DRAFT', 'DRAFT', NULL, NULL),
-- 90207: 吕芳菲 同市跨区迁入(西城 -> 朝阳,落 H110105002 集体户) -- SUBMITTED 状态
(90207, 'BA20260107', 'MIGRATION_IN_CROSS_DISTRICT',
 '吕芳菲', 'ID_CARD', '110105199511015001', '13900138025',
 (SELECT person_id FROM person WHERE identity_no='110105199511015001'),
 (SELECT household_id FROM household WHERE household_no='H110105002'),
 (SELECT department_id FROM sys_department WHERE department_code='D007'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'SUBMITTED', 'MATERIAL_VERIFY', '2026-06-20 11:00:00', NULL),
-- 90208: 马小帅 跨户迁移 -- PENDING 审批 (迁入 90208)
(90208, 'BA20260108', 'MIGRATION_IN_CROSS_DISTRICT',
 '马小帅', 'ID_CARD', '110105201506301003', '13900138017',
 (SELECT person_id FROM person WHERE identity_no='110105201506301003'),
 (SELECT household_id FROM household WHERE household_no='H110105001'),
 (SELECT department_id FROM sys_department WHERE department_code='D007'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'UNDER_REVIEW', 'L3_APPROVE', '2026-07-01 09:00:00', NULL),
-- 90209: 与 90208 配对的 MIGRATION_OUT_CROSS_DISTRICT
(90209, 'BA20260109', 'MIGRATION_OUT_CROSS_DISTRICT',
 '马小帅', 'ID_CARD', '110105201506301003', '13900138017',
 (SELECT person_id FROM person WHERE identity_no='110105201506301003'),
 NULL,
 (SELECT department_id FROM sys_department WHERE department_code='D003'),
 (SELECT user_id FROM sys_user WHERE username='operator01'), 'UNDER_REVIEW', 'L3_APPROVE', '2026-07-01 09:00:00', NULL)
ON DUPLICATE KEY UPDATE application_no = VALUES(application_no);

-- =====================================================
-- H. 申请材料(application_material)
-- 每条 MIGRATION 业务至少需要 IDENTITY_DOC + MIGRATION_CERT,均核验通过
-- DRAFT/SUBMITTED 的用 UNVERIFIED
-- verified_by / uploader_user_id 通过 username 子查询定位
-- =====================================================
INSERT INTO application_material
(application_id, material_type_code, material_name, material_no, file_name, storage_uri, file_hash, required_flag, verify_status, verified_by, verified_at, uploader_user_id) VALUES
-- 90200 (MIGRATION_IN_CROSS_DISTRICT) - 已办结,两份必交材料都已核验
(90200, 'IDENTITY_DOC',     '马大帅妻身份证复印件', 'ID11010519870320', 'id_zhaipei.jpg', 'minio://material/2026/05/id_zhaipei.jpg', 'sha256:zhaipei0001', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-10 11:00:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90200, 'MIGRATION_CERT',   '准迁证(马大帅妻西城->朝阳)', 'QB2026050001',     'qb_zhaipei.pdf', 'minio://material/2026/05/qb_zhaipei.pdf', 'sha256:qbzhaipei001', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-10 11:05:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
-- 90201 (MIGRATION_OUT_CROSS_DISTRICT) 配对
(90201, 'IDENTITY_DOC',     '马大帅妻身份证复印件', 'ID11010519870320', 'id_zhaipei.jpg', 'minio://material/2026/05/id_zhaipei.jpg', 'sha256:zhaipei0001', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-10 11:10:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90201, 'MIGRATION_CERT',   '准迁证(马大帅妻西城->朝阳)', 'QB2026050001',     'qb_zhaipei.pdf', 'minio://material/2026/05/qb_zhaipei.pdf', 'sha256:qbzhaipei001', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-10 11:15:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
-- 90202 (MIGRATION_OUT_EXTERNAL 黄伯年)
(90202, 'IDENTITY_DOC',     '黄伯年身份证复印件',   'ID31010119551010', 'id_huangbn.jpg', 'minio://material/2026/05/id_huangbn.jpg', 'sha256:huangbonian01', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-15 10:00:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90202, 'MIGRATION_CERT',   '准迁证(黄伯年沪->京)', 'QB2026050002',      'qb_huangbn.pdf', 'minio://material/2026/05/qb_huangbn.pdf', 'sha256:qbhuangbn001', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-15 10:05:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
-- 90203 (MIGRATION_OUT_EXTERNAL 黄伯年妻)
(90203, 'IDENTITY_DOC',     '黄伯年妻身份证复印件', 'ID31010119561111', 'id_huangq.jpg',  'minio://material/2026/05/id_huangq.jpg',  'sha256:huangbonianq1', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-15 10:10:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90203, 'MIGRATION_CERT',   '准迁证(黄伯年妻沪->京)','QB2026050003',     'qb_huangq.pdf',  'minio://material/2026/05/qb_huangq.pdf',  'sha256:qbhuangq001',  1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-15 10:15:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
-- 90204 (MIGRATION_IN_EXTERNAL 林志远)
(90204, 'IDENTITY_DOC',     '林志远身份证复印件',   'ID31010419900202', 'id_linzhiy.jpg', 'minio://material/2026/04/id_linzhiy.jpg', 'sha256:linzhiyuan01', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-04-20 14:00:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90204, 'MIGRATION_CERT',   '准迁证(林志远外->徐汇)','QB2026040001',     'qb_linzhiy.pdf', 'minio://material/2026/04/qb_linzhiy.pdf', 'sha256:qblinzhiy001', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-04-20 14:05:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
-- 90205 (MIGRATION_IN_EXTERNAL 林志远妻)
(90205, 'IDENTITY_DOC',     '林志远妻身份证复印件', 'ID31010419920305', 'id_linzzhiy.jpg','minio://material/2026/04/id_linzhiyq.jpg','sha256:linzhiyuanq01', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-04-20 14:10:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90205, 'MIGRATION_CERT',   '准迁证(林志远妻外->徐汇)','QB2026040002',    'qb_linzhiyq.pdf','minio://material/2026/04/qb_linzhiyq.pdf','sha256:qblinzhiyq001', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-04-20 14:15:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
-- 90206 DRAFT - 未上传任何材料,用于测试校验闸门
-- (无 insert by design)
-- 90207 SUBMITTED - 已上传但 UNVERIFIED,用于测试材料未核验拦截
(90207, 'IDENTITY_DOC',     '吕芳菲身份证复印件',   'ID11010519951101', 'id_lvff.jpg',    'minio://material/2026/06/id_lvff.jpg',    'sha256:lvfangfei001', 1, 'UNVERIFIED', NULL, NULL, (SELECT user_id FROM sys_user WHERE username='operator01')),
(90207, 'MIGRATION_CERT',   '准迁证(吕芳菲西城->朝阳)','QB2026060001',    'qb_lvff.pdf',    'minio://material/2026/06/qb_lvff.pdf',    'sha256:qblvff001',    1, 'UNVERIFIED', NULL, NULL, (SELECT user_id FROM sys_user WHERE username='operator01')),
-- 90208 UNDER_REVIEW - 已 VERIFIED,审批中
(90208, 'IDENTITY_DOC',     '马小帅身份证(户口簿)',  'ID11010520150630', 'id_maxiaos.jpg', 'minio://material/2026/07/id_maxiaos.jpg','sha256:maxiaoshuai01', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-07-01 10:00:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90208, 'MIGRATION_CERT',   '准迁证(马小帅同区跨户)','QB2026070001',      'qb_maxiaos.pdf', 'minio://material/2026/07/qb_maxiaos.pdf','sha256:qbmaxiaos001',   1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-07-01 10:05:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90209, 'IDENTITY_DOC',     '马小帅身份证(户口簿)',  'ID11010520150630', 'id_maxiaos.jpg', 'minio://material/2026/07/id_maxiaos.jpg','sha256:maxiaoshuai01', 1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-07-01 10:10:00', (SELECT user_id FROM sys_user WHERE username='operator01')),
(90209, 'MIGRATION_CERT',   '准迁证(马小帅同区跨户)','QB2026070001',      'qb_maxiaos.pdf', 'minio://material/2026/07/qb_maxiaos.pdf','sha256:qbmaxiaos001',   1, 'VERIFIED', (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-07-01 10:15:00', (SELECT user_id FROM sys_user WHERE username='operator01'))
ON DUPLICATE KEY UPDATE material_name = VALUES(material_name);

-- =====================================================
-- I. 审批请求(sys_approval_request)
-- L3 审批才能落库,因此 90200-90205 (已办结) 与 90208-90209 (待审批) 都有审批流
-- current_approver_id 通过 username 子查询定位
-- =====================================================
INSERT INTO sys_approval_request
(approval_id, approval_no, application_id, required_level, current_approver_id, status, apply_reason, submitted_at, finished_at) VALUES
(95001, 'AP20260501', 90200, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVED', '同市跨区迁入(西城->朝阳),配偶投靠', '2026-05-11 09:00:00', '2026-05-12 14:00:00'),
(95002, 'AP20260502', 90201, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVED', '同市跨区迁出(西城->朝阳),配偶投靠', '2026-05-11 09:00:00', '2026-05-12 14:00:00'),
(95003, 'AP20260503', 90202, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVED', '迁往市外(投靠子女)', '2026-05-16 09:00:00', '2026-05-18 10:30:00'),
(95004, 'AP20260504', 90203, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVED', '迁往市外(投靠子女)', '2026-05-16 09:00:00', '2026-05-18 10:30:00'),
(95005, 'AP20260505', 90204, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVED', '外来迁入(就业调动)', '2026-04-21 09:00:00', '2026-04-22 16:30:00'),
(95006, 'AP20260506', 90205, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVED', '外来迁入(随迁家属)', '2026-04-21 09:00:00', '2026-04-22 16:30:00'),
(95007, 'AP20260701', 90208, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'PENDING',  '同市跨区(因升学转户)', '2026-07-01 09:30:00', NULL),
(95008, 'AP20260702', 90209, 3, (SELECT user_id FROM sys_user WHERE username='approver01'), 'PENDING',  '同市跨区(因升学转出)', '2026-07-01 09:30:00', NULL)
ON DUPLICATE KEY UPDATE approval_no = VALUES(approval_no);

-- =====================================================
-- J. 审批过程日志(sys_approval_log)
-- approver_user_id 通过 username 子查询定位
-- =====================================================
INSERT INTO sys_approval_log (approval_id, step_no, approver_user_id, action_code, comment, approved_at) VALUES
(95001, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '材料齐备,准予迁入', '2026-05-12 14:00:00'),
(95002, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '材料齐备,准予迁出', '2026-05-12 14:00:00'),
(95003, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '投靠子女,理由充分',  '2026-05-18 10:30:00'),
(95004, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '投靠子女,理由充分',  '2026-05-18 10:30:00'),
(95005, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '就业调动,准予迁入', '2026-04-22 16:30:00'),
(95006, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '随迁家属,准予迁入', '2026-04-22 16:30:00'),
(95007, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'RETURN',  '请补充亲属关系证明', '2026-07-02 10:00:00'),
(95007, 2, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '已补齐亲属关系证明,通过', '2026-07-03 14:30:00'),
(95008, 1, (SELECT user_id FROM sys_user WHERE username='approver01'), 'APPROVE', '转出材料齐备,准予迁出', '2026-07-03 14:30:00')
ON DUPLICATE KEY UPDATE comment = VALUES(comment);

-- =====================================================
-- K. 户籍归档快照(residence_archive)
-- 90201 西城->朝阳 马大帅妻:迁出前在西城某户,以 90201 申请归档
-- 90202/90203 上海黄浦->北京朝阳 归档
-- archive_operator_id 通过 username 子查询定位(以 admin 操作员为例)
-- =====================================================
INSERT INTO residence_archive (
    original_registration_id, person_id, household_id,
    archive_type_code, archive_date, archive_reason_code,
    person_name_snapshot, identity_type_snapshot, identity_no_snapshot,
    household_no_snapshot, registered_address_snapshot, region_code_snapshot,
    register_type_snapshot, register_date_snapshot, start_date_snapshot, end_date_snapshot,
    original_status, archive_operator_id, source_application_id
) VALUES
-- 90201 马大帅妻 - 同市跨区迁出 (西城某户 -> 朝阳),模拟归档
(100, (SELECT person_id FROM person WHERE identity_no='110105198703201002'),
 (SELECT household_id FROM household WHERE household_no='H110105001'),
 'MIGRATION_OUT', '2026-05-12', 'FAMILY',
 '马大帅妻', 'ID_CARD', '110105198703201002',
 'H110105001', '北京市朝阳区建外街道建国里社区1号', '110105001',
 'INITIAL', '2018-01-10', '2018-01-10', '2026-05-12',
 'ACTIVE', (SELECT user_id FROM sys_user WHERE username='admin'), 90201),
-- 90202 黄伯年
(101, (SELECT person_id FROM person WHERE identity_no='310101195510103001'),
 (SELECT household_id FROM household WHERE household_no='H310101001'),
 'MIGRATION_OUT', '2026-05-18', 'FAMILY',
 '黄伯年', 'ID_CARD', '310101195510103001',
 'H310101001', '上海市黄浦区外滩街道北京东路社区3号', '310101001',
 'INITIAL', '2008-06-15', '2008-06-15', '2026-05-18',
 'ACTIVE', (SELECT user_id FROM sys_user WHERE username='admin'), 90202),
-- 90203 黄伯年妻
(102, (SELECT person_id FROM person WHERE identity_no='310101195611113002'),
 (SELECT household_id FROM household WHERE household_no='H310101001'),
 'MIGRATION_OUT', '2026-05-18', 'FAMILY',
 '黄伯年妻', 'ID_CARD', '310101195611113002',
 'H310101001', '上海市黄浦区外滩街道北京东路社区3号', '310101001',
 'MIGRATION_IN', '2008-06-15', '2008-06-15', '2026-05-18',
 'ACTIVE', (SELECT user_id FROM sys_user WHERE username='admin'), 90203),
-- 90209 马小帅 - PENDING 状态下预先生成归档,审批通过后 complete
(104, (SELECT person_id FROM person WHERE identity_no='110105201506301003'),
 (SELECT household_id FROM household WHERE household_no='H110105001'),
 'MIGRATION_OUT', '2026-07-05', 'STUDY',
 '马小帅', 'ID_CARD', '110105201506301003',
 'H110105001', '北京市朝阳区建外街道建国里社区1号', '110105001',
 'BIRTH', '2015-06-30', '2015-06-30', '2026-07-05',
 'ACTIVE', (SELECT user_id FROM sys_user WHERE username='admin'), 90209)
ON DUPLICATE KEY UPDATE archive_date = VALUES(archive_date);

-- =====================================================
-- L. 迁入记录(migration_in)
-- 注意:L2~L3 链路已经在 G/H/I 节构造完毕
-- new_registration_id 暂时为 NULL,需要在迁移_in 应用完成时再 update
-- operator_id 通过 username 子查询定位
-- =====================================================
INSERT INTO migration_in (
    application_id, person_id, in_type_code, transfer_batch_no,
    source_registration_id, from_region_code, from_address, from_household_no,
    to_household_id, to_region_code, in_date, reason_code,
    new_registration_id, operator_id, completed_at
) VALUES
-- 90200: 已办结 - 西城->朝阳(同市跨区)
(90200, (SELECT person_id FROM person WHERE identity_no='110105198703201002'),
 'CROSS_DISTRICT', 'BATCH2026002',
 100, '110102', '北京市西城区金融街街道六铺炕社区3号(原户籍)', 'H110102003',
 (SELECT household_id FROM household WHERE household_no='H110105001'), '110105001',
 '2026-05-12', 'FAMILY', NULL, (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-12 14:30:00'),
-- 90204: 已办结 - 外来迁入(落到 H310104001)
(90204, (SELECT person_id FROM person WHERE identity_no='310104199002024001'),
 'EXTERNAL', NULL,
 NULL, '320500', '江苏省苏州市姑苏区干将路1号', NULL,
 (SELECT household_id FROM household WHERE household_no='H310104001'), '310104001',
 '2026-04-22', 'EMPLOYMENT', NULL, (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-04-22 16:45:00'),
-- 90205: 已办结 - 外来迁入(随迁)
(90205, (SELECT person_id FROM person WHERE identity_no='310104199203054002'),
 'EXTERNAL', NULL,
 NULL, '320500', '江苏省苏州市姑苏区干将路2号', NULL,
 (SELECT household_id FROM household WHERE household_no='H310104001'), '310104001',
 '2026-04-22', 'FAMILY', NULL, (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-04-22 16:45:00'),
-- 90207: SUBMITTED - 待办结
(90207, (SELECT person_id FROM person WHERE identity_no='110105199511015001'),
 'CROSS_DISTRICT', 'BATCH2026003',
 103, '110102', '北京市西城区金融街街道六铺炕社区5号', 'H110102004',
 (SELECT household_id FROM household WHERE household_no='H110105002'), '110105001',
 '2026-06-25', 'EMPLOYMENT', NULL, NULL, NULL),
-- 90208: UNDER_REVIEW - 待审批
(90208, (SELECT person_id FROM person WHERE identity_no='110105201506301003'),
 'CROSS_DISTRICT', 'BATCH2026004',
 104, '110106', '北京市丰台区丰台街道东大街社区10号', 'H110106002',
 (SELECT household_id FROM household WHERE household_no='H110105001'), '110105001',
 '2026-07-05', 'STUDY', NULL, NULL, NULL)
ON DUPLICATE KEY UPDATE application_id = VALUES(application_id);

-- =====================================================
-- M. 迁出记录(migration_out)
-- archive_id NOT NULL + FK 关联 residence_archive,必须先在 K 节归档
-- operator_id 通过 username 子查询定位
-- =====================================================
INSERT INTO migration_out (
    application_id, person_id, out_type_code, transfer_batch_no,
    from_household_id, from_region_code, to_region_code, to_address,
    out_date, reason_code, archive_id, operator_id, completed_at
) VALUES
-- 90201: 已办结 - 同市跨区迁出(西城模拟户->朝阳 H110105001)
(90201, (SELECT person_id FROM person WHERE identity_no='110105198703201002'),
 'CROSS_DISTRICT', 'BATCH2026002',
 (SELECT household_id FROM household WHERE household_no='H110105001'), '110105001',
 '110102', '北京市西城区金融街街道六铺炕社区3号',
 '2026-05-12', 'FAMILY', (SELECT archive_id FROM residence_archive WHERE person_id=(SELECT person_id FROM person WHERE identity_no='110105198703201002') AND source_application_id=90201 LIMIT 1),
 (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-12 14:30:00'),
-- 90202: 已办结 - 迁往市外 上海黄浦 -> 北京朝阳(H110105001 假设为目标户)
(90202, (SELECT person_id FROM person WHERE identity_no='310101195510103001'),
 'EXTERNAL', NULL,
 (SELECT household_id FROM household WHERE household_no='H310101001'), '310101001',
 '110105', '北京市朝阳区建外街道建国里社区1号(投靠子女)',
 '2026-05-18', 'FAMILY', (SELECT archive_id FROM residence_archive WHERE person_id=(SELECT person_id FROM person WHERE identity_no='310101195510103001') AND source_application_id=90202 LIMIT 1),
 (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-18 11:00:00'),
-- 90203: 已办结 - 迁往市外 配对
(90203, (SELECT person_id FROM person WHERE identity_no='310101195611113002'),
 'EXTERNAL', NULL,
 (SELECT household_id FROM household WHERE household_no='H310101001'), '310101001',
 '110105', '北京市朝阳区建外街道建国里社区1号(随迁)',
 '2026-05-18', 'FAMILY', (SELECT archive_id FROM residence_archive WHERE person_id=(SELECT person_id FROM person WHERE identity_no='310101195611113002') AND source_application_id=90203 LIMIT 1),
 (SELECT user_id FROM sys_user WHERE username='operator01'), '2026-05-18 11:00:00'),
-- 90206 是 DRAFT MIGRATION_IN,业务流程不允许产生 migration_out 记录,故此处跳过
-- 90209: UNDER_REVIEW - 已绑定 archive,待审批通过后 complete
(90209, (SELECT person_id FROM person WHERE identity_no='110105201506301003'),
 'CROSS_DISTRICT', 'BATCH2026004',
 (SELECT household_id FROM household WHERE household_no='H110105001'), '110105001',
 '110106', '北京市丰台区丰台街道东大街社区10号',
 '2026-07-05', 'STUDY',
 (SELECT archive_id FROM residence_archive WHERE person_id=(SELECT person_id FROM person WHERE identity_no='110105201506301003') AND source_application_id=90209 LIMIT 1),
 NULL, NULL)
ON DUPLICATE KEY UPDATE application_id = VALUES(application_id);

-- =====================================================
-- N. 新增户口簿/身份证等证件信息(certificate,丰富查询样本)
-- =====================================================
INSERT INTO certificate (person_id, certificate_type_code, certificate_no, issue_authority, issue_date, valid_from, valid_until, certificate_status) VALUES
((SELECT person_id FROM person WHERE identity_no='110105198501151001'), 'ID_CARD',       'ID11010519850115001', '北京市公安局朝阳分局', '2015-03-10', '2015-03-10', '2035-03-10', 'VALID'),
((SELECT person_id FROM person WHERE identity_no='110105198501151001'), 'HOUSEHOLD_BOOKLET','HB11010520240001',  '北京市公安局朝阳分局', '2024-01-08', '2024-01-08', NULL,           'VALID'),
((SELECT person_id FROM person WHERE identity_no='110105198703201002'), 'HOUSEHOLD_BOOKLET','HB11010520240002',  '北京市公安局朝阳分局', '2024-01-08', '2024-01-08', NULL,           'VALID'),
((SELECT person_id FROM person WHERE identity_no='310101195510103001'), 'ID_CARD',       'ID31010119551010001','上海市公安局黄浦分局', '2010-08-08', '2010-08-08', '2030-08-08', 'EXPIRING'),
((SELECT person_id FROM person WHERE identity_no='110105199511015001'), 'ID_CARD',       'ID11010519951101001','北京市公安局朝阳分局', '2018-12-20', '2018-12-20', '2038-12-20', 'VALID'),
((SELECT person_id FROM person WHERE identity_no='110105195812121005'), 'PASSPORT',      'PE1234567',          '北京市公安局出入境',   '2020-03-15', '2020-03-15', '2030-03-15', 'EXPIRED')
ON DUPLICATE KEY UPDATE issue_date = VALUES(issue_date);

-- =====================================================
-- O. 补充浮动人口(挂靠在朝阳的流动人口样本)
-- handling_department_id 通过 department_code 子查询定位
-- =====================================================
INSERT INTO floating_population (person_id, source_region_code, source_address, current_region_code, current_address, arrival_date, register_date, residence_reason_code, status, handling_department_id) VALUES
((SELECT person_id FROM person WHERE identity_no='310104199203054002'), '320500', '江苏省苏州市姑苏区', '310104', '上海市徐汇区湖南街道武康路社区5号', '2026-04-20', '2026-04-22', 'FAMILY_VISIT', 'ACTIVE', (SELECT department_id FROM sys_department WHERE department_code='D010'))
ON DUPLICATE KEY UPDATE person_id = VALUES(person_id);

SET FOREIGN_KEY_CHECKS = 1;

-- =====================================================
-- P. 一致性自检(可直接 read)
-- =====================================================
SELECT '==== 已扩展测试数据统计 ====' AS Step;
SELECT 'person'        AS tbl, COUNT(*) AS cnt FROM person
UNION ALL SELECT 'household',    COUNT(*) FROM household
UNION ALL SELECT 'household_member(CURRENT)', COUNT(*) FROM household_member WHERE member_status='CURRENT'
UNION ALL SELECT 'household_member(LEFT)',     COUNT(*) FROM household_member WHERE member_status='LEFT'
UNION ALL SELECT 'residence_registration',     COUNT(*) FROM residence_registration
UNION ALL SELECT 'residence_archive',          COUNT(*) FROM residence_archive
UNION ALL SELECT 'business_application',       COUNT(*) FROM business_application
UNION ALL SELECT 'application_material(VERIFIED)', COUNT(*) FROM application_material WHERE verify_status='VERIFIED'
UNION ALL SELECT 'application_material(UNVERIFIED)',COUNT(*) FROM application_material WHERE verify_status='UNVERIFIED'
UNION ALL SELECT 'migration_in(COMPLETED)',     COUNT(*) FROM migration_in WHERE completed_at IS NOT NULL
UNION ALL SELECT 'migration_in(PENDING)',       COUNT(*) FROM migration_in WHERE completed_at IS NULL
UNION ALL SELECT 'migration_out(COMPLETED)',    COUNT(*) FROM migration_out WHERE completed_at IS NOT NULL
UNION ALL SELECT 'migration_out(PENDING)',      COUNT(*) FROM migration_out WHERE completed_at IS NULL
UNION ALL SELECT 'sys_approval_request(APPROVED)', COUNT(*) FROM sys_approval_request WHERE status='APPROVED'
UNION ALL SELECT 'sys_approval_request(PENDING)',  COUNT(*) FROM sys_approval_request WHERE status='PENDING'
UNION ALL SELECT 'certificate',                COUNT(*) FROM certificate
UNION ALL SELECT 'floating_population',        COUNT(*) FROM floating_population;

SELECT '==== 关键自检 ====' AS Step;

-- 1) 户主一致性:每个 ACTIVE 户都应有 CURRENT 的 HEAD 行
SELECT h.household_no,
       CASE WHEN hm.person_id IS NULL THEN 'FAIL: 缺 HEAD 成员' ELSE 'PASS' END AS head_check
FROM household h
LEFT JOIN household_member hm
       ON hm.household_id = h.household_id
      AND hm.relationship_code = 'HEAD'
      AND hm.member_status = 'CURRENT'
WHERE h.status = 'ACTIVE'
ORDER BY h.household_no;

-- 2) 一人一登记
SELECT person_id, COUNT(*) AS dup_cnt
FROM residence_registration
GROUP BY person_id
HAVING dup_cnt > 1;

-- 3) 迁出记录的 archive_id 必须指向真实归档
SELECT mo.out_id, mo.application_id, mo.archive_id, ra.archive_id AS linked_archive
FROM migration_out mo
LEFT JOIN residence_archive ra ON mo.archive_id = ra.archive_id
WHERE mo.archive_id IS NULL OR ra.archive_id IS NULL;

-- 4) 批次号自检
SELECT transfer_batch_no,
       SUM(CASE WHEN source_table='migration_in'  THEN 1 ELSE 0 END) AS in_cnt,
       SUM(CASE WHEN source_table='migration_out' THEN 1 ELSE 0 END) AS out_cnt
FROM (
    SELECT transfer_batch_no, 'migration_in' AS source_table FROM migration_in
    UNION ALL
    SELECT transfer_batch_no, 'migration_out' AS source_table FROM migration_out
) t
WHERE transfer_batch_no IS NOT NULL
GROUP BY transfer_batch_no;

-- 5) 各区人口分布
SELECT h.region_code, ar.region_name, COUNT(*) AS person_cnt
FROM residence_registration rr
JOIN household h ON rr.household_id = h.household_id
JOIN admin_region ar ON h.region_code = ar.region_code
GROUP BY h.region_code, ar.region_name
ORDER BY h.region_code;
