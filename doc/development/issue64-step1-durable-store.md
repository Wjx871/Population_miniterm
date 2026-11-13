# Issue #64 Step 1 交付记录（SCHEMA + DURABLE STORE）

- **日期**：`2026-11-13`
- **基线分支 / SHA**：`integration/20261101-backend-p0-security` @ `359d85e5763951a06f3cd81369e95b6cb52ad426`
- **施工分支**：`feat/20261113-issue64-durable-store-step1`
- **Issue #64 状态**：`OPEN`（`STEP1_IMPLEMENTED 待验收`；`TARGET_GREEN = NOT_RUN`）
- **范围约束**：本轮仅完成数据库迁移脚本、运维激活模板、H2 测试夹具、持久化存储组件 `DurableRevocationStore`、门禁缺口修复与隔离验证；**不接入**认证拦截器、登录、注销、`RevocationProperties.Mode.DURABLE_DB_REQUIRED` 或任何既有业务表。

---

## 1. 本轮交付清单

### 1.1 生产与迁移产物

| 类别 | 文件路径 | 说明 |
| --- | --- | --- |
| MySQL 8.4 正式迁移 | `doc/database/migrations/V4_020_jwt_revocation.sql` | 新增 `jwt_revocation` 与 `jwt_revocation_checkpoint`；无硬编码 `USE`；执行前校验库名/只读/已有表结构一致；幂等初始化 `GLOBAL_JWT_CUTOFF` 为 `UNINITIALIZED + NULL`，绝不覆盖已有 `READY` 水位。 |
| 运维激活模板（默认不执行） | `doc/database/activate_jwt_revocation_checkpoint.sql` | 独立运维脚本：使用无参 `(UNIX_TIMESTAMP() + 1) * 1000` 单调推进 `GLOBAL_JWT_CUTOFF` 至 `READY` 并等待 `SLEEP(1.1)` 后输出 `readiness_verified`；严禁 `UNIX_TIMESTAMP(UTC_TIMESTAMP(...))`。 |
| 持久存储门面 | `src/main/java/com/wjx871/population/security/DurableRevocationStore.java` | 提供 `sha256Hex`（小写 64 位）、`nextWholeUtcSecondCutoffMillis`、`saveRevocation`、`readAuthoritativeState`、`requireReadyCheckpoint`、`advanceCheckpoint`、`cleanupExpiredBatch`（默认宽限 `300_000 ms`）。 |
| 独立事务代理执行器 | `src/main/java/com/wjx871/population/security/DurableRevocationTxExecutor.java` | 独立 Spring `@Service` Bean：写入与水位推进使用 `@Transactional(propagation = REQUIRES_NEW, rollbackFor = Exception.class)` 与 `TransactionSynchronization.afterCommit()` 提交确认；权威查询使用 `@Transactional(propagation = NOT_SUPPORTED, readOnly = false)`，避免自调用失效与 InnoDB `REPEATABLE-READ` 陈旧快照，并保证未来引入读写分离时仍走 Primary RW。 |
| MyBatis Mapper & XML | `src/main/java/com/wjx871/population/security/JwtRevocationMapper.java`<br>`src/main/resources/mapper/JwtRevocationMapper.xml` | 单次主库查询（无 `COALESCE(..., 0)`）；插入冲突由 `REQUIRES_NEW` 回滚释放 InnoDB 唯一索引 S 锁后，在全新 `REQUIRES_NEW` 事务中 `FOR UPDATE` 加 X 锁并同步延长 `expires_at_epoch_ms` 与 `token_expires_at`；有界批量删除 `LIMIT #{limit}`。 |
| 权威状态与检查点模型 | `src/main/java/com/wjx871/population/security/AuthoritativeRevocationState.java`<br>`src/main/java/com/wjx871/population/security/CheckpointSnapshot.java` | 三态：`REVOKED` / `NOT_REVOKED` / `STORE_UNAVAILABLE`；所有缺失、未初始化、无效或负数检查点及 DB 读取异常一律 fail-closed，不转化为 `NOT_REVOKED`；保留 `RevocationStoreUnavailableException` 供 Step 2 映射 HTTP `503`。 |

### 1.2 H2 测试与门禁修复产物

