-- Phase 10 key population consistency checks; every abnormal_count must be 0.
SELECT 'duplicate_active' check_name,COUNT(*) abnormal_count FROM(SELECT person_id,key_type FROM key_population WHERE status='ACTIVE' GROUP BY person_id,key_type HAVING COUNT(*)>1)x
UNION ALL SELECT 'duplicate_open_register',COUNT(*) FROM(SELECT person_id,population_type FROM key_population_application WHERE operation_type='REGISTER' AND business_status IN('DRAFT','UNDER_REVIEW','APPROVED') GROUP BY person_id,population_type HAVING COUNT(*)>1)x
UNION ALL SELECT 'duplicate_open_release',COUNT(*) FROM(SELECT record_id FROM key_population_application WHERE operation_type='RELEASE' AND business_status IN('DRAFT','UNDER_REVIEW','APPROVED') GROUP BY record_id HAVING COUNT(*)>1)x
UNION ALL SELECT 'active_cancelled_person',COUNT(*) FROM key_population k JOIN person p ON p.person_id=k.person_id WHERE k.status='ACTIVE' AND p.current_status_code IN('CANCELLED','DECEASED')
UNION ALL SELECT 'released_without_date',COUNT(*) FROM key_population WHERE status='RELEASED' AND release_date IS NULL
UNION ALL SELECT 'duplicate_application_history',COUNT(*) FROM(SELECT source_application_id,event_type FROM key_population_history GROUP BY source_application_id,event_type HAVING COUNT(*)>1)x
UNION ALL SELECT 'rejected_created_record',COUNT(*) FROM key_population k JOIN business_application a ON a.application_id=k.source_application_id WHERE a.status='REJECTED'
UNION ALL SELECT 'unexecuted_created_record',COUNT(*) FROM key_population k JOIN business_application a ON a.application_id=k.source_application_id WHERE a.status<>'COMPLETED'
UNION ALL SELECT 'log_identity_leak',COUNT(*) FROM operation_log WHERE detail REGEXP '[0-9]{17}[0-9X]'
UNION ALL SELECT 'history_identity_leak',COUNT(*) FROM key_population_history WHERE snapshot_json REGEXP '[0-9]{17}[0-9X]';
