# #64 JWT 吊销状态持久化与 Redis 数据丢失安全恢复架构设计（ADR）

当前状态：`#64 OPEN / DESIGN_CANDIDATE / RED_VERIFIED`

> [!IMPORTANT]
> 本轮不修改 `src/main` 生产代码、不修改正式 MySQL Schema 或迁移脚本、不触碰生产 Redis/MySQL。文末 SQL DDL 仅为**非执行性设计草案**。在生产代码落地并完成真实 MySQL + Redis 故障/数据丢失验收前，严禁将 `#64` 标记为 `FIXED`、`FIXED_VERIFIED` 或 `PRODUCTION_SAFE`。

---

## 1. 背景与精确源码依据

上一轮真实 Redis 双 JVM 验证（见 [issue29-64-real-validation-2026-11-12.md](issue29-64-real-validation-2026-11-12.md)）已证明：当 Redis 正常停机并由同目录恢复 RDB 时，已注销 JWT 在实例 A/B 上均保持 401；但当使用空 Redis 数据目录模拟持久化数据丢失后，尚未过期的已注销 JWT 在恢复后的 Redis 环境中重新获得 HTTP 200。

### 1.1 精确源码依据（基于基线 `c74867721adea57ad16012b4785b4f952b92fe86`）

| 模块 / 文件 | 关键符号与行号 | 当前实现行为与边界 |
|---|---|---|
| `TokenRevocationService.java` | `validateConfiguration()` (`L34-53`) | 强制非 `SINGLE_INSTANCE_LOCAL` 生产环境使用 `SHARED_REDIS_REQUIRED`（否则需显式审批例外）。 |
| `TokenRevocationService.java` | `revoke(String, Instant)` (`L55-74`) | `shared()` 模式下仅调用 `redis.strictPutMarker(key, expiresAt)` 写入 Redis，无任何数据库持久化写入。 |
| `TokenRevocationService.java` | `isRevoked(String)` (`L76-92`) | `shared()` 模式下仅调用 `redis.strictContains(key)`；若 Redis 返回 `false`（键不存在），直接判定未吊销。 |
| `TokenRevocationService.java` | `requireLoginStore()` (`L94-104`) | 登录前仅向 Redis 写读 5 秒 `login-probe` 键，不校验任何持久存储。 |
| `OptionalRedisService.java` | `strictContains()` (`L67-75`) | `redis.hasKey(key) == null` 时抛 `StrictRedisException("UNCONFIRMED")`，但 `false` 被注释为“confirmed absence”。 |
| `OptionalRedisService.java` | `strictPutMarker()` (`L78-90`) | 使用 `Expiration.unixTimestamp(expiresAt.toEpochMilli(), TimeUnit.MILLISECONDS)` + `SetOption.UPSERT` 写单键 `"1"`；Redis 单节点内存写入确认不等于磁盘/跨节点持久提交。 |
| `RevocationProperties.java` | `Mode` (`L12-18`) | 仅定义 `SINGLE_INSTANCE_LOCAL` 与 `SHARED_REDIS_REQUIRED`，尚无数据库权威持久化模式。 |
| `JwtAuthenticationFilter.java` | `doFilterInternal()` (`L37-71`) | 每次携带 Bearer Token 请求：`jwtService.parseClaims()` (`L46`) → `revocations.isRevoked(claims.getId())` (`L50`) → `authService.loadUser(claims.getSubject())` (`L56`)。注意 `loadUser` **本就每次请求查询 MySQL**。 |
| `JwtService.java` | `createToken()` (`L34-43`) / `parseClaims()` (`L49-51`) | 签发 `jti`（`UUID.randomUUID()`）、`sub`、`iat`、`exp`（默认 120 分钟），不含代际/版本号。 |
| `AuthService.java` | `login()` (`L31-55`) / `logout()` (`L82-92`) / `loadUser()` (`L58-65`) | `login` 先验证密码（401）、账号/角色启用（403），再调 `requireLoginStore()`；`logout` 调用 `revocations.revoke()` 后记 `LOGOUT SUCCESS`；`loadUser` 为 `@Transactional(readOnly = true)`。 |
| `HealthController.java` | `health()` (`L22-35`) | `/api/health` 为 `permitAll()`，探测 `SELECT 1` 与 `redis.ping()`，始终返回 HTTP 200 与状态字段。 |
| `SecurityResponseWriter.java` & `GlobalExceptionHandler.java` | `write()` (`L17-22`), `handleBusiness()` (`L36-39`) | 统一返回 `ApiResponse.fail(status, message)`，401/403/503 不回显底层异常、Redis 凭据或主机地址。 |
| `RevocationEvents.java` | `record()` (`L18-33`) | 结构化安全事件与 60s 限流告警，不记录 key/token/凭据。 |
| `doc/database/backend-v1-database-guide.md` & `pom.xml` | 迁移与持久化技术栈 | 项目使用 **MyBatis + JdbcTemplate**，**无 Flyway / Liquibase**；生产迁移通过 `doc/database/migrations/V4_001`..`V4_019` 顺序手工 SQL 执行，测试 H2 同步维护 `src/test/resources/schema.sql`。 |

### 1.2 漏洞根因本质

当前问题**不是**“Redis 连接失败未拦截”。连接失败、超时、ACL 写拒绝均已被 `StrictRedisException` + `RevocationStoreUnavailableException` 拦截为 HTTP 503。

真正的结构性漏洞在于：
1. **将易失性缓存当作唯一事实源（Source of Truth）**：Redis 被用作唯一持久化存储，而项目运维契约（[backend-v1-database-guide.md](../database/backend-v1-database-guide.md)、[backend-v1-operations.md](../deployment/backend-v1-operations.md)）明确规定“Redis 无需备份，因为它不保存业务事实”。
2. **负查询结果误判（False Negative on Empty Store）**：当 Redis 因空数据目录恢复、异常宕机丢失未刷盘写入、`FLUSHDB`、缓存淘汰（eviction）或主从异步切换丢数据时，恢复后的 Redis **能够正常响应 `EXISTS = false`**；应用把“键不存在”错误地解释为“令牌未被吊销”，从而让未过期但已确认注销的 JWT 复活（HTTP 200）。

