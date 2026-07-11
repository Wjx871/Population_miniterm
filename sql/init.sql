-- =====================================================
-- Data Dictionary Initialization Script v4.0
-- Based on: 数据库设计v4.0_Cursor详细说明.md
-- Total: 39 types, 159 items
-- =====================================================

USE population_miniterm;

-- Disable foreign key checks for initial data load
SET FOREIGN_KEY_CHECKS = 0;

-- =====================================================
-- 1. ACCOUNT_STATUS (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('ACCOUNT_STATUS', 'ENABLED', '启用', 1, 'ENABLED', NULL),
('ACCOUNT_STATUS', 'DISABLED', '停用', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 2. ROLE_LEVEL / Permission Level (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('ROLE_LEVEL', 'L1_QUERY', '一级：查询', 1, 'ENABLED', '仅能查询，按数据范围过滤'),
('ROLE_LEVEL', 'L2_HANDLE', '二级：经办', 2, 'ENABLED', '可办理业务，部分需审批'),
('ROLE_LEVEL', 'L3_APPROVE_ADMIN', '三级：审批/管理', 3, 'ENABLED', '可审批重大业务和系统配置')
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 3. DATA_SCOPE (4 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('DATA_SCOPE', 'ALL', '全部数据', 1, 'ENABLED', NULL),
('DATA_SCOPE', 'DEPARTMENT', '本部门数据', 2, 'ENABLED', NULL),
('DATA_SCOPE', 'REGION', '本行政区划数据', 3, 'ENABLED', NULL),
('DATA_SCOPE', 'SELF', '仅本人经办数据', 4, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 4. ACTION_CODE (7 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('ACTION_CODE', 'QUERY', '查询', 1, 'ENABLED', NULL),
('ACTION_CODE', 'CREATE', '新增', 2, 'ENABLED', NULL),
('ACTION_CODE', 'UPDATE', '修改', 3, 'ENABLED', NULL),
('ACTION_CODE', 'ARCHIVE', '归档', 4, 'ENABLED', NULL),
('ACTION_CODE', 'EXPORT', '导出', 5, 'ENABLED', NULL),
('ACTION_CODE', 'APPROVE', '审批', 6, 'ENABLED', NULL),
('ACTION_CODE', 'CONFIG', '配置', 7, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 5. DEPARTMENT_TYPE (4 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('DEPARTMENT_TYPE', 'PUBLIC_SECURITY', '公安机关', 1, 'ENABLED', NULL),
('DEPARTMENT_TYPE', 'POLICE_STATION', '派出所', 2, 'ENABLED', NULL),
('DEPARTMENT_TYPE', 'STREET', '街道办', 3, 'ENABLED', NULL),
('DEPARTMENT_TYPE', 'COMMUNITY', '社区', 4, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 6. REGION_LEVEL (5 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('REGION_LEVEL', 'PROVINCE', '省级', 1, 'ENABLED', NULL),
('REGION_LEVEL', 'CITY', '市级', 2, 'ENABLED', NULL),
('REGION_LEVEL', 'DISTRICT', '区县级', 3, 'ENABLED', NULL),
('REGION_LEVEL', 'STREET', '街道级', 4, 'ENABLED', NULL),
('REGION_LEVEL', 'COMMUNITY', '社区级', 5, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 7. GENDER (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('GENDER', 'MALE', '男', 1, 'ENABLED', NULL),
('GENDER', 'FEMALE', '女', 2, 'ENABLED', NULL),
('GENDER', 'UNKNOWN', '未知', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 8. IDENTITY_TYPE (4 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('IDENTITY_TYPE', 'ID_CARD', '居民身份证', 1, 'ENABLED', NULL),
('IDENTITY_TYPE', 'PASSPORT', '护照', 2, 'ENABLED', NULL),
('IDENTITY_TYPE', 'BIRTH_CERT', '出生医学证明', 3, 'ENABLED', NULL),
('IDENTITY_TYPE', 'OTHER', '其他有效身份证明', 4, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 9. ETHNICITY (2 items) - 正式系统需按国家标准补齐
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('ETHNICITY', 'HAN', '汉族', 1, 'ENABLED', NULL),
('ETHNICITY', 'OTHER', '其他', 2, 'ENABLED', '正式系统需按国家标准维护完整民族代码')
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 10. PERSON_RECORD_STATUS (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('PERSON_RECORD_STATUS', 'ACTIVE', '有效', 1, 'ENABLED', NULL),
('PERSON_RECORD_STATUS', 'CANCELLED', '已注销', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 11. HOUSEHOLD_TYPE (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('HOUSEHOLD_TYPE', 'FAMILY', '家庭户', 1, 'ENABLED', NULL),
('HOUSEHOLD_TYPE', 'COLLECTIVE', '集体户', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 12. HOUSEHOLD_STATUS (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('HOUSEHOLD_STATUS', 'ACTIVE', '正常', 1, 'ENABLED', NULL),
('HOUSEHOLD_STATUS', 'CANCELLED', '已销户', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 13. APPLICATION_STATUS (7 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('APPLICATION_STATUS', 'DRAFT', '草稿', 1, 'ENABLED', NULL),
('APPLICATION_STATUS', 'SUBMITTED', '已提交', 2, 'ENABLED', NULL),
('APPLICATION_STATUS', 'UNDER_REVIEW', '审批中', 3, 'ENABLED', NULL),
('APPLICATION_STATUS', 'APPROVED', '已通过', 4, 'ENABLED', NULL),
('APPLICATION_STATUS', 'REJECTED', '已驳回', 5, 'ENABLED', NULL),
('APPLICATION_STATUS', 'COMPLETED', '已办结', 6, 'ENABLED', NULL),
('APPLICATION_STATUS', 'CANCELLED', '已撤销', 7, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 14. BUSINESS_TYPE (13 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('BUSINESS_TYPE', 'HOUSEHOLD_ESTABLISH', '家庭户立户', 1, 'ENABLED', NULL),
('BUSINESS_TYPE', 'PERSON_REGISTER', '人口登记', 2, 'ENABLED', '含新生儿登记、恢复登记等'),
('BUSINESS_TYPE', 'MIGRATION_IN_CROSS_DISTRICT', '同市跨区迁入', 3, 'ENABLED', NULL),
('BUSINESS_TYPE', 'MIGRATION_IN_EXTERNAL', '外来迁入', 4, 'ENABLED', NULL),
('BUSINESS_TYPE', 'MIGRATION_OUT_CROSS_DISTRICT', '同市跨区迁出', 5, 'ENABLED', NULL),
('BUSINESS_TYPE', 'MIGRATION_OUT_EXTERNAL', '迁往市外', 6, 'ENABLED', NULL),
('BUSINESS_TYPE', 'FLOATING_REGISTER', '流动人口登记', 7, 'ENABLED', NULL),
('BUSINESS_TYPE', 'RESIDENCE_PERMIT_APPLY', '居住凭证/居住证申领', 8, 'ENABLED', NULL),
('BUSINESS_TYPE', 'KEY_REGISTER', '重点人口登记', 9, 'ENABLED', NULL),
('BUSINESS_TYPE', 'KEY_RELEASE', '解除重点管理', 10, 'ENABLED', NULL),
('BUSINESS_TYPE', 'PERSON_CANCEL', '人口注销', 11, 'ENABLED', NULL),
('BUSINESS_TYPE', 'HOUSEHOLD_CANCEL', '家庭户销户', 12, 'ENABLED', NULL),
('BUSINESS_TYPE', 'SENSITIVE_EXPORT', '敏感数据导出', 13, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 15. APPROVAL_STATUS (5 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('APPROVAL_STATUS', 'PENDING', '待审批', 1, 'ENABLED', NULL),
('APPROVAL_STATUS', 'APPROVED', '已通过', 2, 'ENABLED', NULL),
('APPROVAL_STATUS', 'REJECTED', '已驳回', 3, 'ENABLED', NULL),
('APPROVAL_STATUS', 'RETURNED', '退回补充', 4, 'ENABLED', NULL),
('APPROVAL_STATUS', 'CANCELLED', '已撤销', 5, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 16. APPROVE_ACTION (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('APPROVE_ACTION', 'APPROVE', '通过', 1, 'ENABLED', NULL),
('APPROVE_ACTION', 'REJECT', '驳回', 2, 'ENABLED', NULL),
('APPROVE_ACTION', 'RETURN', '退回补充', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 17. MATERIAL_VERIFY_STATUS (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('MATERIAL_VERIFY_STATUS', 'UNVERIFIED', '未核验', 1, 'ENABLED', NULL),
('MATERIAL_VERIFY_STATUS', 'VERIFIED', '核验通过', 2, 'ENABLED', NULL),
('MATERIAL_VERIFY_STATUS', 'REJECTED', '核验不通过', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 18. MATERIAL_TYPE (13 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('MATERIAL_TYPE', 'IDENTITY_DOC', '身份证明', 1, 'ENABLED', NULL),
('MATERIAL_TYPE', 'HOUSEHOLD_BOOKLET', '户口簿或户籍证明', 2, 'ENABLED', NULL),
('MATERIAL_TYPE', 'RESIDENCE_PROOF', '合法稳定住所证明', 3, 'ENABLED', NULL),
('MATERIAL_TYPE', 'RELATIONSHIP_PROOF', '亲属关系证明', 4, 'ENABLED', NULL),
('MATERIAL_TYPE', 'BIRTH_CERT', '出生医学证明', 5, 'ENABLED', NULL),
('MATERIAL_TYPE', 'DEATH_CERT', '死亡证明', 6, 'ENABLED', NULL),
('MATERIAL_TYPE', 'RELEASE_CERT', '释放证明', 7, 'ENABLED', NULL),
('MATERIAL_TYPE', 'MIGRATION_CERT', '迁移或准迁证明', 8, 'ENABLED', NULL),
('MATERIAL_TYPE', 'EMPLOYMENT_PROOF', '就业证明', 9, 'ENABLED', NULL),
('MATERIAL_TYPE', 'ENROLLMENT_PROOF', '就读证明', 10, 'ENABLED', NULL),
('MATERIAL_TYPE', 'PHOTO', '本人照片', 11, 'ENABLED', NULL),
('MATERIAL_TYPE', 'SETTLEMENT_ABROAD_PROOF', '出国定居证明', 12, 'ENABLED', NULL),
('MATERIAL_TYPE', 'OTHER', '其他材料', 13, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 19. RELATIONSHIP (5 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('RELATIONSHIP', 'HEAD', '户主', 1, 'ENABLED', NULL),
('RELATIONSHIP', 'SPOUSE', '配偶', 2, 'ENABLED', NULL),
('RELATIONSHIP', 'CHILD', '子女', 3, 'ENABLED', NULL),
('RELATIONSHIP', 'PARENT', '父母', 4, 'ENABLED', NULL),
('RELATIONSHIP', 'OTHER', '其他', 5, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 20. MEMBER_STATUS (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('MEMBER_STATUS', 'CURRENT', '当前成员', 1, 'ENABLED', NULL),
('MEMBER_STATUS', 'LEFT', '已迁出', 2, 'ENABLED', NULL),
('MEMBER_STATUS', 'CANCELLED', '已注销', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 21. REGISTER_TYPE (4 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('REGISTER_TYPE', 'INITIAL', '初始登记', 1, 'ENABLED', NULL),
('REGISTER_TYPE', 'BIRTH', '出生登记', 2, 'ENABLED', NULL),
('REGISTER_TYPE', 'MIGRATION_IN', '迁入登记', 3, 'ENABLED', NULL),
('REGISTER_TYPE', 'RESTORE', '恢复登记', 4, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 22. ARCHIVE_TYPE (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('ARCHIVE_TYPE', 'MIGRATION_OUT', '迁出归档', 1, 'ENABLED', NULL),
('ARCHIVE_TYPE', 'PERSON_CANCEL', '人口注销归档', 2, 'ENABLED', NULL),
('ARCHIVE_TYPE', 'HOUSEHOLD_CANCEL', '家庭户销户归档', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 23. MIGRATION_REASON (6 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('MIGRATION_REASON', 'EMPLOYMENT', '就业', 1, 'ENABLED', NULL),
('MIGRATION_REASON', 'STUDY', '就学', 2, 'ENABLED', NULL),
('MIGRATION_REASON', 'FAMILY', '投靠亲属', 3, 'ENABLED', NULL),
('MIGRATION_REASON', 'HOUSING', '购房或住所变更', 4, 'ENABLED', NULL),
('MIGRATION_REASON', 'MARRIAGE', '婚姻', 5, 'ENABLED', NULL),
('MIGRATION_REASON', 'OTHER', '其他', 6, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 24. IN_TYPE (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('IN_TYPE', 'CROSS_DISTRICT', '同市跨区迁入', 1, 'ENABLED', NULL),
('IN_TYPE', 'EXTERNAL', '外来迁入', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 25. OUT_TYPE (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('OUT_TYPE', 'CROSS_DISTRICT', '同市跨区迁出', 1, 'ENABLED', NULL),
('OUT_TYPE', 'EXTERNAL', '迁往市外', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 26. FLOATING_STATUS (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('FLOATING_STATUS', 'ACTIVE', '有效', 1, 'ENABLED', NULL),
('FLOATING_STATUS', 'LEFT', '已离开', 2, 'ENABLED', NULL),
('FLOATING_STATUS', 'EXPIRED', '已过期', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 27. RESIDENCE_REASON (5 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('RESIDENCE_REASON', 'EMPLOYMENT', '就业', 1, 'ENABLED', NULL),
('RESIDENCE_REASON', 'STUDY', '就读', 2, 'ENABLED', NULL),
('RESIDENCE_REASON', 'FAMILY_VISIT', '探亲', 3, 'ENABLED', NULL),
('RESIDENCE_REASON', 'BUSINESS', '经营', 4, 'ENABLED', NULL),
('RESIDENCE_REASON', 'OTHER', '其他', 5, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 28. PERMIT_TYPE (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('PERMIT_TYPE', 'TEMP_REGISTRATION_VOUCHER', '临时居住登记凭证', 1, 'ENABLED', NULL),
('PERMIT_TYPE', 'RESIDENCE_PERMIT', '居住证', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 29. CERT_STATUS (4 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('CERT_STATUS', 'VALID', '有效', 1, 'ENABLED', NULL),
('CERT_STATUS', 'EXPIRING', '即将到期', 2, 'ENABLED', NULL),
('CERT_STATUS', 'EXPIRED', '已过期', 3, 'ENABLED', NULL),
('CERT_STATUS', 'CANCELLED', '已注销或停用', 4, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 30. KEY_LEVEL (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('KEY_LEVEL', 'NORMAL', '一般', 1, 'ENABLED', NULL),
('KEY_LEVEL', 'IMPORTANT', '重点', 2, 'ENABLED', NULL),
('KEY_LEVEL', 'HIGH', '高关注', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 31. KEY_STATUS (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('KEY_STATUS', 'ACTIVE', '有效', 1, 'ENABLED', NULL),
('KEY_STATUS', 'RELEASED', '已解除', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 32. KEY_TYPE (3 items) - 正式项目需由有权限的业务方配置真实分类
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('KEY_TYPE', 'K01', '重点类别一', 1, 'ENABLED', '正式项目需配置真实分类并控制可见范围'),
('KEY_TYPE', 'K02', '重点类别二', 2, 'ENABLED', '正式项目需配置真实分类并控制可见范围'),
('KEY_TYPE', 'OTHER', '其他', 3, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 33. CERT_TYPE (5 items) - 不包含居住证
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('CERT_TYPE', 'ID_CARD', '居民身份证', 1, 'ENABLED', NULL),
('CERT_TYPE', 'PASSPORT', '护照', 2, 'ENABLED', NULL),
('CERT_TYPE', 'HOUSEHOLD_BOOKLET', '户口簿', 3, 'ENABLED', NULL),
('CERT_TYPE', 'BIRTH_CERT', '出生医学证明', 4, 'ENABLED', NULL),
('CERT_TYPE', 'OTHER', '其他证件', 5, 'ENABLED', '不包含居住证')
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 34. CANCEL_OBJECT_TYPE (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('CANCEL_OBJECT_TYPE', 'PERSON', '人口', 1, 'ENABLED', NULL),
('CANCEL_OBJECT_TYPE', 'HOUSEHOLD', '家庭户', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 35. CANCEL_REASON (5 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('CANCEL_REASON', 'DEATH', '死亡', 1, 'ENABLED', NULL),
('CANCEL_REASON', 'SETTLE_ABROAD', '出国定居', 2, 'ENABLED', NULL),
('CANCEL_REASON', 'DUPLICATE_REGISTRATION', '重复登记', 3, 'ENABLED', NULL),
('CANCEL_REASON', 'HOUSEHOLD_EMPTY', '空户销户', 4, 'ENABLED', NULL),
('CANCEL_REASON', 'OTHER', '其他', 5, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 36. OPERATION_TYPE (7 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('OPERATION_TYPE', 'LOGIN', '登录', 1, 'ENABLED', NULL),
('OPERATION_TYPE', 'QUERY', '查询', 2, 'ENABLED', NULL),
('OPERATION_TYPE', 'CREATE', '新增', 3, 'ENABLED', NULL),
('OPERATION_TYPE', 'UPDATE', '修改', 4, 'ENABLED', NULL),
('OPERATION_TYPE', 'ARCHIVE', '归档', 5, 'ENABLED', NULL),
('OPERATION_TYPE', 'APPROVE', '审批', 6, 'ENABLED', NULL),
('OPERATION_TYPE', 'EXPORT', '导出', 7, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 37. OPERATION_RESULT (2 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('OPERATION_RESULT', 'SUCCESS', '成功', 1, 'ENABLED', NULL),
('OPERATION_RESULT', 'FAIL', '失败', 2, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 38. EXPORT_TYPE (4 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('EXPORT_TYPE', 'PERSON_LIST', '人口列表', 1, 'ENABLED', NULL),
('EXPORT_TYPE', 'HOUSEHOLD_LIST', '户口列表', 2, 'ENABLED', NULL),
('EXPORT_TYPE', 'MIGRATION_REPORT', '迁移报表', 3, 'ENABLED', NULL),
('EXPORT_TYPE', 'STATISTICS', '统计报表', 4, 'ENABLED', NULL)
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- =====================================================
-- 39. SENSITIVE_LEVEL (3 items)
-- =====================================================
INSERT INTO data_dictionary (dict_type, dict_code, dict_label, sort_no, status, remark) VALUES
('SENSITIVE_LEVEL', 'L1', '一般', 1, 'ENABLED', NULL),
('SENSITIVE_LEVEL', 'L2', '敏感', 2, 'ENABLED', NULL),
('SENSITIVE_LEVEL', 'L3', '高敏', 3, 'ENABLED', '高敏导出必须关联审批')
ON DUPLICATE KEY UPDATE dict_label = VALUES(dict_label);

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Verify data count
SELECT
    dict_type,
    COUNT(*) as item_count
FROM data_dictionary
WHERE status = 'ENABLED'
GROUP BY dict_type
ORDER BY dict_type;

-- Total summary
SELECT CONCAT('Total: ', COUNT(DISTINCT dict_type), ' types, ', COUNT(*), ' items') AS summary
FROM data_dictionary;
