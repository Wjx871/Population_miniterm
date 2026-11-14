# #64 持久吊销设计及可执行 RED 门禁证据索引

当前状态：`#64 OPEN / DESIGN_CANDIDATE / RED_VERIFIED`

- **基线提交**：`c74867721adea57ad16012b4785b4f952b92fe86`（`integration/20261101-backend-p0-security`）
- **工作分支**：`audit/20261113-issue64-durability-design-red`
- **JDK / Python**：`Java 21.0.7` / `Python 3.12.4`

> [!NOTE]
> 本轮未修改 `src/main` 生产代码、未修改正式 MySQL Schema 或迁移脚本、未触碰生产 Redis/MySQL，也未清理上一轮 `#29/#64` 外部 Redis 证据目录。

---

## 1. 可执行 RED 门禁复现结果

### 1.1 JUnit Opt-In RED 门禁（`Issue64DurabilityRedGateTest`）

- **测试文件**：[Issue64DurabilityRedGateTest.java](../../src/test/java/com/wjx871/population/security/Issue64DurabilityRedGateTest.java)
- **触发命令（PowerShell）**：
  ```powershell
  .\mvnw.cmd test "-Dissue64.red=true" "-Dtest=Issue64DurabilityRedGateTest"
  ```
- **首次与过程失败记录（不隐瞒）**：
  1. Attempt 1（`junit-red-gate-attempt1.log`）：退出码 `1`，编译失败——`RedisConnectionFactory` 并非函数式接口，Lambda `() -> connection` 编译报错；保留原始失败日志后改用 `mock(RedisConnectionFactory.class)`。
  2. Attempt 2（仅本机保留 `target/issue64-local-logs/junit-red-gate-attempt2-raw.log`，不入库）：退出码 `1`，成功复现第 168 行 `Status expected:<401> but was:<200>`；但因 `@AutoConfigureMockMvc` 默认 `printOnlyOnFailure=true` 转储了带合成 JWT 的请求/响应体，故修改测试注解为 `@AutoConfigureMockMvc(print = MockMvcPrint.NONE)` 并重新运行。
- **最终 RED 运行（`junit-red-gate.log`）**：
  - **进程退出码**：`1`（Maven Surefire 测试失败）
  - **实际 HTTP 状态**：步骤 1（登录 `200`）、步骤 2（注销 `200`、A/B 均 `401`）、步骤 3（新 JTI `200`）、步骤 4（Redis 断网 `503`、`/api/health` `200`）、步骤 5（原存储恢复 `401`）均符合预期；步骤 6 清空共享存储模拟空库恢复后，已注销未过期旧 JWT 再次请求 `/api/auth/me` 返回 **`200`**。
  - **断言失败位置**：`Issue64DurabilityRedGateTest.revokedJwtMustRemainRejectedAfterRedisEmptyRecovery:169` — `java.lang.AssertionError: Status expected:<401> but was:<200>`。

### 1.2 双 JVM + 隔离 Redis 8.10.3 实时 RED 门禁（`issue64_red_gate.py`）

- **脚本文件**：[issue64_red_gate.py](../../scripts/issue64/issue64_red_gate.py)
- **触发命令**：
  ```powershell
  python scripts/issue64/issue64_red_gate.py --evidence-out doc/development/evidence/issue64-durability-design/live-red-summary.json
  ```
- **首次失败记录（不隐瞒）**：
  - Attempt 1（`live-red-events-attempt1.jsonl`、`live-red-summary-attempt1.json`）：退出码 `1`（`ERROR`，`reason=empty_dir_switch_unverified`）。因 MSYS2 `redis-server.exe` 对 `CONFIG GET dir` 返回 `/cygdrive/c/...`，与脚本初版的 `C:/...` 规范化字符串不一致；脚本安全地将其判定为 `ERROR` 而非冒报 `RED`（虽当时 A/B 均返回 `200`）。修正为同时比对 POSIX 与 `/cygdrive` 规范路径后重跑。
