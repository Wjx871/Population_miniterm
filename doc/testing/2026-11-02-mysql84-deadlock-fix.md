# MySQL 8.4 迁入/迁出并发死锁定点修复

## 基线与范围

- 基线：`7f208cf62d68f4a909d048abe9270718bc37db9d`
- 施工分支：`fix/20261102-mysql84-migration-deadlock`
- 实例：MySQL Community Server `8.4.8`，`127.0.0.1:13389`，`REPEATABLE-READ`。
- 数据库为本轮新建的专用合成测试库。仅从正式初始化 SQL 和 V4_011–V4_013 建库；未连接 3306，未使用真实人口数据。
- 未修改数据库 Schema、API DTO 或审批/执行状态机。

## 旧实现复现与锁证据

先把并发门禁改为只接受 HTTP 200/409，再对未修改的生产代码运行。旧实现返回 `[迁出 409, 迁入 500]`，MySQL 报 `1213 / SQLSTATE 40001`。数据库在事务回滚后满足原子性断言，但严格门禁输出 `ATOMICITY_PASS` 和 `RELIABILITY_FAIL`，JUnit 为 1 failure、0 errors、0 skipped。旧门禁允许 HTTP 500，因此会把该结果误判为通过。

实时 `performance_schema.data_lock_waits` 采样到连接 124 等待 `household.PRIMARY`，由连接 125 阻塞。`SHOW ENGINE INNODB STATUS` 的脱敏等待环如下：

| MySQL 连接 | 当前 SQL | 已持有记录锁 | 等待记录锁 |
|---|---|---|---|
| 124 | `lockHousehold`: `SELECT ... FROM household WHERE household_id=? FOR UPDATE` | `migration_in.PRIMARY`，X、record-only | `household.PRIMARY`，X、record-only |
| 125 | `markHouseholdPending`: `UPDATE household ... AND NOT EXISTS (... migration_in ...)` | `household.PRIMARY`，X、record-only | `migration_in.PRIMARY`，S、record-only |

死锁牺牲事务 1（连接 124）并回滚。对应源码顺序是迁入先 `lockIn`，随后锁户籍和家庭户；迁出先锁户籍、家庭户，再由 `markHouseholdPending` 的 `NOT EXISTS` 访问在途迁入记录。MySQL 锁图中的循环只涉及这两张表的主键记录；记录锁标记为 `locks rec but not gap`，没有观察到 gap/next-key、`residence` 或 `household_member` 锁参与这个等待环。`migration_in` 同时具有 `idx_migration_in_to_household_id`；当前索引下该条件的只读谓词计划使用此二级索引，实际死锁锁图记录的是相应 `migration_in.PRIMARY` 行锁。

## 最小生产修复

迁入执行现在先非锁定读取迁入记录以取得目标家庭户，再锁定目标家庭户、锁定迁入记录，并校验目标户 ID 未变化后继续原版本、材料和业务检查。这样迁入不再持有 `migration_in` 行锁等待 `household`，与迁出持有 `household` 后检查在途迁入的顺序一致；原有事务、版本检查和业务状态条件保持不变。

`GlobalExceptionHandler` 只在异常 cause/`SQLException.getNextException()` 链中同时找到 MySQL error code `1213` 和 SQLSTATE `40001` 时返回 HTTP 409，提示“业务数据正在被并发修改，请刷新后重试”。它不回显 SQL，也不重放请求。锁等待超时 `1205`、单独的 `40001`、错误码/状态组合不匹配仍走原 HTTP 500 通道；迁出故障注入 `1644 / 45000` 也仍返回通用 500。

## MySQL 结果

最终验收通过 `MySql84CriticalAcceptanceIT`：8 tests、0 failures、0 errors、0 skipped。核心迁入/最后成员迁出竞争重复 10 轮：

