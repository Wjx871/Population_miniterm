-- V4_014 rollback. Stop application writers and export/back up the three scope columns first.
-- Dropping these snapshots removes initial visibility for people without residence/application history.
USE population_miniterm;

ALTER TABLE person
    DROP INDEX idx_person_initial_scope_user,
    DROP INDEX idx_person_initial_scope_department,
    DROP INDEX idx_person_initial_scope_region,
    DROP COLUMN initial_scope_user_id,
    DROP COLUMN initial_scope_department_id,
    DROP COLUMN initial_scope_region_code;
