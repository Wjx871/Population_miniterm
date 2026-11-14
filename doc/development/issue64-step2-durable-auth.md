# Issue #64 Step 2：`DURABLE_DB_REQUIRED` 持久认证与吊销接入交付报告

## 1. 范围与结论边界

- **基线分支 / 提交**：`feat/20261114-issue64-durable-auth-step2` 基于 `8d8abca127bba2a383e2a7064a1c5c1f8a2b3960`（与 `origin/integration/20261101-backend-p0-security` 一致）。
- **Step 2 交付范围**：将 Step 1 落地的 `DurableRevocationStore`（`jwt_revocation` 与 `jwt_revocation_checkpoint`）正式接入认证过滤器、注销、登录、健康检查与定期清理主链路，同时保持 `SINGLE_INSTANCE_LOCAL` 与 `SHARED_REDIS_REQUIRED` 向后兼容。
- **门禁与状态边界**：
  - 本轮完成了全部 Step 2 单元测试、HTTP 集成测试、既有 JWT 回归、全量 `mvnw.cmd clean test`、隔离 MySQL `8.4.8` `*IT` 实测、Python 门禁脚本自测与 `baseline-red` 复现。
  - **真实双 JVM + 共享 MySQL 8.4 + Redis 的端到端门禁 `TARGET_GREEN` 当前为 `NOT_RUN`**（Step 2 仅在单 JVM 内模拟了未清理本地黑名单的跨实例场景），真实双 JVM 验收属于 Step 3 范围。
  - 因此，**Issue `#64` 保持 `OPEN`**，不声明已通过 `TARGET_GREEN`，亦不声明已部署至生产环境。

---

## 2. 变更文件清单

### 2.1 生产代码与配置（8 个文件，未改动 `doc/database/migrations/`）

| 文件 | 变更职责 |
|---|---|
| `src/main/java/com/wjx871/population/security/RevocationProperties.java` | 新增 `Mode.DURABLE_DB_REQUIRED` 及 `allowZeroCutoffForTest`（默认 `false`）配置项 |
| `src/main/java/com/wjx871/population/security/RevocationEvents.java` | 新增 `DURABLE_*` 安全事件枚举及脱敏/60s 限流事件辅助 |
| `src/main/java/com/wjx871/population/security/JwtService.java` | 强制要求及解析 JWT `jti`、`sub`、`iat`、`exp` 声明，支持精确 `iatMs` 提取 |
| `src/main/java/com/wjx871/population/security/TokenRevocationService.java` | 接入 `DurableRevocationStore`：三模式启动校验、DB-first 认证/水位检查、DB-first 注销与 Redis 加速/回源、登录前库状态/水位校验与并发签发保护、`cleanupExpired` 隔离 |
| `src/main/java/com/wjx871/population/security/JwtAuthenticationFilter.java` | 接入 `DURABLE_DB_REQUIRED` fail-closed 503 响应语义与 `SecurityContextHolder` / `CurrentUserContext` 清理 |
| `src/main/java/com/wjx871/population/auth/AuthService.java` | 注销保持 DB 先提交、Redis 失败容错及审计日志边界；登录维持 401/403 优先于存储检查点校验及签发竞态防护 |
| `src/main/java/com/wjx871/population/system/HealthController.java` | `/api/health` 公开返回非敏感 `revocationMode` 与 `revocationStoreStatus`（含 `DB_PRIMARY_UP` / `DB_PRIMARY_DOWN` / `DB_CHECKPOINT_NOT_READY`） |
| `src/main/resources/application.properties` | 保留开发默认 `SINGLE_INSTANCE_LOCAL` 与安全基线注释（不自动激活生产模式或 `READY+0`） |

### 2.2 Step 2 新增测试文件（5 个文件，未删除或破坏既有测试）

