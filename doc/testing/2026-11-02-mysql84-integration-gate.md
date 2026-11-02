# MySQL 8.4 真实数据库专项验收

## Git 与环境

- 验收基线：`integration/20261101-backend-p0-security`，`7769421b794a62532519d8c8ec9f7fdce344c5bd`。
- 共享 main 核验值：`b54579b6b7f8b1feeda60f43906da05fb2ffda99`。
- 施工分支：`test/20261102-mysql84-integration-gate`，从上述集成 SHA 建立，无 `origin/main` upstream。
- 实现/测试提交：`6296e93`、`9c726f8`。本报告随之后的 docs 提交；精确最终分支 HEAD 在推送交付中列出。
- 实际服务：MySQL Community Server `8.4.8`，独立 `mysqld.exe`，绑定 `127.0.0.1:13386`，数据库 `population_miniterm_mysql_gate_20261102_run2`。独立数据目录位于当前用户 Temp 下。
- 主机现有 MySQL84 服务及 `3306` 数据目录未连接、未修改；Docker 不可用。后端使用 `127.0.0.1:18086`，`createDatabaseIfNotExist=false`、Redis 与策略 AI 禁用，上传、导出和日志目录均指向任务 Temp。
- `@@transaction_isolation=REPEATABLE-READ`；SQL mode 包含 `STRICT_TRANS_TABLES`、`ONLY_FULL_GROUP_BY`；服务端字符集 `utf8mb4`，默认排序规则 `utf8mb4_0900_ai_ci`。

## 初始化与结构

- 在独立库执行当前 Fresh 主 SQL；对 `V4_011`、`V4_012`、`V4_013` 使用临时副本，将唯一 `USE population_miniterm` 改写到隔离库后执行；发布 SQL 文件未改。V4_001–V4_010 的当前结构已包含在 Fresh 快照中，并通过实际结构检查。
- Fresh 初次预检复现初始化脚本错误：单行 `"0"` 被 PowerShell 展开为字符串，`[int]$result[-1]` 得到字符码 48，导致空库被误判为 48 张表。修复为保留单行数组返回后，Fresh 能继续初始化；随后在非空测试库验证其拒绝门禁仍拒绝且未改库。
- 初始化脚本的最终 Verify 仍报告 `required_table_missing=1`：`check_backend_v1.sql` 与 `init_database.ps1` 要求 `login_log`，但主 SQL、V4 迁移及后端源码均未定义或引用该表。只读一致性查询共 16 项，仅此项非零；未为了通过检查而伪造 Schema 或修改发布 SQL。结构检查因此标记为 **PASS（项目所需表与约束） / BLOCKED（内置 Verify 的 login_log 清单不一致）**。
- 实际 information_schema 与 `SHOW CREATE TABLE` 核对：要求的 20 张业务表全部存在；共 34 张基线表，34 张均为 InnoDB/utf8mb4；61 个外键、82 个唯一索引、1 个 CHECK 约束、1 个 STORED 生成列（`key_population.active_flag`）。V4_013 的 `person_idcard_image` 存在；V4_012 后 `QUERY_VIEWER.data_scope=ALL`。区划表有 7 个启用节点、5 个带父节点。

## 真实 MySQL API 与数据验证

全部数据来自项目标明为虚构的 demo SQL，或本轮临时合成记录；没有真实人口库、课程库或身份信息。

