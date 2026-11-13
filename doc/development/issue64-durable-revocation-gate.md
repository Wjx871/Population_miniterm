# #64 JWT 吊销持久化验收矩阵与两阶段门禁规范

当前状态：`#64 OPEN / DESIGN_CANDIDATE / RED_VERIFIED` · `TARGET_GREEN = NOT_RUN`

> [!IMPORTANT]
> 本矩阵用于指导下一轮生产实现与独立验收。
> 1. 凡本轮未执行的真实 MySQL 8.4、异常宕机或未落地的方案 A 专项场景，均严格标注为 **`NOT_RUN`**，绝不将设计预期或 `SHARED_REDIS_REQUIRED` 基线测试冒充为 `DURABLE_DB_REQUIRED` 的动态通过。
> 2. 禁止将两个独立 JVM 各自挂载的独立 H2 内存库冒充“共享权威持久数据库”用于 `TARGET_GREEN` 验收；`TARGET_GREEN` 必须连接同一独立 MySQL 8.4 测试数据库（或具备明确跨 JVM 共享语义的独立持久测试库），严禁连接生产数据库。

---

## 1. 三层验证语义与可执行门禁契约（审查阻断项 C、D、F、G、H 及最终收口）

验证体系严格区分为 **`BASELINE_RED`** 与未来目标验证的 **三个独立层级**（`TARGET_PREREQUISITE_CHECK`、`TARGET_SCENARIO_PASS`、`TARGET_GREEN_PASS`），**前两者无论单独如何通过均绝不能替代全局 `TARGET_GREEN_PASS`**（均仅使用合成测试账号 `population`/`viewer` 与完全隔离测试环境）：

### 阶段一：`BASELINE_RED`（基线缺陷复现门禁 — 当前可执行）

- **运行模式**：固定 `SHARED_REDIS_REQUIRED`（仅用于表征基线结构性漏洞：空 Redis 恢复后未过期旧 JWT 返回 HTTP `200`）；保留原 `Redis EXISTS marker == 1` 严格前置校验。
- **Maven opt-in 基线 JUnit 入口**：[Issue64DurabilityRedGateTest.java](../../src/test/java/com/wjx871/population/security/Issue64DurabilityRedGateTest.java)
  - 开关：`@EnabledIfSystemProperty(named = "issue64.red", matches = "true")`（常规 `.\mvnw.cmd test` 跳过该 opt-in 门禁）。
  - 触发命令（PowerShell）：
    `.\mvnw.cmd test "-Dissue64.red=true" "-Dtest=Issue64DurabilityRedGateTest"`
- **双 JVM + 真实隔离 Redis 8.10.3 Python 门禁**：[issue64_red_gate.py](../../scripts/issue64/issue64_red_gate.py)（配套自检 [test_issue64_red_gate.py](../../scripts/issue64/test_issue64_red_gate.py)）
  - 触发命令：
    `python scripts/issue64/issue64_red_gate.py --stage baseline-red --evidence-out doc/development/evidence/issue64-durability-design/round4-baseline-red-summary.json`

### 目标验证层级 1：`TARGET_PREREQUISITE_CHECK`（前置预检 — 禁止替代 `TARGET_GREEN_PASS`）

- **独立 JUnit 预检入口**：[Issue64TargetPrerequisiteCheckTest.java](../../src/test/java/com/wjx871/population/security/Issue64TargetPrerequisiteCheckTest.java)（`@EnabledIfSystemProperty(named = "issue64.target.prereq", matches = "true")`）
- **定位限制**：仅核查 `DURABLE_DB_REQUIRED` 枚举、显式 opt-in、隔离测试库命名及参数完备性；**即使 `TARGET_PREREQUISITE_CHECK` 通过（PASS），也绝不代表 `DURABLE_DB_REQUIRED` 运行态正确、`TARGET_GREEN` 已通过或生产实现已完成；禁止仅凭 `src/main` 出现 `DURABLE_DB_REQUIRED` 字符串即认定实现完成**。

### 目标验证层级 2：`TARGET_SCENARIO_PASS`（局部 HTTP / 恢复场景通过 — 禁止替代全局 `TARGET_GREEN_PASS`）

- 指双 JVM 在指定场景（例如空 Redis 恢复后旧 JWT 在 A/B 均返回 `401` 且有效新 JWT 返回 `200`）满足 HTTP 断言。
- **硬性约束**：**严禁将 `TARGET_SCENARIO_PASS` 直接输出为全局 `TARGET_GREEN_PASS`（退出码 `0`）**；若后续第 6/7 项动态条件尚未执行，全局判决只能是 **`INCOMPLETE`（退出码 `1`）**。