| 轮次 | 请求线程（出/入） | Performance Schema 等待（等待连接 → 阻塞连接） | HTTP（出/入） | 数据库最终状态 |
|---:|---:|---|---|---|
| 1 | 62 / 63 | 10 → 9 | 409 / 200 | 迁入 COMPLETED；迁出 APPROVED；家庭户 ACTIVE；有效成员/户籍各 2；原户主唯一 HEAD |
| 2 | 65 / 66 | 9 → 10 | 409 / 200 | 同上 |
| 3 | 68 / 69 | 10 → 9 | 409 / 200 | 同上 |
| 4 | 71 / 72 | 10 → 9 | 409 / 200 | 同上 |
| 5 | 74 / 75 | 9 → 10 | 409 / 200 | 同上 |
| 6 | 77 / 78 | 9 → 10 | 409 / 200 | 同上 |
| 7 | 80 / 81 | 9 → 10 | 409 / 200 | 同上 |
| 8 | 83 / 84 | 10 → 9 | 409 / 200 | 同上 |
| 9 | 86 / 87 | 10 → 9 | 409 / 200 | 同上 |
| 10 | 89 / 90 | 10 → 9 | 409 / 200 | 同上 |

十轮均检查两条申请主/业务状态、有效成员和户籍数量、唯一有效户主及 `head_person_id`、归档和 `sys_approval_log` / `operation_log` 执行审计。迁入成功一次且有对应执行审计；迁出冲突申请没有执行审计或归档。每轮观察到的是 `household.PRIMARY` 上的串行等待，不是死锁环；本次运行 MySQL 1213 计数未增加，HTTP 500 为 0，全部输出 `ATOMICITY_PASS` / `RELIABILITY_PASS`。

同一迁出申请并发执行得到 `[200,409]`，之后显式重复仍返回 409；归档和成功审计各 1 条。迁入版本冲突先返回 409，确认没有户籍、成员或执行审计写入，再以正确版本显式重试并返回 200。原户主交接/迁回、无户主最后成员迁出及后续销户均通过。迁出执行触发 `1644 / 45000` 故障后返回通用 500，申请仍为 APPROVED，家庭户、户籍、成员、人员状态和归档均完整回滚，未产生成功审计。

曾有两次 MySQL 重跑在 `@BeforeEach` 实例身份检查阶段因专用实例已关闭而报连接错误，没有进入业务 API。最终验收将实例启动、测试和关闭放在同一执行中完成；上述 8/8 统计来自该最终完整运行。

## H2 回归与未验证范围

- `.\mvnw.cmd clean test`：475 passed、0 failed、0 errors、0 skipped，共 39 个 Surefire 测试类。原基线为 473 项；增加的 2 项是死锁异常映射单测。MySQL `*IT` 类由单独专项入口执行，不会被普通 H2 测试连接。
- MySQL 证据限定于 8.4.8、REPEATABLE-READ 和该测试实例；未覆盖其他 MySQL 8.4 小版本、生产负载或长期竞争压力。
- 对生产中未观察到的其他锁环不作“全部消除”结论。发生 MySQL lock-wait timeout 1205 时仍按原 HTTP 500 处理，未做自动重试。

## 资源与修改文件

测试结束后本轮 MySQL 和 Java 测试进程已停止，`127.0.0.1:13389` 无监听；预先存在的 `MySQL84` / 3306 服务未纳入本轮连接或停止操作。任务临时目录 ACL 仅允许当前账户、SYSTEM 和 Administrators；随机实例口令及 API 测试口令以当前用户 DPAPI 密文保存在目录内，未提交。用户已授权清理精确目录 `C:\Users\jxgm\AppData\Local\Temp\population_miniterm_mysql84_deadlock_20261102_c38ec0a384`，但标准 `Remove-Item` 被自动审批审核器以 `blocked by policy` 拒绝。未尝试替代删除方式；该目录保留，状态为 `CLEANUP_BLOCKED`。

代码修改：

- `src/main/java/com/wjx871/population/migration/MigrationService.java`
- `src/main/java/com/wjx871/population/common/GlobalExceptionHandler.java`
- `src/test/java/com/wjx871/population/mysqlgate/MySql84CriticalAcceptanceIT.java`
- `src/test/java/com/wjx871/population/common/GlobalExceptionHandlerDeadlockTest.java`
- `doc/testing/2026-11-02-mysql84-deadlock-fix.md`