---

## 2. 安全不变量定义（INV-01 ~ INV-07）

任何候选与最终方案必须同时满足以下可自动化验证的安全不变量：

| 编号 | 安全不变量 | 验证判定标准 |
|---|---|---|
| **INV-01** | **先持久提交后成功返回**：注销接口返回 HTTP 200 与记录 `LOGOUT SUCCESS` 前，撤销事实必须在权威持久存储中完成事务提交确认。 | 若权威存储写入/提交失败，`/api/auth/logout` 必须返回非 200（503），且不得记录 `LOGOUT SUCCESS`。 |
| **INV-02** | **全实例生命周期有效拒绝**：注销成功确认后至 JWT 自然过期（`exp`）前，该令牌在所有运行实例与新启动实例中均被拒绝。 | 多 JVM 跨实例查询同一旧 JWT 必须恒返回 HTTP 401（`身份令牌已撤销`或 `身份令牌已过期`）。 |
| **INV-03** | **Redis 任意状态丢失不复活旧令牌**：Redis 数据丢失、空目录启动、重启、`FLUSHDB`、回滚到旧 RDB 快照、缓存淘汰、异步主从切换均不得使旧 JWT 重新通过认证。 | 上述任一 Redis 异常恢复后，未过期已注销 JWT 不得获得 HTTP 200。 |
| **INV-04** | **不可靠状态必须 Fail-Closed**：当无法可靠判定令牌撤销状态（权威存储不可达、超时、事务失败、未确认提交）时，必须拒绝认证/注销/登录。 | 返回 HTTP 503 `REVOCATION_STORE_UNAVAILABLE`，并清除 `SecurityContextHolder`。 |
| **INV-05** | **跨实例线性可见性**：注销成功后，任何实例上的后续认证读不得因只读副本复制延迟、本地内存缓存或未刷新快照而放行旧令牌。 | 注销返回 200 后，实例 B 立即认证必须 100% 拒绝；禁止在应用进程内缓存“未撤销”状态或路由至延迟副本。 |
| **INV-06** | **既有兼容与语义不破坏**：不得破坏无 Token `/api/health`（Redis/存储故障时仍 200 报告状态）、登录错误优先级（密码错 401 / 停用 403 优先于存储预检 503）、RBAC/DataScope 校验及非生产 `SINGLE_INSTANCE_LOCAL` 本地开发模式。 | 回归测试覆盖所有上述既有语义保持不变。 |
| **INV-07** | **凭据与令牌零泄露**：日志、HTTP 异常响应、监控指标及持久表（除单向摘要外）不得记录完整 JWT、`JWT_SECRET`、Redis 密码、数据库连接串或内部主机地址。 | 日志与响应扫描无 `eyJ...`、`Bearer <token>`、明文密码及内网 Redis URI。 |

---

## 3. 架构方案对比评估

本节完整对比三个备选方案：
- **方案 A**：MySQL 持久化 revoked-JTI 表作为唯一权威事实源（Source of Truth），Redis 仅作可选辅助缓存（**推荐方案**）。
- **方案 B**：数据库用户/会话代际（细分 **B1 用户级 `token_version`** 与 **B2 会话表 `user_session`**）。
- **方案 C**：纯 Redis 持久化、主从复制、AOF/RDB、`WAIT`/`WAITAOF` 及备份恢复约束。

### 3.1 15 维全面评估矩阵