| 类别 | 文件路径 | 说明 |
| --- | --- | --- |
| H2 测试 Schema | `src/test/resources/schema.sql` | 同步 `jwt_revocation`、`jwt_revocation_checkpoint`（含 `chk_jwt_checkpoint_status` 与 `chk_jwt_checkpoint_ready_cutoff` 约束），并显式标注仅在 H2 合成夹具中初始化 `READY + 0`。 |
| Durable Store 单元测试（H2） | `src/test/java/com/wjx871/population/security/DurableRevocationStoreTest.java` | 11 个测试覆盖：小写 SHA-256 与不明文落库、重复写入幂等/不缩短有效期、并发 `UNIQUE KEY` 竞争、已过期记录判定、代理 `REQUIRES_NEW` 提交/回滚及读路径穿过外层事务、`selectAuthoritativeState` 故障 fail-closed、重启新建 Store 持久读取等。 |
| 检查点测试（H2） | `src/test/java/com/wjx871/population/security/DurableRevocationCheckpointTest.java` | 7 个测试覆盖：缺失行 / `UNINITIALIZED` / `INVALID` / `READY+NULL` / `READY+负水位` fail-closed、`iat` 整秒边界判定、多轮并发单调推进防回拨与状态护栏。 |
| 清理测试（H2） | `src/test/java/com/wjx871/population/security/DurableRevocationCleanupTest.java` | 3 个测试覆盖：`300_000 ms` 宽限期保留、有界 `batchSize` 分批清理防全表误删、数据库异常处理。 |
| MySQL 8.4 隔离集成测试 | `src/test/java/com/wjx871/population/security/DurableRevocationMySql84IT.java` | 4 个 opt-in `*IT` 测试（默认 `mvnw test` 跳过）：仅连接 `127.0.0.1` 非 `3306` 端口且仅限于 `issue64_test_*` 库与非 root 账号，覆盖 `V4_020` 幂等与结构护栏、8 线程唯一索引竞争与 `REPEATABLE-READ` 可见性、多时区 `+00:00`/`+08:00`/`-05:00` 水位不变性、`EXPLAIN` 索引与 DB `CHECK` 约束。 |
| 上阶段遗留门禁缺口修复 | `scripts/issue64/issue64_red_gate.py`<br>`scripts/issue64/test_issue64_red_gate.py` | 修复 `evaluate_checkpoint_fault_observation()`：强制要求 `missing`、`UNINITIALIZED`、`INVALID` 三项同时执行且均返回 `503` 才可判 `PASS`；缺任意一项返回 `NOT_RUN`/`FAIL`；新增正反例与 `READY+NULL`/`READY+负水位` 测试（`15/15` 通过）。 |

---

## 2. Primary DataSource 路由约束与 InnoDB 并发死锁修复记录

1. **单 DataSource 现状与未来读写分离约束**：项目当前仅配置单一 Primary RW `DataSource`；`DurableRevocationTxExecutor` 对 `executeReadAuthoritativeState` 与 `executeReadCheckpoint` 显式声明 `@Transactional(propagation = Propagation.NOT_SUPPORTED, readOnly = false)`，写入与水位推进声明 `@Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)`。若后续引入读写分离路由，所有 `jwt_revocation` / `jwt_revocation_checkpoint` 读写必须始终保持在 Primary RW 上，禁止路由至只读副本。
2. **真实 MySQL 8.4.8 暴露的并发锁升级问题与修复**：首次在真实 MySQL 8.4.8 `REPEATABLE-READ` 下运行 8 线程同 `jti` 并发写入时，`executeSaveRevocation` 在同一 `REQUIRES_NEW` 事务内捕获 `DuplicateKeyException` 后直接执行 `UPDATE`，导致多事务同时持有 InnoDB 唯一索引重复检查产生的共享 S 锁并互相等待 X 锁而触发 `DeadlockLoserDataAccessException`。现已拆分为：
   - 插入事务 `executeSaveRevocation` 若命中唯一键冲突，令 `DuplicateKeyException` 正常退出 `REQUIRES_NEW` 边界，立即回滚释放 S 锁；
   - 外层 `DurableRevocationStore.saveRevocation` 随后在全新 `REQUIRES_NEW` 事务中调用 `executeExtendExistingRevocation`，先 `FOR UPDATE` 获取 X 锁再单调延长有效期，并对极少数清理交错产生的 `ConcurrencyFailureException` 做有界重试。修复后在真实 MySQL 8.4.8 上 8 线程竞争测试一次通过。

