# Phase 11 综合查询与统计 API

规范查询入口为 `GET /api/query/persons`、`/households` 和 `/migration-history`，均要求相应查看权限，分页 `size` 最大 100。人口和家庭户输出默认脱敏，数据范围条件直接进入 MyBatis SQL。

## 两套人口查询接口

| 接口 | 用途 | 数据范围 | 结果字段 |
|---|---|---|---|
| `GET /api/query/persons` | Phase 11 综合人员列表（前端列表使用） | `SELF`/`DEPARTMENT`/`REGION`/`ALL`；REGION 为当前 ACTIVE 户籍或当前有效流动登记 | 人口摘要及当前流动登记、当前居住证、最近完成迁移字段 |
| `GET /api/queries/persons` | 既有综合查询列表（兼容入口） | 同上；本轮移除历史 `business_application` 与任意状态 `residence` 的 REGION 兜底 | 人口摘要及相同的当前关联字段 |
| `GET /api/queries/persons/{personId}` | 综合档案详情（前端档案抽屉使用） | 与列表相同的 `personScope` 门禁 | 当前户籍、家庭户、当前流动登记、当前居住证、迁移历史 |

`/api/query/persons` 与 `/api/queries/persons` 当前共享同一 REGION 授权规则，但筛选参数、排序字段和详情入口不同；前端列表读取前者，详情读取后者。

## 人口综合列表字段与过滤语义（自 2026-11-08 起）

`GET /api/query/persons` 在原有字段基础上新增（均为只读、可为 `null`）：

- `floatingId` / `floatingStatus` / `arrivalDate`：仅来自 `floating_population.status='ACTIVE' AND current_flag=1` 的当前记录。
- `permitId` / `maskedPermitNo` / `permitStatus` / `permitValidUntil`：仅来自 `residence_permit.status='ACTIVE' AND current_flag=1` 的当前记录；`maskedPermitNo` 服务端始终脱敏。
- `lastMigrationDirection` / `lastMigrationDate`：仅来自 `business_status='COMPLETED'` 的迁入/迁出记录；同一天同时存在迁入和迁出时确定性返回 `OUT`。

显式过滤参数与数据范围是两层语义：

- `regionCode`：显式业务筛选，匹配“当前有效管辖区划”，即当前 `ACTIVE` 户籍区划或当前 `status='ACTIVE' AND current_flag=1` 流动登记区划。该参数不会扩大或缩小用户的数据范围；不传时按用户数据范围返回。
- `residenceStatus`：显式户籍状态筛选；当前查询的 `residence` 关联仅取 `status='ACTIVE'`，因此 `ACTIVE` 表示要求存在当前有效户籍，其他值不会把历史户籍伪装成当前户籍。
- `floatingStatus`：显式流动登记状态筛选；`ACTIVE` 额外要求 `current_flag=1`，其他值可用于查询指定历史状态，但不会提供 REGION 授权。
- `householdId` / `householdType`：按当前有效户籍所属家庭户筛选；仅有流动登记、无当前户籍的人员不匹配。
- `departmentId`：保持既有“该人员存在该部门业务申请”的显式业务筛选语义，不替代数据范围授权。

REGION 授权只使用以下两类当前证据，`regionCodes` 取 `AuthService` 当前加载的完整真实授权集合（精确 `IN`，不做字符串前缀推断）；集合为空时返回 403/fail-closed：

1. `residence.status='ACTIVE'` 且 `residence.region_code` 属于 `regionCodes`；
2. `floating_population.status='ACTIVE' AND current_flag=1` 且 `current_region_code` 属于 `regionCodes`。

历史 `CLOSED` 流动登记、`current_flag=NULL` 历史登记、单纯历史迁入/迁出、无关 `business_application`、申请人曾经所属行政区以及居住证历史签发区划均不单独构成 REGION 当前授权依据。居住证结果字段照常展示，但 `issue_region_code` 不作为数据范围授权。

统计入口统一为 `/api/statistics`：`overview`、`population-trend`、`region-distribution`、`household-distribution`、`migration-trend`、`floating-population`、`certificate-expiry`、`key-population`。旧 `/api/dashboard` 暂保留兼容，但复用同一 `DashboardService/Mapper` 口径。