- **最终 RED 运行（`live-red-events.jsonl`、`live-red-summary.json`）**：
  - **测试环境标识**：`run_id=3a6b04d933c7`；仅监听 `127.0.0.1` 临时端口（Redis `10918`、JVM A1 `10919`、JVM B1 `10920`）；前缀 `issue64red-3a6b04d933c7`；隔离 Redis 8.10.3（只读调用本机已验证的便携可执行文件，随机凭据，AOF 关闭、自动保存关闭）；两个独立内存 H2 库；进程由脚本启动并在 `finally` 全部停止（`own_processes_stopped count=5`）。
  - **进程退出码**：`2`（严格 `RED` 判定）
  - **关键观察链**：
    1. `login_population` → `200`；`A_before_logout` / `B_before_logout` → `200` / `200`。
    2. `A_logout` → `200`；`A_after_logout` / `B_after_logout` → `401` / `401`。
    3. `login_fresh` → `200`（`jti_distinct=true`，`B_fresh_valid` → `200`）。
    4. 显式 `SAVE` + `SHUTDOWN NOSAVE` 期间：`B_during_outage_fresh` → `503`（fail-closed），`A_during_outage_health` → `200`。
    5. 原目录 `data-initial` 重启后：`A_after_preserved_rdb_old` / `B_after_preserved_rdb_old` → `401` / `401`，`B_after_preserved_rdb_fresh` → `200`（标记 `durable_guarantee_proven=false`）。
    6. **Redis 数据目录切换证据**：
       - 初始目录：`C:\Users\jxgm\AppData\Local\Temp\issue64-durability-red\run-3a6b04d933c7\data-initial`，停机前 `dbsize=3`，`dump.rdb` 存在（389 字节，SHA256 `bbf662f939497b26edb6f16de23ee19b64c4e9eda1736f0755432efabe29c51a`）。
       - 空数据目录：`C:\Users\jxgm\AppData\Local\Temp\issue64-durability-red\run-3a6b04d933c7\data-empty`，`CONFIG GET dir` 返回 `/cygdrive/c/Users/jxgm/AppData/Local/Temp/issue64-durability-red/run-3a6b04d933c7/data-empty`，`dbsize=0`，`revocation_key_exists=0`，`dump_rdb_exists=false`，`verified_switch=true`。
    7. **空库恢复后实际 HTTP 状态**：`a_old_status=200`、`b_old_status=200`、`fresh_b_status=200`，`verdict="RED"`，`vulnerability_reproduced=true`。

### 1.3 脚本阳性 / 阴性 / ERROR 自检（`test_issue64_red_gate.py`）

- **测试文件**：[test_issue64_red_gate.py](../../scripts/issue64/test_issue64_red_gate.py)
- **触发命令**：`python -m unittest -v scripts/issue64/test_issue64_red_gate.py`
- **结果**：`Ran 5 tests in 7.418s — OK`（退出码 `0`，日志 `python-selftest.log`）：
  - 阳性对照（漏洞修复后 A/B 返回 `401`、fresh 返回 `200`）：严格判定 `PASS`（退出码 `0`，`vulnerability_reproduced=false`），**不误报 RED**。
  - 阴性对照（A/B 返回 `200` 及瞬态 `503` 后返回 `200`）：严格判定 `RED`（退出码 `2`，`vulnerability_reproduced=true`）。
  - ERROR 对照（Redis 超时未重连、连接拒绝、前置检查失败、空目录切换未确认、fresh 返回 500）：严格判定 `ERROR`（退出码 `1`，`vulnerability_reproduced=false`）。

---

## 2. 回归测试统计与未验证范围

| 测试集 | 执行命令 | 结果统计 | 说明 |
|---|---|---|---|
| **定向 JWT 撤销安全回归** | `.\mvnw.cmd test "-Dtest=S5A1StrictRevocationHttpIntegrationTest,TokenRevocationServiceConsistencyTest,RevocationConfigurationTest,Issue64ScheduledCleanupTest,Phase11TokenRevocationTest,RevocationEventsTest"` | **77 运行，0 失败，0 错误，0 跳过**（退出码 `0`） | 覆盖既有严格共享 Redis 模拟、配置启动门禁、一致性并发、定时清理与事件脱敏。 |
| **完整 Maven 回归** | `.\mvnw.cmd test` | **1014 运行，0 失败，0 错误，5 跳过**（退出码 `0`） | **跳过项拆分**：4 项为基线既有跳过（`Issue34FirstIssueRedTest` 3 项、`Issue37ExpiryBatchIntegrationTest` 1 项）；**1 项为本轮新增 opt-in RED 门禁 `Issue64DurabilityRedGateTest`**。 |
| **Python 门禁自检** | `python -m unittest -v scripts/issue64/test_issue64_red_gate.py` | **5 运行，0 失败，0 错误**（退出码 `0`） | 验证 `PASS` / `RED` / `ERROR` 严格三态与脱敏摘要。 |
| **真实 MySQL 8.4 / 方案 A 动态门禁** | — | **`NOT_RUN`** | 本轮按施工边界未修改 `src/main` 与正式数据库，方案 A 及 MySQL 8.4 / `maxmemory` 淘汰 / Redis `SIGKILL` / 滚动升级见 [issue64-durable-revocation-gate.md](issue64-durable-revocation-gate.md) 待下一轮验收。 |