| 评估维度 | 方案 A：MySQL 权威 revoked-JTI + Redis 辅助（推荐） | 方案 B：数据库用户/会话代际（B1 / B2） | 方案 C：纯 Redis AOF/RDB + 复制 + WAIT/WAITAOF |
|---|---|---|---|
| **1. 安全不变量（INV-01~07）** | **全部满足**（前提：严禁将 Redis MISS 当作未吊销，MISS 必须回源 MySQL 主库）。 | B1 满足除“单设备独立注销”外的所有 INV；B2 全部满足。 | **无法严格保证 INV-01 / INV-03 / INV-05**（详见 3.2 节约束分析）。 |
| **2. 单令牌注销语义** | **精确支持**：按 `jti_sha256` 精确吊销当前令牌。 | B1 **不支持**（一次注销踢出同一用户全部设备）；B2 支持（按 `sid`）。 | 支持，但受限于 Redis 数据丢失窗口。 |
| **3. 多设备登录及单设备注销** | **原生支持**：同一用户多端登录产生不同 `jti`，单端注销不影响其他设备。 | B1 无法区分多设备；B2 支持但每次登录需创建会话行。 | 支持，但丢数据时全部设备旧令牌复活。 |
| **4. 注销与认证并发** | MySQL 主库 `COMMIT` 后返回 200；先于 `COMMIT` 的在途认证可能成功，**之后**所有认证因读主库最新提交均 401。 | 同左；B1/B2 对 `sys_user` 或 `user_session` 行读主库可见。 | 并发时若读写跨主从或宕机未刷盘，存在已返回 200 却未落盘窗口。 |
| **5. 数据丢失与错误恢复** | **抗 Redis 全丢**：Redis 空库、`FLUSHDB`、旧 RDB 恢复均只产生缓存 MISS，回源 MySQL 主库仍拒绝旧令牌；MySQL 依赖标准 ACID 与常规备份。 | 同左，依赖 MySQL 持久化；且不依赖 Redis 即可保安全。 | **高危**：空目录启动、误删卷、旧 RDB 覆盖 AOF、内存淘汰均直接导致旧令牌复活（本轮 RED 已证）。 |
| **6. Redis 故障** | **安全降级可用**：Redis 宕机/超时时，认证与注销直连 MySQL 主库，仍满足 INV-01~05（可配置告警，不再整体 503）。 | 可完全不依赖 Redis；若用 Redis 缓存代际仍需应对旧快照。 | **全面不可用或安全失效**：严格模式全站 503；若降级放行则彻底失去撤销保护。 |
| **7. MySQL 故障** | **Fail-Closed 503**：注销、登录预检与 Redis MISS 认证均返回 503；`/api/health` 保持 200 并报告 `database=DOWN`。 | 全链路认证/登录/注销 fail-closed 503（注：`loadUser` 本就依赖 MySQL）。 | 若仅依赖 Redis，MySQL 宕机时 `loadUser` 仍会失败；且无法用 MySQL 兜底 Redis 丢数据。 |
| **8. 多 JVM / 多节点** | 所有实例共享同一 MySQL 主库事实源，跨节点零本地状态依赖。 | 同左。 | 需所有 JVM 严格连接同一主节点且无连接池指向滞后副本。 |
| **9. 主从复制与读写延迟** | 规定吊销读写**强制绑定 MySQL 主库**（Primary RW），禁止路由至只读副本，消除复制延迟。 | 同左，需强制读主库。 | Redis 主从为**异步复制**，Sentinel/Cluster 故障切换可能提升滞后从库，直接丢失已 ACK 的撤销写入。 |
| **10. 令牌有效期与时钟偏差** | 以 `expires_at_epoch_ms`（UTC 毫秒）比对；定期清理保留 5 分钟时钟偏差宽限期（grace skew）。 | B2 会话过期同左；B1 用户版本号无 TTL 清理需求，但需防整数溢出（BIGINT 足够）。 | 依赖 Redis 服务端 `PEXPIRETIME` 与应用主机时钟一致性，跨节点 NTP 漂移可能提前过期。 |
| **11. 延迟与吞吐成本** | **可接受**：`JwtAuthenticationFilter` 每次请求**本就执行 `authService.loadUser()` 访问 MySQL 2~4 次**；增加 1 次唯一索引点查（约 0.2~0.5ms）成本极低；Redis 仅命中黑名单时省去 DB 点查（防失效令牌重放风暴）。 | B1 可直接合并进 `authMapper.selectByUsername()`（0 额外 SQL），代价是丧失单设备注销；B2 每登录多 1 次 INSERT、每请求多 1 次 `sid` 校验。 | 网络 RTT 最低，但开启 `appendfsync always` + `WAIT`/`WAITAOF` 后写入尾延迟剧增且受网络抖动阻塞。 |
| **12. 存储规模与清理成本** | 仅存“有效期内提前注销”的令牌，默认有效期 120 分钟；按峰值每小时数千次注销计，表规模常驻仅数百~数万行，有界批量清理极快。 | B1 零额外表增长；B2 需为**每次登录**建行（比仅存注销行大 1~2 个数量级）。 | 内存存储，受 `maxmemory` 限制；一旦触发淘汰策略即丢失黑名单。 |
| **13. 生产部署复杂度** | **低**：复用现有 MySQL 8 唯一事实源；Redis 保持可选加速角色，完全契合仓库运维指南。 | **低~中**：同样复用现有 MySQL 8，无需新增运维组件。 | **极高**：需部署 Redis Sentinel/Cluster、AOF `always`、RDB/AOF 异地备份、禁止空目录启动护栏、`min-replicas-to-write`、禁用 `FLUSHDB`/淘汰等，且 Windows 环境无官方企业级支持。 |
| **14. 数据迁移及回滚风险** | 新增独立表 + 检查点表，**不改业务表结构**；切换需配合 `min_valid_iat_epoch_ms` 或 `JWT_SECRET` 轮换（详见第 6 节）。 | 需修改 `sys_user` 表或新增会话表，且必须改造 `JwtService` Payload 契约与现有测试。 | 无法通过 SQL 脚本保证，高度依赖外部基础设施人工配置审计。 |
| **15. 对现有代码的侵入程度** | **低~中**：无需改动 JWT 载荷格式、`LoginVO`、业务 Controller 或前端；仅扩展 `TokenRevocationService`、`RevocationProperties` 及新增 MyBatis/JDBC 持久化与切面。 | B1 改 `JwtService` + `AuthMapper` + `sys_user`；B2 需大改登录/会话生命周期与大量既有测试断言。 | 仍需改造应用支持 `WAIT`/`WAITAOF`/epoch 护栏，且无法消除空库恢复后的结构性盲区。 |

### 3.2 为何不能仅靠方案 C（即使启用 AOF/RDB 与 WAIT/WAITAOF）

设计评审中常见误区是认为“只要给 Redis 配上 `appendonly yes` + `appendfsync always` + `WAIT`，就可以保证所有已确认撤销不丢失”。本项目明确否决该假设，原因如下：
1. **`appendfsync everysec` 存在 1~2 秒丢失窗口**；即使设为 `always`，仍无法防御宿主磁盘损坏、容器/脚本以空目录挂载重启、人工误操作或 `FLUSHDB`。
2. **`WAIT` / `WAITAOF` 无法提供分布式强一致线性提交**：Redis 复制为异步流；当主节点写入本地内存后若 `WAIT` 超时，撤销标记其实已留存在原主节点但客户端收到失败，或主节点在复制到多数派前宕机、Sentinel 仍可能触发人工/自动故障切换提升滞后从库；`WAITAOF` 在单机双盘或主从切换时同样受限于 Redis 的非 Raft 共识模型。
3. **空目录恢复误判不可消除**：一旦发生任何基础设施失误导致 Redis 以空库启动，应用层无法从 `EXISTS = false` 区分“从未吊销”与“Redis 刚丢了数据”。
4. **违背项目既有运维契约**：[backend-v1-database-guide.md](../database/backend-v1-database-guide.md) 与 [backend-v1-operations.md](../deployment/backend-v1-operations.md) 明确规定 Redis 仅为可选缓存且无需备份。

### 3.3 推荐方案结论

**推荐采用方案 A（MySQL 持久化 `jwt_revocation` + `jwt_revocation_checkpoint` 为权威事实源，Redis 仅作可选“已吊销快速拒绝缓存”），并吸收方案 B1 的轻量全局/用户级时间水位线思想用于上线安全切换与应急失效。**

核心理由：
- 彻底消除 Redis 数据丢失/空库恢复/旧 RDB 导致的旧令牌复活（满足 INV-01~05）。
- 精确保留单设备/单令牌注销语义，不改变 JWT Payload 或前端登录/注销协议（满足 INV-06）。
- `JwtAuthenticationFilter` 本就每请求访问 MySQL `loadUser`，增加 1 次定长 `CHAR(64)` 主键/唯一索引点查在性能上完全可控，且 Redis 仍可拦截高频已吊销重放请求。

