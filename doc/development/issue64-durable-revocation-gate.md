# #64 JWT 吊销持久化验收矩阵与可执行门禁规范

当前状态：`#64 OPEN / DESIGN_CANDIDATE / RED_VERIFIED / REVIEW_HARDENED`（`TARGET_GREEN = NOT_RUN`）

> [!IMPORTANT]
> 本矩阵严格分离**基线缺陷复现门禁（`BASELINE_RED`）**与**未来目标模式生产安全验收门禁（`TARGET_GREEN`）**。凡本轮未执行的真实 MySQL 8.4、`DURABLE_DB_REQUIRED` 或检查点/回滚专项场景，均严格标注为 **`NOT_RUN`**（且当前 **`TARGET_GREEN = NOT_RUN`**），绝不将 `SHARED_REDIS_REQUIRED` 测试结果或设计预期冒充为目标模式 `PASS`。

---

## 1. 两阶段可执行门禁分离与判定语义

### 1.1 阶段划分：`BASELINE_RED` vs `TARGET_GREEN`

| 门禁阶段 | 目标运行模式 | 数据库要求 | Redis 宕机预期行为 | 空 Redis 恢复后预期 | 当前状态 | 适用边界 |
|---|---|---|---|---|---|---|
| **`BASELINE_RED`** | `SHARED_REDIS_REQUIRED`（基线旧模式） | 允许两 JVM 各自独立内存 H2（仅测共享 Redis 行为） | `B_during_outage_fresh` 返回 **`503`**（无 Redis 即阻断） | 已注销旧 JWT 返回 **`200`** → 判 **`RED`**（退出码 `2`） | `EXECUTED_RED`（可复现） | **仅用于表征基线漏洞**，绝不用于验收目标架构。 |
| **`TARGET_GREEN`** | `DURABLE_DB_REQUIRED`（未来目标模式） | **两 JVM 必须共享同一权威测试数据库**：独立本地 MySQL 8.4 测试库（如 `jdbc:mysql://127.0.0.1:3306/population_issue64_test`）或显式共享语义测试库（如共享 H2 TCP/文件测试库）；**禁止用各自独立 `jdbc:h2:mem:` 冒充共享库，禁止连生产库**。 | 经 MySQL 安全降级可用：`old` 返回 **`401`**，`fresh` 返回 **`200`**，`/api/health` 返回 `200`（`redisStatus=DOWN`） | 已注销旧 JWT 在 A/B 上**均返回 `401`** 且 `fresh` 返回 **`200`** → 判 **`PASS`**（退出码 `0`） | **`NOT_RUN`**（目标模式未实现时，强制返回 `ERROR` / 退出码 `1`，绝不伪造 `PASS`） | 下一轮生产代码与 `V4_020` 落地后的**唯一合法生产安全验收入口**。 |

### 1.2 四类可执行入口（均仅使用合成测试账号 `population`/`viewer`，绝不触碰生产数据）

1. **JUnit `BASELINE_RED` 门禁（旧模式缺陷表征）**：[Issue64DurabilityRedGateTest.java](../../src/test/java/com/wjx871/population/security/Issue64DurabilityRedGateTest.java)
   - 开关：`@EnabledIfSystemProperty(named = "issue64.red", matches = "true")`（常规 `.\mvnw.cmd test` 跳过）。
   - 触发命令：`.\mvnw.cmd test "-Dissue64.red=true" "-Dtest=Issue64DurabilityRedGateTest"`
   - 范围：固定表征 `SHARED_REDIS_REQUIRED` 基线空库复活漏洞，不可替代 `TARGET_GREEN`。
2. **JUnit `TARGET_GREEN` 独立门禁入口（目标模式守卫）**：[Issue64DurableDbTargetGateTest.java](../../src/test/java/com/wjx871/population/security/Issue64DurableDbTargetGateTest.java)
   - 开关：`@EnabledIfSystemProperty(named = "issue64.target.green", matches = "true")`（常规 `.\mvnw.cmd test` 跳过，且不引入未实现生产类的编译期依赖）。
   - 触发命令（仅未来实现后运行）：`.\mvnw.cmd test "-Dissue64.target.green=true" "-Dissue64.target.jdbcUrl=jdbc:mysql://127.0.0.1:3306/population_issue64_test" "-Dtest=Issue64DurableDbTargetGateTest"`
   - 未实现前直接失败并报错 `TARGET_GREEN_NOT_IMPLEMENTED`（`TARGET_GREEN=NOT_RUN`），拒绝回退到 `SHARED_REDIS_REQUIRED`。
