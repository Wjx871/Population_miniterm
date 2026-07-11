-- =====================================================
-- Database Views Definition v4.0
-- Based on: 数据库设计v4.0_Cursor详细说明.md Section 9
-- =====================================================

USE population_miniterm;

-- =====================================================
-- View 1: v_person_overview - Person Comprehensive Overview
-- Usage: Person list and detail pages
-- Security: Returns masked identity numbers by default
-- =====================================================
DROP VIEW IF EXISTS v_person_overview;
CREATE VIEW v_person_overview AS
SELECT
    p.person_id,
    p.name,
    p.gender_code,
    CASE p.gender_code
        WHEN 'MALE' THEN '男'
        WHEN 'FEMALE' THEN '女'
        ELSE '未知'
    END AS gender_name,
    p.identity_type_code,
    p.identity_no,
    CONCAT(LEFT(p.identity_no, 4), '********', RIGHT(p.identity_no, 4)) AS identity_no_masked,
    p.birth_date,
    p.ethnicity_code,
    p.phone,
    CONCAT(LEFT(COALESCE(p.phone, ''), 3), '****', RIGHT(COALESCE(p.phone, ''), 4)) AS phone_masked,
    p.contact_address,
    p.record_status_code,
    CASE p.record_status_code
        WHEN 'ACTIVE' THEN '有效'
        WHEN 'CANCELLED' THEN '已注销'
        ELSE p.record_status_code
    END AS record_status_name,
    r.registration_id,
    r.household_id,
    h.household_no,
    h.registered_address,
    h.region_code,
    a_region.region_name AS region_name,
    CASE
        WHEN EXISTS (SELECT 1 FROM floating_population fp WHERE fp.person_id = p.person_id AND fp.status = 'ACTIVE') THEN 1
        ELSE 0
    END AS is_floating,
    CASE
        WHEN EXISTS (SELECT 1 FROM key_population kp WHERE kp.person_id = p.person_id AND kp.status = 'ACTIVE') THEN 1
        ELSE 0
    END AS is_key_population,
    p.created_at,
    p.updated_at
FROM person p
LEFT JOIN residence_registration r ON p.person_id = r.person_id
LEFT JOIN household h ON r.household_id = h.household_id
LEFT JOIN admin_region a_region ON h.region_code = a_region.region_code;

-- =====================================================
-- View 2: v_pending_approval - Pending Approval Task View
-- Usage: Approval workflow platform
-- Security: Filter by current approver, department and status
-- =====================================================
DROP VIEW IF EXISTS v_pending_approval;
CREATE VIEW v_pending_approval AS
SELECT
    ar.approval_id,
    ar.approval_no,
    ar.application_id,
    ba.application_no,
    ba.business_type_code,
    CASE ba.business_type_code
        WHEN 'HOUSEHOLD_ESTABLISH' THEN '家庭户立户'
        WHEN 'PERSON_REGISTER' THEN '人口登记'
        WHEN 'MIGRATION_IN_CROSS_DISTRICT' THEN '同市跨区迁入'
        WHEN 'MIGRATION_IN_EXTERNAL' THEN '外来迁入'
        WHEN 'MIGRATION_OUT_CROSS_DISTRICT' THEN '同市跨区迁出'
        WHEN 'MIGRATION_OUT_EXTERNAL' THEN '迁往市外'
        WHEN 'FLOATING_REGISTER' THEN '流动人口登记'
        WHEN 'RESIDENCE_PERMIT_APPLY' THEN '居住凭证/居住证申领'
        WHEN 'KEY_REGISTER' THEN '重点人口登记'
        WHEN 'KEY_RELEASE' THEN '解除重点管理'
        WHEN 'PERSON_CANCEL' THEN '人口注销'
        WHEN 'HOUSEHOLD_CANCEL' THEN '家庭户销户'
        WHEN 'SENSITIVE_EXPORT' THEN '敏感数据导出'
        ELSE ba.business_type_code
    END AS business_type_name,
    ba.applicant_name,
    ba.applicant_phone,
    ba.target_person_id,
    tp.name AS target_person_name,
    ba.target_household_id,
    bh.household_no AS target_household_no,
    ba.handling_department_id,
    sd.department_name AS handling_department_name,
    ba.submit_user_id,
    su.real_name AS submit_user_name,
    ar.required_level,
    ar.current_approver_id,
    cau.real_name AS current_approver_name,
    ar.status AS approval_status,
    CASE ar.status
        WHEN 'PENDING' THEN '待审批'
        WHEN 'APPROVED' THEN '已通过'
        WHEN 'REJECTED' THEN '已驳回'
        WHEN 'RETURNED' THEN '退回补充'
        WHEN 'CANCELLED' THEN '已撤销'
        ELSE ar.status
    END AS approval_status_name,
    ar.apply_reason,
    ar.submitted_at,
    ar.finished_at