---

## 3. 本轮验证结果（全部实测）

| 检查项 | 执行命令 / 环境 | 结果分类 | 实测摘要 |
| --- | --- | --- | --- |
| Python 门禁修复自测 | `python -m unittest -v scripts/issue64/test_issue64_red_gate.py` | `EXECUTED_PASS` | `Ran 15 tests in 8.397s`（`OK`，全部正反例通过） |
| 隔离 MySQL 8.4.8 DDL/激活验证 | 本机临时 `mysqld.exe 8.4.8`（`127.0.0.1:7541` 非 `3306`，临时数据目录，独立库 `issue64_test_store01` / `issue64_test_mismatch01`，非 root 账号 `issue64_tester@127.0.0.1`） | `EXECUTED_PASS` | `V4_020` 首次（`UNINITIALIZED + NULL`）、二次幂等、运维激活（`READY + 1794577847000`，`readiness_verified=1`）、三次不覆盖已有 `READY` 水位均通过；`issue64_test_mismatch01` 结构不匹配准确中止（`ERROR 1644: V4_020_ABORT_EXISTING_JWT_REVOCATION_SCHEMA_MISMATCH`, exit `1`）；结束已停止临时实例并清理目录与监听端口（`LISTEN_7541=0`）。 |
| MySQL 8.4.8 Spring IT | `.\mvnw.cmd test "-Dtest=DurableRevocationMySql84IT"`（指向上述隔离实例 `127.0.0.1:7541/issue64_test_store01`） | `EXECUTED_PASS` | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0` |
| Step 1 H2 核心存储测试 | `.\mvnw.cmd test "-Dtest=DurableRevocationStoreTest,DurableRevocationCheckpointTest,DurableRevocationCleanupTest"` | `EXECUTED_PASS` | `Tests run: 21, Failures: 0, Errors: 0, Skipped: 0`（`StoreTest` 11 + `CheckpointTest` 7 + `CleanupTest` 3） |
| 既有 JWT 吊销基线回归 | `.\mvnw.cmd test "-Dtest=S5A1StrictRevocationHttpIntegrationTest,TokenRevocationServiceConsistencyTest,RevocationConfigurationTest,Issue64ScheduledCleanupTest,Phase11TokenRevocationTest,RevocationEventsTest"` | `EXECUTED_PASS` | `Tests run: 77, Failures: 0, Errors: 0, Skipped: 0` |
| 基线 RED 门禁验证 | `.\mvnw.cmd test "-Dissue64.red=true" "-Dtest=Issue64DurabilityRedGateTest"` | `EXECUTED_RED` | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`（`Status expected:<401> but was:<200>`，确认未改动主链路） |
| 目标前置条件门禁 | `.\mvnw.cmd test "-Dissue64.target.prereq=true" "-Dtest=Issue64TargetPrerequisiteCheckTest"` | `EXECUTED_FAIL_AS_EXPECTED`（`TARGET_GREEN = NOT_RUN`） | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`（`DURABLE_DB_REQUIRED is not implemented yet`） |
| 全量默认 H2 测试集 | `.\mvnw.cmd test` | `EXECUTED_PASS`（含 6 项既有 opt-in 跳过） | `Tests run: 1036, Failures: 0, Errors: 0, Skipped: 6`（跳过项均为既有 opt-in 用例：`Issue34FirstIssueRedTest` 3、`Issue37ExpiryBatchIntegrationTest` 1、`Issue64DurabilityRedGateTest` 1、`Issue64TargetPrerequisiteCheckTest` 1） |
| `TARGET_GREEN` 全量端到端门禁 | — | `NOT_RUN` | Step 2/3 尚未接入主链路前禁止运行或宣称 `PASS` |

---

## 4. 独立复核安全补丁（`fix/20261113-issue64-step1-cleanup-schema-guard`）

- **补丁基线**：`feat/20261113-issue64-durable-store-step1` @ `731189f7338bf5acd5d8b42d3ab96d82cf6aaef2`
- **补丁范围**：仅修复阻断项 1（清理与延期写入竞态）、阻断项 2（`V4_020` 已有表结构预检强化）、补充 H2/MySQL 8.4 确定性并发回归与 CLI 正反例验证，并登记 `STEP2_PRECONDITION` 生产切流防呆风险；仍**未接入**认证/注销主链路及 `DURABLE_DB_REQUIRED`。

### 4.1 阻断项 1 修复：清理与延期并发竞态

1. **缺陷成因**：原 `JwtRevocationMapper.deleteByRevocationIds(@Param("ids"))` 仅凭前序快照查询的 `revocation_id IN (...)` 执行删除，若在 `selectExpiredRevocationIds` 与 `deleteByRevocationIds` 之间有并发 `REQUIRES_NEW` 事务通过 `saveRevocation()` 延长了某条记录的 `expires_at_epoch_ms`，则无条件 `DELETE` 会误删已重新有效的吊销记录。
2. **修复要点**：
   - 将签名修改为 `deleteByRevocationIds(@Param("ids") List<Long> ids, @Param("cutoffEpochMs") long cutoffEpochMs)`，`DurableRevocationTxExecutor.executeCleanupBatch` 将本次 `cutoffEpochMs` 透传给删除语句；
   - `JwtRevocationMapper.xml` 改为 `DELETE FROM jwt_revocation WHERE expires_at_epoch_ms <= #{cutoffEpochMs} AND revocation_id IN (...)`：在 InnoDB（`REPEATABLE-READ` / `READ-COMMITTED`）及 H2 MVStore 中，DML 删除均为当前读（current read），仅对 PK `IN (...)` 命中的最新已提交行重新核验权威过期条件，不扩大范围 gap 锁，也不会阻塞并发 `saveRevocation()` 导致新死锁；异常继续 fail-closed 抛出 `RevocationStoreUnavailableException`，绝不吞异常返回 `0`。
