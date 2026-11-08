# #81 / #83 行政区划层级状态一致性

## 基线与交付范围

- 开发基线：`5b95f2be76f6b9661767017c12b5c554f69d1861`。
- 分支：`fix/20261107-issue81-83-admin-region-hierarchy`。
- worktree：`C:\Users\jxgm\.codex\worktrees\issue81-83-admin-region\小学期`。
- 本轮仅处理 #81 / #83；未合并 integration/main，未部署，未操作 #43 的目录、登记、分支、进程或目录锁。
- #81 / #83 均为 **CODE_CANDIDATE**，等待 ChatGPT 独立复核。最终 commit SHA 见交付报告及本分支 tip。

## 旧版 RED：先复现，再改生产代码

2026-11-08，生产源码保持上述精确基线，仅新增测试和门禁脚本。`Issue8183BaselineRedProbe` 经真实随机端口 HTTP、登录/JWT、Spring Security/RBAC、Service 事务及 JDBC 测试；不是 Mock Service 或直接调用 Controller。

| 场景 | 旧版真实响应 | 数据库结果 | 目标契约 |
|---|---:|---|---:|
| #81 创建：新建启用父节点，再正常停用，向其下创建子节点 | 201 | 父 DISABLED / version 1；子 ENABLED / version 0 | 409 |
| #81 更新：两个启用根节点，停用目标根，将现存子节点迁入该根 | 200 | 子 parent_id 改为停用根，version 从 0 增至 1 | 409 |
| #83 停用：A ENABLED → B DISABLED → C ENABLED，调用 A.disable | 200 | A 变 DISABLED / version 1；C 仍 ENABLED | 409 |

H2 与 MySQL 各运行 3 项，均为 **3 failures / 0 errors / 0 skipped**，失败原因均是实际成功响应违背预期 409。#83 只对专属隔离库中的合成 B 使用 JDBC 构造历史异常，没有修改生产状态机。旧版前后状态和真实响应保存在 [RED 证据](issue81-83-admin-region-hierarchy-red-evidence.txt)。RED Probe 的文件名不匹配默认 Surefire 测试模式，需显式 `-Dtest`；GREEN 测试继承相同契约用例，修复后必须通过。

MySQL RED：`8.4.8`，`127.0.0.1:13391`，`REPEATABLE-READ`；全新库 `population_miniterm_mysql_gate_issue8183_7c5d58c5d5`，应用身份 `i8183_7c5d58c5d5@127.0.0.1`，仅该库 DML 权限。未复用 #34 或其他测试库。

## 业务不变量与兼容性

1. 新建节点固定 ENABLED，要求直接父节点及整个祖先链均 ENABLED。根节点允许不提供 parentCode，仍要求 regionLevel=1。
2. 更新仍使用完整更新 DTO；目标父节点及其全部祖先须启用，禁止迁入停用链。既有正常名称、排序、迁移及 version 条件更新保持。
3. ENABLE 前检查全部祖先，防止直接父节点启用而更高祖先停用的历史异常绕过检查。
4. DISABLE 前遍历全部后代，包括 DISABLED 中间节点；任意深度出现 ENABLED，返回 409。所有后代 DISABLED 或叶子节点，允许按原状态/version 条件转换。
5. 层级只依据 `parent_id → region_id`，不推断编码前缀、长度或真实行政归属，不硬编码具体编码。
6. 自挂靠/新造循环返回 400；历史循环、缺失祖先、未知状态返回 409。并发改变层级导致锁计划过时也返回 409，由调用者刷新后显式重试。
7. 历史异常只在本次访问的祖先/后代范围内识别，不扫描或自动清洗真实数据。可以停用异常链中的启用叶子，也可以迁出已知 DISABLED 祖先链；不会自动启用父级或级联改动子孙。

HTTP DTO 的 regionLevel 仍限定 1–5、sortNo≥0。直接子级别仍仅要求大于父级别，保留原本允许跳级的契约；没有引入必须恰好相差 1 的新规则。数据库 V4_008 的 status/region_level 没有 CHECK，应用正常状态为 ENABLED/DISABLED。本轮未修改 migration/schema。

四个读取 API 不变：search/detail/children 可以返回停用节点；tree 只装配 ENABLED 节点，停用父节点下的启用分支仍不会提升为根。`region:view` 仅能读取，四个管理入口仍要求 `region:manage`。AuthMapper 的启用区划递归读取另有子级别恰为父级别+1 的既有规则，本轮未改其含义或权限范围。