---

## 3. 证据文件 SHA256 与敏感数据扫描结论

### 3.1 证据文件清单（`doc/development/evidence/issue64-durability-design/`，其中 `.jsonl`/`.json` 已入 Git，`.log` 按 `.gitignore` 保留在本机 worktree）

| 文件名 | SHA256 | 用途说明 |
|---|---|---|
| `junit-red-gate-attempt1.log` | `54e98428a5036a3120ffd8a45b15121e1f179e4c6ec52968383fde05db3dad13` | JUnit 门禁首次编译失败原始日志 |
| `junit-red-gate.log` | `a445a3218044c1bb6c8a3d8bc801b948ad136f9779e8bfcdd766e087a3ed14b6` | JUnit 门禁最终 RED 复现日志（退出码 `1`，第 169 行预期 401 实得 200） |
| `live-red-events-attempt1.jsonl` | `838d0947e04dfd2439ad5102bf8ff815f89ad373db0d4fba7516a5d9c4685e7d` | 双 JVM 实时门禁首次 `ERROR` 原始事件流（MSYS2 `/cygdrive` 路径校验阻断） |
| `live-red-summary-attempt1.json` | `ba6419dda8396ac2cc8fff36897f0d8eb4fa48069f4e3b6a105d36964f3b015b` | 双 JVM 实时门禁首次 `ERROR` 汇总 |
| `live-red-events.jsonl` | `0d26de578d7c9c589d40b92e61896534de285dc34474093c24b0a3af37ab7480` | 双 JVM 实时门禁最终 `RED` 事件流（退出码 `2`） |
| `live-red-summary.json` | `bab742ef87d894198df2b25978bc40822afe21b32d583d5362c27f7990892b6b` | 双 JVM 实时门禁最终 `RED` 完整摘要与目录切换证据 |
| `python-selftest.log` | `d6af76691154dfb9ff97703e64d7a525fe89e3c4b3655b3a3571ba92ec10edf4` | Python 阳性/阴性/ERROR 自检输出 |
| `targeted-revocation-maven.log` | `ff1837f5e1797b1de2cb82d80b9c303d7bbe78e86c5c42bf8f5c64069b3eb454` | 定向 JWT 撤销回归（77/77 通过） |
| `full-maven.log` | `222e1cfd56ef2160ceb142388d0bf6b26e188bfd3126b6cf6fc75ccd148bca81` | 完整 Maven 回归（1014 运行、0 失败、0 错误、5 跳过） |

### 3.2 本机临时运行日志（不入 Git，`run-3a6b04d933c7`）

- `redis-initial.log`: `8f9414ebc3f58bbd59f604f56eb00f2d858958cce21204fe97e20ef7a893a164`
- `redis-restart.log`: `8a30a1bb3d8957b98d4632549ff23e75409fe20d6e609288db212823e7758a86`
- `redis-empty.log`: `e7e039799e1b951d393c00daa64fa4f535bc717de70088120a115c52f4a5ebcb`
- `app-A1.log`: `c831b8aec36d82f77fd528e14d3bc30be7514cf5711ea887ce1401cb42bc1a51`
- `app-B1.log`: `e1f67c428ced2bac30727b2d1e1473c4f7438076186f5e93ddbbc8d2fda018f7`

### 3.3 敏感数据扫描结论

- 对 `doc/development/evidence/issue64-durability-design/*` 全量 9 个文件执行正则扫描（`eyJ|Bearer\s+[A-Za-z0-9._-]+|requirepass|JWT_SECRET|password=`）：**命中数为 `0`**。
- 对本机临时运行日志 `C:\Users\jxgm\AppData\Local\Temp\issue64-durability-red\run-3a6b04d933c7\*.log` 执行同规则扫描：**命中数为 `0`**。
- JUnit Attempt 2 曾触发 MockMvc 默认失败转储的原始日志已移至 Git 忽略目录 `target/issue64-local-logs/junit-red-gate-attempt2-raw.log`，未纳入 Git。

---

## 4. 独立审查修正与复核验证（`audit/20261113-issue64-design-review-hardening`）