### 目标验证层级 3：`TARGET_GREEN_PASS`（全局验收门禁 — 本轮状态：`NOT_RUN`）

- **7 项独立动态验收证据**（`evaluate_target_green_contract` 逐项记为 `PASS` / `FAIL` / `NOT_RUN`，仅当全部 7 项均为 `PASS` 时才允许输出 `TARGET_GREEN PASS` 退出码 `0`）：
  1. `durable_db_mode_active`：目标模式 `DURABLE_DB_REQUIRED` 已在 A/B 运行态实际启用；
  2. `shared_authoritative_db`：双 JVM A/B 共用**同一独立权威持久测试数据库**（严禁 `jdbc:h2:mem:`）；
  3. `mysql_revocation_committed`：经数据库查询实证 MySQL 主库 `jwt_revocation` 已提交对应 `jti_sha256` 撤销记录（**不可仅凭 HTTP 401 或 Redis 存在标记宣称持久化通过**）；
  4. `post_empty_redis_old_jwt_401`：空 Redis 恢复后未过期旧 JWT 在 A/B 均返回 `401`；
  5. `post_empty_redis_fresh_jwt_200`：有效未撤销新 JWT 返回 `200`；
  6. `mysql_fault_fail_closed`：MySQL 不可用时认证与注销均 fail-closed 返回 `503`（未观察到 `503` 判 `FAIL`；尚未接入真实故障注入钩子时保留 `NOT_RUN`）；
  7. `checkpoint_invalid_fail_closed`：检查点行缺失或 `UNINITIALIZED` / `INVALID` 时 fail-closed 返回 `503`（未观察到 `503` 判 `FAIL`；尚未接入钩子时保留 `NOT_RUN`）。
- **部分完成保护契约**：任一项未执行（`NOT_RUN`）时全局判定为 **`INCOMPLETE`（退出码 `1`）**并在摘要 `incomplete_criteria` 中明确列出未完成项；任一项 `FAIL` 时判定 **`ERROR`（退出码 `1`）**。
- **Redis 非阻断与环境错误规则（最终收口，阻断项 H）**：
  - `BASELINE_RED` 仍严格要求 `Redis EXISTS == 1`；
  - `TARGET_GREEN` 下 Redis 标记不存在是合法状态，且 `Redis EXISTS` 连接失败或超时被非阻断捕获，**不得直接把 MySQL 持久吊销判失败**，MySQL 正常时继续基于 MySQL 权威状态与 HTTP 行为判断安全性；
  - 但当测试环境自身的 Redis 进程/容器异常导致无法完成“Redis 空库恢复后旧 JWT 仍为 401”等特定恢复验证（如 `empty_dir_verified=False` 或重连失败）时，必须明确输出 **`ERROR`（退出码 `1`）**，严禁伪造 `PASS`。
- **测试数据库安全隔离防误操作护栏（阻断项 G）**：仅允许显式 opt-in `--allow-target-green`；库名强制匹配 `^issue64_test_[a-z0-9_]{4,40}$` 并禁止 `population_miniterm`；默认限制 loopback 或 `--approved-test-db-hosts` 显式批准主机；密码仅从环境变量读取；启动 JVM 前先完成真实测试库身份验证；明确命名/主机检查只是前置护栏而非物理隔离证明。
- **独立目标验收入口**：
  - Python：`python scripts/issue64/issue64_red_gate.py --stage target-green --allow-target-green --shared-jdbc-url "jdbc:mysql://127.0.0.1:3306/issue64_test_xxx" --shared-db-user issue64_tester ...`
- **未实现保护契约**：在 `src/main` 尚未完成方案 A、未传 `--allow-target-green`、隔离校验失败、未验证测试库身份或第 6/7 项尚未执行前，严禁输出 `TARGET_GREEN PASS`（保持 **`TARGET_GREEN = NOT_RUN`**）。

### 严格三态分类契约（`evaluate_post_empty_recovery`，阻断项 D）

只有在**全部前置检查通过 + Redis 空目录切换核验通过 + Redis 已重连 + 请求观测完整 + `fresh_b_status == 200` + `a_old_status` 与 `b_old_status` 均属于 `{200, 401}`** 时，才允许输出 `PASS` 或 `RED`：

