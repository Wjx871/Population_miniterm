-- Every result must be zero. Run after initialization or upgrade.
SELECT 'orphan_residence_person' check_name,COUNT(*) abnormal_count FROM residence r LEFT JOIN person p ON p.person_id=r.person_id WHERE p.person_id IS NULL
UNION ALL SELECT 'orphan_member_person',COUNT(*) FROM household_member m LEFT JOIN person p ON p.person_id=m.person_id WHERE p.person_id IS NULL
UNION ALL SELECT 'orphan_member_household',COUNT(*) FROM household_member m LEFT JOIN household h ON h.household_id=m.household_id WHERE h.household_id IS NULL
UNION ALL SELECT 'multiple_active_residence',COUNT(*) FROM (SELECT person_id FROM residence WHERE status='ACTIVE' GROUP BY person_id HAVING COUNT(*)>1) x
UNION ALL SELECT 'multiple_active_membership',COUNT(*) FROM (SELECT person_id FROM household_member WHERE status='ACTIVE' GROUP BY person_id HAVING COUNT(*)>1) x
UNION ALL SELECT 'invalid_household_head',COUNT(*) FROM household h LEFT JOIN household_member m ON m.household_id=h.household_id AND m.person_id=h.head_person_id AND m.status='ACTIVE' WHERE h.status='ACTIVE' AND (h.head_person_id IS NULL OR m.member_id IS NULL)
UNION ALL SELECT 'completed_migration_without_application',COUNT(*) FROM migration_in x LEFT JOIN business_application a ON a.application_id=x.application_id WHERE x.business_status='COMPLETED' AND a.status<>'COMPLETED'
UNION ALL SELECT 'completed_cancellation_without_application',COUNT(*) FROM cancellation_record x LEFT JOIN business_application a ON a.application_id=x.application_id WHERE x.business_status='COMPLETED' AND a.status<>'COMPLETED'
UNION ALL SELECT 'duplicate_active_key_population',COUNT(*) FROM (SELECT person_id,key_type FROM key_population WHERE status='ACTIVE' GROUP BY person_id,key_type HAVING COUNT(*)>1) x
UNION ALL SELECT 'approved_specialized_without_detail',COUNT(*) FROM business_application a WHERE a.status='APPROVED' AND ((a.business_type='MIGRATION_IN' AND NOT EXISTS(SELECT 1 FROM migration_in x WHERE x.application_id=a.application_id)) OR (a.business_type='MIGRATION_OUT' AND NOT EXISTS(SELECT 1 FROM migration_out x WHERE x.application_id=a.application_id)) OR (a.business_type IN('PERSON_CANCELLATION','HOUSEHOLD_CANCELLATION') AND NOT EXISTS(SELECT 1 FROM cancellation_record x WHERE x.application_id=a.application_id)))
UNION ALL SELECT 'required_table_missing',COUNT(*) FROM (
    SELECT 'person' object_name UNION ALL SELECT 'household' UNION ALL SELECT 'residence' UNION ALL SELECT 'business_application'
    UNION ALL SELECT 'sys_approval_request' UNION ALL SELECT 'cancellation_record' UNION ALL SELECT 'household_archive'
    UNION ALL SELECT 'floating_registration_application' UNION ALL SELECT 'residence_permit' UNION ALL SELECT 'data_export_request'
    UNION ALL SELECT 'data_export_log' UNION ALL SELECT 'admin_region' UNION ALL SELECT 'data_dictionary'
    UNION ALL SELECT 'key_population' UNION ALL SELECT 'key_population_application' UNION ALL SELECT 'key_population_history'
    UNION ALL SELECT 'operation_log' UNION ALL SELECT 'login_log'
) required LEFT JOIN information_schema.tables actual ON actual.table_schema=DATABASE() AND actual.table_name=required.object_name WHERE actual.table_name IS NULL
UNION ALL SELECT 'required_column_missing',COUNT(*) FROM (
    SELECT 'person' table_name,'current_status_code' column_name UNION ALL SELECT 'household','version'
    UNION ALL SELECT 'residence','status' UNION ALL SELECT 'business_application','status'
    UNION ALL SELECT 'key_population','active_flag' UNION ALL SELECT 'operation_log','operation_time'
) required LEFT JOIN information_schema.columns actual ON actual.table_schema=DATABASE() AND actual.table_name=required.table_name AND actual.column_name=required.column_name WHERE actual.column_name IS NULL
UNION ALL SELECT 'required_index_missing',COUNT(*) FROM (
    SELECT 'idx_household_member_person_status' object_name UNION ALL SELECT 'idx_household_member_household_status'
    UNION ALL SELECT 'idx_key_population_query' UNION ALL SELECT 'idx_person_query'
    UNION ALL SELECT 'idx_operation_log_query' UNION ALL SELECT 'idx_migration_in_query' UNION ALL SELECT 'idx_migration_out_query'
) required LEFT JOIN (SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema=DATABASE()) actual ON actual.index_name=required.object_name WHERE actual.index_name IS NULL
UNION ALL SELECT 'required_permission_missing',COUNT(*) FROM (
    SELECT 'application:view' object_name UNION ALL SELECT 'migration:view' UNION ALL SELECT 'cancellation:view'
    UNION ALL SELECT 'floating:view' UNION ALL SELECT 'residence-permit:view' UNION ALL SELECT 'data:export:normal'
    UNION ALL SELECT 'data:export:log:view' UNION ALL SELECT 'region:view' UNION ALL SELECT 'dictionary:view'
    UNION ALL SELECT 'certificate:view' UNION ALL SELECT 'key-population:view' UNION ALL SELECT 'log:view'
) required LEFT JOIN sys_permission actual ON actual.permission_code=required.object_name AND actual.status='ENABLED' WHERE actual.permission_id IS NULL
UNION ALL SELECT 'required_role_missing',COUNT(*) FROM (
    SELECT 'QUERY_VIEWER' object_name UNION ALL SELECT 'POPULATION_MANAGER' UNION ALL SELECT 'HOUSEHOLD_MANAGER'
    UNION ALL SELECT 'APPROVER' UNION ALL SELECT 'SYSTEM_ADMIN'
) required LEFT JOIN sys_role actual ON actual.role_code=required.object_name AND actual.status='ENABLED' WHERE actual.role_id IS NULL
UNION ALL SELECT 'required_dictionary_type_missing',COUNT(*) FROM (
    SELECT 'CERTIFICATE_TYPE' object_name UNION ALL SELECT 'ETHNICITY' UNION ALL SELECT 'HOUSEHOLD_RELATIONSHIP'
    UNION ALL SELECT 'HOUSEHOLD_TYPE' UNION ALL SELECT 'MIGRATION_REASON' UNION ALL SELECT 'CANCELLATION_REASON'
    UNION ALL SELECT 'FLOATING_RESIDENCE_REASON' UNION ALL SELECT 'KEY_POPULATION_TYPE'
) required LEFT JOIN (SELECT DISTINCT dict_type FROM data_dictionary) actual ON actual.dict_type=required.object_name WHERE actual.dict_type IS NULL;