## 实现与事务协议

沿用 `AdminRegionService`、`AdminRegionMapper` 和 `admin_region`，没有第二套层级数据模型。新增 SQL 方言配置只为 MySQL 共享锁选择语句；其他 Mapper 的通用 SQL 不变。

| 入口 | 操作节点 | 必须锁定的祖先 |
|---|---|---|
| create | INSERT 新节点 | 目标父及完整祖先链，S |
| update | 当前节点 X | 旧祖先链和目标祖先链的并集，S |
| enable / disable | 当前节点 X | 当前完整祖先链，S |

所有锁、业务更新和 `recordTransactional` 成功审计都在原 `@Transactional` 边界内，未降低 MySQL RR 隔离级别。事务结束前不释放锁，不使用 JVM 全局 synchronized 或显式表锁。

步骤：

1. 允许普通 SELECT 发现节点和祖先，使用访问集合终止循环。发现结果仅构造锁计划，不据此批准业务状态。
2. 对计划并集按 region_id 递增逐行加锁。操作节点直接取 X，其他节点取 S；不存在先 S 后升级 X。锁定查询只访问 admin_region 单表主键，避免 LEFT JOIN 隐式增加父行锁。
3. 所有已锁行使用当前读结果，逐行核对 parent_id 与发现阶段相同。任一父指针变化就整体返回 409；不会在持锁后沿新路径无序追加锁。父指针相同保证锁住的链仍完整，状态和级别从当前读判断。
4. 新增、迁入、启用在已锁定链上检查状态，再执行原 INSERT/带 version 的 UPDATE。旧 RR 快照中的 ENABLED 不会覆盖锁定后读到的 DISABLED。
5. 停用持有本节点 X。任何后代的新增、迁入、启用或迁移都必须对该祖先取 S，因此不能与本次停用同时越过检查。此时沿 parent_id 广度遍历子树，使用锁定当前读读取所有状态的孩子，不能用旧快照 COUNT 代替。

