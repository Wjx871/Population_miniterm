# Phase 11 综合查询与统计 API

规范查询入口为 `GET /api/query/persons`、`/households` 和 `/migration-history`，均要求相应查看权限，分页 `size` 最大 100。人口和家庭户输出默认脱敏，数据范围条件直接进入 MyBatis SQL。

统计入口统一为 `/api/statistics`：`overview`、`population-trend`、`region-distribution`、`household-distribution`、`migration-trend`、`floating-population`、`certificate-expiry`、`key-population`。旧 `/api/dashboard` 暂保留兼容，但复用同一 `DashboardService/Mapper` 口径。
