# #68 / #71 综合人口查询字段与 REGION 数据范围治理

状态：**#68 CODE_CANDIDATE；#71 CODE_CANDIDATE**。等待 ChatGPT 独立复核；本分支不合并、不部署、不清理既有 worktree。

## 基线与施工范围

- 精确基线：`5e6ca5082e98a78ab012cd108738b0efcabdcadf`（个人 integration `integration/20261101-backend-p0-security`）。
- 分支：`fix/20261108-issue68-71-comprehensive-query-scope`。
- worktree：`D:\学习用\大二下\小学期-issue68-71-comprehensive-query-scope`。
- 业务代码只修改 `Phase11QueryMapper.xml`、`Phase11QueryService.java`、`ComprehensiveQueryMapper.xml`；测试、脚本和文档为新增/必要调整。
- 未修改既有数据库迁移；本轮没有新增表、列或生产索引。原因见“性能与未验证范围”。

## 两套查询 API 的关系

| 接口 | Controller / Service / Mapper | 角色 |
|---|---|---|
| `GET /api/query/persons` | `Phase11QueryController` / `Phase11QueryService` / `Phase11QueryMapper.xml` | Phase 11 前端“人口综合查询”列表；本轮首要修复对象 |
| `GET /api/queries/persons` | `ComprehensiveQueryController` / `ComprehensiveQueryService` / `ComprehensiveQueryMapper.xml` | 既有综合查询列表兼容入口 |
| `GET /api/queries/persons/{personId}` | 同上 | 综合档案抽屉详情；列表门禁与详情共用 `personScope` |

前端 `frontend/src/api/comprehensiveQuery.js` 和 `query.js` 的列表均调用 `/api/query/persons`，档案抽屉调用 `/api/queries/persons/{personId}`。两套 VO 都复用 `ComprehensivePersonSummaryView`，因此新增字段不需要改变前端 adapter 的字段名；前端 adapter 早已预留 `floatingId/floatingStatus/arrivalDate/permitId/maskedPermitNo/permitStatus/permitValidUntil/lastMigrationDirection/lastMigrationDate`。

## 原始缺陷

### #68 HIGH：Phase11 综合人员查询缺少当前关联与迁移字段

`Phase11QueryMapper.xml` 的 `selectPersons` 只投影人口、家庭户和当前户籍列。`ComprehensivePersonSummaryView` 已有 `floatingId`、`floatingStatus`、`arrivalDate`、`permitId`、`maskedPermitNo`、`permitStatus`、`permitValidUntil`、`lastMigrationDirection`、`lastMigrationDate`，但 Phase11 XML 没有联表/聚合，也没有在 Service 中对 `maskedPermitNo` 做服务端脱敏。结果 JSON 中这些字段全部为 `null`，与前端调用方及第二套接口不一致。

精确旧版 RED（生产代码未修改、真实 HTTP/JWT、隔离 H2）：

- 合成人员 P 的数据库快照：`floating_id=16, floating_status=ACTIVE, floating_current=1, arrival_date=2025-03-01`；`permit_id=4, permit_no=JZP-2026-0001, permit_status=ACTIVE, permit_current=1, valid_until=2027-03-01`；`migration_in` 与 `migration_out` 均为 `COMPLETED` 且同为 `2026-06-15`。
- `GET /api/query/persons?name=Q6871_full` 返回 200，但行内 `floatingId=null, floatingStatus=null, arrivalDate=null, permitId=null, maskedPermitNo=null, permitStatus=null, permitValidUntil=null, lastMigrationDirection=null, lastMigrationDate=null`，`totalElements=1`。
- 原始完整输出见 `evidence/issue68-71-red.txt` / `evidence/issue68-71-red-full.txt`。

### #71 MEDIUM：REGION 仅按户籍过滤漏掉当前流动人口

Phase11 `personScope` 的 REGION 分支为 `AND r.region_code IN (...)`，而 `r` 只 `LEFT JOIN residence ... status='ACTIVE'`。因此没有本区 ACTIVE 户籍、但存在本区 ACTIVE/current_flag=1 流动登记的人员不可见；这正是 #71。