> 第 1~3 节保留基线 `48c701fb6a02d78407897857ee0c3b8c9548dde9` 下的全部原始 RED 证据、历史运行与日志哈希，未作删除、覆盖或重新解释。本节仅记录审查修正分支上的新增验证结果。

### 4.1 原始证据完整性核对

基线目录 `doc/development/evidence/issue64-durability-design/` 中全部 4 个跟踪文件 SHA-256 与基线提交一致：

- `live-red-events-attempt1.jsonl`: `838d0947e04dfd2439ad5102bf8ff815f89ad373db0d4fba7516a5d9c4685e7d`
- `live-red-events.jsonl`: `0d26de578d7c9c589d40b92e61896534de285dc34474093c24b0a3af37ab7480`
- `live-red-summary-attempt1.json`: `ba6419dda8396ac2cc8fff36897f0d8eb4fa48069f4e3b6a105d36964f3b015b`
- `live-red-summary.json`: `bab742ef87d894198df2b25978bc40822afe21b32d583d5362c27f7990892b6b`

### 4.2 本轮可执行验证与守卫检查结果

| 验证项 | 命令 | 退出码 | 核心观察 | 证据文件 |
| --- | --- | --- | --- | --- |
| Python 分类器与 `TARGET_GREEN` 前置守卫自检 | `python -m unittest -v scripts/issue64/test_issue64_red_gate.py` | `0` | `Ran 6 tests in 12.011s - OK`（覆盖 `200/200/500 -> ERROR`、`200/401/200 -> RED`、`401/401/200 -> PASS`、`503/401/200 -> ERROR`、`200/500/200 -> ERROR`、`verified_switch=False -> ERROR` 及 `TARGET_GREEN` 守卫） | [python-selftest.txt](evidence/issue64-design-review-hardening/python-selftest.txt) |
| Python `TARGET_GREEN` 门禁未就绪守卫 | `python scripts/issue64/issue64_red_gate.py --gate-stage TARGET_GREEN` | `1` | `verdict="ERROR"`，`reason="target_mode_not_implemented: DURABLE_DB_REQUIRED not found in RevocationProperties.Mode (TARGET_GREEN=NOT_RUN)"` | [target-green-guard.txt](evidence/issue64-design-review-hardening/target-green-guard.txt) |
| JUnit `BASELINE_RED` 门禁（`SHARED_REDIS_REQUIRED`） | `mvnw.cmd test -Dissue64.red=true -Dtest=Issue64DurabilityRedGateTest` | `1` | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`；`Issue64DurabilityRedGateTest.java:172` 断言 `Status expected:<401> but was:<200>`，无 MockMvc 原始头部/JWT 转储 | [junit-baseline-red.txt](evidence/issue64-design-review-hardening/junit-baseline-red.txt) |
| JUnit `TARGET_GREEN` 入口守卫（`DURABLE_DB_REQUIRED` 未实现） | `mvnw.cmd test -Dissue64.target.green=true -Dtest=Issue64DurableDbTargetGateTest` | `1` | `Tests run: 1, Failures: 1, Errors: 0, Skipped: 0`；`Issue64DurableDbTargetGateTest.java:34->53` 失败提示 `TARGET_GREEN_NOT_IMPLEMENTED: RevocationProperties.Mode.DURABLE_DB_REQUIRED does not exist yet (TARGET_GREEN=NOT_RUN; refusing to fall back to SHARED_REDIS_REQUIRED)` | [junit-target-green-guard.txt](evidence/issue64-design-review-hardening/junit-target-green-guard.txt) |
| 隔离真实 Redis 8.10.3 + 双 JVM `BASELINE_RED` | `python scripts/issue64/issue64_red_gate.py --gate-stage BASELINE_RED --redis-bin ... --classpath-file target/issue64-classpath.txt` | `2` | `run_id=0359668c1b23`（端口 Redis `13296` / A1 `13297` / B1 `13298`）；初始 `dbsize_before_shutdown=3`、`dump.rdb` 389 字节（SHA-256: `9ff06dd688ad19eace17b7ce7206df900172e5c7a4a5c6396b3aad75003ce5cb`）；空目录切换 `verified_switch=true`、`dbsize=0`、`revocation_key_exists=0`；最终 `a_old_status=200`、`b_old_status=200`、`fresh_b_status=200`、`verdict="RED"`、`exit_code=2` | [live-baseline-red-summary.json](evidence/issue64-design-review-hardening/live-baseline-red-summary.json) / [live-baseline-red-events.jsonl](evidence/issue64-design-review-hardening/live-baseline-red-events.jsonl) |
| 定向 JWT 吊销 Maven 回归 | `mvnw.cmd test -Dtest=S5A1StrictRevocationHttpIntegrationTest,TokenRevocationServiceConsistencyTest,RevocationConfigurationTest,Issue64ScheduledCleanupTest,Phase11TokenRevocationTest,RevocationEventsTest` | `0` | `Tests run: 77, Failures: 0, Errors: 0, Skipped: 0`（`Issue64ScheduledCleanupTest`: 1、`Phase11TokenRevocationTest`: 6、`RevocationConfigurationTest`: 27、`RevocationEventsTest`: 2、`S5A1StrictRevocationHttpIntegrationTest`: 23、`TokenRevocationServiceConsistencyTest`: 18） | [targeted-revocation-maven.txt](evidence/issue64-design-review-hardening/targeted-revocation-maven.txt) |
| 全量 Maven 测试回归（常规构建） | `mvnw.cmd test` | `0` | `Tests run: 1015, Failures: 0, Errors: 0, Skipped: 6`（较基线 `1014 / Skipped: 5` 仅新增 1 个 opt-in `Issue64DurableDbTargetGateTest`；Skipped 明细：`Issue34FirstIssueRedTest` 3、`Issue37ExpiryBatchIntegrationTest` 1、`Issue64DurabilityRedGateTest` 1、`Issue64DurableDbTargetGateTest` 1） | [full-maven.txt](evidence/issue64-design-review-hardening/full-maven.txt) |

- **目标模式验收状态**：`TARGET_GREEN = NOT_RUN`（本轮仅完成设计文档、门禁边界分离与未实现守卫，未修改 `src/main/**`、正式 Schema 或迁移 SQL；`#64` 审计账本保持 `OPEN`）。

### 4.3 本轮新增证据与临时日志 SHA-256

`doc/development/evidence/issue64-design-review-hardening/`：

- `full-maven.txt`: `f32d1bb603c9e9a35496c8772205c65d6728002eeb2611a49f804093b0234abd`
- `junit-baseline-red.txt`: `1493adbb0381d2056c8da68bbdd306b9f986cc138bab32abb56977deb826cf06`
- `junit-target-green-guard.txt`: `e455b10d59dab72ae3dd5b3ea8414487889a48848cb329596617969f70bf8d2e`
- `live-baseline-red-events.jsonl`: `9b56fae7f3a2cfaf3d1884f3ce1764fd8d0f850dc700a2117690377a6cbf97a8`
- `live-baseline-red-summary.json`: `c6bf66001f7f146388ac3c5e5363602c62fa8dbbb00b51bb241d093cd27cb5d2`
- `python-selftest.txt`: `20255435fb0adcabca9cd20684b323fed4e282b8370e2b83cfe18b40d6c9882b`
- `target-green-guard.txt`: `2ac4f75f26e4acd54d17f221c64a89580f6158ec7b93978518d0eefa293e448b`
- `targeted-revocation-maven.txt`: `764d64d2c1a684070160b4a37826348535cf26c8fab00e8f2b3134c812d928ec`

本机临时目录 `C:\Users\jxgm\AppData\Local\Temp\issue64-durability-red\run-0359668c1b23\`：

- `app-A1.log`: `cf363538e45ba84a0ec18c8e0a14a2b998aa8fa51cb659fdbf6b6d7b67d44c5a`
- `app-B1.log`: `dfa56cef17dd4b9889e117843fe007831c63f9ec8c33377cdae1fd18056915ad`
- `redis-empty.log`: `0f9b45a847dfcaa4350aae155e4b5f46ced0cae9dedc0204634f429b022dcf4d`
- `redis-initial.log`: `580bd182271754a0f56f7f9cf96ad4f9742f88dc642f146a52995589af0ed8a2`
- `redis-restart.log`: `a0fd12126eb88a25fa4623a4ab0fd2fca71d7907b25f72a4eb21b6a42736331e`

### 4.4 敏感数据扫描结论

- 对 `doc/development/evidence/issue64-design-review-hardening/*` 全部 8 个文件执行正则扫描（`eyJ|Bearer\s+[A-Za-z0-9._-]+|requirepass|JWT_SECRET|password=`）：**命中数为 `0`**。
- 对本机新运行日志 `C:\Users\jxgm\AppData\Local\Temp\issue64-durability-red\run-0359668c1b23\*.log` 全部 5 个文件执行同规则扫描：**命中数为 `0`**。
