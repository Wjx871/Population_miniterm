# #15 家庭户与成员日期边界施工记录

## 范围与基线

- 远端 `integration/20261101-backend-p0-security` 在施工前通过 `git ls-remote` 核验为 `4482522897b2000fb80cef903443a875c44d6c92`，从该精确提交创建新分支 `fix/20261109-issue15-household-date-boundary`。
- 使用桌面 worktree 工具创建 `C:\Users\jxgm\.codex\worktrees\issue15-household-date-boundary\小学期`。用户建议的 D 盘目标在检查时不存在；没有在主、integration、S3 或历史施工 worktree 修改代码。
- 本轮只处理 #15，不处理 #22、#30、#33 等其他问题；不新增 SQL Migration，不修改已发布 Schema 或历史迁移。
- 原始保护快照见 [protected-before.txt](evidence/issue15/protected-before.txt)。远端 main 符合用户给定 SHA；本地 main 原本为 `d7fc1192967a13c7d80d2b60fdc12749fd5218df`，原样保留。主工作区 `.gitignore` 原有修改及 `.agent-teams/` 未清理。

## 现有日期语义与静态发现

`household.establish_date`、`household_member.join_date` 均为非空 DATE。`household_member` 保留离户/注销记录，`(household_id,person_id)` 唯一；主数据成员列表只展示 ACTIVE，不代表历史行已不存在。

实际业务写入口检索结果：

| 路径 | 原行为 | 本次处理 |
|---|---|---|
| HouseholdService.create | 初始户主 joinDate 直接取 establishDate | 保留天然等值逻辑并回归 |
| HouseholdService.update | 锁户后可直接修改 establishDate | 校验所有状态成员的最早 joinDate |
| HouseholdService.add | 锁户后直接写请求 joinDate | 与锁定户的 establishDate 比较 |
| MigrationService.executeIn | 插入户籍后恢复历史成员或插入成员 | 写入前校验 inDate 与待恢复历史日期 |
| 成员离户、迁出、注销 | 改状态/leaveDate，不写 joinDate | 保留现有逻辑并回归 |

迁入草稿创建/修改只保存 `in_date`，不生成成员关系；执行时必须重新校验。既有 `reactivateMember` 将 LEFT/MOVED_OUT 恢复为 ACTIVE、设置 join_date 为本次迁入日期、清空 leave_date。正常再次迁入沿用该语义。

## 统一契约与历史数据

1. 成员 `joinDate >= establishDate`，等值允许。新增成员或迁入执行的日期越界返回 HTTP 400。
2. 修改立户日期时包含 ACTIVE、LEFT、MOVED_OUT、CANCELLED 及其他状态的所有成员记录；立户日期晚于其中最早 joinDate 返回 HTTP 400。
3. 完全没有成员记录时可正常修改立户日期；没有有效成员但保留历史行时仍校验历史日期。
4. 待恢复的 LEFT/MOVED_OUT 历史行若原 joinDate 早于当前 establishDate，返回 HTTP 409，提示核查历史记录。不会用本次迁入自动掩盖既有矛盾。
5. 合法恢复继续使用同一 member_id、设置新 joinDate、清空 leaveDate 并递增版本。未新增 inDate 对上次 leaveDate 等其他日期规则。成员离户现有 `leaveDate >= joinDate` 不变。
6. 既有坏数据需核查原始证据后，经另行批准的更正/受控迁移处理。本次不扫描或自动改写共享/课程数据库，不提供自动历史修复脚本。

具体文案和接口影响见 [家庭户 API](../api/household-master-data-api.md) 与 [迁移 API](../api/household-migration-api.md)。

## 事务与并发设计

新增 `HouseholdDateBoundary` 组件统一三种检查，使用 `Propagation.MANDATORY`，要求检查位于调用方事务内。`update`、`add`、`executeIn` 原本即先取得同一 household 的 `FOR UPDATE` 行锁，本次沿用此每户串行化顺序，不在 Controller/前端承担唯一校验责任。

