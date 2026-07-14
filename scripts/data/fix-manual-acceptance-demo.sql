-- 人工验收 DEMO 数据幂等修复：补齐 DEMO-PERMIT-001 的居住证专业申请链。
-- 仅匹配明确的 DEMO 申请、演示流动人口与同源最终居住证；不修改普通用户数据。
START TRANSACTION;

INSERT INTO residence_permit_application (
    application_id, apply_type, floating_id, permit_id, person_id,
    current_region_code, residence_basis_code, requested_valid_from,
    requested_valid_until, business_status, executed_permit_id,
    operator_id, executed_at, version
)
SELECT
    a.application_id, 'FIRST_ISSUE', f.floating_id, NULL, a.target_person_id,
    f.current_region_code, f.residence_reason_code, p.valid_from,
    p.valid_until, 'COMPLETED', p.permit_id,
    NULL, COALESCE(a.updated_at, a.created_at), 1
FROM business_application a
JOIN floating_population f
  ON f.person_id = a.target_person_id
 AND f.status = 'ACTIVE'
 AND f.current_flag = 1
JOIN residence_permit p
  ON p.source_application_id = a.application_id
 AND p.person_id = a.target_person_id
WHERE a.application_no = 'DEMO-PERMIT-001'
  AND a.business_type = 'RESIDENCE_PERMIT_FIRST_ISSUE'
  AND a.status = 'COMPLETED'
  AND NOT EXISTS (
      SELECT 1 FROM residence_permit_application rpa
      WHERE rpa.application_id = a.application_id
  );

COMMIT;

SELECT a.application_id, a.application_no, a.status,
       rpa.apply_type, rpa.business_status, rpa.executed_permit_id,
       p.permit_no, p.status AS permit_status
FROM business_application a
LEFT JOIN residence_permit_application rpa ON rpa.application_id = a.application_id
LEFT JOIN residence_permit p ON p.permit_id = rpa.executed_permit_id
WHERE a.application_no = 'DEMO-PERMIT-001';