---

## 4. 方案 A 详细设计（面向下一轮开发）

### 4.1 持久化记录的数据字段与摘要脱敏规则

1. **禁止存储原始 JWT**：遵循 INV-07 与 [backend-sensitive-data-audit.md](../security/backend-sensitive-data-audit.md)，数据库与 Redis 中绝不保存完整 JWT 字符串。
2. **存储 `jti_sha256`（`CHAR(64)`）**：对 JWT 的 `jti`（UUID 字符串）做小写 64 位十六进制 SHA-256 摘要（与仓库 `V4_016_s4a_entity_resolution.sql` 中的 `resolution_token_sha256 CHAR(64)` 规范一致），作为定长业务主键/唯一键，避免超长或异常 `jti` 导致的索引截断与日志泄露风险。
3. **核心表结构语义**：
   - **`jwt_revocation`**（已撤销令牌表）：
     - `revocation_id` (`BIGINT AUTO_INCREMENT PK`)
     - `jti_sha256` (`CHAR(64) NOT NULL`) — 唯一键 `uk_jwt_revocation_jti_sha256`
     - `user_id` (`BIGINT NULL`) — 关联 `sys_user.user_id`（仅作审计溯源，认证主查询不依赖它）
     - `expires_at_epoch_ms` (`BIGINT NOT NULL`) — JWT `exp` 的 UTC 毫秒时间戳（**所有过期判定与清理比较均以此整型列为准，彻底规避 MySQL `serverTimezone`、夏令时与多 JVM 本地时区差异**）
     - `token_expires_at` (`DATETIME(3)` / H2 `TIMESTAMP` `NOT NULL`) — 人类可读过期时间（UTC 或与连接时区一致，仅供 DBA 巡检）
     - `revoked_at` (`DATETIME(3)` / H2 `TIMESTAMP` `NOT NULL`) — 注销提交时间
     - `revocation_reason` (`VARCHAR(32) NOT NULL DEFAULT 'LOGOUT'`) — 预留 `LOGOUT` / `ADMIN_REVOKE`
     - `instance_id` (`VARCHAR(64) NULL`) / `trace_id` (`VARCHAR(64) NULL`) — 来自 `S2TraceContext`
     - `created_at` (`DATETIME(3)` / H2 `TIMESTAMP` `NOT NULL DEFAULT CURRENT_TIMESTAMP`)
   - **`jwt_revocation_checkpoint`**（全局/切换时间水位表）：
     - `checkpoint_key` (`VARCHAR(64) PK`) — 固定 `'GLOBAL_JWT_CUTOFF'`
     - `min_valid_iat_epoch_ms` (`BIGINT NOT NULL DEFAULT 0`) — 凡 `iat < min_valid_iat_epoch_ms`（或缺 `iat`）的 JWT 一律视为已失效（401），用于上线安全切换与紧急全量踢出
     - `updated_by` (`VARCHAR(64) NULL`) / `updated_at` (`DATETIME(3)` / H2 `TIMESTAMP` `NOT NULL DEFAULT CURRENT_TIMESTAMP`)

### 4.2 唯一键、索引、过期时间与有界清理规则

- **唯一键**：`UNIQUE KEY uk_jwt_revocation_jti_sha256 (jti_sha256)` — 保证幂等写入与 O(log N) 点查。
- **清理索引**：`KEY idx_jwt_revocation_expires_ms (expires_at_epoch_ms)` — 支持按时间窗口低锁范围扫描删除。
- **审计索引**：`KEY idx_jwt_revocation_user_id (user_id)` — 便于安全排查某用户的近期注销记录。
- **清理规则与时钟偏差边界**：
  - `@Scheduled(fixedDelayString = "${security.jwt.revocation.cleanup-interval:PT30S}")` 定期执行有界批量清理。
  - 清理截止水位：`cleanupCutoffMs = clock.millis() - cleanupGraceSkewMs`（默认宽限 `cleanupGraceSkewMs = 300_000L`，即 5 分钟），绝不删除仍在任何节点时钟偏差容忍期内的记录。
  - 批量删除语句：`DELETE FROM jwt_revocation WHERE expires_at_epoch_ms <= #{cutoffMs} ORDER BY expires_at_epoch_ms ASC LIMIT #{batchSize}`（H2 兼容写法可先查出 `LIMIT #{batchSize}` 的 `revocation_id` 集合再按主键 `IN (...)` 删除，既避免全表锁又兼顾 MySQL 8.4 与 H2 语法统一）。
  - **清理与认证边界并发**：因清理保留 5 分钟宽限期，而 `JwtService.parseClaims()` 默认容忍偏差为 0s，故任何在宽限期内被删除的记录其 JWT 早已被 `ExpiredJwtException` 拦截为 401 `身份令牌已过期`，不存在“刚删行但令牌未过期”的竞态窗口。

### 4.3 注销写入事务、幂等性与提交确认

1. **输入校验与过期语义**：延续 `TokenRevocationService.revoke` 现有校验，`jti` 为空或 `expiresAt` 已过期在严格/持久模式下均抛 401 `BusinessException`。
2. **MySQL 先提交事务（Transaction First）**：
   - 在独立持久化 Service（如 `JdbcTokenRevocationStore` 或 `JwtRevocationMapper`）中使用 `@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)` 执行幂等 upsert：
     - MySQL：
       ```sql
       INSERT INTO jwt_revocation (
           jti_sha256, user_id, expires_at_epoch_ms, token_expires_at,
           revoked_at, revocation_reason, instance_id, trace_id
       ) VALUES (
           #{jtiSha256}, #{userId}, #{expiresAtEpochMs}, #{tokenExpiresAt},
           #{revokedAt}, #{reason}, #{instanceId}, #{traceId}
       )
       ON DUPLICATE KEY UPDATE
           expires_at_epoch_ms = GREATEST(expires_at_epoch_ms, VALUES(expires_at_epoch_ms))
       ```
     - H2 `MODE=MySQL`：可使用 `MERGE INTO jwt_revocation (jti_sha256, ...) KEY (jti_sha256) VALUES (...)` 或捕获 `DuplicateKeyException` 后执行单条 `UPDATE ... WHERE jti_sha256 = ? AND expires_at_epoch_ms < ?`（跨数据库零方言差异的最佳实现为：`INSERT` → 若报 `DuplicateKeyException` 则确认存在且必要时延长 `expires_at_epoch_ms`，确保幂等重复注销均成功返回）。
   - **禁止先写 Redis 再提交 MySQL**：绝不允许出现在 MySQL 事务回滚时 Redis 已写入脏黑名单的情况。
