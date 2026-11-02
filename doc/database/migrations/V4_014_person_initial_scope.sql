-- V4_014: persist trusted initial person ownership for records created without a residence.
-- Execute once against an existing population_miniterm database after a consistent backup.
-- NULL is intentional for legacy people; their access continues to follow residence/application scope.
USE population_miniterm;

ALTER TABLE person
    ADD COLUMN initial_scope_user_id BIGINT NULL COMMENT '初始人员记录归属用户快照',
    ADD COLUMN initial_scope_department_id BIGINT NULL COMMENT '初始人员记录归属部门快照',
    ADD COLUMN initial_scope_region_code VARCHAR(20) NULL COMMENT '初始人员记录归属区划快照',
    ADD KEY idx_person_initial_scope_user (initial_scope_user_id),
    ADD KEY idx_person_initial_scope_department (initial_scope_department_id),
    ADD KEY idx_person_initial_scope_region (initial_scope_region_code);