| 测试文件 | 用例数 | 覆盖范围 |
|---|---:|---|
| `src/test/java/com/wjx871/population/security/DurableRevocationConfigurationStep2Test.java` | 7 | Group A（场景 1-7）：三模式启动兼容、`DURABLE_DB_REQUIRED + READY` 正常启动、missing/`UNINITIALIZED`/`INVALID`/DB 失败启动拒绝、生产 `READY + 0` 拒绝、不自动建表或自动激活检查点、旧模式不伪装及原始 JTI Redis Key 保留 |
| `src/test/java/com/wjx871/population/security/DurableAuthFilterAndWatermarkTest.java` | 7 | Group B（场景 8-16）：HTTP 200/401、缺失 `iat`/`jti`/`sub`/`exp` 401、单参 `isRevoked` fail-closed、`iat == cutoff` 放行与 `iat < cutoff` 401、运行期 cutoff 推进立即生效、DB 查询失败 503、运行期检查点损坏/`READY+0` 503、Redis 正向命中 vs 检查点无效 503 优先级、`SecurityContextHolder` / `CurrentUserContext` 清理 |
| `src/test/java/com/wjx871/population/security/DurableLogoutAndRedisFaultTest.java` | 10 | Group C（场景 17-24）：MySQL 提交 `200` + `jti_sha256`、`PXAT` Redis 回填、Redis MISS/连接故障回源 MySQL、Redis 写失败 DB 成功仍返回 `200` 且后续认证 `401`、DB 回滚 `503` + 不写 Redis + 无 `LOGOUT SUCCESS` 审计、重复/并发 revoke 幂等、JTI 隔离、单 JVM 模拟跨实例、后置审计异常隔离 |
| `src/test/java/com/wjx871/population/security/DurableLoginHealthCleanupTest.java` | 6 | Group D（场景 25-30）：登录 401/403 优先于失效检查点、Redis 故障不阻断登录、签发时间戳早于 cutoff 阻断 `503`、并发窗口推进竞态保护 `503`、三模式 `/api/health` 非敏感输出、`cleanupExpired` `300,000 ms` grace skew / 有界批量 / 并发安全 / 异常隔离、`RevocationEvents` 脱敏与 60s 限流 |
| `src/test/java/com/wjx871/population/security/DurableAuthStep2MySql84IT.java` | 5 | 隔离 MySQL `8.4.x` `*IT`（默认 H2 不执行，无配置 opt-in skip，非隔离或缺失配置 fail-closed）：真实启动检查点异常不自愈、HTTP 主库认证、事务注销与回滚、Redis miss/写故障、运行期 cutoff 立即生效、撤销库/连接池 503 fail-closed 与恢复 |

---

## 3. 已验证的核心行为与契约

1. **三模式兼容与启动安全门**：
   - `SINGLE_INSTANCE_LOCAL` 与 `SHARED_REDIS_REQUIRED` 保持既有构造函数和原始 JTI Redis Key 兼容，从不调用 `DurableRevocationStore`。
   - `DURABLE_DB_REQUIRED` 启动期强制校验证明 `DurableRevocationStore` 存在且 `jwt_revocation_checkpoint` 为有效 `READY`；missing、`UNINITIALIZED`、`INVALID`、`READY + null`、`READY + <0` 或数据库连接失败均以 `IllegalStateException` 拒绝启动；当激活 `prod`/`production` profile、`security.jwt.revocation.production=true` 或 `allowZeroCutoffForTest=false` 时拒绝 `READY + 0`。启动校验证明不会自动修复或写入检查点行。
2. **Redis 加速前置 + DB-first 权威认证、时间水位与 Fail-Closed**：
   - 认证链路在 `DURABLE_DB_REQUIRED` 下执行校验：`jti`/`sub`/`exp`/`iat` 任一缺失拒绝 `401`。当启用 Redis 且命中正向黑名单键（`population:security:jwt:blacklist:<sha256(jti)>`）时直接快速拒绝 `401`（不回源 DB，Redis 正向命中 `401` 优先于 DB 检查点失效 `503`，确保已吊销 JTI 永不复活）；仅当 Redis 未启用、Redis MISS 或 Redis 读取异常时才回源 MySQL 主库查询 `DurableRevocationStore.readAuthoritativeState(jti, issuedAt)`。
   - 回源 MySQL 时同步校验 `READY` 检查点有效性、JTI 撤销记录与 `iatMs < min_valid_iat_epoch_ms`（`iatMs == cutoff` 边界放行）。当检查点缺失/非 `READY`/`READY + 0`（未允许测试零值时）或 DB 查询异常时严格返回 HTTP `503`，异常响应与日志不含敏感凭证/SQL 明文，请求结束后 `SecurityContextHolder` 与 `CurrentUserContext` 均清空。
   - 运行期数据库推进 `min_valid_iat_epoch_ms` 后无本地 TTL 缓存遮蔽，后续 HTTP 请求立即返回 `401`。