3. **确定性并发时序验证结果**：
   - **H2 (`DurableRevocationCleanupTest`)**：通过测试代理在 `selectExpiredRevocationIds` 选中 `[T2_expired, T1_extended]` 后、调用 `deleteByRevocationIds` 前暂停线程 A；线程 B 调用正式 `store.saveRevocation(T1, extendedExp)` 延长到期时间并提交；放行线程 A 后，`cleanupExpiredBatch` 精确返回 `1`（仅删除真正过期的 `T2`），`T1` 完整保留且 `expires_at_epoch_ms` / UTC `token_expires_at` 与延期一致、`readAuthoritativeState(T1) == REVOKED`；同时验证 8 线程清理+延期混跑无死锁/无重复行、`deleteByRevocationIds` DB 异常不返回 `0`。
   - **隔离 MySQL 8.4.8 (`DurableRevocationMySql84IT`)**：在真实 InnoDB `REPEATABLE-READ`（`127.0.0.1:5218`，非 root `issue64_tester@127.0.0.1`）复现同一先选后延确定性交错时序与 8 线程竞争压力测试，`Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`。

### 4.2 阻断项 2 修复：`V4_020` 预检强化与隔离 MySQL 8.4 CLI 实测

1. **修复要点**：`doc/database/migrations/V4_020_jwt_revocation.sql` 的 `v4_020_preflight_guard()` 现对已存在的两张表严格核验：
   - 存储引擎必须为 `InnoDB`；
   - `information_schema.columns` 总列数必须精确等于 `10`（`jwt_revocation`）与 `6`（`jwt_revocation_checkpoint`），且逐列核对类型、长度/精度、`IS_NULLABLE`、`COLUMN_DEFAULT`、`EXTRA`（含 `auto_increment`、`on update CURRENT_TIMESTAMP(3)`）；
   - `jwt_revocation` 的 `PRIMARY`（`revocation_id`）、`uk_jwt_revocation_jti_sha256`（`non_unique = 0` 且仅含单列 `jti_sha256`）、`idx_jwt_revocation_expires_ms`、`idx_jwt_revocation_user_id` 均为单列精确匹配，且 `jwt_revocation_checkpoint` 的 `PRIMARY` 仅含 `checkpoint_key`；
   - `information_schema.table_constraints` + `information_schema.check_constraints` 强制核验 `chk_jwt_checkpoint_status` 与 `chk_jwt_checkpoint_ready_cutoff` 均为 `ENFORCED = 'YES'`，且归一化去除字符集前缀（如 `_gbk` / `_utf8mb4`）后的真实 `check_clause` 表达式完全一致。