更新路径新增 `ORDER BY join_date,member_id LIMIT 1 FOR UPDATE` 当前读，且不按状态过滤。即使事务此前已建立 MySQL REPEATABLE READ 普通查询快照，等待成员写入提交后仍能读取最新最早日期。空成员户也由 household 行锁保护，后来成员写入会读到新立户日期并重新检查。

迁入的普通/锁定 household snapshot 均增加 establish_date 映射。取得户锁、迁入详情锁并完成原权限/状态/版本检查后校验 inDate；待恢复历史行通过锁定读取检查，早于首次 residence 写入。新增锁顺序仍是 household 在前，再检查成员，不引入 member→household 的反向依赖。

无需跨表数据库 CHECK 或新索引。现有 household_id 索引可定位单户成员，按日期排序可能 filesort；未验证大成员集合的性能。

## 真实 RED 与 H2 GREEN

生产实现修改前，真实 MockMvc 登录 JWT、Spring Service 事务、MyBatis Mapper 与 H2 跑出 **21 项、8 失败、0 错误、0 跳过**。旧实现错误接受：

- 立户 2026-01-01，新增 joinDate=2025-12-31：HTTP 201 并插入成员/成功日志。
- 现有最早 joinDate=2026-01-01，将立户日改为 2026-01-02：HTTP 200、户版本 0→1；ACTIVE 及 LEFT/MOVED_OUT/CANCELLED 历史场景均可复现。
- 已批准迁入 inDate=2025-12-31：HTTP 200，写入户籍、成员并完成申请。
- 待恢复历史 joinDate=2025-12-31：旧版将历史日期覆盖后执行成功。

合法等值、新建初始户主、合法调整与迁入等对照用例通过。不是单独 DTO 校验或伪造异常。

证据位于 [evidence/issue15](evidence/issue15)：`red-command.txt`、`red-provenance.txt`、`h2-red.log`、`h2-red-surefire/`、`h2-red-database-evidence.json`。RED XML/文本报告在下一次运行前已从 target 独立复制；命令中的 reportsDirectory 参数未被现有 Surefire 配置采纳，此情况已记录，未依赖 target 保留证据。

第一轮修复后同一组 #15 用例和 Phase08、Phase03、Issue19、Phase04 相关回归：**127 项、0 失败、0 错误、0 跳过**。日志、XML 和数据库快照分别为 `h2-green.log`、`h2-green-surefire/`、`h2-green-database-evidence.json`。随后补充三项验收，最终 #15 专项为 24 项：仅历史无 ACTIVE、ALL/admin 跨区同样遵循日期边界、合法历史遇到非法本次迁入日期。

拒绝请求使用完整业务表及版本、审批记录、成功操作日志前后对比；失败审计按既有独立机制保留，不视为事务成功日志。初始户主、合法恢复、乐观锁、REGION/ALL、人员校验、户主交接、离户、待销户和 #19 取消复用由新旧用例共同验证。

## 真实 MySQL 8.4 验证

执行 `scripts/windows/tests/issue15-mysql84-gate.ps1`：专属 MySQL **8.4.8** 进程、`127.0.0.1`、非 3306 端口、独立 TEMP datadir、随机 Schema；非 root 应用账号仅获各临时 Schema 的 SELECT/INSERT/UPDATE/DELETE 权限。密码由加密随机源生成，仅通过进程环境和标准输入传递，不写入仓库证据。Schema/版本/端口/datadir 在任何账号或数据修改前验证；测试连接再次断言版本、CURRENT_USER 和 REPEATABLE-READ。

每个测试类使用独立临时 Schema，避免旧 H2 测试类之间的合成人员身份证号冲突。仅在空白私有 Schema 对齐 H2 固定用户 ID（1/2/3/6/7），满足旧材料夹具的外键；未修改实际业务历史。使用当前 fresh SQL、已发布 V4_011/012/013/015..018，fresh 已含 V4_014 列及 #19 条件唯一索引，不重复执行它们。SQL 来源 SHA256、表结构/索引/EXPLAIN、账号种子身份均留证。