3. **Post-Commit 写 Redis 辅助缓存**：
   - 仅当 MySQL 事务**已确认提交成功**后，再执行 best-effort `redis.strictPutMarker(redis.key("security:jwt:blacklist:" + jtiSha256), expiresAt)`（或沿用 `jti` 派生键，建议统一为 `jtiSha256` 以防键名携带原始结构）。
   - 若此时 Redis 写入失败（连接断开、ACL 拒绝、超时）：记录 `REVOCATION_CACHE_WRITE_UNAVAILABLE` 告警并计数，**但注销接口仍返回 HTTP 200 且记录 `LOGOUT SUCCESS`**，因为 MySQL 权威状态已提交，后续所有实例在 Redis MISS 时会直接从 MySQL 主库读取到该吊销记录。
   - 若 MySQL 写入或事务提交失败：立即抛出 `RevocationStoreUnavailableException`（HTTP 503），**不写 Redis、不写 `LOGOUT SUCCESS` 审计、不放行返回 200**。

### 4.4 认证读取顺序与 Redis 信任边界

> [!CAUTION]
> **核心铁律**：绝对禁止在任何生产/多实例模式中将 Redis 的“键不存在（MISS）”直接认定为“令牌未撤销”。

严格认证读取顺序（`JwtAuthenticationFilter` → `TokenRevocationService.isRevoked`）：
1. **JWT 验签与基础声明校验**（`JwtService.parseClaims`）：验证 HMAC 签名、`exp > now`、`jti` 非空。过期返回 401 `身份令牌已过期`；签名/格式错误返回 401 `身份令牌无效`。
2. **全局安全水位校验**（`min_valid_iat_epoch_ms`）：
   - 由权威 MySQL 主库加载（可配合短生命周期刷新或直接与步骤 4 合并判断，见下）`min_valid_iat_epoch_ms`；若 `min_valid_iat_epoch_ms > 0` 且令牌 `iat == null` 或 `iat.toEpochMilli() < min_valid_iat_epoch_ms`，直接返回 401 `身份令牌已撤销`（切换期间强拒所有切前旧令牌）。
   - *注：若对 `min_valid_iat_epoch_ms` 做进程内单调缓存，仅当本地缓存的 `cutoff` 已经拉取成功时才允许比较，且该缓存必须周期性（如每 5s）及在发生全量踢出时强制回源刷新；更稳妥的做法是：只要需要回源 MySQL 校验 `jwt_revocation`，可在**同一条或同一数据库往返**顺带核对 `min_valid_iat_epoch_ms`（例如使用单次 JDBC/MyBatis 查询同时返回是否存在吊销与当前 `cutoff`），零额外网络往返！*
3. **Redis 辅助加速检查（仅允许正向命中拒绝，不允许 MISS 放行）**：
   - 若 Redis 已启用且可用，尝试查询 `blacklist:<jti_sha256>`：
     - 若返回 `true`（命中）：**立即拒绝**，返回 401 `身份令牌已撤销`，记录 `REVOCATION_REJECTED(source=REDIS_CACHE)`（无需再打扰 MySQL）。
     - 若返回 `false`（MISS）或 Redis 抛出超时/连接/认证异常：记录缓存异常统计（若异常），**不得放行，必须进入步骤 4**。
4. **权威 MySQL 主库点查（Authoritative Check）**：
   - 使用独立连接/只读非外层长事务快照，路由至 **MySQL Primary (RW)** 执行：
     ```sql
     SELECT
       EXISTS(
         SELECT 1 FROM jwt_revocation
         WHERE jti_sha256 = #{jtiSha256} AND expires_at_epoch_ms > #{nowEpochMs}
       ) AS jti_revoked,
       COALESCE((
         SELECT min_valid_iat_epoch_ms FROM jwt_revocation_checkpoint
         WHERE checkpoint_key = 'GLOBAL_JWT_CUTOFF'
       ), 0) AS min_valid_iat_epoch_ms
     ```
   - 判定结果：
     - 若 `jti_revoked == 1` 或 `(min_valid_iat_epoch_ms > 0 && (iatMs == null || iatMs < min_valid_iat_epoch_ms))`：返回 `revoked = true`（HTTP 401 `身份令牌已撤销`）。（若 Redis 可用但此前 MISS，可异步或顺带 best-effort 回填 Redis `PXAT` 缓存，防止同一无效令牌持续击穿数据库。）
     - 若 MySQL 主库明确返回 `jti_revoked == 0` 且满足水位要求：确认未撤销，放行至 `authService.loadUser(username)`。
     - 若 MySQL 主库查询异常（连接失败、超时、表缺失、SQL 错误）：抛出 `RevocationStoreUnavailableException`，返回 **HTTP 503 `REVOCATION_STORE_UNAVAILABLE`**（Fail-Closed）。

### 4.5 关键事务与故障时序图

