# Issue #64 设计审查成果与当前 `integration` 兼容性核对及择优迁移报告

## 1. 核对范围与结论摘要

- **仓库**：`Wjx871/Population_miniterm`
- **对比基线（当前集成）**：`origin/integration/20261101-backend-p0-security` @ `bb33028db84ae37836052abba6cd7a6b18cd721c`
- **历史审查来源**：`origin/audit/20261113-issue64-design-review-hardening` @ `85bd1612ccc9e50df8b018db2ed8b3c7789474d5`
- **共同祖先（Merge-Base）**：`48c701fb6a02d78407897857ee0c3b8c9548dde9`
- **施工分支**：`audit/20261114-issue64-design-reconciliation`
- **总结论**：**无须迁移代码或门禁脚本，建议直接以当前 `integration` 为准（仅保留本核对报告）。**

`85bd161` 仅包含 1 个相对 `48c701f` 的审查硬化提交（涉及 15 个文件：8 个运行日志/证据文件、3 份开发文档、2 个 Python 门禁脚本及单测、2 个 Java 测试类）；而当前 `bb33028` 已在 `48c701f` 之上演进 7 个提交，完成了 `V4_020` 正式 Schema、Step 1 持久存储层、Step 2 认证与吊销主链路接入、权威性前置探针、三层门禁与全局七项 `TARGET_GREEN` 合同、以及隔离 MySQL `8.4.8` 实测。`85bd161` 中的全部有效设计约束均已被 `bb33028` 覆盖并进一步收严，未发现需要从历史审查分支反向迁入的代码约束；历史占位门禁类与大容量运行日志亦按规则不予迁移。

---

## 2. 阻断项 A–D 逐项核对结果

| 核对项 | 历史审查 (`85bd161`) | 当前集成 (`bb33028`) | 核对结论 |
| :--- | :--- | :--- | :--- |
| **A. 检查点缺失、非法状态、未激活水位 Fail-Closed 契约** | ADR 要求取消 `COALESCE(MAX(...), 0)` 默认放行，对缺失/非法/`UNINITIALIZED`/`NULL`/`0` 水位执行 Fail-Closed（503 + 禁止启动），禁止 `INSERT IGNORE` 覆盖已有水位；草案包含 `checkpoint_state`、`activation_mode`、`checkpoint_version` 及单调更新触发器 | 正式 `doc/database/migrations/V4_020_jwt_revocation.sql` 落地 6 列 `jwt_revocation_checkpoint`（`checkpoint_key`, `checkpoint_status` `UNINITIALIZED\|READY\|INVALID`, `min_valid_iat_epoch_ms`, `activated_at`, `updated_by`, `updated_at`）与两条强制 `CHECK` 约束，并用 `INSERT ... SELECT ... WHERE NOT EXISTS` 与 `v4_020_preflight_guard()` 防止误覆盖；`JwtRevocationMapper.xml` 去除 `COALESCE`；`CheckpointSnapshot.isReady()`、`DurableRevocationStore.probeAuthoritativeStore()` 与 `TokenRevocationService` 在启动、登录前、签发后复核、注销提交前、鉴权 MISS 与 `/api/health`（`DB_REVOCATION_UNAVAILABLE`）全链路执行 Fail-Closed（503），且非测试环境禁止 `READY + 0` | **已完整覆盖并精简落地**（无需迁移） |
| **B. Redis MISS 后 MySQL 主库查询、禁止陈旧 JVM 缓存** | 明确 Redis MISS 不得直接信任放行，禁止在 JVM 内存缓存 `min_valid_iat_epoch_ms` | `TokenRevocationService.isRevokedDurable` 在 Redis 命中时返回 401，在 Redis MISS 或异常（记录 `REVOCATION_CACHE_READ_UNAVAILABLE`）时同步回落至 `DurableRevocationStore.readAuthoritativeState`；后者通过 `DurableRevocationTxExecutor`（`Propagation.NOT_SUPPORTED`, `readOnly = false`）直连 MySQL 主库执行单次查询，无 JVM 水位缓存 | **已完整覆盖**（无需迁移） |
| **C. `BASELINE_RED` 与 `TARGET_GREEN` 独立验收语义** | 将 `issue64_red_gate.py` 拆分为 `baseline-red` 与 `target-green`（需 `--allow-target-green`），并新增占位测试类 `Issue64DurableDbTargetGateTest` | 进一步升级为三层验证级别（`TARGET_PREREQUISITE_CHECK`、`TARGET_SCENARIO_PASS`、`TARGET_GREEN_PASS`）与全局七项合同（`evaluate_target_green_contract`）；用显式 opt-in 的 `Issue64TargetPrerequisiteCheckTest` 替代占位类，且 `Issue64DurabilityRedGateTest` 明确限定 `SHARED_REDIS_REQUIRED` 边界；当前诚实保持 `TARGET_GREEN = NOT_RUN` | **已被更严格实现取代**（禁止回迁旧占位类） |
| **D. HTTP 分类器异常、环境错误及不完整观测判定** | 修正 HTTP 500/503、`None` 观测或 `empty_dir_verified`/`redis_up` 失败被误判为 `RED` 的漏洞，要求 `fresh_b_status == 200` 且旧 token 状态仅允许 `{200, 401}` | 完整保留并扩展为 `evaluate_post_empty_recovery`、`verify_post_empty_recovery_over_http`、`evaluate_mysql_fault_observation`、`evaluate_checkpoint_fault_observation` 与 `observe_target_redis_marker`；`scripts/issue64/test_issue64_red_gate.py`（15 项单元测试）覆盖 6 种分类器分支、HTTP 500/503/超时/不完整状态及 `5/7 -> INCOMPLETE`、`7/7 -> PASS` 边界 | **已完整覆盖**（无需迁移） |

