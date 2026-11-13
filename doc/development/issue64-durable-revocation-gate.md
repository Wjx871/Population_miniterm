# #64 JWT 吊销持久化验收矩阵与可执行 RED 门禁规范

当前状态：`#64 OPEN / DESIGN_CANDIDATE / RED_VERIFIED`

> [!IMPORTANT]
> 本矩阵用于指导下一轮生产实现与独立验收。凡本轮未执行的真实 MySQL 8.4、异常宕机或未落地的方案 A 专项场景，均严格标注为 **`NOT_RUN`**，绝不将设计预期冒充为动态通过。

---

## 1. 可执行 RED 门禁定义与判定语义

本轮交付两套独立可执行门禁（均使用合成测试账号 `population`/`viewer`、隔离 Redis/H2，绝不触碰生产数据）：

1. **Maven opt-in JUnit 门禁**：[Issue64DurabilityRedGateTest.java](../../src/test/java/com/wjx871/population/security/Issue64DurabilityRedGateTest.java)
   - 开关：`@EnabledIfSystemProperty(named = "issue64.red", matches = "true")`（常规 `.\mvnw.cmd test` 跳过该 opt-in 门禁，避免影响既有回归统计）。
   - 触发命令（PowerShell）：
     `.\mvnw.cmd test "-Dissue64.red=true" "-Dtest=Issue64DurabilityRedGateTest"`
2. **双 JVM + 真实隔离 Redis 8.10.3 实时门禁**：[issue64_red_gate.py](../../scripts/issue64/issue64_red_gate.py)（配套自检 [test_issue64_red_gate.py](../../scripts/issue64/test_issue64_red_gate.py)）
   - 触发命令：
     `python scripts/issue64/issue64_red_gate.py --evidence-out doc/development/evidence/issue64-durability-design/live-red-summary.json`
   - 严格三态分类：
     - **`PASS`（退出码 `0`）**：全部前置验证通过 + Redis 切换空目录并重连恢复后，已注销未过期旧 JWT 在实例 A、B 上**均返回 HTTP `401`**，且新登录的独立 JWT 返回 `200`。
     - **`RED`（退出码 `2`）**：全部前置验证通过 + 已验证切换至空数据目录（`DBSIZE=0`、无 `dump.rdb`、`CONFIG GET dir` 为新目录）并重连后，已注销未过期旧 JWT 在 A 或 B 上**获得 HTTP `200`**。
     - **`ERROR`（退出码 `1`）**：进程启动失败、二进制/classpath 缺失、连接失败、超时、前置检查失败、目录切换校验未通过或观测不完整（**绝不把 `ERROR` 包装为 `RED` 或 `PASS`**）。

---

## 2. 20 项生产验收测试矩阵

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
| 19 | **滚动升级及旧令牌迁移** | 混合集群模拟（`v_old` / `v_bridge` / `v_new` 或维护窗口切换） | 1. `v_old` 登录 `T_legacy1`（注销仅在 Redis）与 `T_legacy2`（未注销）；2. 执行 `V4_020` 并按切流方案设置 `min_valid_iat_epoch_ms = T_cutover`（或轮换 `JWT_SECRET`）；3. 清空 Redis；4. 验证 `T_legacy1`、`T_legacy2` 与新登录 `T_new`。 | `T_legacy1` 与 `T_legacy2` 均返回 `401`（禁止默认空表放行旧令牌）；`T_new` 返回 `200`；测试回滚边界（回滚需轮换 `JWT_SECRET`）。 | `jwt_revocation_checkpoint.min_valid_iat_epoch_ms` 持久化在 MySQL 主库，重启后依然生效。 | INV-02, INV-03, INV-05 | `NOT_RUN`（需方案 A 实现后专项验收） | — |
| 20 | **大量令牌时的性能与清理** | MySQL 8.4 + 10,000~50,000 行合成 `jwt_revocation` 数据（含已过宽限期与未过期混合） | 1. 写入大批量合成记录；2. 压测认证查询延迟（Redis 命中 vs Redis MISS 回源 MySQL）及注销延迟；3. 运行有界清理批处理 `cleanupExpired()`。 | 认证/注销接口 P99 满足业务 SLA；清理不阻塞在线认证/注销（无长锁超时）。 | `EXPLAIN` 验证点查命中 `uk_jwt_revocation_jti_sha256`（`const`），清理命中 `idx_jwt_revocation_expires_ms` 且单批受 `LIMIT` 约束。 | INV-02, INV-06 | `NOT_RUN` | — |
