# #22 户籍迁出归档影响行数契约

状态：`CODE_CANDIDATE`，等待 ChatGPT 独立复核；未快进 integration、未合并 main、未部署。

## 基线与范围

- 远端 `integration/20261101-backend-p0-security` 已核对为 `3f2730882583fdb0cfb3b5aafb50583ea1aecade`，从此精确 SHA 建立 `fix/20261110-issue22-archive-rowcount`。
- 施工 worktree：`D:\学习用\大二下\小学期-issue22-archive-rowcount`。既有工作区与受保护 worktree 未改动。
- 只修 `MigrationService.executeOut()` 忽略归档插入行数的问题。生产变更仅将原插入调用改为严格检查；Mapper Java/XML、归档字段、DDL、迁移版本、批次规则均未改。

## 根因、正常业务条件和锁

原流程先读取通用申请，锁定 `migration_out` 明细并核验 APPROVED/版本，再读取人员、锁当前 `residence` 和 `household`；核验成员、户主继任与材料，调用 `archive()` 构造原有归档字段。通用申请的 `applications.require()` 是普通读取，不应声称它已经取得行锁；完成时仍沿用既有乐观版本更新。明细、当前户籍及家庭户的 `FOR UPDATE` 未调整。

`insertArchive()` 返回 `int`，SQL 为 `INSERT INTO residence_archive (...) SELECT ... FROM person p JOIN household h ... WHERE p.person_id=?`。正常前置核验已经找到人员、户籍和家庭户，当前户籍的关联约束及家庭户锁使正常路径通常能命中一行。本轮没有发现或声称自然业务路径可以稳定制造零行。

缺口是 INSERT SELECT 返回 0 时不抛 SQL 异常，而旧代码忽略返回值，仍调用 `afterArchiveInserted()`，再删当前户籍、成员离户、户主交接、更新人员状态、生成跨区批次、完成两类申请并记录成功审计。Spring 事务因此会正常提交，无法自动识别“归档没有写入”。

## 先 RED 的触发方式与证据等级

测试写好并运行 RED 之后才修改生产代码。

| 证据 | 方式 | 是否真实数据库执行 |
|---|---|---|
| A：正常业务 | 完整创建/提交/审批/执行 API，材料为合成的 VERIFIED 数据库夹具 | 是，真实 Spring Service/MyBatis/JDBC；归档返回 1 |
| B：零行故障 | 测试专属 MyBatis 拦截器，仅在完整 statement ID 为 `MigrationMapper.insertArchive` 时，于 StatementHandler.prepare 为当前 BoundSql 追加 `AND 1=0` | 是，H2 与 MySQL 均实际执行原 INSERT SELECT 加限定谓词，返回 0；属于受控 SQL 故障注入，不是自然业务触发 |
| B：其他异常行数 | 实际 SQL 先插入 1 行，再仅在测试拦截器将 Executor 返回值改为 -1 或 2 | 模拟返回值故障，不能称数据库实际插入 -1/2 行；用于验证严格非 1 拒绝与真实插入回滚 |
| A：唯一约束冲突 | 在私有夹具中预置同 application_id 的归档，再执行正常 SQL | 是，触发既有 application_id 唯一约束 |

未使用 Mapper Spy，未添加生产 Hook，未修改生产 SQL。现有无动作 `MigrationTransactionHook` 在测试中用 mock 观察是否被调用。HTTP 证据来自真实 JWT/Spring Security/Controller 的 MockMvc Servlet 分派响应；没有启动独立 TCP HTTP 服务，也不声称完成浏览器或网络端到端测试。

H2 旧版 RED：1 项，1 failure，0 error，0 skipped。MySQL 旧版：6 项，3 failure（零行及模拟 -1/2），0 error，0 skipped。正常成功、真实唯一冲突回滚和并发场景在旧版仍通过。

零行旧版的两种数据库都观测到：

| 状态/数据 | 执行前 | 旧版执行后 |
|---|---|---|
| 实际归档行数 | 0 | 0（SQL affected rows=0） |
| 当前户籍行数 | 1 | 0 |
| 通用申请/迁出明细 | APPROVED/APPROVED | COMPLETED/COMPLETED |
| 迁出明细版本 | 2 | 3 |
| 人员状态/版本 | REGISTERED/0 | MIGRATED_OUT/1 |
| 家庭户主/版本 | 101/0 | 103/1 |
| 原户主成员/继任者关系 | ACTIVE HEAD / ACTIVE CHILD | LEFT HEAD / ACTIVE HEAD |
| EXECUTE 审批记录/成功迁出审计 | 0/0 | 1/1 |
| 响应/事务完成状态 | — | HTTP 200，事务 COMMITTED |

完整响应、SQL、业务前后状态和日志差异见 [H2 RED](evidence/issue22/h2-red.txt) 与 [MySQL RED](evidence/issue22/mysql-red.txt)。

## 最小修复与回滚