3. **注销事务边界与 Redis 非阻塞降级**：
   - `AuthService.logout` 先于 MySQL 提交 `jwt_revocation` 记录（`jti_sha256`）再写入 Redis `PXAT` 加速标记；MySQL 提交成功而 Redis 写入失败时接口仍返回 `200` 且写入 `LOGOUT SUCCESS` 审计，后续请求由 MySQL 权威状态拒绝 `401`。
   - MySQL 写入失败回滚时返回 `503`，不写 Redis 黑名单且不记录 `LOGOUT SUCCESS` 审计。重复或并发撤销保持幂等。
4. **登录门禁、健康检查与周期清理**：
   - `AuthService.login` 维持账号不存在/密码错误 `401` 与禁用账号/角色 `403` 优先于存储不可用检查点校验；Redis 故障不影响登录；签发时间早于当前 cutoff 或签发后被并发推进水位线超越时拒绝 `503`。
   - `GET /api/health` 保持 `permitAll`（200），按模式返回非敏感 `revocationMode` 与 `revocationStoreStatus`。
   - `cleanupExpired` 保持默认 `300,000 ms` grace skew、有界批量删除、单实例互斥并发控制及异常隔离，不影响未过期或宽限期内吊销记录。

### 3.1 三模式差异与生产升级维护窗口约束

| 模式 | 权威状态源 | Redis 角色 | Redis 清空/RDB 重建风险 | 适用定位 |
|---|---|---|---|---|
| `SINGLE_INSTANCE_LOCAL` | JVM 内存 Map（可选补充写/查 Redis） | 非必须加速层 | 不可跨实例同步，重启或空 Redis 重建后失效 | 遗留兼容/本地单实例开发 |
| `SHARED_REDIS_REQUIRED` | Redis（原始 JTI Key） | 强依赖权威源 | **遗留兼容模式**：不可冒充持久安全模式，不抗空目录/RDB 丢失重建 | 既有共享 Redis 配置兼容 |
| `DURABLE_DB_REQUIRED` | **MySQL 主库**（`jwt_revocation` + `jwt_revocation_checkpoint`） | 可选加速层（`sha256(jti)`，`PXAT`） | **可抗空 Redis / Redis 宕机**：MISS 或异常时回源 MySQL 权威状态与时间水位 | Issue `#64` 目标持久多实例模式 |

- **生产升级停机切换路径（不默认启用 `v_bridge` 混合滚动）**：正式生产从旧版本/旧模式升级至 `DURABLE_DB_REQUIRED` 时，必须执行**停旧启新维护窗口**——先停止全部旧实例、执行 `V4_020_jwt_revocation.sql` 与 `activate_jwt_revocation_checkpoint.sql`（写入 `(UNIX_TIMESTAMP() + 1) * 1000` 并等待 UTC 越过 `min_valid_iat_epoch_ms`），再启动 `DURABLE_DB_REQUIRED` 新实例，严禁依赖 `READY + 0` 或新旧模式混跑。

---

## 4. 实测验证台账（按结果状态严格区分）

以下全部命令与结果均来自独立验收员实际执行输出：

