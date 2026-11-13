# #64 持久吊销设计及可执行 RED 门禁证据索引

当前状态：`#64 OPEN / DESIGN_CANDIDATE / RED_VERIFIED` · `TARGET_GREEN = NOT_RUN`

- **基线提交**：`c74867721adea57ad16012b4785b4f952b92fe86`（`integration/20261101-backend-p0-security`）
- **第 1 轮工作分支**：`audit/20261113-issue64-durability-design-red`（`48c701fb6a02d78407897857ee0c3b8c9548dde9`，第 1 轮原始证据文件均原样保留、未改动）
- **第 2 轮审查修正分支**：`audit/20261113-issue64-design-review-hardening`（基于 `48c701fb6a02d78407897857ee0c3b8c9548dde9` 创建）
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

## 4. 第 2 轮独立审查阻断项修正与复跑验证（`audit/20261113-issue64-design-review-hardening`）

本轮严格保留第 1 轮全部原始证据文件（第 1~3 节所列 `junit-red-gate*`、`live-red-*`、`python-selftest.log`、`targeted-revocation-maven.log`、`full-maven.log`）原样不变，所有第 2 轮复跑输出使用独立 `round2-*` 文件名记录。

### 4.1 第 2 轮门禁与回归复跑结果

| 验证项 | 执行命令 | 实测结果 |
|---|---|---|
| **Python 分类器与阶段隔离自检（阻断项 C、D）** | `python -m unittest -v scripts/issue64/test_issue64_red_gate.py` | **7 运行，0 失败，0 错误**（`Ran 7 tests in 8.488s — OK`，退出码 `0`）。显式验证全部 6 组分类边界（含 `oldA=200, oldB=200, fresh=500 -> ERROR`、`oldA=200, oldB=401, fresh=200 -> RED`、`oldA=401, oldB=401, fresh=200 -> PASS`、`oldA=503, oldB=401, fresh=200 -> ERROR`、`oldA=200, oldB=500, fresh=200 -> ERROR`、`empty_dir_verified=False -> ERROR`）及 `--stage target-green` 拒绝对 `jdbc:h2:mem:` 或未实现状态伪造 PASS。 |
| **`TARGET_GREEN` 前置拦截验证（Python）** | `python scripts/issue64/issue64_red_gate.py --stage target-green` | **退出码 `1`（`ERROR`）**，`reason="target_green_prereq_unmet: DURABLE_DB_REQUIRED is not implemented yet in RevocationProperties"`，`TARGET_GREEN = NOT_RUN`。 |
| **`TARGET_GREEN` 前置拦截验证（JUnit）** | `.\mvnw.cmd test "-Dissue64.target.green=true" "-Dtest=Issue64TargetGreenGateTest"` | **1 运行，1 失败，0 错误，0 跳过**（退出码 `1`）：`TARGET_GREEN_NOT_READY (NOT_RUN)`，绝不冒充通过。 |
| **双 JVM + 真实隔离 Redis `BASELINE_RED` 实时门禁** | `python scripts/issue64/issue64_red_gate.py --stage baseline-red --evidence-out doc/development/evidence/issue64-durability-design/round2-baseline-red-summary.json` | **`run_id=b4ff998c9a11`**（`stage="BASELINE_RED"`, `revocation_mode="SHARED_REDIS_REQUIRED"`, `shared_db_configured=false`；端口 Redis `14218` / A1 `14219` / B1 `14220`）。空目录切换校验 `verified_switch=true`（`dbsize=0`、`dump_rdb_exists=false`）；空库恢复后 `a_old_status=200`、`b_old_status=200`、`fresh_b_status=200`，**退出码 `2`（`verdict="RED"`, `vulnerability_reproduced=true`）**。 |
| **JUnit Opt-In `BASELINE_RED` 门禁** | `.\mvnw.cmd test "-Dissue64.red=true" "-Dtest=Issue64DurabilityRedGateTest"` | **1 运行，1 失败，0 错误，0 跳过**（退出码 `1`）：`Issue64DurabilityRedGateTest.revokedJwtMustRemainRejectedAfterRedisEmptyRecovery:176 Status expected:<401> but was:<200>`。 |
| **定向 JWT 撤销安全回归** | `.\mvnw.cmd test "-Dtest=S5A1StrictRevocationHttpIntegrationTest,TokenRevocationServiceConsistencyTest,RevocationConfigurationTest,Issue64ScheduledCleanupTest,Phase11TokenRevocationTest,RevocationEventsTest"` | **77 运行，0 失败，0 错误，0 跳过**（退出码 `0`）。 |
| **完整 Maven 回归** | `.\mvnw.cmd test` | **1015 运行，0 失败，0 错误，6 跳过**（退出码 `0`）。**跳过项精确拆分**：4 项为基线既有跳过（`Issue34FirstIssueRedTest` 3 项、`Issue37ExpiryBatchIntegrationTest` 1 项）；**2 项为 `#64` opt-in 门禁（`Issue64DurabilityRedGateTest` 1 项 + `Issue64TargetGreenGateTest` 1 项）**。 |
| **`TARGET_GREEN`（`DURABLE_DB_REQUIRED` + 共享 MySQL 8.4）** | — | **`NOT_RUN`**（留待下一轮生产实现后验收）。 |