FROM sys_approval_request ar
INNER JOIN business_application ba ON ar.application_id = ba.application_id
LEFT JOIN person tp ON ba.target_person_id = tp.person_id
LEFT JOIN household bh ON ba.target_household_id = bh.household_id
LEFT JOIN sys_department sd ON ba.handling_department_id = sd.department_id
LEFT JOIN sys_user su ON ba.submit_user_id = su.user_id
LEFT JOIN sys_user cau ON ar.current_approver_id = cau.user_id;

-- =====================================================
-- View 3: v_expiring_credentials - Credentials Expiring Reminder View
-- Usage: Credential expiration alerts
-- Security: Filter for records expiring within 30 days
-- =====================================================
DROP VIEW IF EXISTS v_expiring_credentials;
CREATE VIEW v_expiring_credentials AS
SELECT
    credential_type,
    credential_id,
    person_id,
    person_name,
    credential_type_code,
    credential_type_name,
    credential_no,
    permit_no,
    issue_authority,
    issue_date,
    valid_from,
    valid_until,
    credential_status,
    credential_status_name,
    days_until_expiry,
    CASE
        WHEN days_until_expiry < 0 THEN '已过期'
        WHEN days_until_expiry <= 30 THEN '即将到期'
        ELSE '有效'
    END AS expiry_warning
FROM (
    -- Certificate records
    SELECT
        'CERTIFICATE' AS credential_type,
        c.certificate_id AS credential_id,
        c.person_id,
        p.name AS person_name,
        c.certificate_type_code,
        CASE c.certificate_type_code
            WHEN 'ID_CARD' THEN '居民身份证'
            WHEN 'PASSPORT' THEN '护照'
            WHEN 'HOUSEHOLD_BOOKLET' THEN '户口簿'
            WHEN 'BIRTH_CERT' THEN '出生医学证明'
            ELSE '其他证件'
        END AS credential_type_name,
        c.certificate_no,
        NULL AS permit_no,
        c.issue_authority,
        c.issue_date,
        c.valid_from,
        c.valid_until,
        c.certificate_status AS credential_status,
        CASE c.certificate_status
            WHEN 'VALID' THEN '有效'
            WHEN 'EXPIRING' THEN '即将到期'
            WHEN 'EXPIRED' THEN '已过期'
            WHEN 'CANCELLED' THEN '已注销'
            ELSE c.certificate_status
        END AS credential_status_name,
        DATEDIFF(c.valid_until, CURDATE()) AS days_until_expiry
    FROM certificate c
    INNER JOIN person p ON c.person_id = p.person_id
    WHERE c.valid_until IS NOT NULL
    
    UNION ALL
    
    -- Residence permit records
    SELECT
        'RESIDENCE_PERMIT' AS credential_type,
        rp.permit_id AS credential_id,
        rp.person_id,
        p.name AS person_name,
        rp.permit_type_code,
        CASE rp.permit_type_code
            WHEN 'TEMP_REGISTRATION_VOUCHER' THEN '临时居住登记凭证'
            WHEN 'RESIDENCE_PERMIT' THEN '居住证'
            ELSE rp.permit_type_code
        END AS credential_type_name,
        NULL AS credential_no,
        rp.permit_no,
        rp.issue_authority,
        rp.issue_date,
        rp.valid_from,
        rp.valid_until,
        rp.permit_status AS credential_status,
        CASE rp.permit_status
            WHEN 'VALID' THEN '有效'
            WHEN 'EXPIRING' THEN '即将到期'
            WHEN 'EXPIRED' THEN '已过期'
            WHEN 'CANCELLED' THEN '已注销'
            ELSE rp.permit_status
        END AS credential_status_name,
        DATEDIFF(rp.valid_until, CURDATE()) AS days_until_expiry
    FROM residence_permit rp
    INNER JOIN person p ON rp.person_id = p.person_id
    WHERE rp.valid_until IS NOT NULL
) AS combined_credentials
WHERE days_until_expiry <= 30
ORDER BY days_until_expiry;