| 检查项 | 执行命令 / 环境 | 状态 | 实测输出与说明 |
|---|---|---|---|
| 静态与契约检查 | `git diff --check`、迁移目录与敏感词扫描 | `EXECUTED_PASS` | 8 个 `src/main` 与 5 个新增测试文件合规；`doc/database/migrations/` 无改动；`git diff --check` 退出码 `0`；生产 diff 敏感扫描仅 `AuthService.java` 含 `"Bearer"` |
| Step 2 新增 H2 测试 | `.\mvnw.cmd test "-Dtest=DurableRevocationConfigurationStep2Test,DurableAuthFilterAndWatermarkTest,DurableLogoutAndRedisFaultTest,DurableLoginHealthCleanupTest"` | `EXECUTED_PASS` | `Tests run: 30, Failures: 0, Errors: 0, Skipped: 0`（7 + 7 + 10 + 6） |
| 既有 JWT/Auth 回归 | `.\mvnw.cmd test "-Dtest=S5A1StrictRevocationHttpIntegrationTest,TokenRevocationServiceConsistencyTest,RevocationConfigurationTest,Issue64ScheduledCleanupTest,Phase11TokenRevocationTest,RevocationEventsTest"` | `EXECUTED_PASS` | `Tests run: 77, Failures: 0, Errors: 0, Skipped: 0`（23 + 18 + 27 + 1 + 6 + 2） |
| 隔离 MySQL `8.4.8` 实测 | 临时 `mysqld.exe Ver 8.4.8`（`127.0.0.1:17542`，非 root 用户 `issue64_u_82954c13@127.0.0.1`，独立库 `issue64_test_step2_01`，先执行 `V4_020_jwt_revocation.sql` 与 `activate_jwt_revocation_checkpoint.sql`）下运行 `.\mvnw.cmd test "-Dissue64.mysql84.required=true" "-Dtest=DurableAuthStep2MySql84IT"` | `EXECUTED_PASS` | `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`；验收后已停库删除临时目录，`RESIDUAL_LISTEN_COUNT=0` |
| 全量洁净测试 | `.\mvnw.cmd clean test` | `EXECUTED_PASS` | `Tests run: 1069, Failures: 0, Errors: 0, Skipped: 6, BUILD SUCCESS`（6 项既有 opt-in 跳过：`Issue34FirstIssueRedTest`: 3、`Issue37ExpiryBatchIntegrationTest`: 1、`Issue64DurabilityRedGateTest`: 1、`Issue64TargetPrerequisiteCheckTest`: 1） |
| Python 门禁自测 | `python -m unittest -v scripts/issue64/test_issue64_red_gate.py` | `EXECUTED_PASS` | `15 tests OK` |
| 基线 RED JUnit 门禁 | `Issue64DurabilityRedGateTest`（`-Dissue64.red=true`） | `EXECUTED_RED` | `1 run, 1 failure (expected 401 was 200)`，按预期复现基线缺陷 |
| 目标前置 FAIL-CLOSED 校验 | `Issue64TargetPrerequisiteCheckTest`（`-Dissue64.target.prereq=true`，无 JDBC） | `EXECUTED_RED` | `1 run, 1 failure`，按预期 fail-closed |
| Python 基线 RED 门禁 | `scripts/issue64/issue64_red_gate.py --stage baseline-red` | `EXECUTED_RED` | `exit 2`, `verdict=RED`, `a_old_status=200`, `b_old_status=200`, `fresh_b_status=200`, `vulnerability_reproduced=true` |
| Python 目标 GREEN 门禁（无 opt-in/库） | `scripts/issue64/issue64_red_gate.py --stage target-green` | `ERROR` | `exit 1`, `verdict=ERROR`, `TARGET_PREREQUISITE_CHECK=FAIL`, `TARGET_SCENARIO_PASS=NOT_RUN`, `TARGET_GREEN_PASS=NOT_RUN` |
| **真实双 JVM `TARGET_GREEN`** | 双 JVM 实例 + 共享 MySQL 8.4 + Redis 端到端门禁 | **`NOT_RUN`** | Step 2 未执行真实双 JVM 联调与 Python `target-green` 全链路门禁，留待 Step 3 执行 |

---

## 5. 遗留限制与 Step 3 交接事项

1. **真实双 JVM 门禁未执行（`TARGET_GREEN = NOT_RUN`）**：Step 2 中的 `DurableLogoutAndRedisFaultTest` 仅在单 JVM 内通过独立 `TokenRevocationService` 实例模拟“另一实例未清理内存”，未启动双独立 JVM 进程连接共享 MySQL `8.4` 与 Redis 完成 `issue64_red_gate.py --stage target-green`（以及 Redis 清空/RDB 重启后旧 Token 仍被主库拒绝 401）。
2. **上线前生产切换前置约束**：沿用 Step 1 `STEP2_PRECONDITION`，生产切换至 `DURABLE_DB_REQUIRED` 前必须通过 `activate_jwt_revocation_checkpoint.sql` 设置下一整秒有效 UTC 水位并等待跨秒，`prod`/`production` 环境严禁 `READY + 0`。
3. **审计台账状态**：`doc/testing/2026-11-02-antigravity-audit-ledger.md` 中 Issue `#64` 继续保持 `OPEN`，待 Step 3 完成真实双 JVM `TARGET_GREEN` 门禁及独立复核后再评估状态关闭。

---

## 6. 独立复核安全补丁（`fix/20261114-issue64-step2-authority-preflight`）

基于原 Step 2 提交 `8da6e547729a1b458734b0285aadbe2148206f25`，补齐了权威撤销存储的完整只读探针校验，修复“`jwt_revocation_checkpoint` 为 `READY` 但 `jwt_revocation` 缺失、无 `SELECT` 权限或查询失败时启动、登录、签发复核及健康检查仍误放行 / 误报 `DB_PRIMARY_UP`”的阻断项：