### 4.2 第 2 轮证据文件 SHA256 与敏感数据扫描

| 文件名 | SHA256 | 入 Git / 说明 |
|---|---|---|
| `round2-python-selftest.log` | `c81a9d86011a29b71d36200b8e42fc3378ef47648a0166af5e1d81544d03a217` | `.gitignore` 忽略 `.log`，保留在本机 worktree；7/7 自检通过 |
| `round2-baseline-red-events.jsonl` | `8ca5cef8be42220f609c72bf2a837c13863fe05710e99fdf6534ec241bdf5fcf` | **已入 Git**；第 2 轮 `BASELINE_RED` 实时事件流（退出码 `2`） |
| `round2-baseline-red-summary.json` | `2b08693d26e4554ec77a31295d177f109e7d2fdd0736590a31d72f1b3f26b46f` | **已入 Git**；第 2 轮 `BASELINE_RED` 完整摘要（含 `stage`/`revocation_mode`） |
| `round2-junit-red-gate.log` | `4f9f521c84afbf2b5c2cf02510013c71a3531b270c8d6a95bd7ee3f4844fbc85` | `.gitignore` 忽略 `.log`，保留在本机 worktree；复现第 176 行预期 401 实得 200 |
| `round2-junit-target-guard.log` | `d51d39597e2695d0cd317d9898f3e2f1d835bd25a763f42f7aaa6754d78a128f` | `.gitignore` 忽略 `.log`，保留在本机 worktree；验证 `Issue64TargetGreenGateTest` 保护性失败 |
| `round2-targeted-revocation-maven.log` | `8d30b3113b8bb220ad61c2342f097f7a9b3798ab794e207c814a832728f96153` | `.gitignore` 忽略 `.log`，保留在本机 worktree；77/77 通过 |
| `round2-full-maven.log` | `31bddbdbf005cb045d09834cc929fbe4521565e7a5723318d8a2b0bd1ce9e3c1` | `.gitignore` 忽略 `.log`，保留在本机 worktree；1015 运行、0 失败、6 跳过 |

- 第 2 轮本机临时运行日志（`C:\Users\jxgm\AppData\Local\Temp\issue64-durability-red\run-b4ff998c9a11\*.log`）：
  - `redis-initial.log`: `fab6d34aae1ae3932cba2b349548b7c2359ac5a61ff6a2a7218547f055625f54`
  - `redis-restart.log`: `c7a21c0eb555fa2eb389b79b2620be79f2c7db6bb0ebb244b1ca21c55eae4cc1`
  - `redis-empty.log`: `60f8714dadb12be9ee96dee8c3abc5b30d9ae2cbfd2bceec368bcaebd011de91`
  - `app-A1.log`: `f274e8f73d691e74f2acda1d3079b134af754f11da89f184b0764424e0769955`
  - `app-B1.log`: `ae75a6919782354e36b0912b6a0fffc13459237fc991d4b412a535f73cfd8034`
- **第 2 轮敏感数据扫描**：对上述全量 `round2-*` 文件及 `run-b4ff998c9a11\*.log` 执行正则扫描（`eyJ|Bearer\s+[A-Za-z0-9._-]+|requirepass|JWT_SECRET|password=`）：**命中数为 `0`**。

---

