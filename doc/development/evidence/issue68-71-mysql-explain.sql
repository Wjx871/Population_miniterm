USE population_miniterm_mysql_gate_issue6871_71879d0412;
SELECT '=== SHOW INDEX residence ===' AS section;
SHOW INDEX FROM residence;
SELECT '=== SHOW INDEX floating_population ===' AS section;
SHOW INDEX FROM floating_population;
SELECT '=== SHOW INDEX residence_permit ===' AS section;
SHOW INDEX FROM residence_permit;
SELECT '=== SHOW INDEX migration_in ===' AS section;
SHOW INDEX FROM migration_in;
SELECT '=== SHOW INDEX migration_out ===' AS section;
SHOW INDEX FROM migration_out;
SELECT '=== PHASE11 LIST EXPLAIN ===' AS section;
EXPLAIN FORMAT=JSON
SELECT p.person_id,p.name,p.gender,p.birth_date,p.id_card masked_identity_no,p.phone masked_phone,p.current_status_code person_status,h.household_id,h.household_no,hp.name head_person_name,hm.relationship,(h.head_person_id=p.person_id) household_head,r.region_code current_region_code,r.registered_address current_address,r.status residence_status,f.floating_id,f.status floating_status,f.arrival_date,rp.permit_id,rp.permit_no masked_permit_no,rp.status permit_status,rp.valid_until permit_valid_until,CASE WHEN latest_in.last_in_date IS NULL AND latest_out.last_out_date IS NULL THEN NULL WHEN latest_out.last_out_date IS NULL THEN latest_in.last_in_date WHEN latest_in.last_in_date IS NULL THEN latest_out.last_out_date WHEN latest_out.last_out_date>=latest_in.last_in_date THEN latest_out.last_out_date ELSE latest_in.last_in_date END last_migration_date,CASE WHEN latest_in.last_in_date IS NULL AND latest_out.last_out_date IS NULL THEN NULL WHEN latest_out.last_out_date IS NULL THEN 'IN' WHEN latest_in.last_in_date IS NULL THEN 'OUT' WHEN latest_out.last_out_date>=latest_in.last_in_date THEN 'OUT' ELSE 'IN' END last_migration_direction
FROM person p LEFT JOIN residence r ON r.person_id=p.person_id AND r.status='ACTIVE' LEFT JOIN household h ON h.household_id=r.household_id LEFT JOIN person hp ON hp.person_id=h.head_person_id LEFT JOIN household_member hm ON hm.household_id=h.household_id AND hm.person_id=p.person_id AND hm.status='ACTIVE' LEFT JOIN floating_population f ON f.person_id=p.person_id AND f.status='ACTIVE' AND f.current_flag=1 LEFT JOIN residence_permit rp ON rp.person_id=p.person_id AND rp.status='ACTIVE' AND rp.current_flag=1 LEFT JOIN (SELECT person_id,MAX(in_date) last_in_date FROM migration_in WHERE business_status='COMPLETED' GROUP BY person_id) latest_in ON latest_in.person_id=p.person_id LEFT JOIN (SELECT person_id,MAX(out_date) last_out_date FROM migration_out WHERE business_status='COMPLETED' GROUP BY person_id) latest_out ON latest_out.person_id=p.person_id
WHERE p.name LIKE '%Q6871%' AND (EXISTS(SELECT 1 FROM residence rs WHERE rs.person_id=p.person_id AND rs.status='ACTIVE' AND rs.region_code IN ('980000','980100','980101')) OR EXISTS(SELECT 1 FROM floating_population fs WHERE fs.person_id=p.person_id AND fs.status='ACTIVE' AND fs.current_flag=1 AND fs.current_region_code IN ('980000','980100','980101')))
GROUP BY p.person_id,p.name,p.gender,p.birth_date,p.id_card,p.phone,p.current_status_code,h.household_id,h.household_no,h.head_person_id,hp.name,hm.relationship,r.region_code,r.registered_address,r.status,f.floating_id,f.status,f.arrival_date,rp.permit_id,rp.permit_no,rp.status,rp.valid_until,latest_in.last_in_date,latest_out.last_out_date
ORDER BY p.person_id DESC LIMIT 10 OFFSET 0;
SELECT '=== PHASE11 COUNT EXPLAIN ===' AS section;
EXPLAIN FORMAT=JSON
SELECT COUNT(DISTINCT p.person_id)
FROM person p LEFT JOIN residence r ON r.person_id=p.person_id AND r.status='ACTIVE' LEFT JOIN household h ON h.household_id=r.household_id LEFT JOIN person hp ON hp.person_id=h.head_person_id LEFT JOIN household_member hm ON hm.household_id=h.household_id AND hm.person_id=p.person_id AND hm.status='ACTIVE' LEFT JOIN floating_population f ON f.person_id=p.person_id AND f.status='ACTIVE' AND f.current_flag=1 LEFT JOIN residence_permit rp ON rp.person_id=p.person_id AND rp.status='ACTIVE' AND rp.current_flag=1 LEFT JOIN (SELECT person_id,MAX(in_date) last_in_date FROM migration_in WHERE business_status='COMPLETED' GROUP BY person_id) latest_in ON latest_in.person_id=p.person_id LEFT JOIN (SELECT person_id,MAX(out_date) last_out_date FROM migration_out WHERE business_status='COMPLETED' GROUP BY person_id) latest_out ON latest_out.person_id=p.person_id
WHERE p.name LIKE '%Q6871%' AND (EXISTS(SELECT 1 FROM residence rs WHERE rs.person_id=p.person_id AND rs.status='ACTIVE' AND rs.region_code IN ('980000','980100','980101')) OR EXISTS(SELECT 1 FROM floating_population fs WHERE fs.person_id=p.person_id AND fs.status='ACTIVE' AND fs.current_flag=1 AND fs.current_region_code IN ('980000','980100','980101')));
SELECT '=== LATEST MIGRATION AGGREGATE EXPLAIN ===' AS section;
EXPLAIN FORMAT=JSON SELECT person_id,MAX(in_date) FROM migration_in WHERE business_status='COMPLETED' GROUP BY person_id;
EXPLAIN FORMAT=JSON SELECT person_id,MAX(out_date) FROM migration_out WHERE business_status='COMPLETED' GROUP BY person_id;
SELECT '=== ACTUAL COUNTS ===' AS section;
SELECT COUNT(*) AS person_count FROM person;
SELECT COUNT(*) AS query_rows FROM person p WHERE p.name LIKE '%Q6871%';