```java
if(mapper.insertArchive(archive)!=1)throw conflict("ARCHIVE_INSERT_ROW_COUNT_MISMATCH");
hook.afterArchiveInserted();
```

检查紧跟归档 SQL，在 `afterArchiveInserted()` 之前。非 1 时复用 `BusinessException(HttpStatus.CONFLICT, ...)`，HTTP 409，message=`ARCHIVE_INSERT_ROW_COUNT_MISMATCH`。`BusinessException` 继承 RuntimeException，触发现有 `@Transactional executeOut()` 默认回滚。未改变归档快照，也未交换“先归档、后删除”的顺序。

## GREEN 与回滚证据

新增 `Issue22ArchiveAffectedRowsIntegrationTest` 6 项：正常恰好一行和重复执行拒绝、SQL 实际零行、模拟 -1/2 返回、真实唯一键冲突、并发执行。

拒绝场景在独立请求前后比较以下 10 张表的全行快照：residence_archive、residence、household_member、household、person、business_application、migration_out、sys_approval_request、sys_approval_log、operation_log。整个测试无 test-managed 外层事务，响应后从数据库重新读取。零行与模拟非 1 均验证 HTTP 409、明确错误消息、现有三个后置 Hook 无调用、事务 afterCompletion=ROLLED_BACK、10 表快照一致。

因此当前户籍保留；原成员状态/关系/日期/版本不变；户主指针、继任者及家庭户状态/版本不变；人员状态/地址/版本不变；通用申请和迁出明细保持 APPROVED；批次、完成记录及成功审计未增加。模拟 -1/2 用例额外证明已经真实插入的那一条归档被整体回滚。唯一键冲突保留预置归档，其他数据不变。

H2 专项合计 **93 项，0 failure / 0 error / 0 skipped**：

| 测试类 | H2 | MySQL GREEN |
|---|---:|---:|
| Issue22ArchiveAffectedRowsIntegrationTest / Issue22MySql84IT | 6 | 6 |
| Phase03MigrationIntegrationTest | 36 | 35 |
| Issue19MigrationBatchReuseIntegrationTest | 13 | 13 |
| Issue15HouseholdDateBoundaryIntegrationTest | 24 | 24 |
| CoreTransactionBoundaryIntegrationTest | 14 | 14 |
| 合计 | 93 | 92 |

覆盖要求对应关系：

- 正常 1 行、零行拒绝、非 1 拒绝及成员/户主/人员/批准状态/日志保持不变：新增契约测试及 10 表快照。
- 唯一冲突：真实数据库唯一键错误；事务整体回滚，Hook 未调用。
- 并发：新增双请求场景与既有 `concurrentOutExecutionAllowsOnlyOneTransactionToComplete`，响应为一个 200、一个 409，最终一条归档、一次完成与 EXECUTE 记录。
- 跨区批次：`crossDistrictOutAndInUseOneTransferBatch`；取消批次复用：#19 全部 13 项，含并发占用/取消交错及取消回滚。
- 日期边界：#15 全部 24 项；既有迁入、迁出、归档查询/脱敏/范围、晚期异常回滚：Phase03 与 CoreTransactionBoundary 全部相关用例。

H2 逐类统计及状态证据见 [h2-green.txt](evidence/issue22/h2-green.txt)、[h2-green-surefire.json](evidence/issue22/h2-green-surefire.json)。

## MySQL 8.4 实际验证

脚本 `scripts/windows/tests/issue22-mysql84-gate.ps1` 在每次运行建立全新临时 datadir、随机非 3306 端口和专属 schema；绑定 127.0.0.1，REPEATABLE READ，非 root 应用用户仅授予所属 schema 的 SELECT/INSERT/UPDATE/DELETE；root 仅用于该实例初始化/身份验证/关停。初始化前后核验 datadir、端口、版本与隔离级别，不接入课程共享或生产库。

| 阶段 | 端口 | schema | 应用用户 |
|---|---:|---|---|
| 旧版 RED | 3000 | `population_miniterm_mysql_gate_issue22_d2be671f6921` | `i22_d2be671f6921` |
| GREEN 主批次 | 13443 | `population_miniterm_mysql_gate_issue22_2bb55ad7d55c` | `i22_2bb55ad7d55c` |
| #15 补跑 | 7191 | `population_miniterm_mysql_gate_issue22_03c3b6a2d62a` | `i22_03c3b6a2d62a` |


所有实际版本均为 **MySQL Community 8.4.8**。GREEN 业务用例合计 **92 项，0 failure / 0 error / 0 skipped**（两次运行的业务测试汇总，不把失败的类名选择计成测试通过）。正常 INSERT SELECT 返回 1，受控零行 SQL 返回 0，修复代码返回 409 且事务回滚；正常/重复/并发、唯一冲突及跨区批次都通过。

首次 GREEN 批次误将 #15 的类名写成不存在的 `Issue22HouseholdDateBoundaryIntegrationTest`，Maven 报 No tests matching pattern，整个启动脚本 exit=1；其余 4 类实际执行 68 项通过。仅修正本轮脚本选择器后，在全新私有实例补跑正确的 `Issue15HouseholdDateBoundaryIntegrationTest` 24 项，exit=0。没有掩盖第一次启动失败，也没有为了通过而关闭 failIfNoSpecifiedTests。