## 五、第 3 轮最终审查修正复验（阻断项 E / F / G / H，2026-11-13）

> 施工分支：`audit/20261113-issue64-final-design-gates`（严格自 `201caf2a7893f4196b7635a336bb26d2459511a2` 分出）；仅修订设计文档与测试门禁安全，未改动 `src/main/**`、正式 Schema 或历史第 1/2 轮证据文件。

### 5.1 本轮门禁执行汇总（第 3 轮实跑输出，绝不伪装为 GREEN）

| 门禁 / 用例 | 执行命令与参数 | 实测结果 | 运行结论 | 证据路径 |
|---|---|---|---|---|
| Python 分类器与 DB 隔离护栏自检 | `python -m unittest -v scripts/issue64/test_issue64_red_gate.py` | 8/8 用例通过（覆盖 6 项分类器边界、目标缺失阻断、`--allow-target-green`、`issue64_test_*` 命名、`population_miniterm`/特权用户/`jdbc:h2:mem:`/未批准远端主机/未验证 DB 身份拦截），退出码 `0` | **`EXECUTED_PASS`**（仅隔离逻辑自检） | [round3-python-selftest.log](evidence/issue64-durability-design/round3-python-selftest.log) |
| `target-green` 无 opt-in / 未实现负向拦截 | `python scripts/issue64/issue64_red_gate.py --stage target-green` 及追加 `--allow-target-green` | 缺 opt-in 立即报 `target_green_opt_in_required`，加 opt-in 后立即报 `target_green_prereq_unmet: DURABLE_DB_REQUIRED is not implemented yet`，两次均未启动子进程且退出码 `1` | **`ERROR`（预期阻断；`TARGET_GREEN = NOT_RUN`）** | 第 3 轮终端实测 |
| 真实 Redis + 双 JVM `BASELINE_RED` 复现 | `python scripts/issue64/issue64_red_gate.py --stage baseline-red --evidence-out .../round3-baseline-red-summary.json` | Redis `8.10.3` 临时目录 `run-7a064ef3eb47`（端口 `2675`、A1 `2676`、B1 `2677`）：注销后 RDB 保留恢复 `401`（`dump_rdb_bytes=389`），Redis 断连期间业务 `503`、`/api/health` `200`；切换空目录（`DBSIZE=0`、`EXISTS=0`）恢复后旧 JWT 在 A1、B1 **双双返回 `200`**，`fresh_b_status=200`，退出码 `2` | **`EXECUTED_RED`**（`vulnerability_reproduced=true`） | [round3-baseline-red-summary.json](evidence/issue64-durability-design/round3-baseline-red-summary.json) / [round3-baseline-red-events.jsonl](evidence/issue64-durability-design/round3-baseline-red-events.jsonl) |
| JUnit RED 门禁 | `.\mvnw.cmd test "-Dissue64.red=true" "-Dtest=Issue64DurabilityRedGateTest"` | 运行 1，Failures: 1（第 177 行 `Status expected:<401> but was:<200>`），退出码 `1` | **`EXECUTED_RED`**（预期红灯） | [round3-junit-red-gate.log](evidence/issue64-durability-design/round3-junit-red-gate.log) |
| 目标前置条件检查守卫 | `.\mvnw.cmd test "-Dissue64.target.prereq=true" "-Dtest=Issue64TargetPrerequisiteCheckTest"` | 运行 1，Failures: 1（`[TARGET_PREREQUISITE_CHECK] FAILED (TARGET_GREEN = NOT_RUN): RevocationProperties.Mode.DURABLE_DB_REQUIRED is not implemented yet`），退出码 `1` | **`FAILED`（前置守卫，非 `TARGET_GREEN`）** | [round3-junit-target-prereq-guard.log](evidence/issue64-durability-design/round3-junit-target-prereq-guard.log) |
| `#64` 相关定向 Maven 回归 | `.\mvnw.cmd test "-Dtest=S5A1StrictRevocationHttpIntegrationTest,TokenRevocationServiceConsistencyTest,RevocationConfigurationTest,Issue64ScheduledCleanupTest,Phase11TokenRevocationTest,RevocationEventsTest"` | **77/77 通过**，0 失败，0 错误，0 跳过 | **`EXECUTED_PASS`** | [round3-targeted-revocation-maven.log](evidence/issue64-durability-design/round3-targeted-revocation-maven.log) |
| 全量 Maven 测试套 | `.\mvnw.cmd test` | **1015 项运行，0 失败，0 错误，6 跳过**（既有基线 `Issue34FirstIssueRedTest` 3 + `Issue37ExpiryBatchIntegrationTest` 1 = 4 项；`#64` 显式 opt-in 门禁 `Issue64DurabilityRedGateTest` 1 + `Issue64TargetPrerequisiteCheckTest` 1 = 2 项） | **`EXECUTED_PASS`**（基线套件回归） | [round3-full-maven.log](evidence/issue64-durability-design/round3-full-maven.log) |
| MySQL 8.4 跨时区 & 动态 `TARGET_GREEN` | 场景 #27（`+00:00`/`+08:00`/`-05:00`）及 7 项动态持久化验证 | 未连接真实 MySQL 8.4、未实现 `DURABLE_DB_REQUIRED` | **`NOT_RUN`**（严禁宣称通过） | [issue64-durable-revocation-gate.md](issue64-durable-revocation-gate.md) |