-- =====================================================
-- View 4: v_region_population_summary - Region Population Statistics View
-- Usage: Statistics dashboard
-- Security: Only outputs aggregated counts, prevents detail leakage
-- =====================================================
DROP VIEW IF EXISTS v_region_population_summary;
CREATE VIEW v_region_population_summary AS
SELECT
    ar.region_code,
    ar.region_name,
    ar.region_level_code,
    ar.parent_code,
    COUNT(DISTINCT r.registration_id) AS registered_population_count,
    COUNT(DISTINCT fp.floating_id) AS floating_population_count,
    COUNT(DISTINCT kp.key_id) AS key_population_count,
    COUNT(DISTINCT h.household_id) AS household_count
FROM admin_region ar
LEFT JOIN household h ON ar.region_code = h.region_code AND h.status = 'ACTIVE'
LEFT JOIN residence_registration r ON ar.region_code = r.region_code
LEFT JOIN floating_population fp ON ar.region_code = fp.current_region_code AND fp.status = 'ACTIVE'
LEFT JOIN key_population kp ON ar.region_code IN (
    SELECT region_code FROM admin_region WHERE region_code = ar.region_code
) AND kp.status = 'ACTIVE'
GROUP BY ar.region_code, ar.region_name, ar.region_level_code, ar.parent_code;

-- =====================================================
-- Additional Useful Views
-- =====================================================

-- View: v_household_member_detail - Household member detail view
DROP VIEW IF EXISTS v_household_member_detail;
CREATE VIEW v_household_member_detail AS
SELECT
    hm.member_id,
    hm.household_id,
    h.household_no,
    h.registered_address,
    h.region_code,
    ar.region_name,
    hm.person_id,
    p.name AS person_name,
    p.identity_no,
    CONCAT(LEFT(p.identity_no, 4), '********', RIGHT(p.identity_no, 4)) AS identity_no_masked,
    p.gender_code,
    CASE p.gender_code WHEN 'MALE' THEN '男' WHEN 'FEMALE' THEN '女' ELSE '未知' END AS gender_name,
    p.birth_date,
    p.phone,
    hm.relationship_code,
    CASE hm.relationship_code
        WHEN 'HEAD' THEN '户主'
        WHEN 'SPOUSE' THEN '配偶'
        WHEN 'CHILD' THEN '子女'
        WHEN 'PARENT' THEN '父母'
        ELSE '其他'
    END AS relationship_name,
    hm.join_date,
    hm.leave_date,
    hm.member_status,
    CASE hm.member_status
        WHEN 'CURRENT' THEN '当前成员'
        WHEN 'LEFT' THEN '已迁出'
        WHEN 'CANCELLED' THEN '已注销'
        ELSE hm.member_status
    END AS member_status_name
FROM household_member hm
INNER JOIN household h ON hm.household_id = h.household_id
INNER JOIN person p ON hm.person_id = p.person_id
LEFT JOIN admin_region ar ON h.region_code = ar.region_code;

-- View: v_migration_history - Migration history view
DROP VIEW IF EXISTS v_migration_history;
CREATE VIEW v_migration_history AS
SELECT
    'IN' AS migration_direction,
    mi.in_id AS migration_id,
    mi.application_id,
    ba.application_no,
    mi.person_id,
    p.name AS person_name,
    mi.in_type_code,
    CASE mi.in_type_code
        WHEN 'CROSS_DISTRICT' THEN '同市跨区迁入'
        WHEN 'EXTERNAL' THEN '外来迁入'
        ELSE mi.in_type_code
    END AS in_type_name,
    mi.from_region_code,
    fr.region_name AS from_region_name,
    mi.from_address,
    mi.to_region_code,
    tr.region_name AS to_region_name,
    mi.in_date,
    mi.reason_code,
    mi.completed_at,
    su.real_name AS operator_name
FROM migration_in mi
INNER JOIN business_application ba ON mi.application_id = ba.application_id
INNER JOIN person p ON mi.person_id = p.person_id
LEFT JOIN admin_region fr ON mi.from_region_code = fr.region_code
LEFT JOIN admin_region tr ON mi.to_region_code = tr.region_code
LEFT JOIN sys_user su ON mi.operator_id = su.user_id

UNION ALL