MySQL Phase03 明确排除 1 个既有 H2 专用重复成员关系夹具 `duplicateHistoricalMembershipsRejectMigrationInWithoutPartialActivation`：MySQL 原有 (household_id,person_id) 唯一索引会在夹具阶段拒绝重复插入；该用例在本轮 H2 已通过。不是业务失败的跳过，也没有修改该测试或 MySQL 索引。

全部专属实例已确认正常 SHUTDOWN，datadir 和原始日志保留，未清理。版本/端口/schema/用户、归档 SHOW CREATE TABLE/SHOW INDEX、schema 输入哈希、逐类日志摘录及完整 Surefire 数字均独立保存在 [evidence/issue22](evidence/issue22)：重点为 `mysql-red-identity.txt`、`mysql-green-identity.txt`、`mysql-date-green-identity.txt`、`mysql-*-schema-index-plan*.txt`、`mysql-green-combined-surefire.json`。日志摘录不含随机数据库口令、JWT 或账户密码。

重跑 GREEN：

```powershell
.\mvnw.cmd '-Dtest=Issue22ArchiveAffectedRowsIntegrationTest,Phase03MigrationIntegrationTest,Issue19MigrationBatchReuseIntegrationTest,Issue15HouseholdDateBoundaryIntegrationTest,CoreTransactionBoundaryIntegrationTest' '-Dspring.test.mockmvc.print=NONE' test
pwsh -NoProfile -File scripts/windows/tests/issue22-mysql84-gate.ps1
.\mvnw.cmd test
```

## 完整 Maven

原命令 `.\mvnw.cmd test` 已完成，BUILD SUCCESS，用时 03:20：**943 项，0 failure / 0 error / 3 skipped（940 项实际执行通过）**。统计来自本轮 Maven 汇总与本轮日志实际列出的各 Surefire suite XML 交叉求和；没有把 target 中前面留下的 MySQL IT 报告混入默认全量数字，也没有沿用上一轮 937。

3 个跳过为基线已存在的 `Issue34FirstIssueRedTest` 旧缺陷诊断方法，本轮未改该类：

- `Issue34FirstIssueRedTest.anotherApplicantAlsoCreatesDuplicate`。
- `Issue34FirstIssueRedTest.simultaneousHttpRequestsBothCreateDrafts`。
- `Issue34FirstIssueRedTest.duplicateCreatesTwoRealHttpDrafts(String)`。

完整逐类统计与跳过原因见 [full-maven-surefire.json](evidence/issue22/full-maven-surefire.json)，日志统计/原始日志 SHA256 见 [full-maven.txt](evidence/issue22/full-maven.txt)。本轮不作 JaCoCo 覆盖率声明。


## Git 与保护

远端 main=`b54579b6b7f8b1feeda60f43906da05fb2ffda99`、S3=`758014cbcccdfe820efb2a6fae73bc3168030484`、snapshot=`d6b0b87b74af7a413214d7da9dd30b075cec882b`、#43=`8b0730fa474c8b533ab8f3b571bd1bf73b4e65f2`、#81/#83=`f5c3a959cf99f69fed8e8640d845a99b23428563`。本轮开工和回归期间均核对一致，提交推送后再次核对。

原 main 本地工作区 HEAD 开工时已经是 `d7fc1192967a13c7d80d2b60fdc12749fd5218df`，不同于远端 main；没有自行纠正。本轮前后 `.gitignore` SHA256 必须保持 `8399B5DE049E3989E123B747D457A02A5D8EDADF6A57867B57F1BD760FA68DE3`。原有 `.gitignore` 修改、`.agent-teams/` 与 `.claude/` 均保留。

桌面 worktree 工具另自动建立了同基线 detached checkout：`C:\Users\jxgm\.codex\worktrees\issue22-archive-rowcount\小学期`，未用于施工，保持原样且未清理；正式 feature 在用户指定 D 盘路径。所有本轮 worktree 和 feature 保留等待独立复核，未来清理仍需单独授权并使用 Windows 回收站。

## 限制与剩余风险

- 零行是精确受控 SQL 故障，未证明自然业务可以触发；-1/2 是模拟返回值，不是数据库真实异常行数。
- 证据范围为合成数据库夹具、Servlet API、真实 H2/MySQL 事务；未验证真实材料上传、网络 HTTP 服务、前端或生产环境。
- 本机运行 Java 21.0.7，编译 release 17；未宣称在 JDK 17 运行时验证。
- 并发证据限本轮双请求与既有并发测试，不代表所有负载和竞争交错均已穷尽。
- 本轮未调整既有锁顺序、通用申请的读取方式或批次机制，不把 #22 扩成其他并发/结构整改。
- 仅 CODE_CANDIDATE；独立复核、快进个人 integration 和部署均未执行。