同时审计第二套接口的旧 `ComprehensiveQueryMapper.personScope`：

```sql
EXISTS (SELECT 1 FROM residence rs
        WHERE rs.person_id=p.person_id
          AND rs.region_code IN (...))          -- 任意状态，含历史 INACTIVE
OR EXISTS (SELECT 1 FROM business_application a
        WHERE a.target_person_id=p.person_id
          AND a.applicant_region_code IN (...)) -- 任意历史业务申请
```

这不是“漏查”，而是可证明的数据越权：只有历史 INACTIVE 户籍、只有历史 CLOSED/current_flag=NULL 流动登记、只有无关历史业务申请的人员会被 REGION 用户看到；`business_application` 的申请人区划只是历史办理区划，不能等同于人员当前管辖区划。

精确旧版 RED（REGION A 与 REGION B 授权集合互不包含；A 用户 `population:view`，无其他 A 区授权）：

| 合成人员 | 旧 Phase11 `/api/query/persons` A 用户 | 旧第二套 `/api/queries/persons` A 用户 | 说明 |
|---|---|---|---|
| P1 `resA`：A 区 ACTIVE 户籍 | 可见 | 可见 | 正常户籍人口 |
| P2 `floatA`：无户籍，A 区 ACTIVE/current_flag=1 流动登记 | **不可见** | 可见（业务申请兜底） | #71 精确复现 |
| P3 `closedA`：只有 A 区 CLOSED 流动登记 | 不可见 | **可见（历史业务申请兜底）** | 第二套越权 |
| P4 `nullFlagA`：A 区 status=ACTIVE/current_flag=NULL | 不可见 | **可见** | 第二套越权 |
| P5 `historyAppA`：只有 A 区历史业务申请，无当前关联 | 不可见 | **可见** | 第二套越权；Phase11 旧版未越权 |
| P6 `crossAB`：A 区 ACTIVE 户籍 + B 区 ACTIVE/current=1 流动登记 | A 可见（户籍） | A 可见 | B 用户应通过 B 流动登记看到 |
| P7 `inactiveResA`：只有 A 区 INACTIVE 户籍 | 不可见 | **可见（任意状态 residence）** | 第二套越权 |
| P8 `permitOnlyA`：只有 A 区 ACTIVE/current=1 居住证 | 不可见 | **可见（业务申请兜底）** | 居住证不单独构成 REGION 授权 |
| P9 `resB`：B 区 ACTIVE 户籍 | A 不可见 | A 不可见 | 区划隔离 |

旧版 A 用户 Phase11 HTTP 只返回 `resA` 与 `crossAB`（`totalElements=2`），缺少 `floatA`；旧版第二套 HTTP 返回 8 个名字，包含 `closedA/nullFlagA/historyAppA/inactiveResA/permitOnlyA`。原始输出见证据文件。

## 授权来源与业务过滤的区别

### REGION 授权（用户“有资格看到哪些人”）

本轮统一为以下两类**当前证据**，不满足即不可见：

1. `residence.status='ACTIVE'` 且 `residence.region_code` 属于 `DataScopeCriteria.regionCodes`；
2. `floating_population.status='ACTIVE' AND current_flag=1` 且 `current_region_code` 属于 `regionCodes`。

`regionCodes` 由 `AuthService.regionCodesFor(regionCode)` 基于 `admin_region` 递归查询当前启用区划得到，`DataScopeCriteria.current()` 对 REGION 空集合直接抛 403（fail-closed）。授权比较是完整代码集合的精确 `IN`，不使用字符串前缀、`LIKE` 或长度推断。

明确不构成 REGION 当前授权：CLOSED 流动登记、`current_flag=NULL` 历史登记、单纯历史迁入/迁出、无关 `business_application`、申请人曾经所属行政区、居住证历史 `issue_region_code`。`residence_permit.issue_region_code` 在签发时通常抄自当时流动登记区划，但会随人员流动、关闭、重新登记而变化，业务模型没有“签发区划永久等于当前管辖区”的语义，因此不把居住证作为独立 REGION 授权依据；它仅作为结果字段展示。