SELECT
    'OUT' AS migration_direction,
    mo.out_id AS migration_id,
    mo.application_id,
    ba.application_no,
    mo.person_id,
    p.name AS person_name,
    mo.out_type_code,
    CASE mo.out_type_code
        WHEN 'CROSS_DISTRICT' THEN '同市跨区迁出'
        WHEN 'EXTERNAL' THEN '迁往市外'
        ELSE mo.out_type_code
    END AS out_type_name,
    mo.from_region_code,
    fr.region_name AS from_region_name,
    mo.to_address,
    mo.to_region_code,
    tr.region_name AS to_region_name,
    mo.out_date,
    mo.reason_code,
    mo.completed_at,
    su.real_name AS operator_name
FROM migration_out mo
INNER JOIN business_application ba ON mo.application_id = ba.application_id
INNER JOIN person p ON mo.person_id = p.person_id
LEFT JOIN admin_region fr ON mo.from_region_code = fr.region_code
LEFT JOIN admin_region tr ON mo.to_region_code = tr.region_code
LEFT JOIN sys_user su ON mo.operator_id = su.user_id;

-- Print view creation summary
SELECT 'Views created successfully!' AS Result;

-- =====================================================
-- View 5: v_household_population_overview
-- Usage: 户籍档案列表页（户号、户主姓名、人口数、区划名、地址）
-- =====================================================
DROP VIEW IF EXISTS v_household_population_overview;
CREATE VIEW v_household_population_overview AS
SELECT
    h.household_id,
    h.household_no,
    h.household_type_code,
    CASE h.household_type_code WHEN 'FAMILY' THEN '家庭户' WHEN 'COLLECTIVE' THEN '集体户' ELSE h.household_type_code END AS household_type_name,
    h.head_person_id,
    hp.name AS head_person_name,
    h.registered_address,
    h.region_code,
    ar.region_name,
    ar.region_level_code,
    h.department_id,
    sd.department_name,
    h.establish_date,
    h.status,
    CASE h.status WHEN 'ACTIVE' THEN '正常' WHEN 'CANCELLED' THEN '已销户' ELSE h.status END AS status_name,
    h.created_at,
    h.updated_at,
    (SELECT COUNT(*) FROM household_member hm
        WHERE hm.household_id = h.household_id AND hm.member_status = 'CURRENT') AS current_member_count
FROM household h
LEFT JOIN person hp ON h.head_person_id = hp.person_id
LEFT JOIN admin_region ar ON h.region_code = ar.region_code
LEFT JOIN sys_department sd ON h.department_id = sd.department_id;

-- =====================================================
-- View 6: v_migration_batch_detail
-- Usage: 按 transfer_batch_no 聚合 in/out，展示"同市跨区联办"是否完成
-- =====================================================
DROP VIEW IF EXISTS v_migration_batch_detail;
CREATE VIEW v_migration_batch_detail AS
SELECT
    t.transfer_batch_no,
    t.in_count,
    t.out_count,
    CASE WHEN t.in_count > 0 AND t.out_count > 0 THEN 'BOTH'
         WHEN t.in_count > 0 THEN 'IN_ONLY'
         WHEN t.out_count > 0 THEN 'OUT_ONLY'
         ELSE 'NONE' END AS batch_status,
    CASE WHEN t.in_count > 0 AND t.out_count > 0 THEN '同市跨区联办'
         WHEN t.in_count > 0 THEN '仅迁入'
         WHEN t.out_count > 0 THEN '仅迁出'
         ELSE '空批次' END AS batch_status_name,
    t.first_in_date,
    t.last_out_date
FROM (
    SELECT
        COALESCE(mi.transfer_batch_no, mo.transfer_batch_no) AS transfer_batch_no,
        COUNT(DISTINCT mi.in_id) AS in_count,
        COUNT(DISTINCT mo.out_id) AS out_count,
        MIN(mi.in_date) AS first_in_date,
        MAX(mo.out_date) AS last_out_date
    FROM migration_in mi
    LEFT JOIN migration_out mo ON mi.transfer_batch_no = mo.transfer_batch_no
                                 AND mi.person_id = mo.person_id
    WHERE mi.transfer_batch_no IS NOT NULL OR mo.transfer_batch_no IS NOT NULL
    GROUP BY COALESCE(mi.transfer_batch_no, mo.transfer_batch_no)
) t
WHERE t.transfer_batch_no IS NOT NULL;