3. **Python 双 JVM + 隔离 Redis 8.10.3 实时门禁**：[issue64_red_gate.py](../../scripts/issue64/issue64_red_gate.py)
   - `BASELINE_RED` 命令：
     `python scripts/issue64/issue64_red_gate.py --gate-stage BASELINE_RED --evidence-out doc/development/evidence/issue64-design-review-hardening/live-baseline-red-summary.json`
   - `TARGET_GREEN` 命令（当前前置守卫直接返回 `ERROR` 退出码 `1`）：
     `python scripts/issue64/issue64_red_gate.py --gate-stage TARGET_GREEN --target-db-url jdbc:mysql://127.0.0.1:3306/population_issue64_test`
4. **Python 分类器与前置守卫自检**：[test_issue64_red_gate.py](../../scripts/issue64/test_issue64_red_gate.py)
   - 触发命令：`python -m unittest -v scripts/issue64/test_issue64_red_gate.py`

### 1.3 `evaluate_post_empty_recovery()` 严格分类契约（阻断项 D 加固）

**先决条件铁律**：仅当全部前置检查通过（`prechecks_ok=True`）、空数据目录切换已实证核验（`empty_dir_verified=True`）、Redis 重连健康检查恢复（`redis_up=True`）、全部 HTTP 状态完整观测、**且 `fresh_b_status == 200` 与 `a_old_status, b_old_status ∈ {200, 401}`** 时，才允许判为 `PASS` 或 `RED`。任何不满足条件的情况一律判为 **`ERROR`（退出码 `1`）**：

| 用例条件（`prechecks_ok`, `empty_dir_verified`, `redis_up`, `old A`, `old B`, `fresh B`） | 判定结果 | 退出码 | 说明 |
|---|---|---|---|
| `True, True, True, 401, 401, 200` | **`PASS`** | **`0`** | 全实例拒绝对旧令牌放行且新令牌可用 |
| `True, True, True, 200, 401, 200`（或 `200, 200, 200` / `401, 200, 200`） | **`RED`** | **`2`** | 新令牌正常但至少一个实例放行了已注销旧令牌 |
| `True, True, True, 200, 200, 500` | **`ERROR`** | **`1`** | `fresh` 为 `500`（非 `200`）属环境异常，禁止误判 `RED` |
| `True, True, True, 503, 401, 200` | **`ERROR`** | **`1`** | `old A` 在重连窗口后仍 `503`，不得视为有效分类 |
| `True, True, True, 200, 500, 200` | **`ERROR`** | **`1`** | `old B` 返回 `500`，不得忽视异常状态而判 `RED` |
| `True, False, True, 200, 200, 200`（及 `401, 401, 200`） | **`ERROR`** | **`1`** | Redis 未确认成功切换空目录，无论 HTTP 是否 `200` 一律 `ERROR` |
| `False, *, *, *, *, *` 或 `*, *, False, *, *, *` | **`ERROR`** | **`1`** | 前置失败、Redis 未重连或 HTTP 连接失败均判 `ERROR` |

---

## 2. 28 项生产验收测试矩阵（含检查点 Fail-Closed、即时一致性与安全恢复专项）