| 测试类 | 实际选中 | 失败/错误/跳过 | 证据 |
|---|---:|---|---|
| Issue15MySql84IT | 28（24 专项 + 4 并发） | 0/0/0 | mysql84-run4 |
| Phase08HouseholdMasterDataIntegrationTest | 24 | 0/0/0 | mysql84-run4 |
| Phase03MigrationIntegrationTest | 35 | 0/0/0 | mysql84-run5 |
| Issue19MigrationBatchReuseIntegrationTest | 13 | 0/0/0 | mysql84-run4 |
| Phase04CancellationIntegrationTest | 33 | 0/0/0 | mysql84-run4 |
| 合计 | **133** | **0/0/0** | mysql84-summary.json |

**明确排除一个不适用于真实 MySQL 的旧夹具**：`duplicateHistoricalMembershipsRejectMigrationInWithoutPartialActivation` 尝试插入同一 `(household_id,person_id)` 的两条历史行。H2 测试 Schema 允许该夹具；真实 MySQL 既有 `uk_household_member_pair` 在 HTTP 执行前即拒绝它。run4 的 Phase03 原始结果为 36 项、0 断言失败、1 夹具错误，报告保留；run5 在全新私有实例运行其余 35 项并通过。没有删除/修改唯一键或旧测试，也没有把这个排除说成 JUnit skip。H2 仍执行全部 36 项。

首轮 gate 的启动配置、第二轮过长随机 Schema 名、第三轮用户 ID/跨类夹具隔离问题均属于本轮测试准备问题；之后通过修正新 gate 解决，没有改变生产实现来迎合夹具。各次 runner 日志和后两轮完整测试报告保留。前三次临时原始产物路径可从 `mysql-gate-run1/2/3.log` 找到；TEMP 目录未清理。后两轮日志/报告/身份/Schema 证据已独立复制到 `mysql84-run4/` 与 `mysql84-run5/`，不会被后续 Maven 覆盖。两个验证实例均由 gate 优雅关闭；首次无法登录的启动尝试核对专属 datadir/进程后单独停止，未操作其他 MySQL 服务。

### 并发与回滚结论

四项并发均为真实 JWT + MockMvc + Service + Mapper + MySQL，在两个不同连接的 Spring REQUIRED 事务中执行；独立管理员连接观测 `performance_schema.data_lock_waits`，匹配 household / PRIMARY / RECORD / X,REC_NOT_GAP，随后才允许成功方提交。不是只靠 sleep 或 SQL 模拟业务。

| 先持户锁的成功方 | 等待方 | 成功/等待方 HTTP | 最终立户日期/版本 | 最终成员 |
|---|---|---|---|---|
| update | add | 200 / 400 | 2026-01-02 / 1 | 未插入 |
| add | update | 201 / 400 | 2026-01-01 / 0 | join=2026-01-01、version=0 |
| update | executeIn | 200 / 400 | 2026-01-02 / 1 | 未插入，申请仍 APPROVED |
| executeIn | update | 200 / 400 | 2026-01-01 / 0 | join=2026-01-01、version=0，申请 COMPLETED |

每项实测连接 20 等待连接 21，主键分别为 95102（add）和 95101（迁入）。两个成员先行场景中，更新事务先建立普通 RR 快照，候选成员计数在 HTTP 前后都为 0；锁定日期查询仍看到新提交成员，正确拒绝更新。

所有成功方提交、拒绝方回滚；最终 `join_date < establish_date` 的成员数为 0。失败方与成功方事务内快照逐表比较，未产生额外业务写入、版本推进或成功日志。迁移旧回归同时验证 residence 插入后注入 hook 故障、归档/删除后故障等晚期事务回滚；这类故障注入不冒充 #15 原始 RED。并发和全表快照见 `mysql84-concurrency.json`，日期矩阵见 `mysql84-date-evidence.json`。

## 全量 Maven 与证据索引

最终按要求运行 `.\mvnw.cmd test`，**BUILD SUCCESS：937 项、0 失败、0 错误、3 跳过**，耗时 03:45。78 份本次 Surefire XML 汇总与 Maven 控制台总数一致；最终 #15 H2 专项 24 项通过。显式 MySQL `*IT` 未混入该总数。