### 5.2 第 3 轮证据文件 SHA256 与敏感信息审计

- **提交入仓的第 3 轮文件 SHA256（无 UTF-8 BOM）**：
  - `round3-baseline-red-events.jsonl`: `76597d9cdcbcfa0ebf3de8a5510aff6c2a8b43a66c6ae4a2163d7fa205f69a02` (`3674` bytes)
  - `round3-baseline-red-summary.json`: `bc50c889db1d11f3b082eb5c06a01e8c2e9823cb0f72beaa78b6f5cf77f83b31` (`7081` bytes)
- **本机验证日志 SHA256（位于同目录/临时目录并纳入审计记录）**：
  - `round3-python-selftest.log`: `a63ea5997445d5322186ce0bccaac6e50ddd5867c81e9a8371f75a2b868392f9` (`1904` bytes)
  - `round3-junit-red-gate.log`: `fb3cdf58f5df9db7e1178b5713c088d43fd2f71ed3d96de4d93ad5fd54436694` (`43406` bytes)
  - `round3-junit-target-prereq-guard.log`: `207d40836212774f075fc4dc3606a5c3d9444ff407e2de0ce4ddf020f6039ecc` (`37222` bytes)
  - `round3-targeted-revocation-maven.log`: `186ab8b506a1dee16f1d421a1188b9d9ba94f59a2ff8b79ce0984322803a0b15` (`44904` bytes)
  - `round3-full-maven.log`: `6878a0dc3ffa522425f5f754b2ea17d5d6218c91f37e4f89bcf550184ffcca03` (`1899095` bytes)
- **第 3 轮临时运行目录**（保留本机：`C:\Users\jxgm\AppData\Local\Temp\issue64-durability-red\run-7a064ef3eb47\`）：
  - `data-initial\dump.rdb`: `2681e038f5ac644f007b8224cccbc189f4e24ee8d99c223223aefe8cd2f87dd4` (`389` bytes)
  - `gate-summary.json`: `bc50c889db1d11f3b082eb5c06a01e8c2e9823cb0f72beaa78b6f5cf77f83b31` (`7081` bytes)
  - `redis-initial.log`: `b7d0250aaac7c09a3a46a6e83aed2fb40877238f04013d45183cb610eba36314` (`1221` bytes)
  - `redis-restart.log`: `d11b4b7f2a2b6e2abf374fc19e70e7ffef4ffbf32e799a5dc303fece3dffbd38` (`1291` bytes)
  - `redis-empty.log`: `98ecc4e6214ec5a04b75c333031ce362b87b766d01f633aa1ab78f95b008cad2` (`940` bytes)
  - `app-A1.log`: `aeb9c4c1de3c388bca9733ff24705f5d30b12a2625396ed2303f67cc03977352` (`10069` bytes)
  - `app-B1.log`: `49befb11280af24dee5866ad95714ffd4c4df11956017ee80d3bda7f28e9e5d2` (`9480` bytes)
- **第 3 轮敏感数据扫描**：对全部 7 个 `round3-*` 产物执行正则扫描（`eyJ|Bearer\s+[A-Za-z0-9._-]+|requirepass|JWT_SECRET|password=`）：**命中数为 `0`**。