1. **只读权威存储完整性探针（`DurableRevocationStore`）**：
   - 新增 `probeAuthoritativeStore()`（及包内重载 `probeAuthoritativeStore(String probeJtiSha256)`），复用 MyBatis `selectAuthoritativeState(GLOBAL_JWT_CUTOFF, probeJtiSha256, nowEpochMs)`，在单次主库读写事务中同时校验：`jwt_revocation_checkpoint` 可读、`GLOBAL_JWT_CUTOFF` 行存在且 `READY` 与非负水位、以及 `jwt_revocation` 通过 `EXISTS(...)` 真实可读；不写库、不创建/激活检查点、不依赖 Redis 或 JVM 缓存；若随机摘要发生碰撞命中（`REVOKED`）仍判定存储健康可读；SQL/权限/缺表错误映射为非 `IllegalStateException` 根因的 `RevocationStoreUnavailableException`。
   - 保留原有 `requireReadyCheckpoint()` 供 Step 1 测试使用。
2. **启动、登录、签发窗口、健康检查与注销前接线**：
   - `TokenRevocationService.validateConfiguration()`：启动改调 `durableStore.probeAuthoritativeStore()`，任一表不可读/未就绪均 fail-fast 抛出 `IllegalStateException`。
   - `TokenRevocationService.requireLoginStore()` & `verifyIssuedTokenNotSuperseded(Instant)`：统一通过 `requireTrustedAuthoritativeStore("LOGIN_PREFLIGHT", started)` 调用 `probeAuthoritativeStore()`，登录前与 JWT 签发后写入前均 fails closed `503`，不返回 JWT、不写 `LOGIN_SUCCESS`、不更新 `last_login_time`/`last_login_ip`。
   - `TokenRevocationService.revokeDurable(...)`：在 `durableStore.saveRevocation(...)` 之前先执行 `requireTrustedAuthoritativeStore("LOGOUT", started)` 预检，在认证过滤后、提交前发生检查点篡改或主表不可用时直接 503，不改变已有 `REQUIRES_NEW` 提交与尽力而为 Redis 补偿边界。
   - `HealthController.resolveDurableStoreStatus(boolean)`：保持 HTTP 200 且不泄露秘密/SQL/URL；`SELECT 1` 正常但 `probeAuthoritativeStore()` 因 `jwt_revocation` 缺失/SQL 错误失败时返回 `DB_REVOCATION_UNAVAILABLE`（绝不返回 `DB_PRIMARY_UP`）；检查点未激活/异常仍返回 `DB_CHECKPOINT_NOT_READY`；仅 `!database` 或 Store 缺失返回 `DB_PRIMARY_DOWN`。
3. **补丁后复测事实（全部基于真实 SQL/隔离 MySQL 8.4.8 与 H2，无伪装模拟）**：

| 检查项 | 命令/方式 | 判定 | 实测结果 |
|---|---|---|---|
| 探针/负例+Step1/2 核心 H2 | `mvnw.cmd test -Dtest=DurableAuthStep2AuthorityPreflightNegativeTest,...`（8 类） | `EXECUTED_PASS` | `51 run, 0 failed, 0 errors, 0 skipped` |
| Python 门禁自测 | `python -m unittest -v scripts/issue64/test_issue64_red_gate.py` | `EXECUTED_PASS` | `15 tests OK` |
| 独立隔离 MySQL 8.4.8 | `127.0.0.1:10932` 非 root `issue64_app`，导入 `V4_020`+激活脚本，`DurableAuthStep2MySql84IT`（含真实 `RENAME TABLE jwt_revocation` 故障与恢复） | `EXECUTED_PASS` | `6 run, 0 failed, 0 errors, 0 skipped`；完成后停库验证 `listen_count=0` 并清理临时目录 |
| 全量洁净回归 | `.\mvnw.cmd clean test` | `EXECUTED_PASS` | `Tests run: 1073, Failures: 0, Errors: 0, Skipped: 6`（同前 6 项既有 opt-in 跳过；其中 `AuthRbacIntegrationTest`(19)+`Phase11TokenRevocationTest`(6)+`RevocationConfigurationTest`(27)+`S5A1StrictRevocationHttpIntegrationTest`(23)+`RevocationEventsTest`(2) 既有 JWT 回归 `77/77` 通过） |
| 真实双 JVM `TARGET_GREEN` | 双 JVM 联调门禁 | **`NOT_RUN`** | 仍留待 Step 3 执行，#64 保持 `OPEN` |