MyBatis 的锁定 SELECT 显式 `flushCache=true/useCache=false`，也清除事务内本地结果缓存，避免重复锁定查询被缓存短路。[MySQL 锁定读说明](https://dev.mysql.com/doc/refman/8.4/en/innodb-locking-reads.html)及[MyBatis 方言配置](https://mybatis.org/mybatis-3/configuration#databaseIdProvider)是方案依据；下面的动态结果独立于这些推理。

### 二阶段锁顺序与冲突

计划阶段按全局 ID 顺序取锁；后代遍历是第二阶段，按队列及孩子 ID 顺序读取。历史迁移后，孩子 ID 可能小于父 ID，此时不能声称整个遍历仍全局递增。为避免持有祖先 X 后再等待较老孩子的 X，后代查询使用 MySQL `FOR SHARE NOWAIT`：遇到行锁占用即拒绝本次停用。仅该查询的 MySQL 3572 / H2 50200 被转换为业务 409，事务整体回滚；其他数据库错误继续原异常路径。

原有 MySQL 1213/40001 死锁处理仍返回 409，没有自动重放业务请求。NOWAIT 仅针对该后代锁定查询的行锁；没有宣称 DDL/元数据锁或所有数据库锁都不等待。普通锁计划若最终出现 1205，仍按原全局异常处理返回 500；本轮动态门禁没有观察到 1205 或 1213。

### H2 与 MySQL 的差别

MySQL 使用 `FOR SHARE`，共享祖先的两个独立分支可同时修改；只有操作节点/受影响祖先发生 S-X 或 X-X 竞争才互斥。H2 没有同等共享锁语法，测试方言以 `FOR UPDATE` 实现更强的保护，会增加测试时串行化。H2 结果不用于代替 MySQL RR 证据。

### 遍历与性能

祖先使用集合检测环，不按 regionLevel 或固定 5 次截断；后代使用队列和 visited 集合，每个访问节点至多入队一次。后代查询使用既有 `(parent_id,status,sort_no)` 索引的 parent_id 左前缀，不依赖编码。遇到启用节点即拒绝；允许停用的大子树需 O(节点数) 内存及约每个已访问父节点一条查询，当前实现没有批量 CTE 优化或新索引。大型子树的事务时长、范围/gap 锁与高并发负载仍需另行量测。没有增加无依据的全表/全树排他锁。

## GREEN 验证

H2 相关专项 78/78：

| 测试类 | 项数 |
|---|---:|
| Issue8183HierarchyHttpTest | 21 |
| Phase09AdminRegionIntegrationTest | 11 |
| Phase09CertificateIntegrationTest | 20 |
| ReferenceCacheIsolationIntegrationTest | 15 |
| ReferenceCacheTransactionIntegrationTest | 11 |

全部 failures/errors/skipped 为 0。21 项新增默认用例覆盖三项 RED、五级后代、全祖先启用检查、根/叶、循环、读 API、正常名称排序及迁移、乐观锁、四种 viewer 管理拒绝、拒绝前后完整行/成功审计对比、真实事务审计故障回滚和并发。[H2 GREEN 与全量汇总证据](issue81-83-admin-region-hierarchy-h2-green-evidence.txt)包含最终全量运行中的对应类结果、HTTP/锁等待/状态输出及全量日志 SHA256。

MySQL `Issue8183MySqlIT` 22/22：继承上述 21 项，另验证同根不同分支并行写入。2026-11-08 11:34:36 +08:00，Maven BUILD SUCCESS，22 passed、0 failed/error/skipped。实例 `8.4.8 / 127.0.0.1:13391`，新库 `population_miniterm_mysql_gate_issue8183_7e2454da2b`，应用账号 `i8183_7e2454da2b@127.0.0.1`。连接前核验 URL、库名、非默认端口和非 root 应用账号；连接后核验版本、bind_address、port、CURRENT_USER、DATABASE 和隔离级别。暂停中的真实 Service 事务内部也断言 `@@transaction_isolation=REPEATABLE-READ`。

### 实际并发请求与结果

夹具在第一个请求持锁、尚未执行 INSERT/UPDATE 时暂停，只控制交错，不替换业务结果；第二个请求由另一 HTTP 连接发起。必须先在 H2 SESSIONS 或 MySQL Performance Schema 看到真实阻塞，才释放第一个请求。每次检查只有第一个请求产生一处行变更和一条成功审计，新增 version=0，更新 version 恰好 +1，失败请求不产生额外修改。

| 竞争顺序 | 请求 | 轮数 | 实际 HTTP（先/后） |
|---|---|---:|---|
| 创建先提交 | POST /api/admin-regions；POST /{parent}/disable | 5 | 201 / 409 |
| 停用先提交 | POST /{parent}/disable；POST /api/admin-regions | 5 | 200 / 409 |
| 迁入先提交 | PUT /{child} parentCode=target；POST /{target}/disable | 5 | 200 / 409 |
| 停用先提交 | POST /{target}/disable；PUT /{child} parentCode=target | 5 | 200 / 409 |
| 启用先提交 | POST /{descendant}/enable；POST /{ancestor}/disable | 5 | 200 / 409 |
| 停用先提交 | POST /{ancestor}/disable；POST /{descendant}/enable | 5 | 200 / 409 |
| 相邻节点互迁 | PUT /A parentCode=B；PUT /B parentCode=A | 5 | 200 / 409，旧路径被拒绝，无循环 |
| 父节点迁移后创建 | PUT /B parentCode=X；POST 新子节点 parentCode=B | 1 | 200 / 409，发现/锁定路径不一致 |

共 **36 对请求 / 36 次真实行锁等待**。启用竞争置于三级 A→B→C 夹具中，竞争 C.enable 与其祖先 B.disable；另有五级历史状态及完整祖先检查用例。MySQL 等待记录为 `admin_region.PRIMARY` 的 S/X、`REC_NOT_GAP`，记录了 key、requestingTx、blockingTx。各测试前后 1213 与 1205 计数增量均为 0，竞争响应无 500。全部最终状态检查启用节点祖先均启用、无环，以及版本/审计一致。

额外的“较老孩子行被另一事务 X 锁占用”用例返回 409，完整行和成功审计不变；释放锁后同一版本停用成功，覆盖 NOWAIT 路径。同根不同分支测试在第一个事务仍持有根 S 时，第二个分支已 HTTP 200 并提交，之后第一个也成功，证明 MySQL 下未人为串行化整棵树。完整记录见 [MySQL GREEN 证据](issue81-83-admin-region-hierarchy-mysql-green-evidence.txt)。

### 回滚与权限

非法状态、版本、循环等拒绝均比较完整 admin_region 行（含 timestamp/version）和 REGION 成功审计数量；viewer 的 create/update/enable/disable 均 403，允许原权限机制记录失败访问日志。使用审计 Mapper 故障注入，使区域 INSERT/UPDATE/status UPDATE 已执行后审计失败；真实 HTTP 返回通用 500，区域和 version 整体回滚，没有成功审计。这些受控故障响应与并发失败 409 分开统计。H2 和 MySQL 都执行这些用例。没有其他业务写路径；参考数据/缓存事务回归另有覆盖。

### 完整 Maven 回归

`.\mvnw.cmd test` 于 2026-11-08 11:38:31 +08:00 完成，耗时 3 分 7 秒，**869 total / 866 passed / 3 intentionally skipped / 0 failures / 0 errors，BUILD SUCCESS，退出码 0**。相对给定基线 848 项增加 21 项默认专项测试；MySQL 的 22 项另行显式运行，不混入此汇总。原有 #34 三个默认跳过的 RED 方法保持不变。JaCoCo 报告提示缺少执行数据，本轮不据此声称覆盖率。

未清理 Surefire 目录，因此其中还保留显式运行的 RED Probe 和 MySQL 报告；不能将所有历史 XML 累加当作本次默认全量结果。上述 869 项来自本次全量 Maven 的实际汇总。

## 重跑与证据位置

```powershell
.\mvnw.cmd '-Dtest=Issue8183HierarchyHttpTest,Phase09AdminRegionIntegrationTest,Phase09CertificateIntegrationTest,ReferenceCacheTransactionIntegrationTest,ReferenceCacheIsolationIntegrationTest' test
.\scripts\windows\tests\issue8183-mysql84-gate.ps1
.\mvnw.cmd test
git diff --check
```

门禁脚本每次使用新的任务目录、数据库和应用用户，只绑定回环非 3306 端口；若端口已占用就退出，不操作占用进程。随机凭据以当前用户 DPAPI 密文存放在新任务目录，未提交。测试结束只关闭本次创建的 MySQL 实例，保留数据、配置和日志，不执行删除/清理。

- RED 原始运行：`issue8183-red-h2.log`（worktree 内，Git 忽略）；MySQL Temp 目录 `C:\Users\jxgm\AppData\Local\Temp\population_mysql84_issue8183_7c5d58c5d5`。
- GREEN MySQL 原始运行：`C:\Users\jxgm\AppData\Local\Temp\population_mysql84_issue8183_7e2454da2b`，其中 maven.log、schema.log、mysql-error.log、数据目录及 DPAPI 凭据保留。
- MySQL 提交证据取自 UTF-8 Surefire system-out；PowerShell 原始 Maven 控制台日志中的中文可能受宿主编码影响，HTTP code、状态/版本和 XML 原文不受该转录问题影响。
- H2 专项：`issue8183-targeted-h2.log`；全量：`issue8183-full-maven.log`（均 Git 忽略）。

## Git 保护与未验证范围

施工前及 MySQL GREEN 后核验远端，四条保护分支均保持：

| 分支 | SHA |
|---|---|
| integration/20261101-backend-p0-security | `5b95f2be76f6b9661767017c12b5c554f69d1861` |
| main | `b54579b6b7f8b1feeda60f43906da05fb2ffda99` |
| feat/20261103-s3-rabbitmq-async-precheck | `758014cbcccdfe820efb2a6fae73bc3168030484` |
| snapshot/20261103-pre-course-upgrade-backend | `d6b0b87b74af7a413214d7da9dd30b075cec882b` |

主工作区 HEAD 仍为 `d7fc1192967a13c7d80d2b60fdc12749fd5218df`，仅保留原 `.gitignore` 修改，其 SHA256 仍为 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`。仅更新原账本 #81/#83；#33/#41/#45/#64 及其他条目保持原样。保留本 worktree/分支等待独立复核。

动态结论仅覆盖隔离合成数据、MySQL 8.4.8 RR 和本轮受控交错；未验证生产规模/长期压力、其他数据库版本、真实业务区划、前端或生产部署。运行 JDK 21.0.7、编译 release 17，未在 JDK 17 运行时重测。协议要求所有并发层级写入遵守上述 Service 锁规则；绕过服务的手工 SQL/外部写入不能由本修复兜底。没有新增数据库约束或自动历史修复，也不宣称全系统所有死锁已消除。
