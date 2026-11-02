# Population_miniterm MySQL 8.4 专项验收收尾

## 基线与范围

- 原 MySQL 验收分支基线：`5631013f114466a47c999febfbeeb353fcfd71c6`。
- 个人集成基线：`7769421b794a62532519d8c8ec9f7fdce344c5bd`；共享 `main`：`b54579b6b7f8b1feeda60f43906da05fb2ffda99`。
- 本地独立分支：`test/20261102-mysql84-gate-finalization`，起自 `5631013f`，未跟踪 `origin/main`。
- 未修改业务服务、Mapper、正式 Schema 或 API DTO。

## Fresh / Verify 契约

源码与正式 SQL 中没有 `login_log` 表定义或引用。认证及登录审计使用 `operation_log`；登录信息的 `last_login_*` 字段不构成 `login_log` 表。旧检查清单把遗留的 `login_log` 当成必需表，导致真实 Fresh 后 Verify 虚假失败。

已从 `scripts/windows/init_database.ps1` 和 `doc/database/check_backend_v1.sql` 的必需表清单中移除该无效要求。其他表、列、索引、权限、角色、字典及业务一致性检查均保留。`invalid_household_head` 现在把户主指针校验为唯一的 ACTIVE HEAD：允许 `head_person_id=NULL` 且没有有效 HEAD 的空户，也允许无户主但只有普通 ACTIVE 成员的家庭户；非空户主指针缺少对应 ACTIVE HEAD、或存在多个 ACTIVE HEAD，仍记为异常。

验证证据：静态回归确认检查项仍在；MySQL 8.4 上真实执行检查 SQL，验证无户主空户、无户主普通成员户和正确户主均通过，重复 ACTIVE HEAD 与户主关系错配均被检出。MySQL Fresh 内置 Verify、应用 V4_011–V4_013 后的 Verify，以及高风险场景执行后的 Verify 均通过；所有有效一致性计数为 0。未创建虚构的 `login_log` 表。

## 可重复运行入口

`scripts/windows/tests/mysql84-prepare-schema.ps1` 支持专用隔离库 Fresh 和 Verify；Fresh 执行项目初始化 SQL、演示数据、V4_011–V4_013，并创建仅限该库 DML 的非 root 应用账号。`scripts/windows/tests/mysql84-critical-acceptance.ps1` 显式运行 MySQL 集成类 `MySql84CriticalAcceptanceIT`。类名以 `IT` 结尾，普通 H2 `mvnw clean test` 不会连接 MySQL。

两脚本均拒绝非 `127.0.0.1` 地址、3306、课程库、未命名测试库、root 应用账号和 `createDatabaseIfNotExist=true`，并核对 MySQL 8.4 版本、实例监听地址、端口、当前库和账号。初始化账户与应用测试账户分开。现有 `mysql84-api-smoke.ps1` 已从固定的 13386 / run2 改为读取当前连接配置，并在调用前复核实例身份。API 仍须绑定 loopback。

干净隔离实例运行步骤：先启动一台 MySQL 8.4 实例，将 `bind-address` 设为 `127.0.0.1`，使用非 3306 端口和专用数据目录；再在当前 PowerShell 进程设置下列变量。密码应使用随机十六进制值并仅保存在进程环境中，不写入仓库或命令历史。

```powershell
$env:MYSQL84_GATE_DB_URL = 'jdbc:mysql://127.0.0.1:13387/population_miniterm_mysql_gate_<独立后缀>?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai&createDatabaseIfNotExist=false'
$env:MYSQL84_GATE_DB_USERNAME = 'population_gate_app'
$env:MYSQL84_GATE_DB_PASSWORD = '<32-128 位私有随机十六进制密码>'
$env:MYSQL84_GATE_ADMIN_USERNAME = 'root'
$env:MYSQL84_GATE_ADMIN_PASSWORD = '<该隔离实例的管理员密码>'
$env:MYSQL84_GATE_API_TEST_PASSWORD = '<演示账号密码>'

.\scripts\windows\tests\mysql84-prepare-schema.ps1 -Mode Fresh
.\scripts\windows\tests\mysql84-critical-acceptance.ps1
```

可选的真实 API 统计冒烟运行还需先以同一非 root 应用账号启动打包后端，设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`SERVER_PORT`、隔离的 `APP_UPLOAD_DIR` / `EXPORT_DIR`，并设置 `REDIS_ENABLED=false`、`POLICY_AI_ENABLED=false`；之后运行 `mysql84-api-smoke.ps1`。脚本会对比实际 API 计数与直连 MySQL 查询。

## MySQL 8.4 结果

环境：MySQL Community Server 8.4.8，绑定 `127.0.0.1:13387`，事务隔离级别 `REPEATABLE-READ`。测试数据只来自项目虚构 demo 数据及合成记录；未访问现有 3306 服务、课程库或真实人口数据。