```mermaid
sequenceDiagram
    autonumber
    participant C as Client
    participant A as App Instance A
    participant B as App Instance B
    participant R as Redis (Fast-Reject Cache)
    participant DB as MySQL Primary (Authoritative SoT)

    Note over C,DB: 场景 1：正常注销（MySQL 先提交，Redis 后置辅助）
    C->>A: POST /api/auth/logout (Bearer JWT)
    A->>DB: BEGIN TX -> UPSERT jwt_revocation(jti_sha256, exp_ms)
    DB-->>A: COMMIT OK
    A->>R: Best-effort SET blacklist:<jti_sha256> PXAT exp_ms
    alt Redis 写入失败/超时
        R-->>A: Timeout / Connection Error
        A->>A: 记录 REVOCATION_CACHE_WRITE_UNAVAILABLE (不回滚 DB)
    else Redis 写入成功
        R-->>A: OK
    end
    A-->>C: HTTP 200 OK (记录 LOGOUT SUCCESS)

    Note over C,DB: 场景 2：Redis 数据丢失 / FLUSHDB / 空库恢复后 B 端认证
    C->>B: GET /api/auth/me (同一旧 JWT)
    B->>R: EXISTS blacklist:<jti_sha256>
    R-->>B: false (CACHE MISS — 空库/被清空/旧RDB)
    Note right of B: 铁律：绝不信任 Redis MISS！
    B->>DB: 查询 Primary: jwt_revocation + checkpoint
    DB-->>B: jti_revoked = 1
    B-->>C: HTTP 401 身份令牌已撤销 (可选 best-effort 回填 Redis)

    Note over C,DB: 场景 3：Redis 正常运行，但 MySQL 主库不可用
    C->>B: GET /api/auth/me (有效未注销 JWT)
    B->>R: EXISTS blacklist:<jti_sha256>
    R-->>B: false (MISS)
    B->>DB: 查询 Primary: jwt_revocation
    DB-->>B: Connection Refused / Timeout
    B->>B: SecurityContextHolder.clearContext()
    B-->>C: HTTP 503 REVOCATION_STORE_UNAVAILABLE (Fail-Closed)
```

### 4.6 7 大关键故障与并发边界处理清单

| 边界场景 | 行为与机制保证 | 结果 |
|---|---|---|
| **1. MySQL 提交成功，Redis 辅助写入失败** | DB 事务先提交后才写 Redis；Redis 异常仅记限流日志与计数。 | 注销返回 200；B 端在 Redis MISS 后查 MySQL 主库仍拒绝旧 JWT（401）。 |
| **2. Redis 写入成功但 MySQL 事务回滚** | 禁止在 DB `COMMIT` 前写入 Redis；彻底根除“先写缓存后写库”导致的状态分裂。 | 不会发生脏黑名单残留；DB 回滚直接映射为 503，不记 `LOGOUT SUCCESS`。 |
| **3. DB 已提交瞬间，实例 B 同时认证** | `JwtAuthenticationFilter` 使用主库独立短读（`READ COMMITTED` / autocommit），在注销返回 200 后发起的任何请求均处于 MySQL 事务提交之后。 | B 端立即可见，返回 401（满足 INV-05）。 |
| **4. 主库提交成功，只读副本尚未同步** | 本模块显式禁止数据源读写分离到只读副本；吊销写入与回源点查均强制走 Primary RW DataSource。 | 零副本延迟复活窗口（满足 INV-05）。 |
| **5. Redis 正常但发生 `FLUSHDB`** | `FLUSHDB` 后 Redis 对所有键返回 `EXISTS = false`（MISS）；因 MISS 强制回源 MySQL 主库，仍命中 `jwt_revocation`。 | 旧 JWT 仍返回 401（满足 INV-03）。 |
| **6. Redis RDB 恢复到旧时间点快照** | 旧 RDB 缺少快照时间点之后的新注销键 → 返回 MISS → 强制查 MySQL 主库；同时因**不缓存“未撤销”正向结果**，旧 RDB 绝不会给出错误放行信号。 | 旧 JWT 仍返回 401（满足 INV-03）。 |
| **7. 定期清理与过期边界并发** | 清理任务仅删除 `expires_at_epoch_ms <= now - 300_000`（5 分钟宽限期）；此时所有满足删除条件的 JWT 均已超过 `exp` 5 分钟，必被 `JwtService.parseClaims` 拒绝为 `ExpiredJwtException` (401)。 | 清理与认证之间无竞态漏洞。 |

### 4.7 异常映射、健康检查与可观测性设计

1. **HTTP 响应契约保持**：
   - 未认证/缺 Token：401 `缺少有效身份令牌`
   - 签名/格式错：401 `身份令牌无效`
   - 已过期：401 `身份令牌已过期`
   - 已撤销或低于 `min_valid_iat_epoch_ms`：401 `身份令牌已撤销`
   - MySQL 权威吊销库不可用（认证回源、登录预检、注销写入）：503 `REVOCATION_STORE_UNAVAILABLE：身份令牌撤销服务暂不可用，请稍后重试`
   - `/api/health`：保持 `permitAll()` 无 Token 恒返回 HTTP 200，在返回体中可新增 `revocationMode` / `revocationStoreStatus`（例如 `DB_PRIMARY_UP` / `DB_PRIMARY_DOWN`）而不改变既有 `database`、`redisEnabled`、`redisStatus`、`cacheMode` 字段语义（满足 INV-06）。
2. **运行模式兼容（`RevocationProperties.Mode`）**：
   - 保留 `SINGLE_INSTANCE_LOCAL`（非 prod profile 本地开发兼容，或带显式审批的生产单机例外）。
   - 新增推荐生产模式 `DURABLE_DB_REQUIRED`（或将严格多实例生产默认要求从 `SHARED_REDIS_REQUIRED` 演进为 `DURABLE_DB_REQUIRED`；启动门禁在 `prod/production` profile 或 `production=true` 时，若既非 `DURABLE_DB_REQUIRED` 也无单机审计例外则拒绝启动）。
   - 若显式配置 `SHARED_REDIS_REQUIRED` 作为过渡兼容模式，应在启动日志中发出高优先级安全告警提示其不抗 Redis 空恢复，或直接由 `DURABLE_DB_REQUIRED` 接管生产标准。
3. **监控指标、审计事件与告警**：
   - 在 `RevocationEvents` 中维持有界事件名（禁止输出 `jti` 明文、JWT、密码、Redis/DB URI）：
     - `REVOCATION_DB_CONFIRMED` (`LOGOUT` 成功落库)
     - `REVOCATION_DB_UNAVAILABLE` (`LOGOUT` / `LOGIN_PREFLIGHT` / `AUTHENTICATE` 数据库不可用，触发 503 紧急告警)
     - `REVOCATION_REJECTED_CACHE` / `REVOCATION_REJECTED_DB` / `REVOCATION_REJECTED_CUTOFF`
     - `REVOCATION_CACHE_WRITE_UNAVAILABLE` / `REVOCATION_CACHE_READ_UNAVAILABLE`（Redis 辅缓存异常告警，服务不中断但通知运维排查 Redis）
     - `REVOCATION_CLEANUP_BATCH`（记录清理行数与耗时，不记录明细）