> 注：关于阻断项 D 的 Python 单测写法差异，`85bd161` 中有 3 个分类分支（`503/401/200`、`200/500/200`、`200/401/200`）经本地 mock HTTP server 调用 `verify_post_empty_recovery_over_http`，`bb33028` 将其拆为直接测 `evaluate_post_empty_recovery` 并另测 `verify_post_empty_recovery_over_http` 的 HTTP 500 与超时分支，底层分类器覆盖等价。

---

## 3. 额外检查项逐项核对结果

1. **UTC 整秒 cutoff 与 `iat` 秒级精度**：
   - 历史审查草案 `85bd161` 曾使用 `UNIX_TIMESTAMP(CURRENT_TIMESTAMP(3)) * 1000`，存在 MySQL 会话时区解释偏差风险。
   - 当前 `bb33028` 已在 `doc/database/activate_jwt_revocation_checkpoint.sql` 修正为无参 `(UNIX_TIMESTAMP() + 1) * 1000` 并配合 `SLEEP(1.1)`；Java 侧 `DurableRevocationStore.nextWholeUtcSecondCutoffMillis()` 与 `JwtService.createIssuedToken()` 统一采用 UTC 整秒精度，`TokenRevocationService.requireLoginStore()` 校验 `clock.instant().getEpochSecond() * 1000L < snapshot.minValidIatEpochMs()`；`DurableRevocationMySql84IT` 已验证 `+00:00`、`+08:00`、`-05:00` 时区一致性。
2. **维护窗口切换、`v_bridge`、`JWT_SECRET` 轮换与 PITR 安全恢复**：
   - `bb33028` 的 `doc/development/issue64-durable-revocation-adr.md`（§5.2–§5.4）完整保留并固化了维护窗口停写激活顺序、回滚时 `v_bridge` 与严格推进 `min_valid_iat_epoch_ms`、单秘钥轮换严格单调推进、以及 PITR 恢复水位不得低于历史最后有效令牌过期上限的安全边界。
3. **MySQL `V4_020` 正式字段与文档一致性**：
   - 正式 `doc/database/migrations/V4_020_jwt_revocation.sql` 与当前 ADR §4、`issue64-step1-durable-store.md` 的表结构定义（`jwt_revocation` 10 列、`jwt_revocation_checkpoint` 6 列及 2 条 `CHECK` 约束）一致。
   - **历史草案显式标注**：`85bd161` 中出现但未落地的 `activation_mode`、`checkpoint_version`、`checkpoint_state` 字段及单调更新触发器 `trg_jwt_revocation_checkpoint_monotonic_update` **均为历史提案（Historical Proposals）**，不构成当前生产或集成实现。
   - **轻度文档陈旧提示（不触发代码改写）**：`bb33028` 中 `issue64-durable-revocation-adr.md` §6.1 仍保留迁移前的草案 SQL 片段（含 `USE population_miniterm;` 与 `INSERT IGNORE`），而正式落盘 SQL 已改用 `v4_020_preflight_guard()` 与 `INSERT ... SELECT ... WHERE NOT EXISTS` 且不硬编码 `USE`；另 `issue64-durable-revocation-evidence.md` 索引停留在 Step 1 补丁，Step 2 交付证据记录在 `issue64-step2-durable-auth.md` 与 `doc/testing/2026-11-02-antigravity-audit-ledger.md`。