3 项跳过均来自已有 `Issue34FirstIssueRedTest`，原因是未设置 `issue34.red` 系统属性，本轮未修改这些测试或门禁。MySQL 夹具排除不计入这 3 项。

- `full-command.txt`、`full-maven.log`、`full-maven-surefire/`、`full-maven-summary.json`：最终原始命令、日志、XML 与逐类/跳过明细。
- `red-green-http-matrix.json`：25 条原始 RED/GREEN 配对 HTTP、数据库日期/版本与成功日志差量；新增三项验收见最终 H2 / MySQL XML。
- `mysql84-summary.json`、`mysql84-concurrency.json`、`mysql84-date-evidence.json`：真实 MySQL 最终选中统计、4 条行锁等待/回滚记录和 30 条日期/权限等数据库快照。
- `mysql84-run4/`、`mysql84-run5/`：原始 Maven、Surefire、MySQL server 日志及索引/Schema 来源证据；run4 的一项夹具错误如实保留，由 run5 的兼容方法重跑结果补充。
- `source-sha256.txt`：本轮生产实现、测试和 gate 的源文件版本；`protected-before.txt` / `protected-after.txt`：保护对象前后核对。

证据目录的 `.gitattributes` 保留 runner 原始字节与空白，避免日志被自动转换换行或规范化。未把 datadir、临时 SQL、账号密码或上传临时文件加入交付。

## 保护引用核对

下列远端引用施工前后完全相同，实际 `git ls-remote` 结果见保护快照：

| 引用 | 施工前 = 施工后 SHA |
|---|---|
| main | b54579b6b7f8b1feeda60f43906da05fb2ffda99 |
| integration/20261101-backend-p0-security | 4482522897b2000fb80cef903443a875c44d6c92 |
| feat/20261103-s3-rabbitmq-async-precheck | 758014cbcccdfe820efb2a6fae73bc3168030484 |
| snapshot/20261103-pre-course-upgrade-backend | d6b0b87b74af7a413214d7da9dd30b075cec882b |
| fix/20261107-issue43-keypopulation-submit-permission | 8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2 |
| fix/20261107-issue81-83-admin-region-hierarchy | f5c3a959cf99f69fed8e8640d845a99b23428563 |

本地主工作区 HEAD 前后均为 `d7fc1192967a13c7d80d2b60fdc12749fd5218df`，其 `.gitignore` SHA256 前后均为 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`。原有 `M .gitignore`、`?? .agent-teams/` 保留；没有 prune、reset、force push、删除/清理 worktree 或恢复 #19 feature。

## 具体变更文件

- `household/HouseholdDateBoundary.java`：统一事务内边界组件。
- `household/HouseholdService.java`、`HouseholdMapper.java`、`mapper/HouseholdMapper.xml`：新增/更新校验与全状态锁定日期/历史查询。
- `migration/MigrationService.java`、`HouseholdSnapshot.java`、`mapper/MigrationMapper.xml`：执行前检查、目标户日期映射。
- `Issue15HouseholdDateBoundaryIntegrationTest.java`、`Issue15MySql84IT.java`、`scripts/windows/tests/issue15-mysql84-gate.ps1`：真实 RED/GREEN、MySQL 与并发门禁。
- 两份 API 文档、本记录、#15 账本行及独立证据目录（含原始输出属性）。没有修改其他审计项状态或 #19 旧记录。

## 验证环境与剩余边界

本机运行 Oracle JDK 21.0.7、Maven Wrapper 3.9.16，编译目标 Java 17；没有以 JDK 17 原生运行测试。测试均使用合成数据，不代表生产容量、真实历史数据质量或所有系统并发组合。直接 SQL/外部程序绕过应用户锁不在本次保证范围。未新增数据库强制跨表约束，不自动修复任何旧日期。

保留本轮 feature 与 worktree；账本最多标为 CODE_CANDIDATE，待外部独立代码复核，不合并或推送 integration/main，不清理旧 worktree 或恢复 #19 feature。