---

## 5. 上线切换、混合部署与回滚安全方案

### 5.1 为什么禁止“直接建空表切新代码”

上线瞬间，系统中可能存在此前由旧版本签发并已在 Redis 中注销、但新表 `jwt_revocation` 内尚未记录的 JWT：
- 若直接启用 `v_new` 且 Redis 发生重启/丢数据/换新前缀，`v_new` 查询空表会认为这些旧令牌全部有效，直接造成**已撤销令牌大规模复活**。
- **因此绝对禁止默认“空表 = 所有存量 JWT 有效”的直接切换方式！**

### 5.2 推荐切换方案：维护窗口 + `min_valid_iat_epoch_ms` 安全水位切分（可选叠加 `JWT_SECRET` 轮换）

对比两种可证明安全的切换路径：

| 方案 | 流程 | 安全性证明 |
|---|---|---|
| **方案 1：维护窗口切分（推荐，契合本项目部署方式）** | 1. 停止旧实例写入/进入维护窗口。<br>2. 备份 MySQL，执行 `V4_020_jwt_revocation.sql`。<br>3. 将 `jwt_revocation_checkpoint.min_valid_iat_epoch_ms` 设为当前切换时刻 `T_cutover`（并可同步轮换 `JWT_SECRET`，详见 [backend-v1-operations.md](../deployment/backend-v1-operations.md) 第 6 条）。<br>4. （可选辅助）对在线 Redis 扫描现存未过期 `blacklist:*` 键做最佳努力迁移入表，但**不以它作为安全边界**。<br>5. 启动 `v_new` 实例。 | 凡 `iat < T_cutover` 的所有历史令牌均被强制拒绝（401）要求重新登录；凡 `iat >= T_cutover` 的新令牌均在 `v_new` 全生命周期管控之下，不可能存在“只在旧 Redis 注销而未入库”的盲区。 |
| **方案 2：零停机双阶段滚动升级（若未来要求不停机）** | 1. **阶段 A（Bridge 版本 `v_bridge`）**：上线双写双读兼容版本——注销同时写 MySQL 与旧 Redis 键，认证同时查 Redis 与 MySQL（任一命中即拒，MySQL 与 Redis 在此阶段均按严格失败处理）。<br>2. 等待所有实例完成滚动替换至 `v_bridge` 后，等待 `>= JWT_EXPIRE_MINUTES`（默认 120 分钟），或将 `min_valid_iat_epoch_ms` 推进至全部节点均已升为 `v_bridge` 的时刻 `T_all_bridge`。<br>3. **阶段 B（Target 版本 `v_new`）**：滚动升级至 `v_new`（去除了对 Redis 严格可用性的阻断依赖，保留 Redis 为可选 fast-reject）。 | 在阶段 B 启动前，所有尚在有效期内的令牌要么已超过 `120` 分钟过期，要么是在 `v_bridge` 上注销（已同时写入 MySQL）。 |

### 5.3 混合部署与回滚安全边界

1. **严禁 `v_old` 与 `v_new` 直接混跑**：若无 `v_bridge` 阶段直接滚动部署，客户在 `v_new` 注销（写 MySQL 成功、假设 Redis 偶发失败仍返回 200）后，下一请求路由到 `v_old`（只查 Redis）就会立刻复活！故必须采用**维护窗口停旧启新**或**`v_bridge` 两步滚动**。
2. **升级失败处理**：`V4_020` 仅新增两张独立表，不改任何业务表。若切流前 DDL 失败，旧实例与原数据库完全不受影响。
3. **上线后回滚安全边界（`v_new` → `v_old`）**：
   - 在 `v_new` 运行期间，如果曾发生 Redis 辅助写入失败或 Redis 重启丢数据，则 `jwt_revocation` 中存在 Redis 里没有的已注销记录；此时直接切回只认 Redis 的 `v_old` **会导致这批令牌复活**。
   - **回滚铁律**：若必须从 `v_new` 回滚到 `v_old`，**必须同时轮换 `JWT_SECRET`**（使回滚前签发/注销的全部 JWT 一次性失效，通知用户重新登录）；否则禁止回滚至仅读 Redis 的旧版代码。

---

## 6. 非执行性 SQL DDL 草案（供下一轮评审）

> [!NOTE]
> 以下 DDL 未在本轮执行，仅作为设计附录。正式迁移文件应按仓库顺序命名（紧随 `V4_019_migration_batch_reuse.sql` 之后，建议 `doc/database/migrations/V4_020_jwt_revocation.sql`），并同步更新 `src/test/resources/schema.sql`。

### 6.1 MySQL 8.4 DDL 草案（`V4_020_jwt_revocation.sql`）

```sql
-- =============================================================================
-- DRAFT ONLY (NON-EXECUTABLE IN THIS DESIGN ROUND)
-- V4_020: Issue #64 - Durable JWT revocation store and cutover checkpoint.
-- Target: MySQL 8.4, apply after V4_019 on a backed-up database.
-- =============================================================================
USE population_miniterm;

CREATE TABLE IF NOT EXISTS jwt_revocation (
    revocation_id BIGINT NOT NULL AUTO_INCREMENT,
    jti_sha256 CHAR(64) NOT NULL COMMENT 'SHA-256 hex digest of JWT jti claim',
    user_id BIGINT NULL COMMENT 'Optional sys_user.user_id for security audit',
    expires_at_epoch_ms BIGINT NOT NULL COMMENT 'JWT exp claim in UTC epoch milliseconds',
    token_expires_at DATETIME(3) NOT NULL COMMENT 'Human-readable token expiration timestamp',
    revoked_at DATETIME(3) NOT NULL COMMENT 'Authoritative revocation timestamp',
    revocation_reason VARCHAR(32) NOT NULL DEFAULT 'LOGOUT' COMMENT 'LOGOUT | ADMIN_REVOKE',
    instance_id VARCHAR(64) NULL COMMENT 'App instance id from S2TraceContext',
    trace_id VARCHAR(64) NULL COMMENT 'Request trace id from S2TraceContext',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (revocation_id),
    UNIQUE KEY uk_jwt_revocation_jti_sha256 (jti_sha256),
    KEY idx_jwt_revocation_expires_ms (expires_at_epoch_ms),
    KEY idx_jwt_revocation_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS jwt_revocation_checkpoint (
    checkpoint_key VARCHAR(64) NOT NULL,
    min_valid_iat_epoch_ms BIGINT NOT NULL DEFAULT 0 COMMENT 'Reject JWTs with iat < min_valid_iat_epoch_ms',
    updated_by VARCHAR(64) NULL,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (checkpoint_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

INSERT INTO jwt_revocation_checkpoint (checkpoint_key, min_valid_iat_epoch_ms, updated_by)
VALUES ('GLOBAL_JWT_CUTOFF', 0, 'V4_020_INIT')
ON DUPLICATE KEY UPDATE checkpoint_key = VALUES(checkpoint_key);
```

