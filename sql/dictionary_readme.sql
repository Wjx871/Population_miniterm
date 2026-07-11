-- =====================================================
-- 字典维护说明（执行顺序：在 init.sql 之后执行；本身不修改数据）
-- 说明：
--   1) 本库字典以 (dict_type, dict_code) 为唯一键，由 init.sql 初始化；
--   2) 任何业务代码引用字典值必须用 code 而非 label，避免翻译/页面调整导致脏数据；
--   3) 严禁直接 UPDATE dict_label，要改走 SQL 脚本，便于审计与回滚；
--   4) 字典变更应同步更新 docs/db/数据字典_v4.0.md；
--   5) status 默认 ENABLED，停用项字典值为 DISABLED，查询接口默认仅返回 ENABLED。
-- =====================================================

USE population_miniterm;

-- 字典项总数与分类统计（幂等可重复运行）
SELECT
    CONCAT('Total: ', COUNT(DISTINCT dict_type), ' types, ', COUNT(*), ' items') AS summary
FROM data_dictionary;

SELECT dict_type, COUNT(*) AS item_count
FROM data_dictionary
WHERE status = 'ENABLED'
GROUP BY dict_type
ORDER BY dict_type;