MySQL 集成类共 5 个 JUnit 测试方法，0 test failure、0 test error、0 skipped，覆盖一个 Verify 契约场景及 A–E 五个业务场景；D 并发场景重复 3 轮。测试用真实 MySQL DataSource、后端 API 调用和数据库状态断言，不以 HTTP 状态码替代状态检查。

| 场景 | 结果 | 关键断言 |
|---|---|---|
| A 户主迁出并交接 | PASS | 迁出、审批、执行完成；户主指针指向继任者，只有一条 ACTIVE HEAD。 |
| B 原户主迁回 | PASS | 原户主恢复为普通 ACTIVE 成员；现任户主与户主指针不变，ACTIVE HEAD 仍唯一。 |
| C 无户主家庭户最后成员迁出并销户 | PASS | 有效成员及户籍归零、head 仍为 NULL、家庭户进入 `PENDING_CANCELLATION`；后续销户申请、执行及归档完成。 |
| D 同户迁入与最后成员迁出竞争 | FAIL（可靠性） | 3/3 轮出现 API `409` 与未处理 `500`；MySQL 错误 `1213 / SQLSTATE 40001`。两工作线程并发调用；最终每轮都完整回滚，仍为 1 个 ACTIVE 成员、1 条有效户籍、ACTIVE 家庭户、原户主指针不变、无迁出归档。当前 API 将死锁暴露为 500，需后续单独评估死锁重试/冲突映射。 |
| E 注销中途故障注入 | PASS | 测试库临时触发器在归档、户籍删除和成员更新之后返回 `1644 / SQLSTATE 45000`；HTTP 500 后归档为 0、原户籍和成员仍 ACTIVE、人员仍 `REGISTERED`、家庭户 ACTIVE、申请和注销记录仍 APPROVED。测试从 Performance Schema 读取并记录 MySQL thread ID。 |

D 的一次三轮观察记录为：worker thread IDs `59/60`、`61/62`、`63/64`；死锁的 MySQL Performance Schema thread IDs 为 `507`、`508`、`508`。A–E 的所有提交、冲突和回滚路径均检查了最终数据库状态。D 的数据安全不变量通过，但客户端可见的未处理死锁属于失败项；本轮只补充验收工具和证据，未修改生产业务代码。

真实打包 API 冒烟：健康检查报告 MySQL UP、Redis disabled；QUERY_VIEWER 登录后取得 ALL 和 `statistics:view`。统计 API 与相同 MySQL 库的直接 SQL 一致：registered `30`、active floating `1`、active permits `1`、30 日到期 `1`、pending `0`、migration in `2`、migration out `6`；趋势返回 7 点。API 绑定 `127.0.0.1:18087`，测试后已停止。

MySQL 准备脚本和完整业务门禁均使用隔离库；高风险测试后两库的 Verify 仍通过。未运行真实 MySQL 的 RETURNED 补材、材料防自审并发和重点人口并发场景，均标记 **NOT_TESTED**。

## H2 回归与改动边界

`.\mvnw.cmd clean test`：473 tests，473 passed，0 failures、0 errors、0 skipped。MySQL 的 5 个集成测试与此 H2 数量分开统计。`BackendV1ReleaseAuditTest` 定向运行：10 tests，10 passed。

数据库 Schema 与 API 均未改变。修改限于数据库 Verify SQL / PowerShell 清单、测试入口和本报告；V4_011–V4_013 是在隔离测试库中执行的既有迁移文件，没有改动迁移文件。

## 临时资源与权限

- 本轮独立 MySQL 8.4 进程和打包 API 进程均已停止；端口 `13386`、`18086`、`13387`、`18087` 均无监听。Windows 上原有 MySQL84 服务仍为运行状态；本轮没有连接或修改它。
- 上轮目录 `C:\Users\jxgm\AppData\Local\Temp\population_miniterm_mysql_gate_20261102_7769421` 仍保留，内容为隔离数据目录、合成导出/上传、诊断日志和测试凭据文件。它的 ACL 含 `WALKER\CodexSandboxUsers` 修改权限及多个未知 SID 的修改/删除权限，不能确认目录只对当前用户可访问，也不能据 ACL 证明旧凭据没有被其他主体访问。该目录没有出现在 Git 跟踪文件中。
- 本轮目录 `C:\Users\jxgm\AppData\Local\Temp\population_miniterm_mysql84_gate_final_20261102` 保留测试数据库文件、随机测试凭据和临时日志；ACL 仅授予当前用户、SYSTEM 和本机 Administrators。随机密码未提交、未输出，也未复用上轮凭据。
- 两个目录都未删除或更改权限。清理状态为 **CLEANUP_BLOCKED**；如需删除，请通过正常流程明确授权这两个完整路径。未尝试绕过删除审查。