SELF、DEPARTMENT、ALL 保持原有合法语义：SELF 仍按本人业务申请关联，DEPARTMENT 仍按部门业务申请关联，ALL 不做范围过滤；本轮未把它们改成区划规则。

### 显式业务过滤（用户“希望筛选哪些业务特征”）

| 参数 | 规则 |
|---|---|
| `regionCode` | 匹配任意当前有效管辖区划：`r.region_code = ?`（当前 ACTIVE 户籍）或当前 `status='ACTIVE' AND current_flag=1` 流动登记的 `current_region_code = ?`。不取消过滤，也不把历史记录算入 |
| `residenceStatus` | 当前 `residence` 关联仅取 `status='ACTIVE'`；`ACTIVE` 表示必须存在当前有效户籍。历史 INACTIVE 不会返回 ACTIVE |
| `floatingStatus` | `ACTIVE` 额外要求 `current_flag=1`；其他值仍可作为历史状态筛选，但不提供 REGION 授权 |
| `householdId` / `householdType` | 按当前有效户籍所属家庭户过滤；仅流动登记人员没有当前 household，不匹配 |
| `departmentId` | 保持既有“存在该部门业务申请”的筛选语义，作为独立显式过滤叠加在范围条件之上 |

示例：REGION A 用户因 A 区当前流动登记合法看到无 A 区户籍的 P2；当查询传 `residenceStatus=ACTIVE` 时，P2 必须被排除（没有 ACTIVE 户籍），这只是显式筛选，不是把 P2 的流动登记冒充成户籍。

## JOIN / EXISTS 设计与结果字段

`Phase11QueryMapper.xml`：

- `personScope` 的 REGION 分支使用两个相关 `EXISTS`（ACTIVE residence、ACTIVE/current_flag=1 floating），避免把授权判断做成可重复的宽 JOIN。
- `personFrom` 增加：
  - `LEFT JOIN floating_population f ON f.person_id=p.person_id AND f.status='ACTIVE' AND f.current_flag=1`
  - `LEFT JOIN residence_permit rp ON rp.person_id=p.person_id AND rp.status='ACTIVE' AND rp.current_flag=1`
  - `LEFT JOIN (SELECT person_id, MAX(in_date) last_in_date FROM migration_in WHERE business_status='COMPLETED' GROUP BY person_id) latest_in`
  - `LEFT JOIN (SELECT person_id, MAX(out_date) last_out_date FROM migration_out WHERE business_status='COMPLETED' GROUP BY person_id) latest_out`
- `selectPersons` 投影 `f.floating_id`、`f.status`、`f.arrival_date`、`rp.permit_id`、`rp.permit_no AS masked_permit_no`、`rp.status`、`rp.valid_until` 以及两个 `CASE` 迁移列；`GROUP BY` 显式覆盖全部非聚合投影列及 `latest_in.last_in_date/latest_out.last_out_date`，满足 MySQL `ONLY_FULL_GROUP_BY`。
- `countPersons` 保持 `COUNT(DISTINCT p.person_id)`；迁移聚合表按 person 聚合后与主表最多一行，因此多条历史迁移不会使列表重复或 count 膨胀。
- `Phase11QueryService` 对 `maskedPermitNo` 调用 `SensitiveDataMaskingService.permitAlways`；身份证、手机号、地址继续沿用原有脱敏路径。

第二套 `ComprehensiveQueryMapper.xml` 仅做最小修改：REGION `personScope` 把 `residence` 限定 `status='ACTIVE'`，并用 `floating_population` 当前记录替换 `business_application` 兜底；`regionCode` 显式筛选同步为当前有效管辖（ACTIVE 户籍或当前流动）规则。`summaryJoins`、`summaryColumns`、分页/排序逻辑不变。

## current_flag / status 语义

`floating_population` 和 `residence_permit` 的业务生命周期约定：