- **`PASS`（退出码 `0`）**：前置与环境可靠、`fresh_b_status == 200`、且 `a_old_status == 401` 且 `b_old_status == 401`。
- **`RED`（退出码 `2`）**：前置与环境可靠、`fresh_b_status == 200`、A/B 对旧令牌状态均为有效 `{200, 401}` 且至少一个为 `200`。
- **`ERROR`（退出码 `1`）**：任一环境异常/前置失败/空目录未核验/`fresh_b_status != 200`（如 `500`/`503`）/A 或 B 旧令牌返回 `500`/`503` 等异常状态码（即便另一实例曾出现 `200` 也必须归类为 `ERROR` 而非 `RED` 或 `PASS`）。

---

## 2. 29 项生产验收测试矩阵（含审查最终时区、隔离与全局 PASS 收口项）

| # | 场景名称 | 测试环境 | 操作步骤 | 预期 HTTP 行为 | 数据库 / Redis 断言 | 覆盖安全不变量 | 本轮执行状态 | 证据路径 |
|---:|---|---|---|---|---|---|---|---|
| 1 | **单 JVM** | 单 Spring Boot JVM + MySQL 8.4 + Redis（或 H2+Mock） | 1. 登录获取 JWT；2. `GET /api/auth/me`；3. `POST /api/auth/logout`；4. 再次 `GET /api/auth/me`。 | 登录 `200`；注销前 `200`；注销 `200`；注销后 `401`（`身份令牌已撤销`）。 | `jwt_revocation` 存在 1 条对应 `jti_sha256` 行；Redis 存在黑名单标记且带 `PXAT` 过期时间。 | INV-01, INV-02 | `EXECUTED_PASS`（基线无 DB；Redis+H2 单 JVM HTTP 通过） | [S5A1StrictRevocationHttpIntegrationTest.java](../../src/test/java/com/wjx871/population/security/S5A1StrictRevocationHttpIntegrationTest.java) / [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 2 | **双 JVM** | 独立 JVM A、B + 共享 MySQL 8.4 + 共享 Redis 8.x | 1. A 登录得 `T1`；2. B 用 `T1` 访问 `/api/auth/me`；3. A 注销 `T1`；4. B 立即连续访问 `/api/auth/me` 5 次。 | 注销前 B 为 `200`；A 注销 `200`；注销后 A、B 均 `401`。 | `jwt_revocation` 在主库已提交；两实例不依赖进程内本地映射。 | INV-01, INV-02, INV-05 | `EXECUTED_PASS`（基线无 DB；双 JVM + 真实 Redis 注销即时拒访通过） | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) / [TokenRevocationServiceConsistencyTest.java](../../src/test/java/com/wjx871/population/security/TokenRevocationServiceConsistencyTest.java) |
| 3 | **同一 JWT 多次查询** | 双 JVM + 共享 MySQL 8.4 + Redis | 注销 `T1` 后，分别向 A、B 连续发送 10 次 `GET /api/auth/me`。 | 10/10 均返回 `401`；命中 Redis 快速拒绝后仍一致，不发生缓存抖动放行。 | Redis 命中时无需打 MySQL；若清空 Redis 后再查，仍命中 MySQL 返回 `401`。 | INV-02, INV-03 | `NOT_RUN`（方案 A 含 MySQL 路径未实现；基线仅在 [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) 验证未丢数据前 401） | — |
| 4 | **同一 JWT 重复注销** | 单/双 JVM + MySQL 8.4 + Redis | 1. 对 `T1` 调用 `POST /api/auth/logout`（第 1 次）；2. 再次用 `T1` 调用 `POST /api/auth/logout`（第 2 次）；或并发重复提交。 | 第 1 次 `200`；第 2 次由于认证过滤器先拦截已撤销令牌返回 `401`；内部 `revoke()` 服务直调幂等不抛异常、不缩短 `expires_at_epoch_ms`。 | `jwt_revocation` 对同一 `jti_sha256` 仅存 1 行，`expires_at_epoch_ms` 单调不减。 | INV-01, INV-02 | `NOT_RUN`（`jwt_revocation` 表幂等 upsert 待下一轮实现） | — |
| 5 | **不同 JWT 相互独立** | 双 JVM + 隔离 Redis + 测试库 | 1. 同一用户登录得 `T1` 和 `T2`（不同 `jti`），另取其他用户 `T_other`；2. 注销 `T1`；3. 用 `T2`、`T_other` 访问 `/api/auth/me`。 | `T1` 返回 `401`；`T2` 与 `T_other` 保持 `200`。 | `jwt_revocation` 仅含 `sha256(T1.jti)`，不含 `T2` 与 `T_other`。 | INV-02, INV-06 | `EXECUTED_PASS`（基线 Redis 隔离验证通过；DB 版本 `NOT_RUN`） | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 6 | **Redis 停机** | 双 JVM + 共享 MySQL 8.4 + 停机 Redis | 1. 注销 `T1`，保留有效 `T2`；2. 停止 Redis；3. 用 `T1`、`T2` 分别访问 `/api/auth/me`，并访问 `/api/health` 与 `/api/auth/login`。 | **`BASELINE_RED`（旧模式）**：带令牌与登录 `503`，`/api/health` 为 `200`。<br>**`TARGET_GREEN`（方案 A）**：`T1` 经 MySQL 仍 `401`，`T2` 经 MySQL 降级可用 `200`，Redis 停机期间对 `T2` 调用注销仍提交 DB 返回 `200` 且立即全实例 `401`，`/api/health` 为 `200`（`redisStatus=DOWN`）。 | `RevocationEvents` 记录 `REVOCATION_CACHE_*_UNAVAILABLE` 告警，不输出密码/URI。 | INV-02, INV-03, INV-04, INV-06, INV-07 | `EXECUTED_PASS`（`BASELINE_RED` 旧模式 503 已测）；`TARGET_GREEN = NOT_RUN` | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 7 | **Redis 恢复原 RDB** | 双 JVM + 真实 Redis 8.10.3 | 1. 注销 `T1` 后 Redis 执行 `SAVE` 并正常 `SHUTDOWN NOSAVE`；2. 使用原数据目录重启 Redis；3. 等待重连后查询 `T1`、`T2`。 | 恢复后 A、B 对 `T1` 返回 `401`，对 `T2` 返回 `200`。注：本项单独通过不代表具备持久化保证。 | 原 `dump.rdb` 加载成功，`DBSIZE` 恢复；方案 A 下即使 RDB 旧于 MySQL，仍以 MySQL 为准。 | INV-02 | `EXECUTED_PASS` | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 8 | **Redis 恢复空库** | 双 JVM + 真实 Redis 8.10.3（+ `TARGET_GREEN` 为共享隔离 MySQL 8.4） | 1. A 登录得 `T1` 并注销（A/B 均 401）；2. 停 Redis 并换用**全新空目录**启动（`DBSIZE=0`、无 `dump.rdb`）；3. 等待重连后用 `T1` 访问 A、B。 | **`BASELINE_RED`（`RED`）**：A、B 均返回 **`200`**。<br>**`TARGET_GREEN`（`PASS`）**：A、B 均返回 **`401`**，`T2` 返回 `200`。 | `BASELINE_RED` 核验切换前 `Redis EXISTS == 1`；`TARGET_GREEN` Redis 仅作观察指标，强制直查共享 MySQL `jwt_revocation` 确认已提交记录且切换后空目录 `EXISTS=0`。 | INV-02, INV-03, INV-05 | **`EXECUTED_RED`**（`BASELINE_RED` 已复现）；**`TARGET_GREEN = NOT_RUN`** | [round3-baseline-red-summary.json](evidence/issue64-durability-design/round3-baseline-red-summary.json) / [round3-junit-red-gate.log](evidence/issue64-durability-design/round3-junit-red-gate.log) |
| 9 | **Redis 丢失部分标记** | 双 JVM + 共享 MySQL 8.4 + Redis | 1. 先后注销 `T1`、`T2`；2. 停 Redis 后恢复到只包含 `T1` 的**旧时间点 RDB 快照**（或手动 `DEL` `T2` 标记）；3. 用 `T2` 访问 A、B。 | `T1`、`T2` 均返回 `401`（不得因 `T2` 在 Redis MISS 而返回 200）。 | Redis 仅见 `T1`、未见 `T2`；MySQL `jwt_revocation` 含 `T1` 与 `T2`，MISS 回源命中后可选回填 Redis。 | INV-03, INV-05 | `NOT_RUN`（需方案 A 实现） | — |
| 10 | **Redis 数据过期和淘汰** | 双 JVM + 共享 MySQL 8.4 + Redis（`maxmemory 1mb` + `allkeys-lru` / 人工 `FLUSHDB`） | 1. 注销 `T1`；2. 灌入大量无关键触发 Redis 内存淘汰使 `T1` 黑名单键被驱逐（及对照 `FLUSHDB`）；3. 用 `T1` 访问 A、B。 | A、B 对 `T1` 仍返回 `401`；不被 `maxmemory` 淘汰绕过。 | Redis 中 `blacklist:<jti_sha256>` 已被驱逐（`EXISTS=0`）；MySQL `jwt_revocation` 仍完整保留至过期后宽限期。 | INV-03 | `NOT_RUN`（需方案 A 实现） | — |
| 11 | **Redis 正常但 MySQL 不可用** | Spring Boot HTTP + 正常 Redis + 故障/断网/超时 MySQL | 1. 保持 Redis 正常在线且无某有效令牌 `T2` 的黑名单键；2. 切断 MySQL 或注入查询超时；3. 用 `T2` 调 `/api/auth/me`、调 `/api/auth/logout`、调 `/api/auth/login` 与 `/api/health`。 | 带令牌认证（Redis MISS 时）、注销、有效凭据登录预检均 **fail-closed 返回 `503`**；错误密码登录仍优先 `401`；`/api/health` 仍 `200`（`database=DOWN`）。 | Redis 不可达判定与正常 MISS 均不得在 MySQL 不可用时放行；`SecurityContextHolder` 为空；响应无 JDBC URL/密码。 | INV-04, INV-06, INV-07 | `NOT_RUN`（需方案 A 实现；当前基线未查 MySQL 吊销表） | — |
| 12 | **MySQL 已提交但 Redis 写入失败（阻断项 H）** | 双 JVM + 共享 MySQL 8.4 + ACL 禁 `SET` 或断开 Redis | 1. 对应用 Redis ACL 执行 `-set`（或断开 Redis）；2. 在 A 对 `T1` 调用 `POST /api/auth/logout`；3. 立即用 `T1` 访问 B `/api/auth/me`。 | **`v_new`（`DURABLE_DB_REQUIRED`）目标**：A 注销返回 **`200`**；Redis 无标记（仅观察指标），B 认证回源 MySQL 返回 **`401`**。<br>（注：仅若未来启用 **`v_bridge` 与 `v_old` 共存阶段**，见场景 #24，必须返回 **`503`**） | `jwt_revocation` 已实证存在已提交 `T1` 记录；`operation_log` 记 `LOGOUT SUCCESS`；Redis 无 `T1` 键**不中止验收**；`RevocationEvents` 记 `REVOCATION_CACHE_WRITE_UNAVAILABLE`。 | INV-01, INV-02, INV-05, INV-07 | `NOT_RUN`（基线当前 ACL `-set` 时注销返回 503，见第 1 轮记录） | — |
| 13 | **MySQL 事务回滚** | Spring Boot HTTP + Mock/故障注入 MySQL 异常 | 1. 注入 `jwt_revocation` 插入后提交前抛 `DataAccessException` 回滚；2. 对 `T1` 调 `POST /api/auth/logout`。 | 注销返回 **`503`**（`REVOCATION_STORE_UNAVAILABLE`）。 | MySQL `jwt_revocation` 无新增行；`operation_log` 无 `LOGOUT SUCCESS`；**Redis 中绝不允许写入 `T1` 黑名单**。 | INV-01, INV-04 | `NOT_RUN`（需方案 A 实现） | — |
| 14 | **注销与认证并发** | 双 JVM + 共享 MySQL 8.4 `REPEATABLE-READ` + 多线程压测 | 1. 线程组在 B 持续并发查询 `T1`；2. A 对 `T1` 发起注销并拿到 `200` 响应时刻记为 `t_ack`；3. 检查所有在 `t_ack` 之后发往 B 的认证请求。 | `t_ack` 之后发往 A、B 的请求 **100% 返回 `401`**，零例 `200`。 | 注销返回 `200` 时 MySQL 主库已 `COMMIT`；B 认证读不复用过期事务快照，直接读到最新已提交行。 | INV-01, INV-02, INV-05 | `NOT_RUN`（仅模拟并发单测存在，真实 MySQL+双 JVM 并发 `NOT_RUN`） | — |
| 15 | **两实例同时注销** | 双 JVM + 共享 MySQL 8.4 | 使用 `CountDownLatch` / 屏障在 A、B 同时对同一 `T1` 发起 `POST /api/auth/logout`（或服务层并发 `revoke`）。 | 至少一方或双方（若同时通过前置过滤）均幂等完成，不返回 `500` 或死锁；随后任何查询均为 `401`。 | `jwt_revocation` 仅 1 行，无 `DuplicateKeyException` 外泄为 409/500。 | INV-01, INV-02 | `NOT_RUN`（需方案 A 实现） | — |
| 16 | **应用重启** | 双 JVM（重启前后）+ 共享 MySQL 8.4 + Redis | 1. A 注销 `T1`；2. 停止 A、B 并清空（或重启）Redis；3. 启动新实例 A2、B2；4. 查询 `T1`、`T2`。 | A2、B2 对 `T1` 均返回 `401`，对 `T2` 返回 `200`。 | 重启后不依赖任何内存 Map，通过 MySQL `jwt_revocation` 保持状态连续。 | INV-02, INV-03 | `NOT_RUN`（基线仅测带 RDB 重启，未测空 Redis + 应用重启） | — |
| 17 | **Redis 非正常终止** | 双 JVM + 共享 MySQL 8.4 + 真实 Redis 8.x | 1. A 注销 `T1`；2. 立即对 Redis 进程执行强制 `SIGKILL` / `TerminateProcess`（未经 `SAVE`）；3. 分别以损坏/空目录或未落盘状态重启 Redis；4. 查询 `T1`、`T2`。 | 若 Redis 丢失未刷盘写入，A、B 仍通过 MySQL 主库对 `T1` 返回 `401`，`T2` 返回 `200`。 | 证明不依赖 Redis 内存或 AOF/RDB 是否及时刷盘即可保障 INV-01~03。 | INV-01, INV-03 | `NOT_RUN` | — |
| 18 | **令牌过期边界** | 单/双 JVM + `MutableClock` / 短 `JWT_EXPIRE_MINUTES` | 1. 构造 `exp = T0 + 2s` 的注销记录；2. 验证 `T0 + 1s`（未过期）、`T0 + 2s`（恰过期）、`T0 + 5m + 2s`（过宽限期）；3. 并发执行 `cleanupExpired()` 与认证。 | 未过期被拒 `401`（已撤销）；恰过期及之后被 `JwtService` 拒 `401`（已过期）；清理任务在宽限期内不删除该行，超过宽限期后批量安全删除。 | 清理条件 `expires_at_epoch_ms <= now - 300_000` 保证与认证无竞争间隙。 | INV-02, INV-03 | `NOT_RUN`（基线仅测了本地 Map 时钟边界，未测 DB 清理边界） | — |
| 19 | **维护窗口升级及旧令牌迁移** | 模拟 `v_old` → 停旧启新（`V4_020` + `(UNIX_TIMESTAMP() + 1) * 1000` 激活）→ `v_new` | 1. `v_old` 登录 `T_legacy1`（注销仅在 Redis）与 `T_legacy2`（未注销）；2. 执行 `V4_020`（初始为 `UNINITIALIZED`）并执行显式激活 SQL 设置 `READY` + `(UNIX_TIMESTAMP() + 1) * 1000`；3. 等待真实 UTC 跨过边界启动 `v_new` 并清空 Redis；4. 验证 `T_legacy1`、`T_legacy2` 与 `T_new`。 | `T_legacy1` 与 `T_legacy2` 均返回 `401`（**绝不允许空表 + `0` 水位放行旧令牌**）；`T_new` 返回 `200`；普通重跑初始化脚本不得将水位重置回 `0`。 | `jwt_revocation_checkpoint` 为 `READY` 且水位单调持久化在 MySQL 主库；`v_new -> v_old` 回滚强制轮换 `JWT_SECRET`。 | INV-02, INV-03, INV-05 | `NOT_RUN`（需方案 A 实现后专项验收） | — |
| 20 | **大量令牌时的性能与清理** | MySQL 8.4 + 10,000~50,000 行合成 `jwt_revocation` 数据（含已过宽限期与未过期混合） | 1. 写入大批量合成记录；2. 压测认证查询延迟（Redis 命中 vs Redis MISS 回源 MySQL）及注销延迟；3. 运行有界清理批处理 `cleanupExpired()`。 | 认证/注销接口 P99 满足业务 SLA；清理不阻塞在线认证/注销（无长锁超时）。 | `EXPLAIN` 验证点查命中 `uk_jwt_revocation_jti_sha256`（`const`），清理命中 `idx_jwt_revocation_expires_ms` 且单批受 `LIMIT` 约束。 | INV-02, INV-06 | `NOT_RUN` | — |
| 21 | **检查点缺失或状态无效（阻断项 A）** | 单/双 JVM（`DURABLE_DB_REQUIRED`）+ 隔离 MySQL 8.4 | 分别构造 4 种破坏态并调用启动校验与受保护接口（有效未注销 `T2`）：(a) 删除 `GLOBAL_JWT_CUTOFF` 行；(b) `checkpoint_status = 'UNINITIALIZED'`；(c) `checkpoint_status = 'INVALID'`；(d) `READY` 但 `min_valid_iat_epoch_ms IS NULL` 或 `< 0`。 | **启动校验 fail-fast 拒绝启动**；运行期被篡改时认证/登录预检一律 **Fail-Closed 返回 HTTP `503`**（绝不 `COALESCE(..., 0)` 放行）。 | 清空 `SecurityContextHolder`；`RevocationEvents` 记 `REVOCATION_DB_UNAVAILABLE`。 | INV-04 | `NOT_RUN`（需方案 A 实现） | — |
| 22 | **检查点即时一致性与水位单调性（阻断项 B）** | 双 JVM A、B + 共享隔离 MySQL 8.4 + 正常 Redis | 1. A、B 先分别用有效 `T_alive`（未在 Redis 黑名单中）认证返回 `200`；2. 直接在 MySQL 主库执行单调水位推进至未来下一整秒并等待真实 UTC 跨秒后 `10ms` 内，**不重启 A/B、不发广播**；3. 立即向 A 和 B 使用 `T_alive` 发请求，并尝试注入非法回拨更新。 | **A、B 必须在推进后第一次请求立即返回 `401`**（0 秒本地缓存窗口）；水位回拨 SQL 被拒绝/不变更，普通重启脚本不将水位归零。 | 证明无 5 秒进程内 JVM 水位缓存；每次 MISS 认证直接读取 MySQL 主库最新 `min_valid_iat_epoch_ms`。 | INV-02, INV-05 | `NOT_RUN`（需方案 A 实现） | — |
| 23 | **`iat` 秒级截断 vs 毫秒水位边界（阻断项 A）** | 单 JVM + 共享隔离 MySQL 8.4 / `MutableClock` | 1. 在整秒内 `T = S.200s` 签发 `T_before`（JWT `iat = S`）；2. 在 `T_cut = S.500s` 按整秒上界对齐规则设置 `min_valid_iat_epoch_ms = (S + 1) * 1000` 并等待至 `(S + 1).050s`；3. 签发 `T_after`（`iat = S + 1`）。 | `T_before` 严格返回 **`401`**；`T_after` 严格返回 **`200`**（零同秒逃逸、零同秒误杀）。 | 验证 `(iatSec * 1000) < min_valid_iat_epoch_ms` 比较逻辑与整秒上界对齐策略严格匹配 RFC 7519。 | INV-02, INV-06 | `NOT_RUN`（需方案 A 实现） | — |
| 24 | **可选 `v_bridge` 阶段 Redis 故障注销双写失败** | 混合滚动过渡测试（`v_bridge` + 模拟仍在线的 `v_old`）+ MySQL + 断网 Redis | 1. 启用 `v_bridge` 双写强确认开关；2. 断开或 ACL 禁用 Redis；3. 在 `v_bridge` 上对 `T1` 调用 `POST /api/auth/logout`。 | 注销**必须 fail-closed 返回 `503`**（禁止在 `v_old` 可能在线时只写 MySQL 就返回 `200`）。 | `operation_log` 无 `LOGOUT SUCCESS`；确认切到 `v_old` 时不会因未写旧 Redis 而放行 `T1`。 | INV-01, INV-02, INV-04 | `NOT_RUN`（仅若未来启用 `v_bridge` 滚动路径时必测） | — |
| 25 | **MySQL PITR / 快照回滚安全 SOP** | 双 JVM + 隔离 MySQL 8.4 + 模拟快照还原 | 1. 在 `T_b` 做快照备份；2. 之后用户注销 `T1`（并推进水位）；3. 将 MySQL 还原至 `T_b` 快照；4. 按恢复 SOP 执行 `JWT_SECRET` 轮换与使用 `(UNIX_TIMESTAMP() + 1) * 1000` 推进 `GLOBAL_JWT_CUTOFF` 后等待跨秒再启服务。 | 未执行 SOP 前禁止接入流量；执行 SOP 后使用 `T1` 与任何快照前签发令牌均返回 **`401`**，重新登录新令牌返回 `200`。 | 证明 PITR 还原不会因回滚吊销表和检查点而静默复活旧令牌。 | INV-02, INV-03, INV-05 | `NOT_RUN`（运维/演练验收项） | — |
| 26 | **Python 分类器与阶段自检（阻断项 D）** | 本机隔离单元测试 `unittest` | 校验 `(oldA=200, oldB=200, fresh=500)`、`(oldA=200, oldB=401, fresh=200)`、`(oldA=401, oldB=401, fresh=200)`、`(oldA=503, oldB=401, fresh=200)`、`(oldA=200, oldB=500, fresh=200)`、`empty_dir_verified=False` 及 `--stage target-green` 未满足前置条件。 | 第 1/4/5/6 项与未就绪 `target-green` 一律返回 `ERROR`（退出码 `1`）；第 2 项返回 `RED`（`2`）；第 3 项返回 `PASS`（`0`）。 | 证明不把异常 `500`/`503` 或未核验目录包装为 `RED` 或 `PASS`。 | INV-04, INV-07 | **`EXECUTED_PASS`**（见第 3 轮证据文档） | [test_issue64_red_gate.py](../../scripts/issue64/test_issue64_red_gate.py) / [round3-python-selftest.log](evidence/issue64-durability-design/round3-python-selftest.log) |
| 27 | **MySQL 8.4 跨时区 UTC 水位计算验收（阻断项 E）** | 隔离测试 MySQL 8.4（依次设置 `SET SESSION time_zone = '+00:00'`、`'+08:00'`、`'-05:00'`） | 分别在三种会话时区下执行激活表达式 `(UNIX_TIMESTAMP() + 1) * 1000` 并对比当前真实 `UNIX_TIMESTAMP() * 1000`（及对照验证 `UNIX_TIMESTAMP(UTC_TIMESTAMP(3))` 确实发生 `-8h`/`+5h` 错误偏移）；随后等待 UTC 达到 `cutoff` 再签发新 JWT。 | 三种会话时区下 `(UNIX_TIMESTAMP() + 1) * 1000` 均落在同一真实 UTC 秒上界（误差仅跨秒 `<= 1s`），**零按小时偏移**；旧令牌严格 `401`、等跨秒后新令牌严格 `200`。 | 实证 `cutoff` 与会话 `time_zone` 无关；`UNINITIALIZED` 绝不自动变为 `READY`。 | INV-02, INV-05 | **`NOT_RUN`**（本轮未连接/操作真实 MySQL 8.4，严禁宣称已动态验证） | — |
| 28 | **目标数据库隔离护栏与非阻断 Redis 自检（阻断项 F、G、H）** | Python 单元测试 + `Issue64TargetPrerequisiteCheckTest` | 验证 `target-green` 防误操作护栏（合法 `issue64_test_*` 参数、`population_miniterm`、缺 `--allow-target-green`、`jdbc:h2:mem:`、未批准远端主机、未验证 DB 身份）、缺失权威 DB 撤销记录判 `ERROR`、Redis 标记缺失仅观察不阻断。 | 不合规参数/缺失 opt-in/身份未确认均立即 **`ERROR`（退出码 `1`）且不启动任何测试 JVM**；合规护栏仅代表预检通过，不冒充 `TARGET_GREEN PASS`。 | 环境变量传递密码，命令行/日志/JSON 均无密码与生产凭据。 | INV-04, INV-07 | **`EXECUTED_PASS`**（Python 隔离自检通过；真实 MySQL 动态阶段 `NOT_RUN`） | [test_issue64_red_gate.py](../../scripts/issue64/test_issue64_red_gate.py) / [round3-python-selftest.log](evidence/issue64-durability-design/round3-python-selftest.log) |
| 29 | **`TARGET_GREEN_PASS` 7 项收口与 Redis 异常边界自检** | Python 单元测试（`evaluate_target_green_contract`、`observe_target_redis_marker` 等） | 校验：(1) 仅满足前 5 项、第 6/7 项 `NOT_RUN`；(2) 全部 7 项 `PASS`；(3) MySQL 故障未观察到 `503`；(4) 检查点异常未观察到 `503`；(5) 目标模式 `Redis EXISTS` 抛连接异常 vs 恢复环境自身异常；(6) 原 `BASELINE_RED` 仍输出 `RED`。 | 场景 (1) 输出 **`INCOMPLETE`（退出码 `1`）**且禁发 `PASS`；(2) 输出 **`PASS`（退出码 `0`）**；(3)(4) 输出 **`ERROR`（退出码 `1`）**；(5) `EXISTS` 抛异常非阻断继续查 DB，但恢复环境损坏报 `ERROR`；(6) 输出 **`RED`（退出码 `2`）**。 | 三层字段 `TARGET_PREREQUISITE_CHECK`、`TARGET_SCENARIO_PASS`、`TARGET_GREEN_PASS` 清晰分离；`incomplete_criteria` 明示未完成项。 | INV-03, INV-04, INV-07 | **`EXECUTED_PASS`**（第 4 轮 Python 隔离合同自检通过；真实 MySQL 动态阶段 `NOT_RUN`） | [test_issue64_red_gate.py](../../scripts/issue64/test_issue64_red_gate.py) / [round4-python-selftest.log](evidence/issue64-durability-design/round4-python-selftest.log) |