| # | 场景名称 | 测试环境 | 操作步骤 | 预期 HTTP 行为 | 数据库 / Redis 断言 | 覆盖安全不变量 | 本轮执行状态 | 证据路径 |
|---:|---|---|---|---|---|---|---|---|
| 1 | **单 JVM** | 单 Spring Boot JVM + MySQL 8.4 + Redis（或 H2+Mock） | 1. 登录获取 JWT；2. `GET /api/auth/me`；3. `POST /api/auth/logout`；4. 再次 `GET /api/auth/me`。 | 登录 `200`；注销前 `200`；注销 `200`；注销后 `401`（`身份令牌已撤销`）。 | `jwt_revocation` 存在 1 条对应 `jti_sha256` 行；Redis 存在黑名单标记且带 `PXAT` 过期时间。 | INV-01, INV-02 | `EXECUTED_PASS`（基线无 DB；Redis+H2 单 JVM HTTP 通过） | [S5A1StrictRevocationHttpIntegrationTest.java](../../src/test/java/com/wjx871/population/security/S5A1StrictRevocationHttpIntegrationTest.java) / [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 2 | **双 JVM** | 独立 JVM A、B + 共享 MySQL 8.4 + 共享 Redis 8.x | 1. A 登录得 `T1`；2. B 用 `T1` 访问 `/api/auth/me`；3. A 注销 `T1`；4. B 立即连续访问 `/api/auth/me` 5 次。 | 注销前 B 为 `200`；A 注销 `200`；注销后 A、B 均 `401`。 | `jwt_revocation` 在主库已提交；两实例不依赖进程内本地映射。 | INV-01, INV-02, INV-05 | `EXECUTED_PASS`（基线无 DB；双 JVM + 真实 Redis 注销即时拒访通过） | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) / [TokenRevocationServiceConsistencyTest.java](../../src/test/java/com/wjx871/population/security/TokenRevocationServiceConsistencyTest.java) |
| 3 | **同一 JWT 多次查询** | 双 JVM + 共享 MySQL 8.4 + Redis | 注销 `T1` 后，分别向 A、B 连续发送 10 次 `GET /api/auth/me`。 | 10/10 均返回 `401`；命中 Redis 快速拒绝后仍一致，不发生缓存抖动放行。 | Redis 命中时无需打 MySQL；若清空 Redis 后再查，仍命中 MySQL 返回 `401`。 | INV-02, INV-03 | `NOT_RUN`（方案 A 含 MySQL 路径未实现；基线仅在 [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) 验证未丢数据前 401） | — |
| 4 | **同一 JWT 重复注销** | 单/双 JVM + MySQL 8.4 + Redis | 1. 对 `T1` 调用 `POST /api/auth/logout`（第 1 次）；2. 再次用 `T1` 调用 `POST /api/auth/logout`（第 2 次）；或并发重复提交。 | 第 1 次 `200`；第 2 次由于认证过滤器先拦截已撤销令牌返回 `401`；内部 `revoke()` 服务直调幂等不抛异常、不缩短 `expires_at_epoch_ms`。 | `jwt_revocation` 对同一 `jti_sha256` 仅存 1 行，`expires_at_epoch_ms` 单调不减。 | INV-01, INV-02 | `NOT_RUN`（`jwt_revocation` 表幂等 upsert 待下一轮实现） | — |
| 5 | **不同 JWT 相互独立** | 双 JVM + 隔离 Redis + 测试库 | 1. 同一用户登录得 `T1` 和 `T2`（不同 `jti`），另取其他用户 `T_other`；2. 注销 `T1`；3. 用 `T2`、`T_other` 访问 `/api/auth/me`。 | `T1` 返回 `401`；`T2` 与 `T_other` 保持 `200`。 | `jwt_revocation` 仅含 `sha256(T1.jti)`，不含 `T2` 与 `T_other`。 | INV-02, INV-06 | `EXECUTED_PASS`（基线 Redis 隔离验证通过；DB 版本 `NOT_RUN`） | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 6 | **Redis 停机** | 双 JVM + MySQL 8.4 + 停机 Redis | 1. 注销 `T1`，保留有效 `T2`；2. 停止 Redis；3. 用 `T1`、`T2` 分别访问 `/api/auth/me`，并访问 `/api/health` 与 `/api/auth/login`。 | **基线当前**：带令牌与登录 `503`，`/api/health` 为 `200`。<br>**方案 A 目标**：`T1` 经 MySQL 仍 `401`，`T2` 经 MySQL 降级可用 `200`，`/api/health` 为 `200`（`redisStatus=DOWN`）。 | `RevocationEvents` 记录 `REVOCATION_CACHE_*_UNAVAILABLE` 告警，不输出密码/URI。 | INV-02, INV-03, INV-04, INV-06, INV-07 | `EXECUTED_PASS`（基线 fail-closed 503 已测；方案 A 安全降级 `NOT_RUN`） | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 7 | **Redis 恢复原 RDB** | 双 JVM + 真实 Redis 8.10.3 | 1. 注销 `T1` 后 Redis 执行 `SAVE` 并正常 `SHUTDOWN NOSAVE`；2. 使用原数据目录重启 Redis；3. 等待重连后查询 `T1`、`T2`。 | 恢复后 A、B 对 `T1` 返回 `401`，对 `T2` 返回 `200`。注：本项单独通过不代表具备持久化保证。 | 原 `dump.rdb` 加载成功，`DBSIZE` 恢复；方案 A 下即使 RDB 旧于 MySQL，仍以 MySQL 为准。 | INV-02 | `EXECUTED_PASS` | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) |
| 8 | **Redis 恢复空库** | 双 JVM + 真实 Redis 8.10.3（+ 方案 A 为 MySQL 8.4） | 1. A 登录得 `T1` 并注销（A/B 均 401）；2. 停 Redis 并换用**全新空目录**启动（`DBSIZE=0`、无 `dump.rdb`）；3. 等待重连后用 `T1` 访问 A、B。 | **基线当前（RED）**：A、B 均返回 **`200`**。<br>**方案 A 目标（PASS）**：A、B 均返回 **`401`**，`T2` 返回 `200`。 | 校验 `CONFIG GET dir` 已切换、空目录无 `dump.rdb`、`EXISTS=0`；方案 A 下 `jwt_revocation` 仍存在记录。 | INV-02, INV-03, INV-05 | **`EXECUTED_RED`**（真实双 JVM + JUnit 均稳定复现 HTTP `200`） | [live-red-summary.json](evidence/issue64-durability-design/live-red-summary.json) / [junit-red-gate.log](evidence/issue64-durability-design/junit-red-gate.log) |
| 9 | **Redis 丢失部分标记** | 双 JVM + MySQL 8.4 + Redis | 1. 先后注销 `T1`、`T2`；2. 停 Redis 后恢复到只包含 `T1` 的**旧时间点 RDB 快照**（或手动 `DEL` `T2` 标记）；3. 用 `T2` 访问 A、B。 | `T1`、`T2` 均返回 `401`（不得因 `T2` 在 Redis MISS 而返回 200）。 | Redis 仅见 `T1`、未见 `T2`；MySQL `jwt_revocation` 含 `T1` 与 `T2`，MISS 回源命中后可选回填 Redis。 | INV-03, INV-05 | `NOT_RUN`（需方案 A 实现） | — |
| 10 | **Redis 数据过期和淘汰** | 双 JVM + MySQL 8.4 + Redis（`maxmemory 1mb` + `allkeys-lru` / 人工 `FLUSHDB`） | 1. 注销 `T1`；2. 灌入大量无关键触发 Redis 内存淘汰使 `T1` 黑名单键被驱逐（及对照 `FLUSHDB`）；3. 用 `T1` 访问 A、B。 | A、B 对 `T1` 仍返回 `401`；不被 `maxmemory` 淘汰绕过。 | Redis 中 `blacklist:<jti_sha256>` 已被驱逐（`EXISTS=0`）；MySQL `jwt_revocation` 仍完整保留至过期后宽限期。 | INV-03 | `NOT_RUN`（需方案 A 实现） | — |
| 11 | **Redis 正常但 MySQL 不可用** | Spring Boot HTTP + 正常 Redis + 故障/断网/超时 MySQL | 1. 保持 Redis 正常在线且无某有效令牌 `T2` 的黑名单键；2. 切断 MySQL 或注入查询超时；3. 用 `T2` 调 `/api/auth/me`、调 `/api/auth/logout`、调 `/api/auth/login` 与 `/api/health`。 | 带令牌认证（Redis MISS 时）、注销、有效凭据登录预检均 **fail-closed 返回 `503`**；错误密码登录仍优先 `401`；`/api/health` 仍 `200`（`database=DOWN`）。 | Redis 不可达判定与正常 MISS 均不得在 MySQL 不可用时放行；`SecurityContextHolder` 为空；响应无 JDBC URL/密码。 | INV-04, INV-06, INV-07 | `NOT_RUN`（需方案 A 实现；当前基线未查 MySQL 吊销表） | — |
| 12 | **MySQL 已提交但 Redis 写入失败** | 双 JVM + MySQL 8.4 + ACL 禁 `SET` 或断开 Redis | 1. 对应用 Redis ACL 执行 `-set`（或断开 Redis）；2. 在 A 对 `T1` 调用 `POST /api/auth/logout`；3. 立即用 `T1` 访问 B `/api/auth/me`。 | A 注销返回 **`200`**；B 认证在 Redis MISS 后回源 MySQL 返回 **`401`**。 | `jwt_revocation` 已提交 `T1` 记录；`operation_log` 记 `LOGOUT SUCCESS`；Redis 无 `T1` 键；`RevocationEvents` 记 `REVOCATION_CACHE_WRITE_UNAVAILABLE`。 | INV-01, INV-02, INV-05, INV-07 | `NOT_RUN`（基线当前 ACL `-set` 时注销返回 503，见上一轮记录） | — |
| 13 | **MySQL 事务回滚** | Spring Boot HTTP + Mock/故障注入 MySQL 异常 | 1. 注入 `jwt_revocation` 插入后提交前抛 `DataAccessException` 回滚；2. 对 `T1` 调 `POST /api/auth/logout`。 | 注销返回 **`503`**（`REVOCATION_STORE_UNAVAILABLE`）。 | MySQL `jwt_revocation` 无新增行；`operation_log` 无 `LOGOUT SUCCESS`；**Redis 中绝不允许写入 `T1` 黑名单**。 | INV-01, INV-04 | `NOT_RUN`（需方案 A 实现） | — |
| 14 | **注销与认证并发** | 双 JVM + MySQL 8.4 `REPEATABLE-READ` + 多线程压测 | 1. 线程组在 B 持续并发查询 `T1`；2. A 对 `T1` 发起注销并拿到 `200` 响应时刻记为 `t_ack`；3. 检查所有在 `t_ack` 之后发往 B 的认证请求。 | `t_ack` 之后发往 A、B 的请求 **100% 返回 `401`**，零例 `200`。 | 注销返回 `200` 时 MySQL 主库已 `COMMIT`；B 认证读不复用过期事务快照，直接读到最新已提交行。 | INV-01, INV-02, INV-05 | `NOT_RUN`（仅模拟并发单测存在，真实 MySQL+双 JVM 并发 `NOT_RUN`） | — |
| 15 | **两实例同时注销** | 双 JVM + MySQL 8.4 | 使用 `CountDownLatch` / 屏障在 A、B 同时对同一 `T1` 发起 `POST /api/auth/logout`（或服务层并发 `revoke`）。 | 至少一方或双方（若同时通过前置过滤）均幂等完成，不返回 `500` 或死锁；随后任何查询均为 `401`。 | `jwt_revocation` 仅 1 行，无 `DuplicateKeyException` 外泄为 409/500。 | INV-01, INV-02 | `NOT_RUN`（需方案 A 实现） | — |
| 16 | **应用重启** | 双 JVM（重启前后）+ MySQL 8.4 + Redis | 1. A 注销 `T1`；2. 停止 A、B 并清空（或重启）Redis；3. 启动新实例 A2、B2；4. 查询 `T1`、`T2`。 | A2、B2 对 `T1` 均返回 `401`，对 `T2` 返回 `200`。 | 重启后不依赖任何内存 Map，通过 MySQL `jwt_revocation` 保持状态连续。 | INV-02, INV-03 | `NOT_RUN`（基线仅测带 RDB 重启，未测空 Redis + 应用重启） | — |
| 17 | **Redis 非正常终止** | 双 JVM + MySQL 8.4 + 真实 Redis 8.x | 1. A 注销 `T1`；2. 立即对 Redis 进程执行强制 `SIGKILL` / `TerminateProcess`（未经 `SAVE`）；3. 分别以损坏/空目录或未落盘状态重启 Redis；4. 查询 `T1`、`T2`。 | 若 Redis 丢失未刷盘写入，A、B 仍通过 MySQL 主库对 `T1` 返回 `401`，`T2` 返回 `200`。 | 证明不依赖 Redis 内存或 AOF/RDB 是否及时刷盘即可保障 INV-01~03。 | INV-01, INV-03 | `NOT_RUN` | — |
| 18 | **令牌过期边界** | 单/双 JVM + `MutableClock` / 短 `JWT_EXPIRE_MINUTES` | 1. 构造 `exp = T0 + 2s` 的注销记录；2. 验证 `T0 + 1s`（未过期）、`T0 + 2s`（恰过期）、`T0 + 5m + 2s`（过宽限期）；3. 并发执行 `cleanupExpired()` 与认证。 | 未过期被拒 `401`（已撤销）；恰过期及之后被 `JwtService` 拒 `401`（已过期）；清理任务在宽限期内不删除该行，超过宽限期后批量安全删除。 | 清理条件 `expires_at_epoch_ms <= now - 300_000` 保证与认证无竞争间隙。 | INV-02, INV-03 | `NOT_RUN`（基线仅测了本地 Map 时钟边界，未测 DB 清理边界） | — |
| 19 | **维护窗口升级及回滚边界** | `v_old` 与 `v_new` 切换模拟 + MySQL 8.4 + Redis | 1. `v_old` 登录 `T_legacy1`（仅 Redis 注销）与 `T_legacy2`（未注销）；2. 停全量 `v_old`，执行 `V4_020` 并设置 `CUTOVER` 整秒向上取整水位 `min_valid_iat_epoch_ms = cutoff_ms`；3. 清空 Redis；4. 启动 `v_new` 验证 `T_legacy1`、`T_legacy2` 与 `T_new`；5. 验证回滚必须同时轮换 `JWT_SECRET`。 | `T_legacy1` 与 `T_legacy2` 均返回 `401`（禁止空表 + `0` 水位放行）；`T_new` 返回 `200`；轮换密钥回滚后全旧 JWT `401`。 | `jwt_revocation_checkpoint.checkpoint_state='READY'`、`activation_mode='CUTOVER'`、`min_valid_iat_epoch_ms % 1000 == 0`。 | INV-02, INV-03, INV-05 | `NOT_RUN`（需方案 A 实现后专项验收） | — |
| 20 | **大量令牌时的性能与清理** | MySQL 8.4 + 10,000~50,000 行合成 `jwt_revocation` 数据（含已过宽限期与未过期混合） | 1. 写入大批量合成记录；2. 压测认证查询延迟（Redis 命中 vs Redis MISS 回源 MySQL）及注销延迟；3. 运行有界清理批处理 `cleanupExpired()`。 | 认证/注销接口 P99 满足业务 SLA；清理不阻塞在线认证/注销（无长锁超时）。 | `EXPLAIN` 验证点查命中 `uk_jwt_revocation_jti_sha256`（`const`），清理命中 `idx_jwt_revocation_expires_ms` 且单批受 `LIMIT` 约束。 | INV-02, INV-06 | `NOT_RUN` | — |
| 21 | **检查点缺失行 Fail-Closed** | `DURABLE_DB_REQUIRED` + MySQL 8.4 | 1. 删除或清空 `jwt_revocation_checkpoint` 中的 `GLOBAL_JWT_CUTOFF` 行；2. 携带有效未注销 JWT 请求 `/api/auth/me` 与 `/api/auth/logout`；3. 重启应用验证启动校验。 | 运行期请求均返回 **HTTP `503`**（`REVOCATION_STORE_UNAVAILABLE`），绝不允许 `COALESCE` 置 `0` 放行；新实例启动拒绝退出。 | `RevocationEvents` 记录 `REVOCATION_CHECKPOINT_MISSING`；`SecurityContextHolder` 为空。 | INV-04 | `NOT_RUN`（需方案 A 实现） | — |
| 22 | **检查点 `UNINITIALIZED`（水位 0）阻断** | `DURABLE_DB_REQUIRED` + MySQL 8.4 | 1. 仅执行建表 DDL（`checkpoint_state='UNINITIALIZED'`, `min_valid_iat_epoch_ms=0`），不执行激活脚本；2. 尝试启动 `v_new` 并使用存量或全新 JWT 认证。 | 启动期 `validateConfiguration()` 拒绝启动；运行期若遭遇 `UNINITIALIZED` 则返回 **HTTP `503`**，绝不以 `0` 水位放行存量 JWT。 | 数据库仅存在 `UNINITIALIZED` 行，认证绝不退化为 200 放行。 | INV-04 | `NOT_RUN`（需方案 A 实现） | — |
| 23 | **检查点 `INVALID` / 非法字段 Fail-Closed** | `DURABLE_DB_REQUIRED` + MySQL 8.4 | 分别注入：① `checkpoint_state='INVALID'`；② `READY` 但 `min_valid_iat_epoch_ms <= 0`；③ `READY` 但 `min_valid_iat_epoch_ms % 1000 != 0`；④ `activation_mode` 非法或 `checkpoint_version < 1`。 | 启动期拒绝启动；运行期请求返回 **HTTP `503`**。 | 记录 `REVOCATION_CHECKPOINT_INVALID` 告警，不输出库连接详情。 | INV-04, INV-07 | `NOT_RUN`（需方案 A 实现） | — |
| 24 | **检查点单调更新与防 DDL 重置** | MySQL 8.4（含 `CHECK` 与 `BEFORE UPDATE` 触发器） | 1. 完成 `INITIAL_EMPTY_CLUSTER` 或 `CUTOVER` 激活（`version=1`）；2. 尝试将 `min_valid_iat_epoch_ms` 改小、退回 `UNINITIALIZED` 或重跑 `V4_020` 初始化脚本；3. 合法推进水位至新整秒。 | 降级更新或退回 `UNINITIALIZED` 被 SQL 触发器/约束拒绝（SQLSTATE `45000`）；重跑 `V4_020` 幂等跳过且**不重置水位为 `0`**；单调推进成功且 `version` 递增。 | `min_valid_iat_epoch_ms` 保持单调不减。 | INV-02, INV-04 | `NOT_RUN`（需方案 A + `V4_020` 实现） | — |
| 25 | **检查点即时一致性（零 JVM 缓存窗口）** | 双 JVM（`DURABLE_DB_REQUIRED`）+ MySQL 8.4 + 空 Redis | 1. 用 A 登录得未注销 JWT `T_old`（A、B 均 200）；2. 直接在 MySQL 主库推进 `GLOBAL_JWT_CUTOFF` 至下一整秒并等待时钟越过该整秒；3. **立即（0ms 延迟内）**持 `T_old` 连续访问实例 B。 | 实例 B **首条及后续 100% 请求立即返回 HTTP `401`**，绝不出现 5 秒本地缓存放行窗口。 | 实例 B 每次在 Redis MISS 时从 MySQL 主库单次联合查询读取最新检查点。 | INV-02, INV-05 | `NOT_RUN`（需方案 A 实现） | — |
| 26 | **JWT `iat` 秒级精度与整秒边界** | 单/双 JVM + `DURABLE_DB_REQUIRED` + MySQL 8.4 | 1. 在整秒 `S` 的毫秒前半段（如 `S.200`）签发 `T_before`；2. 在 `S.500` 触发 `CUTOVER`（向上取整到 `(S+1)*1000`）并等待至 `S+1.000`；3. 签发 `T_after`；4. 分别用 `T_before` 与 `T_after` 认证。 | `T_before`（`iat_ms = S*1000 < (S+1)*1000`）必返回 **`401`**；`T_after`（`iat_ms >= (S+1)*1000`）必返回 **`200`**。 | 不存在同秒毫秒截断造成的放行漏洞或误杀新令牌。 | INV-02, INV-06 | `NOT_RUN`（需方案 A 实现） | — |
| 27 | **`v_bridge` 双写旧 Redis 失败阻断** | 若实施 `v_bridge` 阶段 + `v_old` 共存模拟 | 1. 对 `v_bridge` 注入 Redis ACL `-set` 或断开旧 Redis（MySQL 正常）；2. 调用 `POST /api/auth/logout`。 | **必须返回 HTTP `503`** 且不得记录 `LOGOUT SUCCESS`（禁止仅因 MySQL 提交成功就对客户端返回 `200`）。 | MySQL 已落库可拒，但绝不向客户端确认成功以免 `v_old` 放行。 | INV-01, INV-05 | `NOT_RUN`（若使用维护窗口路径可不启用 `v_bridge`） | — |
| 28 | **MySQL 备份恢复 / PITR 安全规程演练** | MySQL 8.4 + 双 JVM + Redis | 1. `t1` 时刻备份 MySQL；2. `t2` 时刻注销 `T1` 并推进水位；3. 将 MySQL 恢复至 `t1` 快照（模拟状态倒退）；4. 按 ADR 第 5.5 节规程轮换 `JWT_SECRET`、推进 `EMERGENCY_ROTATION` 水位并清空 Redis；5. 验证 `T1` 与备份前其它令牌。 | 恢复操作后 `T1` 及恢复前全部旧令牌均返回 **`401`**，全新登录新令牌返回 **`200`**。 | 证明通过强制密钥轮换 + 检查点推进弥补快照恢复的状态丢失窗口。 | INV-01, INV-03, INV-05 | `NOT_RUN`（需方案 A 实现后演练） | — |
