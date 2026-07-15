-- Phase 13 dashboard scope: grant QUERY_VIEWER the ALL data scope.
-- The viewer role is a read-only statistician; binding it to DEPARTMENT
-- would zero out every KPI on the dashboard because person / residence /
-- residence_permit / floating_population are master data with no
-- applicant_department_id. ALL keeps the dashboard usable while the
-- write APIs still rely on the QUERY_VIEWER permission set for
-- authorization. Safe to execute repeatedly on MySQL 8.
USE population_miniterm;

UPDATE sys_role
SET data_scope = 'ALL',
    description = '只读查询与统计：工作台与统计接口应见全量数据，写接口受权限控制'
WHERE role_code = 'QUERY_VIEWER';