### 6.2 H2 2.3.232 测试 Schema 兼容草案（`src/test/resources/schema.sql`）

```sql
-- H2 MODE=MySQL parity draft (TIMESTAMP instead of DATETIME(3))
CREATE TABLE IF NOT EXISTS jwt_revocation (
    revocation_id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    jti_sha256 CHAR(64) NOT NULL,
    user_id BIGINT NULL,
    expires_at_epoch_ms BIGINT NOT NULL,
    token_expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NOT NULL,
    revocation_reason VARCHAR(32) NOT NULL DEFAULT 'LOGOUT',
    instance_id VARCHAR(64) NULL,
    trace_id VARCHAR(64) NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_jwt_revocation_jti_sha256 UNIQUE (jti_sha256)
);
CREATE INDEX IF NOT EXISTS idx_jwt_revocation_expires_ms ON jwt_revocation (expires_at_epoch_ms);
CREATE INDEX IF NOT EXISTS idx_jwt_revocation_user_id ON jwt_revocation (user_id);

CREATE TABLE IF NOT EXISTS jwt_revocation_checkpoint (
    checkpoint_key VARCHAR(64) PRIMARY KEY,
    min_valid_iat_epoch_ms BIGINT NOT NULL DEFAULT 0,
    updated_by VARCHAR(64) NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
MERGE INTO jwt_revocation_checkpoint (checkpoint_key, min_valid_iat_epoch_ms, updated_by)
KEY (checkpoint_key) VALUES ('GLOBAL_JWT_CUTOFF', 0, 'SCHEMA_INIT');
```

*注：`user_id` 故意不加外键约束至 `sys_user`（或采用 `NULL` 可选），以便合成单测、`TokenRevocationService` 直接按 JTI 测试及未来异步清理都能保持独立解耦；不阻碍安全审计。*

---

## 7. 代码模块级实施清单与下一轮修复拆分

### 7.1 模块级实施清单

| 文件 / 模块 | 下一轮变更内容 |
|---|---|
| `doc/database/migrations/V4_020_jwt_revocation.sql` | 正式落地 `jwt_revocation` 与 `jwt_revocation_checkpoint` 表及升级预检/回滚说明文档。 |
| `src/test/resources/schema.sql` | 同步新增两张表的 H2 `MODE=MySQL` 定义与初始检查点行。 |
| `RevocationProperties.java` | `Mode` 增加 `DURABLE_DB_REQUIRED`；新增 `cleanupGraceSkew`（默认 `PT5M`）、`dbCleanupBatchSize`（默认 `500`）等配置。 |
| `security/DurableRevocationStore.java`（新增）+ `mapper/JwtRevocationMapper.xml` | 封装主库 `REQUIRES_NEW` 事务幂等写入、单次联合查询（`jti_revoked` + `min_valid_iat_epoch_ms`）、健康探活及分批清理；统一异常映射为 `RevocationStoreUnavailableException`。 |
| `TokenRevocationService.java` | `validateConfiguration()` 校验生产环境强制 `DURABLE_DB_REQUIRED`；`revoke(...)` 先提交 DB 再 best-effort 写 Redis；`isRevoked(...)`（扩展接收 `jti`、`iat`、`exp`）先检查 Redis fast-reject 命中再无条件回源 DB；`requireLoginStore()` 探活 DB 持久存储；`cleanupExpired()` 在持久模式下同时清理 DB 过期批次。 |
| `JwtAuthenticationFilter.java` & `AuthService.java` | 传入 `claims.getIssuedAt()` 与 `userId` 给 `TokenRevocationService`，不改动 `JwtService` Payload 契约与 `LoginVO`。 |
| `HealthController.java` | 新增 `revocationStoreStatus` / `revocationMode` 非敏感字段，保留 `permitAll()` 与 HTTP 200 语义。 |
| `RevocationEvents.java` | 新增 DB/Cache 分离事件与限流统计，继续严格禁用明文 JTI/JWT/密码日志。 |

### 7.2 建议拆分顺序（下一轮生产施工）

1. **Step 1（Schema 与 Store 层）**：提交 `V4_020_jwt_revocation.sql`、`schema.sql`、`JwtRevocationMapper` 与 `DurableRevocationStore`，配套 H2 与 MySQL 8.4 幂等插入/并发清理单测。
2. **Step 2（核心吊销协议与模式切换）**：升级 `RevocationProperties`、`TokenRevocationService`、`JwtAuthenticationFilter`、`AuthService`、`HealthController` 与 `RevocationEvents`；调整并扩充 `S5A1StrictRevocationHttpIntegrationTest`、`RevocationConfigurationTest`、`TokenRevocationServiceConsistencyTest`。
3. **Step 3（门禁转绿与全量验收）**：运行本轮交付的 `Issue64DurabilityRedGateTest`（`-Dissue64.red=true`）与 `scripts/issue64/issue64_red_gate.py` 验证由 `RED` 转为 `PASS`，并逐项执行 [issue64-durable-revocation-gate.md](issue64-durable-revocation-gate.md) 中的 MySQL 8.4 与真实 Redis 全矩阵验收后，方可提交独立审查。