- 当前有效记录：`status='ACTIVE' AND current_flag=1`；
- 关闭/注销/过期/取消：`current_flag=NULL`（历史行保留）；
- `current_flag=NULL` 即使 `status` 字段残留 `ACTIVE`（历史异常数据），也不得作为当前记录；
- `status='CLOSED'` 即使有业务申请关联，也不得提供当前 REGION 授权或当前展示字段。

本轮没有改写任何历史记录的状态，只改变读取口径。

## 最近迁移规则

- 只从 `migration_in` / `migration_out` 中 `business_status='COMPLETED'` 的记录取 `MAX(in_date)` / `MAX(out_date)`。
- 对比两个最大日期：较大者决定 `last_migration_direction` 和 `last_migration_date`；相等时按既有接口契约取 `OUT`。
- DRAFT、UNDER_REVIEW、APPROVED、REJECTED、WITHDRAWN、CANCELLED 等其他状态不参与，哪怕日期更晚。
- 同一天多条同方向记录只影响最大日期，不产生随机结果；无 COMPLETED 记录返回 `null/null`。
- 该规则与第二套 `ComprehensiveQueryMapper.summaryColumns` 的既有契约一致，避免两套接口对同一人员给出不同“最近迁移”。

## API 兼容性

- `/api/query/persons` 新增 9 个响应字段是**加法变更**；原有字段名、大小写、分页 `page/size`、排序白名单（`personId/name/birthDate/createdAt`）和默认 `personId,DESC` 未变。
- `/api/queries/persons` 的 REGION 范围收紧为当前证据；这是修复历史越权，不是放宽。其 `regionCode` 过滤同步支持当前流动登记区划。
- `residenceStatus`/`floatingStatus` 的 `ACTIVE` 语义明确为“当前有效”；历史状态显式筛选仍可用 `floatingStatus` 表达。
- 脱敏字段继续脱敏：身份证/手机号始终脱敏；居住证号始终 `permitAlways`；地址按现有权限服务处理；未新增返回明文敏感字段。
- 文档同步更新：`doc/api/query-statistics-api.md`。

## 验收与证据

- H2 真实 HTTP/JWT 测试：`Issue6871ComprehensiveQueryIntegrationTest`，6 个测试方法全部通过，覆盖 #68 字段、#71 授权正反例、第二套历史兜底移除、分页/count 一致性、SELF/DEPARTMENT/ALL 回归、权限与空 regionCodes fail-closed、脱敏、查询不修改业务状态。
- MySQL 8.4 专用门禁：`Issue6871MySqlIT`，在自建 `127.0.0.1:13392`、非 3306、非 root 应用账号、专属 schema、`REPEATABLE-READ` 上继承同一组契约测试，并额外断言 `ONLY_FULL_GROUP_BY` 保持启用。原始日志与 EXPLAIN 见 `evidence/issue68-71-mysql.txt`。
- 全量 `.\mvnw.cmd test`：**899 total, 0 failures, 0 errors, 3 skipped（896 passed）**，BUILD SUCCESS；完整输出见 `evidence/issue68-71-full-test.txt`。
- RED 原始日志：`evidence/issue68-71-red-full.txt`；精简对照：`evidence/issue68-71-red.txt`。

## 性能与未验证范围

- 列表和 count 均为单条 SQL（无 N+1）；迁移使用按 person 聚合的派生表，避免相关子查询逐行执行。
- `residence` 有 `person_id` 索引；`floating_population`/`residence_permit` 有 `UNIQUE(person_id,current_flag)`，当前关联按 person 定位；`migration_in/out` 有既有查询索引。实际 EXPLAIN 计划见 MySQL 证据；本轮没有新增索引，因为在本轮数据集和 MySQL 8.4 下未证明需要新增。
- 未验证：生产规模数据、真实生产库、浏览器端人工点击回归、居住证历史签发区作为授权依据（明确不采用）、跨库字符集/排序规则边界。
- 安全边界：MySQL 验证使用独立临时实例和专属 schema，不连接 3306，不修改团队共享数据库；实例在证据采集后关闭，数据目录保留供复核。