| 场景 | 结果 | 实际断言 |
|---|---|---|
| 启动、健康检查和登录 | PASS | `/api/health` 报 `database=UP`、Redis disabled；viewer、admin、household、approver、population 登录并加载相应权限。 |
| 统计 SQL | PASS | Viewer `ALL` 下 overview、流动人口、居住证到期和趋势 API 与同库 SQL 对照；最终数据值为登记人口 12、有效流动人口 1、有效居住证 1、30 日到期 1、待审批 0、迁入 0、迁出 3；趋势返回 7 点。 |
| 人口生命周期编辑 | PASS | 通过 API 创建合成人员及 `skipOcr=true` 影印本；数据库置为 `CANCELLED` 后，普通编辑显式携带“正常”仍保留注销状态；置为 `DECEASED` 后省略 status 编辑，仍保留死亡状态。 |
| 迁出重复执行并发 | PASS（3/3） | 每轮两个独立 HTTP 客户端线程经同步屏障同时执行同一迁出申请；每轮恰好一个 HTTP 200、一个 409。三轮最终数据库均为申请/迁出 `COMPLETED`、单条归档、0 条有效户籍、0 个有效成员、家庭户 `PENDING_CANCELLATION`。默认隔离级别为 REPEATABLE-READ。 |
| 普通导出与 XLSX | PASS | PERSON 导出日志行数与同库 SQL 均为 4；本人下载 HTTP 200；实际解压 XLSX XML，测试的完整合成身份号不出现，脱敏标记存在。 |
| SELF / DEPARTMENT 范围 | PASS | 临时合成申请归属记录后，分别用 SELF 与 DEPARTMENT 登录导出；API 行数与申请人/部门 EXISTS SQL 均为 1。角色范围在测试后恢复为 QUERY_VIEWER=ALL、POPULATION_MANAGER=REGION。 |
| REGION 敏感导出 | PASS | REGION 申请人申请含两个同名合成对象的数据，审批后由 ALL 管理员执行；全库匹配 2 条、申请人区划匹配 1 条、日志行数 1。实际 XLSX 含区内记录、不含区外记录，证明执行人权限未扩大申请范围。 |
| 导出日志查看与下载 | PASS（按当前契约） | QUERY_VIEWER 能查看他人导出日志的 `filterSnapshot` 与 `exportedFields`，但 `downloadable=false` 且下载 HTTP 403。现有 Phase06 测试也明确要求 viewer 可读详情；本轮只用合成过滤词验证。此契约会暴露查询条件和申请字段信息，需由日志可见性契约持续评估。 |

以上 MySQL 场景均通过真实 JDBC/API 查询和数据库状态或 XLSX 内容断言，不以 H2 结果代替。

## 回归结果与限制

- 定向 H2 回归：`Phase03MigrationIntegrationTest`、`Phase06ExportAuditIntegrationTest`、`Phase08PersonIdentityIntegrationTest`，75 项通过，0 失败、0 错误、0 跳过。
- 完整 H2 回归：`.\mvnw.cmd clean test`，472 项通过，0 失败、0 错误、0 跳过。普通测试仍使用原 H2 配置。
- 新增可重复的 MySQL API 冒烟脚本：`scripts/windows/tests/mysql84-api-smoke.ps1`。脚本要求 loopback、专用端口/库、非 root 账号、`createDatabaseIfNotExist=false`，并将 API 统计与直接 MySQL 计数比较。
- 本轮未在真实 MySQL 上验证：注销/销户全闭环、RETURNED 补材、户主交接与迁回、迁入、最后成员迁出与迁入并发、材料审批并发、重点人口并发，以及受控故障注入的应用级回滚。未比较 READ COMMITTED。并发测试记录了独立 HTTP 线程和结果/最终不变量，但未捕获每个请求的 MySQL connection ID、SQLSTATE 或错误码；不得据此声称这些场景均通过。
- 未发现生产业务 SQL 的 MySQL 兼容性缺陷；没有修改 API 或正式 Schema。修复仅涉及 Fresh/Verify 脚本的单行数组返回错误。
- 后端已停止；独立 MySQL PID 已停止，`13386` 与 `18086` 均无监听。删除该轮 Temp 数据目录的命令被自动审查拒绝，故任务目录（含合成测试库文件、临时凭据、日志和导出文件）保留在：`C:\Users\jxgm\AppData\Local\Temp\population_miniterm_mysql_gate_20261102_7769421`。该目录不在 Git 中，服务已停止；未尝试绕过审查再次删除。