2. **真实 `mysql.exe 8.4.8` CLI 正反例实测（每例独立库，非 root `issue64_tester@127.0.0.1`）**：
   - 首次建表（`issue64_test_store01`）-> 成功，初始化 `UNINITIALIZED + NULL`；
   - 正确结构重复执行 -> 成功幂等；
   - 运维激活 `READY + 1794582830000` 后第三次执行 -> 成功且保留原 `READY` 水位（`1794582830000` 未被覆盖）；
   - 反例 1（`jwt_revocation` 增加额外列）-> 中止（`exit 1`，`V4_020_ABORT_EXISTING_JWT_REVOCATION_SCHEMA_MISMATCH`）；
   - 反例 2（`uk_jwt_revocation_jti_sha256` 改为 `(jti_sha256, user_id)` 复合唯一索引）-> 中止（`exit 1`）；
   - 反例 3（`chk_jwt_checkpoint_status` 同名但放行 `'BOGUS'`）-> 中止（`exit 1`，`V4_020_ABORT_EXISTING_JWT_CHECKPOINT_SCHEMA_MISMATCH`）；
   - 反例 4（`chk_jwt_checkpoint_ready_cutoff` 设为 `NOT ENFORCED`）-> 中止（`exit 1`）；
   - 反例 5（`jwt_revocation.revocation_reason` 改为 `VARCHAR(16) DEFAULT 'NONE'`）-> 中止（`exit 1`）；
   - 反例 6（`jwt_revocation_checkpoint.checkpoint_status` 默认值改为 `'READY'`）-> 中止（`exit 1`）；
   - 反例 7（`jwt_revocation` 存储引擎改为 `MyISAM`）-> 中止（`exit 1`）。

### 4.3 补丁后全量回归统计

- Python 门禁自测：`python -m unittest -v scripts/issue64/test_issue64_red_gate.py` -> `15/15 PASS`
- H2 核心 Store/Checkpoint/Cleanup 测试：`24/24 PASS`（`CheckpointTest` 7 + `CleanupTest` 6 + `StoreTest` 11）
- 隔离 MySQL `8.4.8` `DurableRevocationMySql84IT`：`5/5 PASS`（端口 `5218`，完成后 `listen_count=0`，临时数据目录已清理）
- 既有 JWT 吊销定向回归：`77/77 PASS`
- 全量洁净测试：`.\mvnw.cmd clean test` -> `Tests run: 1039, Failures: 0, Errors: 0, Skipped: 6`（6 项跳过为既有 opt-in：`Issue34FirstIssueRedTest` 3、`Issue37ExpiryBatchIntegrationTest` 1、`Issue64DurabilityRedGateTest` 1、`Issue64TargetPrerequisiteCheckTest` 1）
- 基线 RED 门禁：`Issue64DurabilityRedGateTest`（`-Dissue64.red=true`）-> `EXECUTED_RED`（`expected:<401> but was:<200>`）
- 目标前置条件门禁：`Issue64TargetPrerequisiteCheckTest`（`-Dissue64.target.prereq=true`）-> `EXECUTED_FAIL_AS_EXPECTED`（`TARGET_GREEN = NOT_RUN`）

### 4.4 `STEP2_PRECONDITION` 下一阶段风险登记

- **已知风险**：`DurableRevocationStore.advanceCheckpoint(long candidateMinValidIatEpochMs, String updatedBy)` 当前允许调用方传入 `0L`（用于 H2/IT 合成测试夹具）；若后续升级切流流程中存在任意代码路径或运维脚本在真实生产环境将 `UNINITIALIZED` 直接激活为 `READY + 0`，会绕过既有的旧版本签发 JWT 强制失效保障（`iat_ms < cutoff`）。
- **前置门禁要求（`STEP2_PRECONDITION`，本轮未视为已解决）**：在 Step 2 接入认证/登录/注销主链路及 `DURABLE_DB_REQUIRED` 前，**必须**设计并验证生产激活防呆边界：
  1. 任何应用启动、健康检查或生产运维流程不得自动将检查点激活为 `READY + 0`；
  2. 首次生产切流从 `UNINITIALIZED` 激活至 `READY` 时，必须强制使用当前 UTC 下一整秒有效水位（`(UNIX_TIMESTAMP() + 1) * 1000`），并等待真实 UTC 时间跨过该 `cutoff` 后才能恢复 JWT 登录签发；
  3. `src/test/resources/schema.sql` 及测试调用的 `READY + 0` 合成夹具不得作为生产默认行为或无保护暴露给生产入口。