-- =====================================================
-- View 7: v_cancellation_detail
-- Usage: 注销详情列表
-- =====================================================
DROP VIEW IF EXISTS v_cancellation_detail;
CREATE VIEW v_cancellation_detail AS
SELECT
    cr.cancel_id,
    cr.cancellation_no,
    cr.application_id,
    cr.cancel_object_type,
    CASE cr.cancel_object_type WHEN 'PERSON' THEN '人口' WHEN 'HOUSEHOLD' THEN '家庭户' ELSE cr.cancel_object_type END AS cancel_object_type_name,
    cr.person_id,
    p.name AS person_name,
    p.identity_no,
    CONCAT(LEFT(p.identity_no, 4), '********', RIGHT(p.identity_no, 4)) AS identity_no_masked,
    cr.household_id,
    h.household_no,
    cr.cancel_reason_code,
    CASE cr.cancel_reason_code
        WHEN 'DEATH' THEN '死亡'
        WHEN 'SETTLE_ABROAD' THEN '出国定居'
        WHEN 'DUPLICATE_REGISTRATION' THEN '重复登记'
        WHEN 'HOUSEHOLD_EMPTY' THEN '空户销户'
        WHEN 'OTHER' THEN '其他'
        ELSE cr.cancel_reason_code END AS cancel_reason_name,
    cr.cancel_date,
    cr.archive_id,
    cr.operator_id,
    op.real_name AS operator_name,
    cr.completed_at
FROM cancellation_record cr
LEFT JOIN person p ON cr.person_id = p.person_id
LEFT JOIN household h ON cr.household_id = h.household_id
LEFT JOIN sys_user op ON cr.operator_id = op.user_id;

-- =====================================================
-- View 8: v_person_residence_history
-- Usage: 人口户籍时间线：registration + archive UNION
-- =====================================================
DROP VIEW IF EXISTS v_person_residence_history;
CREATE VIEW v_person_residence_history AS
SELECT
    'CURRENT' AS record_kind,
    p.person_id,
    p.name AS person_name,
    rr.registration_id AS record_id,
    NULL AS archive_id,
    rr.household_id,
    hh.household_no,
    rr.register_type_code,
    CASE rr.register_type_code WHEN 'INITIAL' THEN '初始登记' WHEN 'BIRTH' THEN '出生登记' WHEN 'MIGRATION_IN' THEN '迁入登记' WHEN 'RESTORE' THEN '恢复登记' ELSE rr.register_type_code END AS register_type_name,
    rr.register_date AS start_date,
    NULL AS end_date,
    rr.registered_address,
    rr.region_code,
    ar.region_name,
    NULL AS archive_reason_code,
    NULL AS archive_reason_name,
    rr.source_application_id,
    rr.created_at
FROM residence_registration rr
INNER JOIN person p ON rr.person_id = p.person_id
LEFT JOIN household hh ON rr.household_id = hh.household_id
LEFT JOIN admin_region ar ON rr.region_code = ar.region_code
UNION ALL
SELECT
    'ARCHIVE' AS record_kind,
    p.person_id,
    p.name AS person_name,
    NULL AS record_id,
    ra.archive_id,
    ra.household_id,
    ra.household_no_snapshot AS household_no,
    ra.register_type_snapshot AS register_type_code,
    CASE ra.register_type_snapshot WHEN 'INITIAL' THEN '初始登记' WHEN 'BIRTH' THEN '出生登记' WHEN 'MIGRATION_IN' THEN '迁入登记' WHEN 'RESTORE' THEN '恢复登记' ELSE ra.register_type_snapshot END AS register_type_name,
    ra.start_date_snapshot AS start_date,
    ra.end_date_snapshot AS end_date,
    ra.registered_address_snapshot AS registered_address,
    ra.region_code_snapshot AS region_code,
    ar.region_name,
    ra.archive_reason_code,
    CASE ra.archive_type_code
        WHEN 'MIGRATION_OUT' THEN '迁出归档'
        WHEN 'PERSON_CANCEL' THEN '人口注销归档'
        WHEN 'HOUSEHOLD_CANCEL' THEN '家庭户销户归档'
        ELSE ra.archive_type_code END AS archive_reason_name,
    ra.source_application_id,
    ra.created_at
FROM residence_archive ra
INNER JOIN person p ON ra.person_id = p.person_id
LEFT JOIN admin_region ar ON ra.region_code_snapshot = ar.region_code;

SELECT 'All views (8) created successfully!' AS Result;