4. **测试数据库隔离与生产库防误操作**：
   - `scripts/issue64/issue64_red_gate.py` 强制实施 `^issue64_test_[a-z0-9_]{4,40}$` 库名正则、禁止生产/系统库名（`FORBIDDEN_PROD_DB_NAMES`）、禁止高权限账号（`FORBIDDEN_PRIVILEGED_DB_USERS`）、仅允许环回或显式白名单远端主机，且在启动 `TARGET_GREEN` JVM 前通过 `default_db_identity_verifier` 连接实时查询 `SELECT DATABASE(), @@hostname, @@port, @@read_only`。
   - `V4_020_jwt_revocation.sql` 与 `activate_jwt_revocation_checkpoint.sql` 内置 `v4_020_preflight_guard()` / 激活前置守卫，拒绝系统库与只读库。
5. **`TARGET_GREEN` 全局七项合同不得退化**：
   - `evaluate_target_green_contract` 严格要求 7 项运行时观测（`http_post_empty_recovery`、`mysql_shared_revocation_write`、`mysql_outage_failover`、`mysql_fault_fail_closed`、`checkpoint_fault_fail_closed`、`redis_optional_after_db`、`db_identity_verified`）全部 `PASS` 方可给出 `TARGET_GREEN_PASS`（exit 0）；当前状态下各台账均诚实标注 `TARGET_GREEN = NOT_RUN`。

---

## 4. 顺带登记的观察与后续建议（本轮不改动 `src/main` 与正式 SQL）

本轮核对未发现破坏现有安全主链路的阻断级缺陷，以下两条低优先级加固建议仅作登记，供后续 Step 3 或运维加固参考：

1. **整秒水位模 1000 约束未在 DB/Java 探针层强制校核（低风险运维边界）**：
   - 现状：`V4_020_jwt_revocation.sql` 的 `chk_jwt_checkpoint_ready_cutoff` 校验 `min_valid_iat_epoch_ms >= 0`，`CheckpointSnapshot.isReady()` 同理检查 `>= 0`；正常生成路径（`activate_jwt_revocation_checkpoint.sql` 与 `nextWholeUtcSecondCutoffMillis()`）均严格生成 1000 的整倍数。但若 DBA 手工直接 `UPDATE` 写入非 1000 倍数的毫秒值，Schema 与探针不会主动拒绝。
   - 建议：保持由 `activate_jwt_revocation_checkpoint.sql` 唯一执行激活，后续可在 Step 3 或运维规范中决定是否追加整秒校验。
2. **`--shared-jdbc-url` 内嵌凭据字符串防护（仅脚本防御纵深）**：
   - 现状：`issue64_red_gate.py` 已把密码独立到 `--shared-db-password-env`，且不在事件流中打印 `shared_jdbc_url`；但 `parse_isolated_mysql_jdbc_url` 尚未显式拒绝 URL 中意外携带的 `user:pass@` 或 `?password=` 查询参数。
   - 建议：可在后续 Step 3 脚本优化时增加拒绝内嵌口令参数的前置校验。

---

## 5. 不予迁移清单（按约束明确排除）

1. `doc/development/evidence/issue64-design-review-hardening/*`（8 个 2026-11-13 历史 Maven/Redis 运行日志，不重复纳入新分支）。
2. `src/test/java/com/wjx871/population/security/Issue64DurableDbTargetGateTest.java`（已被 `Issue64TargetPrerequisiteCheckTest` 取代的历史占位类）。
3. 历史草案未落地字段/对象：`activation_mode`、`checkpoint_version`、`checkpoint_state`、`trg_jwt_revocation_checkpoint_monotonic_update`（标为历史提案）。

---

## 6. 本轮验证记录

- **Python 门禁单元测试（已执行）**：`python -m unittest -v scripts/issue64/test_issue64_red_gate.py` -> `15/15 OK`（确认 3 层门禁分类器、Blocker D 6 分支、7 项合同 `INCOMPLETE`/`PASS` 判定及 DB 隔离预检回归通过）。
- **Maven Java 回归测试（未重跑，标注跳过原因）**：本轮核对结论为无须修改测试代码、`src/main`、正式 SQL 或配置，未执行 Java 回归重跑；当前 `integration`（`bb33028`）在交付台账 `doc/testing/2026-11-02-antigravity-audit-ledger.md` 中登记的最近基线实测记录为 Step 1/2 H2（`51/51`）、既有 JWT 回归（`77/77`）、隔离 MySQL 8.4.8 `DurableAuthStep2MySql84IT`（`6/6`）及全量 `1073` 项（含 `6` 项 opt-in 跳过）。
- **端到端双 JVM `TARGET_GREEN` 门禁（`NOT_RUN`）**：未执行，严禁改写或伪造为 `PASS`，留待 Step 3 独立执行。
- **空白与变更范围检查（已执行）**：`git diff --check` 无告警，仅新增本审查报告。
