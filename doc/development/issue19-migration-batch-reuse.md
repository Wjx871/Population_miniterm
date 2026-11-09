# 邑循 #19：同市跨区迁入批次号被已取消草稿永久占用——修复与验证报告

状态：`CODE_CANDIDATE`（功能实现、H2 与真实 MySQL 8.4 验证、全量 Maven 回归均已完成；等待独立复核，不等于 `FIXED_VERIFIED`，也不代表生产已部署）。

- 精确施工基线：`integration/20261101-backend-p0-security` @ `1f3f4427775f55b853a7b42d2cb759b4cdfa7411`
- 施工分支：`fix/20261108-issue19-migration-batch-reuse`
- 隔离 worktree：`D:\学习用\大二下\小学期-issue19-migration-batch-reuse`
- 本轮范围：仅审计问题 **#19**；**#33 城市归属规则明确 out of scope**，其他审计问题状态一律不动。
- 证据目录：`doc/development/evidence/issue19-red-h2.txt`、`issue19-h2-green.txt`、`issue19-mysql84.txt`、`issue19-mysql84-schema.txt`、`issue19-mysql84-gate-rerun.txt`；Surefire/Maven 原始输出见各证据文件所列 `target/` 路径。
- 本报告只做文档对账，不修改源码、测试、迁移脚本或 schema。

---

## 1. 需求核对与结论摘要

### 1.1 审计台账原文

修复前 `doc/testing/2026-11-02-antigravity-audit-ledger.md:27`：

> `| 19 | HIGH | 跨区迁移批次号全局唯一使取消草稿占号 | OPEN | 批次号生成与表唯一约束仍未按已取消草稿释放；本轮未改。 | 后端 |`

原始审计报告（工作区归档 `D:\学习用\大二下\小学期\.agents\teamwork\orchestrator\SYSTEM_CORE_BUSINESS_AUDIT_REPORT.md:196-202`）说明：迁入表 `transfer_batch_no` 为全表物理唯一；草稿取消或驳回后批次号仍残留；重新使用同一合法批次创建迁入单会触发唯一键冲突 409，合法跨区迁移单据无法办理。其建议方向是“仅检查有效状态或条件约束”。本轮按团队验收契约进一步收紧为：**仅 `CANCELLED` 释放，不扩大到 `REJECTED`/`WITHDRAWN`/`RETURNED`**。

### 1.2 用户可感知的旧缺陷

1. 用户引用一个合法、已完成（`COMPLETED`）的同市跨区迁出批次，创建迁入草稿 A；
2. 用户取消草稿 A（`DELETE /api/applications/{id}`，底层 `ApplicationService.cancelDraft`）；
3. 用户重新创建迁入 B，仍使用同一批次号；
4. 旧实现因 `uk_migration_in_batch(transfer_batch_no)` 全局唯一继续拒绝 B：HTTP **409**（通用文案“数据已存在，请勿重复提交”）或理论上的 500；批次被已取消草稿“永久占号”。

目标结果：**取消 A 后 B 可以创建并完成整个迁入流程；A 的记录、原始批次号、审批/操作日志完整保留可追溯；B 仍受人员、目标区划、批次有效性、材料、审批和执行权限的全部校验约束。**

### 1.3 结论摘要

- 生产修复已完成：取消草稿在**同一事务**内把 `migration_in`/`migration_out` 明细同步为 `CANCELLED`，原 `transfer_batch_no` 永远保留；数据库以 **MySQL `STORED` 生成列 `active_transfer_batch_no` + 条件唯一索引 `uk_migration_in_active_batch`** 取代旧 `uk_migration_in_batch`；AI 预检/确认路径也排除 `CANCELLED` 行，AI 辅助入口现在可复用取消批次。
- 旧实现 RED 已真实复现：`Issue19MigrationBatchReuseIntegrationTest` 在旧实现上 **4 tests / 2 failures / 0 errors / 0 skipped**，主场景返回 HTTP 409；MySQL 8.4 旧 V4_018 schema 也真实复现 `ERROR 1062 ... uk_migration_in_batch`。
- H2 GREEN：`Issue19MigrationBatchReuseIntegrationTest` **13/13**，`Phase03MigrationIntegrationTest` **36/36**，`Issue33CrossDistrictCharacterizationIntegrationTest` **6/6**，`AiConfirmIntegrationTest` **15/15**，`AiPrecheckIntegrationTest` **36/36**；H2 G 并发 10 轮、H 交错 20 轮全部通过。
- 真实 MySQL 8.4.8 隔离实例：V4_011～V4_013、V4_015～V4_018 应用成功（V4_014 基线已含初始 scope 列，未重复应用）、V4_019 连续应用两次成功；旧索引消失、新索引与 `STORED` 生成列存在；历史 CANCELLED+DRAFT 回填与备份通过；10 轮抢占 + 10 轮取消/新建交错通过；临时实例已关闭。
- 全量 Maven：`.\mvnw.cmd test` exit 0，**913 tests / 0 failures / 0 errors / 3 skipped**，BUILD SUCCESS（03:12 min，77 个 Surefire XML）。
- 测试入口补充：t10 已修复 `Issue19MySql84IT.verifyMysqlIdentity` 的 Matcher 缺陷；t11 exact `.\mvnw.cmd -Dtest=Issue19MySql84IT test` 曾因继承的 H2-only `dumpH2MigrationInMetadata()` 得到 **13 tests / 0F / 1E**；t12 已用 `isH2Database()` 保护修复，t13 在全新 MySQL 8.4.8 实例（13373）正式复跑 exact 命令 **13 tests / 0F / 0E / 0S，exit 0**（见 §10.1；不影响 SQL gate 的 10+10 真实 MySQL 结论）。
- **不引用历史轮次数字**；本报告所有测试数字来自本轮 `doc/development/evidence/issue19-*.txt` 与 `target/surefire-reports`。

---

## 2. 六条静态发现与真实根因

### 2.1 六条静态发现（精确文件与行号）

以下为 t2 在精确基线上的静态核对结果，均已逐条读源码/脚本确认。

| 编号 | 静态发现 | 精确证据（文件:行） | 对 #19 的意义 |
|---|---|---|---|
| S1 | `cancelDraft` 只更新 `business_application` 并写一条审批日志，**完全没有通知任何迁入专用记录**，也没有调用 `ApplicationStatusListener`/`notifyStatus`。 | `src/main/java/com/wjx871/population/application/ApplicationService.java:103-119`（现实现；t2 基线 101-110；第 108 行 `mapper.updateStatus(DRAFT→CANCELLED)`，第 110-111 行仅插入 `ApprovalLog`）；`src/main/java/com/wjx871/population/approval/ApprovalService.java:158` 定义 `notifyStatus`，调用点只在 `ApprovalService` 的 submit/withdraw/decide（43、89、101、149 行），`ApplicationService` 无此依赖。 | 取消动作无法把 `migration_in` 从 DRAFT 同步为取消态，明细行原样残留。 |
| S2 | `MigrationBusinessStatus` 枚举**没有 `CANCELLED`**；而 `MigrationStatusListener` 用 `MigrationBusinessStatus.valueOf(s.name())` 做状态映射，若以 `ApplicationStatus.CANCELLED` 调用会抛 `IllegalArgumentException`。 | `src/main/java/com/wjx871/population/migration/MigrationBusinessStatus.java:2`（仅 `DRAFT, UNDER_REVIEW, APPROVED, COMPLETED, REJECTED, WITHDRAWN`）；`src/main/java/com/wjx871/population/migration/MigrationStatusListener.java:7`；`src/main/java/com/wjx871/population/application/ApplicationStatus.java:4`（含 `CANCELLED`）。 | 即使补上通知，明细也没有合法的“已取消”业务状态可落库。 |
| S3 | `uk_migration_in_batch` 是**无任何状态条件的全表唯一键**，作用于 `migration_in.transfer_batch_no`；已取消/已驳回/已撤回/已完成的迁入行与草稿一视同仁地占用同一命名空间。 | `doc/database/migrations/V4_003_household_migration_archive.sql:48`（`uk_migration_in_batch(transfer_batch_no)`）；`src/test/resources/schema.sql:206-212`（第 210 行 `transfer_batch_no VARCHAR(40) UNIQUE`）；新装脚本 `doc/database/population_miniterm.sql:528-534`（第 533 行同唯一键）。对照：`migration_out` 也有 `uk_migration_out_batch`（V4_003:64；schema.sql:217）。 | “永久占号”的数据库层根本机制：只要明细行保留批次值，任何状态都无法复用。 |
| S4 | `MigrationMapper.updateInStatus` 只更新 `business_status/operator_id/executed_at/version`，**从不清理、迁移或置空 `transfer_batch_no`**；Mapper 接口也没有任何取消/释放批次的方法。 | `src/main/resources/mapper/MigrationMapper.xml:16`（`updateInStatus` 的 SET 列表无 `transfer_batch_no`）；`src/main/java/com/wjx871/population/migration/MigrationMapper.java:4-7`（只有 insert/find/lock/updateDraft/updateStatus，无 cancel/release）。 | 现有状态流转不存在释放批次的 SQL 路径；取消同步必须在同一事务内实现。 |
| S5 | `createIn`/`updateIn` 在创建/编辑草稿时**直接写入客户端传入的 `transferBatchNo`**，创建阶段只校验“同市跨区必填”（`validateInType`）；真正校验批次存在/人员/目标/是否已用的 `validateBatch` 只在 `executeIn` 调用。 | `src/main/java/com/wjx871/population/migration/MigrationService.java:10-21`（createIn 直接 `mapper.insertIn(v)`）、`:23-39`（updateIn 直接 `updateInDraft`）、`:100`（`validateBatch` 只在 executeIn 的 50 行被调用）、`:101`（`validateInType` 仅非空校验）；`src/main/resources/mapper/MigrationMapper.xml:8`（`insertIn` 写 `transfer_batch_no`）、`:14`（`updateInDraft` 可改写 `transfer_batch_no`）。 | 草稿创建/编辑阶段就会撞上数据库唯一键；错误发生在业务校验之前，且错误信息不明确。 |
| S6 | 批次号只在迁出执行完成时生成并写入 `migration_out`；`findCompletedBatch`/`countCompletedInBatch` 只认 `COMPLETED`；旧 AI 预检链路用 `countAnyInBatch` 统计**所有状态**的 `migration_in` 行，并在确认时要求 `incoming.isEmpty()`。 | `src/main/java/com/wjx871/population/migration/MigrationService.java:55-91`（`executeOut` 仅 `IN_CITY_CROSS_DISTRICT` 迁出执行生成批次）、`:91`（批次赋值）、`:115`（`batchNo()` 格式 `TR-yyyyMMdd-XXXXXX`）；`src/main/resources/mapper/MigrationMapper.xml:46`（`findCompletedBatch`/`countCompletedInBatch`）；修复前 `src/main/java/com/wjx871/population/ai/precheck/AiResolutionService.java:294`（`countAnyInBatch(batchNo) > 0`）、`:309-314`（确认时 `incoming.isEmpty()` 要求）、`:356-358`（`countAnyInBatch` 无状态过滤）。 | 条件唯一键若保留已取消行原批次值，旧 AI 预检仍会按“任意一行即占用”拦截；因此 t4 同步修改 `AiResolutionService`，最终已修复，见 §5.2。 |

**补充静态事实**：`MigrationInRequest.transferBatchNo` 最大长度 40、允许为空（`MigrationInRequest.java:3`）；`MigrationDetailView` 直接暴露 `MigrationIn` 实体（`MigrationDetailView.java:3`），因此原 `transfer_batch_no` 保留在原行原列对取消详情可追溯很重要；`ApplicationStatus` 的 `SUBMITTED`/`RESUBMITTED` 当前无生产写入，迁移明细枚举也不含这两个值，保持遗留、不纳入本契约。

### 2.2 真实根因

```text
DELETE /api/applications/{id}
  → ApplicationService.cancelDraft
      ├─ business_application.status: DRAFT → CANCELLED
      ├─ 写 sys_approval_log(action=CANCEL)
      └─ 没有任何 status listener / migration_in 同步
  → migration_in.business_status 仍 = DRAFT
  → migration_in.transfer_batch_no 仍 = 原批次号（非空）
  → uk_migration_in_batch(transfer_batch_no) 继续把该值视为已占用
  → 新迁入 INSERT 同批次
      → DuplicateKeyException / DataIntegrityViolationException
      → 409（通用文案）或 500，申请行回滚，合法迁移无法办理
```

三条必须同时成立的根因：

1. **`cancelDraft` 只改 `business_application`、未通知迁入专用记录**：`ApplicationService.java:101-110` 无任何 `ApplicationStatusListener`/迁移 Mapper 调用；`ApprovalService.java:158` 的通知机制只在审批服务内部被调用。
2. **`MigrationBusinessStatus` 无 `CANCELLED`**：`MigrationBusinessStatus.java:2`；迁移明细不能表达“已取消”，`MigrationStatusListener.java:7` 的 `valueOf(s.name())` 也无法安全承接取消状态。
3. **`uk_migration_in_batch` 对 `transfer_batch_no` 全局唯一**：`V4_003:48`、`schema.sql:210`、`population_miniterm.sql:533`；约束不区分业务状态，已取消草稿的残留行因此永久占用批次。

### 2.3 静态、动态与未执行证据的边界

| 类别 | 本轮事实 | 证据/说明 |
|---|---|---|
| 静态源码/DDL 检查 | 六条发现、生产改动逐文件、V4_019/V4_018/schema 结构差异 | 本文 §2、§5、§6；`git diff --check` exit 0 |
| H2 动态验证 | Spring Boot + MockMvc + 真实 JWT/Service/MyBatis/H2 2.3.232；13/13 等 | `issue19-red-h2.txt`、`issue19-h2-green.txt` |
| 真实 MySQL 8.4 动态验证 | 临时隔离 MySQL 8.4.8（非 3306、REPEATABLE-READ、非 root 应用账号）；V4_019 两次、10+10 并发、14 项补充 HTTP | `issue19-mysql84.txt`、`issue19-mysql84-schema.txt` |
| 未实际执行 | 生产库存量数据规模/生产升级窗口、生产部署、浏览器 UI、跨进程网络压力测试、MySQL 8.4 以外版本 | 见 §10；不得冒充通过 |

---

## 3. 最小正确业务契约与状态矩阵

### 3.1 业务契约（1-7）

1. **批次来源唯一且可核对**：只有 `migration_out.migration_type='IN_CITY_CROSS_DISTRICT'` 的迁出在执行完成时生成批次号（`MigrationService.java:64`、`:88`），并在同一事务写入 `migration_out.transfer_batch_no`；非跨区迁出的批次号必须为空。迁入的 `transfer_batch_no` 必须能对应到唯一一条 `business_status='COMPLETED'` 的同类型迁出记录，且人员、目标区划一致（`MigrationService.java:73`、`MigrationMapper.xml:46`）。
2. **草稿即占用**：同一批次在任一时刻最多被一条“活动（未取消、未完成）”的 `migration_in` 占用。`DRAFT`、`UNDER_REVIEW`、`APPROVED` 状态均占用；创建/编辑草稿时写入的批次值立即参与占用判定。
3. **完成即永久消费**：`migration_in.business_status='COMPLETED'` 的批次永久不可再被任何新的迁入申请占用或执行；`countCompletedInBatch` 与数据库条件唯一键必须双重阻止（`MigrationMapper.xml:46`、`MigrationService.java:73`）。
4. **仅 CANCELLED 释放**：只有 `business_application` 从 `DRAFT` 经统一取消入口转为 `CANCELLED` 时，`migration_in` 才在同一事务转为 `CANCELLED` 并退出“活动占用”唯一范围；原始 `transfer_batch_no` 必须**保留在原行原列**用于历史追踪，不得删除、覆盖、置空历史批次号。
5. **释放后可复用**：取消事务提交后，同一批次可被新的迁入 DRAFT 重新引用；新申请必须重新满足第 1 条及人员/目标家庭/材料/审批/执行权限全部校验。原取消申请、原批次值、审批日志与操作日志必须仍可查询，原记录不得被物理删除或改成非取消状态。
6. **并发由数据库负责**：占用与释放的最终裁判必须是数据库级唯一约束/等价的数据库级机制，必须能在 MySQL 8.4 REPEATABLE READ 下保证“同一批次最多一个活动占用”。**禁止**以 `DROP UNIQUE + 应用层 SELECT COUNT` 作为唯一保护；**禁止**依赖应用层预锁（`SELECT ... FOR UPDATE` 锁批次/锁表后再插入）作为正确性基础；服务层可做前置查询用于友好提示，但并发正确性必须由唯一键兜底。
7. **状态边界不扩大**：`REJECTED`、`WITHDRAWN`、`RETURNED`（其明细保持 `APPROVED`）在现有契约下继续占用，不得在本轮释放；`COMPLETED` 永久占用；不得把“所有非 COMPLETED”当作释放条件。历史数据仅允许按 §6 对“主申请已 CANCELLED、明细仍 DRAFT”做有备份、有日志的状态对齐，不得重分类其他状态。

### 3.2 状态占用矩阵

| # | `business_application.status` | 修复前 `migration_in.business_status` | 修复后 `migration_in.business_status` | 是否占用批次 | 依据/说明 |
|---:|---|---|---|---|---|
| 1 | `DRAFT` | `DRAFT` | `DRAFT` | **占用** | 创建即写批次；条件唯一键中的非 NULL 值参与唯一性。 |
| 2 | `UNDER_REVIEW` | `UNDER_REVIEW` | `UNDER_REVIEW` | **占用** | `ApprovalService.submit` → `notifyStatus` → `MigrationStatusListener`（ApprovalService:43/158）。 |
| 3 | `APPROVED` | `APPROVED` | `APPROVED` | **占用** | `approve` → `notifyStatus`（ApprovalService:149）；`executeIn` 还需 `validateBatch`。 |
| 4 | `RETURNED` | `APPROVED`（明细未联动） | `APPROVED`（保持） | **占用（不扩大释放）** | `returnApplication` 不调用 `notifyStatus`；`Phase03MigrationIntegrationTest.java:133-134` 明确断言；重提后变 `UNDER_REVIEW`。 |
| 5 | `COMPLETED` | `COMPLETED` | `COMPLETED` | **永久占用** | `countCompletedInBatch` 阻止再次执行（MigrationMapper.xml:46）；条件唯一键保留原值（非 CANCELLED）。 |
| 6 | `REJECTED` | `REJECTED` | `REJECTED` | **占用（现状，不扩大释放）** | `reject` → listener 更新为 REJECTED；批次值保留。 |
| 7 | `WITHDRAWN`（正常撤回） | `WITHDRAWN` | `WITHDRAWN` | **占用（现状，不扩大释放）** | `withdraw`（非 RETURNED 分支）→ listener；批次值保留。 |
| 8 | `WITHDRAWN`（RETURNED 后撤回） | `WITHDRAWN` | `WITHDRAWN` | **占用（现状，不扩大释放）** | `withdraw` RETURNED 分支 → `notifyStatus(a, WITHDRAWN)`，明细从 APPROVED → WITHDRAWN。 |
| 9 | `CANCELLED`（草稿取消） | `DRAFT`（缺陷现状） | **`CANCELLED`** | **释放（本轮唯一释放路径）** | `cancelDraft` 同事务更新明细并写审批日志；原 `transfer_batch_no` 保留在原行。 |
| 10 | `SUBMITTED` / `RESUBMITTED` | 无生产写入，明细枚举无对应值 | 不定义（保持遗留） | 不适用 | 仅状态机白名单（`ApplicationStateMachine.java:22-30`）；未来若引入必须单独设计。 |

迁移明细层：`DRAFT`/`UNDER_REVIEW`/`APPROVED`/`COMPLETED`/`REJECTED`/`WITHDRAWN` 均占用；仅新增的 `CANCELLED` 释放。**不要用 `business_status != 'COMPLETED'` 作为释放条件；正确条件是仅 `CANCELLED` 为释放。**

---

## 4. 方案比较与最终设计选择

### 4.1 比较维度

- **并发正确性（RR）**：MySQL 8.4 REPEATABLE READ 下，两个并发请求（创建/取消交错）是否由数据库保证“同一批次最多一个活动占用”。
- **历史数据**：是否改写/复制/删除存量 `migration_in` 数据；是否保留原始 `transfer_batch_no`；是否可审计/回滚。
- **迁移成本**：DDL 复杂度、是否重建索引/表、代码改动面、需要同步的 schema 载体数量。
- **H2/MySQL 对齐**：H2 2.3.232 与真实 MySQL 8.4 的语义是否一致；DDL 是否可在两边表达。
- **可追溯性**：取消后原始批次号、申请记录、审批/操作日志是否仍完整可查。

### 4.2 方案 A：生成列 + 条件唯一键（最终采用）

**机制**：`migration_in` 增加 `active_transfer_batch_no VARCHAR(40) GENERATED ALWAYS AS (CASE WHEN business_status='CANCELLED' THEN NULL ELSE transfer_batch_no END) STORED`；在生成列上建 `uk_migration_in_active_batch(active_transfer_batch_no)`，删除旧 `uk_migration_in_batch(transfer_batch_no)`。H2 schema 使用同一 CASE 计算列（无 `STORED` 关键字）与唯一索引。

- **并发（RR）**：✅ 数据库唯一索引做最终裁判；两个并发 INSERT 同一批次最多一个成功；取消事务把 `business_status` 改为 CANCELLED 时生成列在提交后变 NULL，释放唯一键。真实 MySQL 8.4 10 轮抢占 + 10 轮交错通过。
- **历史数据**：✅ 不改写 `transfer_batch_no`；仅将存量“主 CANCELLED / 明细 DRAFT”受控回填为 CANCELLED（§6），原值保留、可审计。
- **迁移成本**：⚠️ 中：新增生成列 + 建/删唯一索引；MySQL `STORED` 可能触发表重建，需要维护窗口；H2 schema、V4_019、新装基线三处需保持语义一致。
- **H2/MySQL 对齐**：✅ H2 2.3.232 支持无 `STORED` 的等价计算列及唯一索引；MySQL 8.4 支持 `STORED` 生成列 + 唯一索引。t5 `information_schema` 与 t6 `SHOW CREATE TABLE`/`information_schema.columns` 均已核对。
- **可追溯性**：✅ 最佳：原 `transfer_batch_no` 原行原列保留；取消记录、审批日志、备份表均可查。
- **结论**：**最终采用**。真实 MySQL 8.4 验证通过（见 §7.3）。

### 4.3 方案 B：独立批次占用表

**机制**：新增 `migration_batch_claim`（`batch_no` 唯一、`application_id`、`claim_status`、`claimed_at`、`released_at`、`consumed_at` 等），创建/取消/完成时双写。

- **并发（RR）**：✅ 若 `batch_no` 为唯一键可保证；但需要所有创建/取消/完成路径双写，任何漏写导致占用漂移；软释放会回到条件唯一问题。
- **历史数据**：⚠️ 要为存量所有活动/已完成 `migration_in` 回填 claim；存量取消行也要判定是否已释放；数据迁移面大于 A。
- **迁移成本**：❌ 高：新表 + Mapper + 服务层三条路径 + 回填 + 对账；双写一致性/补偿逻辑需要额外测试。
- **H2/MySQL 对齐**：✅ 普通表/唯一键两边完全一致。
- **可追溯性**：✅ 可设计 claim/release 历史，但依赖双写可靠。
- **结论**：**不推荐**；只在无法使用生成列/条件唯一键时作为备选，且必须补双写对账与补偿设计。

### 4.4 方案 C：取消时把 `transfer_batch_no` 置 NULL（配历史列或日志）

**机制**：新增历史列（如 `cancelled_transfer_batch_no`、`batch_released_at`），取消时复制原值并置 `transfer_batch_no=NULL`；保留旧 `uk_migration_in_batch`（NULL 可重复）。

- **并发（RR）**：✅ 普通可空唯一列在 H2/MySQL 均允许多个 NULL；活动行唯一性仍由旧唯一键保证。
- **历史数据**：⚠️ 会改写原 `transfer_batch_no` 为 NULL；虽可用历史列保存，但不满足“原 transfer_batch_no 保留在原行原列”的验收表述，且所有按 `transfer_batch_no` 查询/展示的旧代码（如详情、AI 预检）需要额外兼容。
- **迁移成本**：⚠️ 中低：新增历史列和取消 SQL，但需改详情读取/接口展示；历史回填必须复制再置空，漏拷不可恢复。
- **H2/MySQL 对齐**：✅ 普通列 + 唯一 NULL 语义一致。
- **可追溯性**：⚠️ 依赖历史列/日志正确写入；原列已空，审计需改查历史列。
- **结论**：**不推荐为主方案**；仅在生成列方案被真实 MySQL 阻断、且证明历史完整保留并经船长/复核批准后作为降级备选。

### 4.5 方案 D：简单 `DROP UNIQUE` + `SELECT COUNT` 预检查

**机制**：删除 `uk_migration_in_batch`，在 `createIn/updateIn` 写库前查询活动状态行数是否为 0。

- **并发（RR）**：❌ 不成立：两个事务在 REPEATABLE READ 下都读到 0（快照/无锁），都插入成功，产生两个活动占用；没有数据库唯一约束兜底。即使 `SELECT ... FOR UPDATE`，无合适索引/间隙锁也难保证 phantom；引入预锁又违背“不要预锁设计”。
- **历史数据**：✅ 不需要改数据（但主申请/明细状态仍不一致）。
- **迁移成本**：✅ 最低（只删索引/加查询）。
- **H2/MySQL 对齐**：❌ 两边都不会提供并发唯一性；H2 可能因 MVCC 表现更“侥幸”，无法证明 MySQL RR 正确。
- **可追溯性**：✅ 原记录不动。
- **结论**：**明确否决**；不得作为实现或验收方案。

### 4.6 五维比较汇总

| 方案 | 并发 RR | 历史数据 | 迁移成本 | H2/MySQL 对齐 | 可追溯性 | 结论 |
|---|---|---|---|---|---|---|
| A：生成列 + 条件唯一键 | ✅ 数据库唯一索引；MySQL 10+10 轮通过 | ✅ 不改批次号；仅状态回填 | ⚠️ 中（列+索引） | ✅ H2/MySQL 均已实测 | ✅ 原行原列保留 | **最终采用** |
| B：独立占用表 | ✅（双写正确时） | ⚠️ 需全量回填 claim | ❌ 高（新表+双写+对账） | ✅ | ✅（依赖双写） | 不推荐；备选 |
| C：置 NULL + 历史列 | ✅（NULL 可重复） | ⚠️ 原列被改写，需历史列 | ⚠️ 中低（需改读取） | ✅ | ⚠️ 依赖历史列 | 不推荐为主；降级备选 |
| D：DROP UNIQUE + COUNT | ❌ 并发双占用 | ✅ 无 | ✅ 最低 | ❌ 无数据库保证 | ✅ | **否决** |

### 4.7 关于预锁

本设计**不采用**任何预锁作为正确性基础：不先 `SELECT ... FOR UPDATE` 锁 `migration_out` 批次行、不锁“批次锁表”再插入、不使用 Redis/本地锁串行化。方案 A 的唯一性由**数据库唯一索引**给出；应用层 `countActiveInBatch` 只是友好提示，不能作为唯一保护。

---

## 5. 生产改动逐文件（已实现）

### 5.1 源码与 Mapper

| 文件 | 改动 | 关键位置/说明 |
|---|---|---|
| `src/main/java/com/wjx871/population/application/ApplicationDraftCancellationListener.java`（新增） | 新增 opt-in 草稿取消接口：`boolean supports(BusinessType)` + `void onDraftCancelled(BusinessApplication)` | 1-22 行；明确说明不向 `ApplicationStatusListener` 全体广播 CANCELLED，避免 CancellationStatus/Floating/Export/KeyPopulation 状态枚举无 CANCELLED 而回归。 |
| `src/main/java/com/wjx871/population/application/ApplicationService.java` | `cancelDraft` 注入 `List<ApplicationDraftCancellationListener>`；在 `business_application` DRAFT→CANCELLED 更新和 CANCEL 审批日志之后，按 `supports(type)` 调用 `onDraftCancelled`；异常向外传播使整个事务回滚。 | 第 34 行字段；第 104-119 行取消事务与回调循环；`@Transactional` 保持。 |
| `src/main/java/com/wjx871/population/application/ApplicationStatusListener.java` | Javadoc 明确“草稿取消不经该接口广播”，专用业务类型必须 opt-in 新接口。 | 1-13 行；不改变 `onStatusChanged` 既有语义。 |
| `src/main/java/com/wjx871/population/migration/MigrationBusinessStatus.java` | 增加 `CANCELLED`。 | 第 2 行：`DRAFT, UNDER_REVIEW, APPROVED, COMPLETED, REJECTED, WITHDRAWN, CANCELLED`。 |
| `src/main/java/com/wjx871/population/migration/MigrationStatusListener.java` | 实现 `ApplicationDraftCancellationListener`；`onDraftCancelled` 对 `MIGRATION_IN`/`MIGRATION_OUT` 同事务调用 `updateInStatus`/`updateOutStatus` 到 `CANCELLED`；已是 CANCELLED 时幂等返回；0 行更新抛 409。 | 第 4 行实现接口；第 8-18 行回调；原 `onStatusChanged` 对其他状态不变。 |
| `src/main/java/com/wjx871/population/migration/MigrationMapper.java` | 新增 `long countActiveInBatch(batchNo, excludeApplicationId)`（排除 CANCELLED，更新草稿时排除自身）。 | 第 14 行。 |
| `src/main/resources/mapper/MigrationMapper.xml` | 新增 `countActiveInBatch`：`WHERE transfer_batch_no=#{batchNo} AND business_status<>'CANCELLED'`，可选 `application_id<>#{excludeApplicationId}`。 | 第 47 行；仅用于友好提示，不作为唯一保护。 |
| `src/main/java/com/wjx871/population/migration/MigrationService.java` | `createIn`/`updateIn` 增加 `requireBatchAvailable` 前置查询（创建时排除 null，更新时排除自身）；插入/更新捕获 `DataIntegrityViolationException` 并映射为明确 409；保持原 `transfer_batch_no` 写入，不做置空。 | createIn 10-21 行；updateIn 23-39 行；`requireBatchAvailable`/`batchConflict` 116-117 行；错误文案“迁移批次已被占用，请使用未使用的合法批次”。 |
| `src/main/java/com/wjx871/population/ai/precheck/AiResolutionService.java` | `validateTransferBatchForConfirmation` 的 `migration_in` 查询与 `countAnyInBatch` 均增加 `business_status<>'CANCELLED'`，使取消批次在 AI 预检/确认入口可复用。 | 309-310 行、358-359 行；对应 `AiConfirmIntegrationTest` 新场景通过。 |

### 5.2 数据库、新装基线与测试 schema

| 文件 | 改动 | 关键位置/说明 |
|---|---|---|
| `doc/database/migrations/V4_019_migration_batch_reuse.sql`（新增） | 完整升级脚本：Q1-Q4 前置统计、`issue19_preflight()` 异常阻断、`migration_in_issue19_backup` 备份、生成列、条件唯一索引、受控回填、删除旧索引、升级后验证、失败/回滚说明。 | 305 行；核心结构见 §6。未修改 V4_003/V4_015-V4_018。 |
| `doc/database/population_miniterm.sql` | 新装基线移除旧 `uk_migration_in_batch`，增加 `active_transfer_batch_no`（`STORED` 生成列）与 `uk_migration_in_active_batch(active_transfer_batch_no)`。 | 第 535-536 行；与 V4_019 后的升级结构一致。 |
| `doc/database/check_backend_v1.sql` | 增加 `multiple_active_migration_batch`、`cancelled_application_detail_not_cancelled`、`unexpected_cancelled_migration_detail`、`legacy_migration_batch_unique_index` 检查，以及 `active_transfer_batch_no` 列/`uk_migration_in_active_batch` 索引存在性检查。 | 7-10 行、34 行、42 行；所有 abnormal_count 应为 0。 |
| `src/test/resources/schema.sql` | H2 `migration_in` 移除 `transfer_batch_no UNIQUE`，增加等价 CASE 计算列与 `uk_migration_in_active_batch` 唯一索引。 | 210-214 行；H2 不接受 `STORED`，语义等价，MySQL 8.4 使用 `STORED`。 |
| `src/test/java/com/wjx871/population/migration/Issue19MigrationBatchReuseIntegrationTest.java`（新增） | 13 个 H2 专项测试，覆盖 A-L 的 H2 部分；真实 Spring MVC/JWT/Service/MyBatis，不 mock 核心链路；I 场景注入真实 `ApplicationDraftCancellationListener` 故障；`dumpH2MigrationInMetadata()` 已用 `isH2Database()` 保护，MySQL gate 子类不会执行 H2-only 诊断。 | 当前 853 行；测试清单见 §7.2。 |
| `src/test/java/com/wjx871/population/migration/Issue19MySql84IT.java`（新增） | MySQL 8.4 gate 入口（`*IT` 默认不进 `mvn test`），继承 #19 主测试；使用 `MYSQL84_GATE_DB_URL/USERNAME/PASSWORD` 与 admin 变量；`guard()` 返回已匹配 Matcher。 | `verifyMysqlIdentity` Matcher 缺陷已由 t10 修复；t11 复跑确认 identity 正常（13 次输出，无 `No match found`），但继承的 `dumpH2MigrationInMetadata()` 仍有 H2-only `SELECT H2VERSION()`/`INFORMATION_SCHEMA.INDEXES`，导致 exact `Issue19MySql84IT` 13 tests 中 1 error；t12 用 `isH2Database()` 保护修复；t13 全新实例正式复跑 **13/13**（详见 §10.1）。 |
| `src/test/java/com/wjx871/population/ai/precheck/AiConfirmIntegrationTest.java` | 新增 `cancelledTransferBatchCanBeReusedThroughAiPrecheckAndConfirmation`（474 行起）：取消后的批次在 AI 预检/确认中可复用。 | 15/15 通过；详细输出 `ISSUE19_AI_REUSE_FIRST/SECOND warnings=[] confirmable=true`。 |
| `scripts/windows/tests/issue19-mysql84-gate.ps1`（新增） | 隔离 MySQL 8.4 gate：临时实例、V4_018 基线、旧版 RED、V4_019 两次、历史回填、10+10 并发、Maven 回归、实例关闭。 | t6 验证脚本，不属于生产实现；证据 `issue19-mysql84*.txt`。 |

### 5.3 错误映射与事务行为

- 同一批次已有活动占用（含并发竞争失败）：HTTP **409**，文案“迁移批次已被占用，请使用未使用的合法批次”；不得 500。
- `createIn`/`updateIn` 捕获 `DataIntegrityViolationException`：`batchConflict` 对 `uk_migration_in_active_batch`/`transfer_batch_no`/`CONSTRAINT_INDEX_` 映射批次冲突，其他数据冲突映射“迁移申请数据冲突，请刷新后重试”，仍为 409。
- 取消路径：`ApplicationService.cancelDraft` 在一个 `@Transactional` 内完成 `business_application` DRAFT→CANCELLED、`sys_approval_log` 写入、迁移明细 CANCELLED 同步；任何异常（含 listener 抛异常）全部回滚，申请仍为 DRAFT、明细原状态、批次仍被占用。
- `createIn`/`updateIn` 在一个事务内完成业务申请与迁入明细；唯一键冲突时两表均回滚，无孤儿 `business_application`；`failedDuplicateDraftCreationLeavesNoOrphanApplicationOrDetail` 验证。

---

## 6. 历史数据升级策略（V4_019 实际落地）

> 适用对象：`business_application.status='CANCELLED'` 且 `migration_in.business_status='DRAFT'` 的存量迁入记录（精确目标集）。V4_019 只改 `migration_in.business_status` 为 `CANCELLED`，**不改 `transfer_batch_no`、不删行、不改主申请状态**。

### 6.1 识别与前置统计（Q1-Q4 + 阻断过程）

V4_019 在 DDL 前执行：

- **Q1**：精确目标明细清单（application CANCELLED + detail DRAFT），列出 application/in/person/to_region/to_household/version；
- **Q2**：总体统计：`affected_rows`、`with_batch`、`without_batch`、`distinct_batches`、`max_in_id`、`in_id_checksum`；
- **Q3**：邻近异常（CANCELLED 主申请但明细非 DRAFT/CANCELLED，或非 CANCELLED 主申请但明细 CANCELLED），必须为 0；
- **Q4**：重复活动批次（非 CANCELLED 同批次 >1），必须为 0；
- `issue19_preflight()` 对 Q3/Q4 及 `unexpected_cancelled_detail` 检查，发现异常 `SIGNAL SQLSTATE '45000'` 阻断（`ISSUE19_PRECHECK_*`），不得自动修复非目标行；
- 同时查询 `information_schema.statistics`/`columns` 记录旧 `uk_migration_in_batch` 是否存在、`active_transfer_batch_no` 是否已存在；状态不符先阻断核对。

### 6.2 备份（不可跳过）

- `migration_in_issue19_backup`：`in_id` 唯一，保存 `original_business_status`、`original_transfer_batch_no`、`original_version`、`application_status`、`application_version`、`backup_run`、`backup_at`；
- `INSERT IGNORE` 使重跑保留第一次快照（原始 DRAFT/原批次/原版本），不覆盖；
- 另需 `mysqldump --single-transaction` 导出 `migration_in`、`business_application`（或数据库快照）并记录校验和/行数；
- 实际 MySQL 8.4 证据：首跑 `expected_rows=2 updated_rows=2 backup_rows=2`，第二跑 `expected_rows=0 updated_rows=0 backup_rows=2`；升级前后 `migration_in|business_application` 行数均为 `3|4`，无 DELETE。

### 6.3 DDL 执行步骤（V4_019）

1. 前置统计与 `issue19_preflight()`；
2. 创建备份表并写入目标行；
3. `ALTER TABLE migration_in ADD COLUMN active_transfer_batch_no VARCHAR(40) GENERATED ALWAYS AS (CASE WHEN business_status='CANCELLED' THEN NULL ELSE transfer_batch_no END) STORED`（`information_schema.columns` 守卫，可重复）；
4. `ADD UNIQUE INDEX uk_migration_in_active_batch(active_transfer_batch_no)`（旧 `uk_migration_in_batch` 仍在，期间始终有唯一保护）；
5. 精确目标集 `UPDATE ... SET business_status='CANCELLED', version=version+1, updated_at=CURRENT_TIMESTAMP`，不改 `transfer_batch_no`；用 `ROW_COUNT()` 与 `@issue19_backfill_expected` 断言一致（不一致 `ISSUE19_BACKFILL_ROW_COUNT_MISMATCH` 阻断）；
6. 仅在上述成功后 `DROP INDEX uk_migration_in_batch`（`information_schema.statistics` 守卫）；
7. 执行升级后验证（目标异常 0、活动批次重复 0、取消行原值+`active_transfer_batch_no=NULL`、仅新索引存在、行数不变）。

### 6.4 失败处理与回滚原则

| 失败点 | 处理 |
|---|---|
| Q3 邻近异常 / Q4 重复批次 / 非法批次 | **阻断升级**，人工复核；不得自动改动非目标行。 |
| 备份表写入失败 / 校验不一致 | 阻断，不执行 DDL。 |
| 新增生成列或新唯一索引失败 | 旧 `uk_migration_in_batch` 仍在，数据安全但功能未生效；修复后重跑（脚本有守卫、可重复）。 |
| 回填 UPDATE 失败 / 行数不一致 | 单条 UPDATE 回滚；业务状态/批次不动；修复后重跑；旧唯一键仍在。 |
| 删除旧唯一键失败 | 旧键仍在，功能未生效但不破坏数据；修复后重试 drop。 |
| 升级后验证失败 | 停止应用写入；检查 `migration_in_issue19_backup`；必要时重建旧 `uk_migration_in_batch`（回填只改状态、不改批次号）；不得删除/覆盖历史数据。 |
| 回滚到旧版本 | 先从备份表恢复 `business_status` 原值，再恢复旧索引；若已有新申请复用原批次，严禁直接回滚为旧唯一键，需人工对账/业务决策；回滚脚本需单独评审。 |

### 6.5 明确禁止

- 不得 `DELETE` 任何 `migration_in`/`business_application` 行；
- 不得直接 `UPDATE migration_in SET transfer_batch_no=NULL` 作为“清理”手段；
- 不得把 `business_application.status` 从未取消改成 CANCELLED 来“凑数”；
- 不得静默改动非目标行，不得把 REJECTED/WITHDRAWN/RETURNED 记录当作已释放；
- 不得跳过备份/统计直接执行回填。

### 6.6 新装基线与升级路径一致性

- `doc/database/population_miniterm.sql:535-536` 已直接创建 `active_transfer_batch_no`（`STORED` 生成列）和 `uk_migration_in_active_batch`，并移除了旧 `uk_migration_in_batch`；
- `V4_019` 后升级库与 fresh install 的 `migration_in` 结构一致（`SHOW CREATE TABLE` 对齐，见 `issue19-mysql84-schema.txt`）；
- `check_backend_v1.sql` 在 fresh install 和升级后均应报告全部 abnormal_count=0；
- 注意：`population_miniterm.sql` 是 fresh install 脚本，不自动执行 V4_019；已有库必须按 `doc/database/V4_019-migration-batch-reuse-upgrade.md` 执行 V4_019。

---

## 7. 验证结果

### 7.1 旧版 RED 实际失败证据（t3）

- 命令：`.\mvnw.cmd -Dtest=Issue19MigrationBatchReuseIntegrationTest test`
- 结果：exit code **1**（BUILD FAILURE，RED 预期）；**Tests run: 4, Failures: 2, Errors: 0, Skipped: 0**；Surefire：`target/surefire-reports/TEST-com.wjx871.population.migration.Issue19MigrationBatchReuseIntegrationTest.xml`（旧轮次 SHA256 见 `issue19-red-h2.txt`）。
- 失败 1：`cancelDraftReleasesBatchAndNewDraftCanBeCreated` 断言 B 创建应 201，旧实现实际 HTTP **409**，响应体 `{"code":409,"message":"数据已存在，请勿重复提交","data":null,...}`（`issue19-red-h2.txt` §1/§5）。
- 失败 2：`cancelledMigrationRecordAndLogsRemainTraceable` 期望 `migration_in.business_status=CANCELLED`，旧实现实际 **DRAFT**。
- 旧实现快照：取消 A 后 `business_application.status=CANCELLED`，`migration_in={business_status=DRAFT, transfer_batch_no=TR-20261108-0FB03D, version=0}`；`sys_approval_log` 有 `CANCEL(DRAFT→CANCELLED)`；`operation_log` 无该取消记录。
- H2 旧结构：`migration_in.transfer_batch_no` 上唯一索引 `CONSTRAINT_INDEX_B6`（`transfer_batch_no` 单列），无任何状态条件；直接重复 INSERT 触发 `JdbcSQLIntegrityConstraintViolationException [23505-232]`。
- 真实 MySQL 8.4 旧 V4_018 schema 也复现：`ERROR 1062 (23000) ... Duplicate entry 'TR-RED-46c30189ad' for key 'migration_in.uk_migration_in_batch'`，失败 INSERT 回滚后 `orphan_application=0`、该批次 `migration_in_rows=1`（`issue19-mysql84.txt` 77-90 行）。
- 旧版完成批次观察：迁入完成后 `migration_in.business_status=COMPLETED`，同批次再次创建 HTTP 409、该批次仅 1 条 COMPLETED；DRAFT/UNDER_REVIEW/APPROVED/REJECTED/WITHDRAWN/RETURNED 第二创建全部 409（继续占用）。

### 7.2 H2 GREEN、并发与事务结果（t5）

- 专项：`.\mvnw.cmd -Dtest=Issue19MigrationBatchReuseIntegrationTest test`，exit 0（BUILD SUCCESS），**13 tests / 0 failures / 0 errors / 0 skipped**，Maven 总耗时 44.848 s（`issue19-h2-green.txt` §2.1）。
- 13 个测试方法：`cancelDraftReleasesBatchAndNewDraftCanBeCreated`、`failedDuplicateDraftCreationLeavesNoOrphanApplicationOrDetail`、`cancelledMigrationRecordAndLogsRemainTraceable`、`completedMigrationConsumesBatchPermanently`、`existingStatusOccupancyObservationMatrix`、`updateDraftReleasesOldBatchAndClaimsNewBatch`、`batchMismatchIsRejectedOnExecution`、`cancelRecreateCannotBypassMaterialsApprovalOrExecutePermission`、`concurrentClaimsAllowOnlyOneActiveDraft`、`cancelAndCreateInterleavingHasNoDoubleActiveClaim`、`cancellationListenerFailureRollsBackApplicationAndDetail`、`repeatedCancelAndNonDraftCancelAreRejected`、`genericDraftCancellationDoesNotBroadcastToSpecializedListeners`。
- 回归：`Phase03MigrationIntegrationTest` **36/36** + `Issue33CrossDistrictCharacterizationIntegrationTest` **6/6** = 42 tests，0/0/0，exit 0（01:01 min）。
- AI：`AiConfirmIntegrationTest` **15/15** + `AiPrecheckIntegrationTest` **36/36** = 51 tests，0/0/0，exit 0（59.086 s）；`cancelledTransferBatchCanBeReusedThroughAiPrecheckAndConfirmation` 输出 `ISSUE19_AI_REUSE_FIRST warnings=[] confirmable=true`、`ISSUE19_AI_REUSE_SECOND warnings=[] confirmable=true`。
- G 并发：`concurrentClaimsAllowOnlyOneActiveDraft` 10/10 轮全部 `{201,409}`，每轮活动占用行数=1，无 500/未处理死锁。
- H 交错：`cancelAndCreateInterleavingHasNoDoubleActiveClaim` 10 次执行 × 内部 2 轮 = **20 轮**；每轮 `cancelHttp=200`、`createHttp∈{201,409}`，最终活动占用 = `createStatus==201 ? 1 : 0`，无双重有效占用。
- I 事务故障注入：`cancellationListenerFailureRollsBackApplicationAndDetail` 注入抛异常的 `ApplicationDraftCancellationListener`，DELETE 返回 500（真实异常栈）；回滚后 `business_application.status=DRAFT`、`migration_in.business_status=DRAFT`、原批次保留、CANCEL 日志数=0。
- H2 schema：`INFORMATION_SCHEMA` 实测 `uk_migration_in_active_batch` 为 `active_transfer_batch_no` 唯一索引；生成列 `CASE WHEN business_status='CANCELLED' THEN NULL ELSE transfer_batch_no END`；旧 `uk_migration_in_batch` 不存在；独立 H2 Shell 验证同一批次 2 条 CANCELLED（active=NULL）+1 条 DRAFT（active=批次）成功，第 2 条非 CANCELLED 触发 23505。

### 7.3 真实 MySQL 8.4 结果（t6）

- 实例：MySQL **8.4.8**，临时端口 **13370**（非 3306），`bind=127.0.0.1`，隔离 schema `population_miniterm_mysql_gate_issue19_46c30189ad`，应用账户 `i19_46c30189ad@127.0.0.1`（非 root，仅 schema DML）；`@@transaction_isolation=REPEATABLE-READ`；身份查询原文见 `issue19-mysql84-schema.txt:13-20`。
- 迁移链：基线 `population_miniterm.sql` + V4_011/V4_012/V4_013/V4_015/V4_016/V4_017/V4_018 全部 exit 0；V4_018 旧 schema `uk_migration_in_batch` count=1、`active_transfer_batch_no` count=0；旧版 RED 真实 1062；V4_019 第一、二次均 exit 0（可重复）。
- V4_019 后：旧 `uk_migration_in_batch` count=0；`uk_migration_in_active_batch` count=1；`active_transfer_batch_no` 为 `STORED GENERATED`，表达式 `CASE WHEN business_status='CANCELLED' THEN NULL ELSE transfer_batch_no END`（`SHOW CREATE TABLE`/`information_schema.columns`，`issue19-mysql84-schema.txt:37-127`）。
- 历史数据：目标行 `TR-HIST-...-0007` 由 `DRAFT` 对齐为 `CANCELLED`，原批次值保留、`active_transfer_batch_no=NULL`、version 3→4；已 CANCELLED 的 legacy 行未被修改（version 2）；备份表保留原 DRAFT/原批次/version 3；首跑 `expected=2/updated=2/backup=2`，第二跑 `0/0/2`；升级前后行数 `3|4 → 3|4`（无 DELETE）。
- 新行为：取消批次可复用（`NEW_A_REUSE_OK`）；活动 DRAFT 独占（`ERROR 1062 ... uk_migration_in_active_batch`，active=1）；COMPLETED 批次永久拒绝（1062，rows=1）；失败重复事务回滚 `orphan_applications=0, migration_rows=0`。
- 并发：10 轮 claim（每轮 A=B 中恰好一个 SUCCESS、另一个 DUPLICATE，final_active=1，记录连接/事务 id 与 `LOCK WAIT`）+ 10 轮 cancel/create 交错（final_active=0 或 1，无双重有效占用），`issue19-mysql84.txt:165-225`。
- Maven/补充：gate 内 Maven 回归命令 `-Dtest=Issue19MigrationBatchReuseIntegrationTest,AiConfirmIntegrationTest,AiPrecheckIntegrationTest,Phase03MigrationIntegrationTest test` exit 0，**100 tests / 0 failures / 0 errors**；另用临时 verifier 子类在真实 MySQL 跑 **14 tests / 0 failures / 0 errors**（含错误人员、错误目标区划 409 `TARGET_REGION_MISMATCH`、未知批次 409、取消/回滚/并发）；临时 mysqld 已关闭，13370 不再监听。
- **注意**：上述 gate 内 100 tests 是 **H2 路径回归**（脚本注释明示 `Maven regression (H2 paths, same worktree)`）；真实 MySQL 的 HTTP 补充为 14/14。不要把两者混写为 MySQL 全量测试。
- `Issue19MySql84IT` 当前状态：t10 已修复 `verifyMysqlIdentity` Matcher 缺陷（`guard()` 返回已匹配 Matcher）；t11 在全新 MySQL 8.4.8（13372）实跑 exact `.\mvnw.cmd -Dtest=Issue19MySql84IT test` 为 **13 tests / 0 failures / 1 error / 0 skipped**，identity 13 次输出无 `No match found`；唯一失败是继承的 `dumpH2MigrationInMetadata()`（t11 日志定位第 730 行）的 H2-only `SELECT H2VERSION()`（及后续 `INFORMATION_SCHEMA.INDEXES`），排除该诊断方法后其余 **12/12** 通过。t12 已用 `isH2Database()` 保护修复；t13 在全新 MySQL 8.4.8（13373）正式复跑 exact 命令 **13 tests / 0 failures / 0 errors / 0 skipped，exit 0**（零 `H2VERSION` 调用），该测试入口已闭环。

### 7.4 全量 Maven 回归实际数字

- 命令：`.\mvnw.cmd test`，工作目录为隔离 worktree。
- exit code **0**；Maven `BUILD SUCCESS`，Total time **03:12 min**，Finished at `2026-11-09T22:42:39+08:00`（`issue19-h2-green.txt` §2.5）。
- 本轮 Surefire XML（77 个文件）：**tests=913, failures=0, errors=0, skipped=3**；与 Maven Results 一致。
- 未引用任何历史轮次数字；本报告使用本轮证据文件的全部测试数字。
- H2/MySQL 证据文件、命令、时间戳与 `target/surefire-reports` 路径均已在上文列明。

### 7.5 验收矩阵 A-L 落点与结果

| 编号 | 场景 | 实际结果（H2 / MySQL） | 主要证据/测试 |
|---|---|---|---|
| **A** | 合法已完成同市跨区迁出 → 创建迁入 DRAFT A（201）→ `DELETE /api/applications/{id}` 取消 A → 同一批次创建 DRAFT B | H2 PASS：A `business_application=CANCELLED`、`migration_in=CANCELLED`、原批次保留；B **201**；同批次 2 行共存。MySQL PASS：取消批次可复用（NEW_A_REUSE_OK）。 | `cancelDraftReleasesBatchAndNewDraftCanBeCreated`；`issue19-h2-green.txt` §4[A]；`issue19-mysql84.txt` 154-155 |
| **B** | DRAFT/UNDER_REVIEW/APPROVED 活动占用同批次，第二创建；编辑换批次 | H2 PASS：六种状态（含 REJECTED/WITHDRAWN/RETURNED）第二创建全部 409；编辑旧批次释放、新批次被占用；无孤儿。MySQL PASS：活动 DRAFT 独占 1062、active=1。 | `existingStatusOccupancyObservationMatrix`、`updateDraftReleasesOldBatchAndClaimsNewBatch`、`failedDuplicateDraftCreationLeavesNoOrphanApplicationOrDetail`；`issue19-mysql84.txt` 156-158 |
| **C** | 迁入 COMPLETED 后再次创建/执行同批次 | H2 PASS：第二次创建 409，批次仅 1 条 COMPLETED，PERSON_IN 无申请行；MySQL PASS：COMPLETED 永久拒绝（1062，rows=1）。 | `completedMigrationConsumesBatchPermanently`；`issue19-mysql84.txt` 159-161 |
| **D** | 取消记录/日志可追溯；存量 CANCELLED+DRAFT 升级 | H2 PASS：取消后详情返回 `businessStatus=CANCELLED`、原 `transferBatchNo`；CANCEL 审批日志=1；`operation_log` 无该取消记录（基线既有行为，见 §10.2）。MySQL PASS：V4_019 目标行回填、备份表保留原 DRAFT/批次/version、legacy CANCELLED 不变、行数不变。 | `cancelledMigrationRecordAndLogsRemainTraceable`；`issue19-mysql84.txt` 91-151 |
| **E** | 错误人员/目标区划/批次不存在/已完成批次 | H2 PASS：错误人员执行 409、目标区划 409 `TARGET_REGION_MISMATCH`、Phase03/Issue33 负向用例通过；MySQL 补充 PASS：错误人员、错误目标区划、未知批次均 409，无 partial write。 | `batchMismatchIsRejectedOnExecution`、`Phase03MigrationIntegrationTest.targetRegionMismatchIsRejectedOnCreateUpdateAndExecute`、`Issue33...`；`issue19-mysql84.txt` 279-282 |
| **F** | 取消 A 后 B 未提交/缺材料/无执行权限/未审批/错误版本执行 | H2 PASS：未提交 409、缺材料 409（列出 IDENTITY_PROOF）、viewer 执行 403、最终 B 仍 DRAFT、PERSON_OUT 无 residence 写入；MySQL 补充覆盖取消/回滚路径。 | `cancelRecreateCannotBypassMaterialsApprovalOrExecutePermission`；`issue19-h2-green.txt` §4[F] |
| **G** | 双请求并发创建同批次（≥10 轮） | H2 PASS：10/10 轮 {201,409}、活动占用=1；MySQL PASS：10/10 轮 A=SUCCESS/B=DUPLICATE、final_active=1、记录 LOCK WAIT。 | `concurrentClaimsAllowOnlyOneActiveDraft`；`issue19-h2-green.txt` §2.4；`issue19-mysql84.txt` 165-195 |
| **H** | 取消 A 与创建 B 交错（≥10 轮） | H2 PASS：10 次 × 2 = 20 轮无双重有效占用；MySQL PASS：10 轮交错 final_active 0/1、无双重有效占用。 | `cancelAndCreateInterleavingHasNoDoubleActiveClaim`；`issue19-mysql84.txt` 196-225 |
| **I** | 取消事务中途失败；创建重复事务失败 | H2 PASS：注入抛异常 listener 后 DELETE 500，回滚后 application/DETAIL 仍 DRAFT、批次保留、CANCEL 日志=0；重复创建 409 且两表无半行。MySQL PASS：失败重复事务 `orphan_applications=0/migration_rows=0`。 | `cancellationListenerFailureRollsBackApplicationAndDetail`、`failedDuplicateDraftCreationLeavesNoOrphanApplicationOrDetail`；`issue19-mysql84.txt` 162-163 |
| **J** | 重复取消/非 DRAFT 取消/版本冲突 | H2 PASS：首次 DELETE 200，第二次 DELETE 409；UNDER_REVIEW DELETE 409 且状态不变；Phase03 版本冲突回归 36/36；MySQL 补充 PASS。 | `repeatedCancelAndNonDraftCancelAreRejected`；`Phase03MigrationIntegrationTest` |
| **K** | 人工入口 + AI 预检/确认入口（含取消批次复用） | H2 PASS：人工入口 13/13；AI `AiConfirm` 15/15、`AiPrecheck` 36/36；`ISSUE19_AI_REUSE_FIRST/SECOND warnings=[] confirmable=true`，取消批次经 AI 再次确认建草稿成功（`AiResolutionService` 排除 CANCELLED）。MySQL 补充覆盖取消/复用/回滚。 | `cancelledTransferBatchCanBeReusedThroughAiPrecheckAndConfirmation`；`issue19-h2-green.txt` §2.3/§4[K] |
| **L** | 既有迁移/归档回归 + 全量 Maven | H2 `Phase03` 36/36、`Issue33` 6/6；全量 `mvnw test` 913/0/0/3、BUILD SUCCESS（03:12）；MySQL gate 的 100 项 H2 回归 0 失败/错误。 | `issue19-h2-green.txt` §2.2/§2.5；`issue19-mysql84.txt` 228-266 |

> A-L 的 MySQL 部分按实际证据分别标注：SQL gate（真实 V4_018→V4_019、并发、历史回填）与临时 HTTP 子类 14/14；未把 H2 结果冒充 MySQL。D 的 `operation_log` 观察见 §10.2。

---

## 8. 状态边界与 Out of Scope

### 8.1 本轮释放边界

- **只有 `application.status='CANCELLED'` 且由 DRAFT 经 `cancelDraft` 产生的迁移明细 `CANCELLED` 释放批次。**
- `DRAFT`/`UNDER_REVIEW`/`APPROVED`/`COMPLETED` 必须占用；`REJECTED`/`WITHDRAWN`/`RETURNED`（明细 `APPROVED`）**维持现状继续占用**，不扩大释放。
- 不修改 `migration_out.transfer_batch_no` 的生成时点/规则；不修改 `MigrationService.validateBatch` 的人员/类型/目标区划/已完成消费校验。
- 不通过取消绕过材料核验、审批或执行权限：取消只释放批次，不删除原申请材料/审批记录，新申请必须重新提交材料、重新审批、重新校验执行权限。

### 8.2 #33 与其他 out of scope

- 本任务**不修改** #33 的同市/城市归属规则，不解析 `admin_region` 父链，不推断编码前缀/长度，不改变 `IN_CITY_CROSS_DISTRICT` 的“同市跨区”定义，不修改 `MigrationMapper.xml` 中任何区划匹配逻辑。`Issue33CrossDistrictCharacterizationIntegrationTest` 仅作为回归（矩阵 L）执行，不修改其断言/种子；#33 保持其审计状态不变。
- 不修改其他审计问题状态/代码/测试；不触碰 #43/#81/#83 等历史 worktree。
- 不修改前端；不新增前端字段/接口文案。

---

## 9. 验收矩阵 A-L 落点（实现与验证的对应关系）

- 矩阵 A-L 的逐项实际结果见 §7.5；H2 与 MySQL 的分工见 §7.2/§7.3。
- 每项均可由 `Issue19MigrationBatchReuseIntegrationTest` 的 13 个测试方法、`Phase03MigrationIntegrationTest` 36 个、`Issue33CrossDistrictCharacterizationIntegrationTest` 6 个、`AiConfirmIntegrationTest` 15 个、`AiPrecheckIntegrationTest` 36 个逐一复核；具体方法名见 §7.2 及 `issue19-h2-green.txt` §4。
- MySQL 专属证据主要覆盖：A/B/C/D（历史回填与批次复用/独占/完成消费）、G/H（10+10 并发）、E（MySQL 补充 HTTP 14/14）；其余矩阵项的 H2 与 MySQL gate 对应关系已在 §7.5 标注，未混用环境。

---

## 10. 剩余风险与未完成项

### 10.1 测试入口历史记录（t11 发现、t12 修复、t13 已通过）

`Issue19MySql84IT` 的 `verifyMysqlIdentity` Matcher 缺陷已由 t10 修复（`guard()` 返回已匹配 Matcher；t11 identity 13 次输出，0 次 `No match found`）。t11 使用全新 MySQL 8.4.8（端口 13372、非 root、`REPEATABLE-READ`、独立 schema）执行 exact `.\mvnw.cmd -Dtest=Issue19MySql84IT test`，曾得到 **13 tests / 0 failures / 1 error / 0 skipped，exit 1**：

- 历史失败点：`Issue19MigrationBatchReuseIntegrationTest.dumpH2MigrationInMetadata()`（t11 日志定位第 730 行）无条件执行 H2-only `SELECT H2VERSION()`（后续还使用 `INFORMATION_SCHEMA.INDEXES/INDEX_COLUMNS`，MySQL 8.4 不存在 `INFORMATION_SCHEMA.INDEXES`）；
- 当时同实例排除该 H2 诊断方法后其余 **12/12** 通过，说明不是 #19 业务场景失败；
- t12 已修复：`dumpH2MigrationInMetadata()` 开头增加 `if (!isH2Database()) return;`，通过 `DatabaseMetaData.getDatabaseProductName()` 区分 H2；t12 临时实例复跑 exact 命令 **13 tests / 0 failures / 0 errors / 0 skipped**（`target/t12-issue19-mysql84-rerun.log`）；
- **t13 最终结果**：全新 MySQL 8.4.8 实例（端口 13373、独立 datadir/schema、非 root、`REPEATABLE-READ`）`.\mvnw.cmd -Dtest=Issue19MySql84IT test` **exit 0；13 tests / 0 failures / 0 errors / 0 skipped**；identity 13 次输出、无 `No match found`、零 `H2VERSION`/`ISSUE19_H2_VERSION` 调用；同时 `mvnw -DskipTests test-compile` exit 0、`Issue19MigrationBatchReuseIntegrationTest` H2 13/13；临时 mysqld 已关闭；
- 证据：`doc/development/evidence/issue19-mysql84-gate-rerun.txt`（t11 失败 + t13 ROUND 2 通过两段）与 `target/t12-issue19-mysql84-rerun.log`。

### 10.2 非阻断观察

- `operation_log` 在取消草稿时没有记录（`ISSUE19_RED_CANCEL_OPERATION_LOGS=[]`），这是基线既有行为，不是 #19 引入；当前验收只要求 `sys_approval_log` 的 CANCEL 记录与 `migration_in` 状态可追溯。若后续要求“取消动作必须写 operation_log”，需单独任务。
- `Issue19MigrationBatchReuseIntegrationTest` 的 `printDuplicateConstraintDiagnostic` 是 RED 期辅助方法，GREEN 下会打印 `ISSUE19_DIRECT_DUPLICATE unexpected-success`；这是旧标签，不是断言失败（`issue19-h2-green.txt` §5.5）。
- `Issue19MySql84IT` 的 `*IT` 默认不进入 `mvn test`，需要显式环境变量与独立 MySQL 8.4 实例；未配置环境时跳过/不可执行属于设计而非失败。

### 10.3 未执行/不可冒充

- 生产库真实存量规模、实际升级窗口、生产 DBA 执行：未执行；`V4_019` 的统计/备份/回填只在隔离 MySQL 8.4 gate schema 验证，不能代表生产数量。
- 生产部署、前端页面展示、浏览器端取消按钮实际交互：未执行；后端保证 API 行为与 `transfer_batch_no` 可查。
- MySQL 8.0/5.7、MariaDB、其他存储引擎：未验证；本方案只在 MySQL 8.4 验证，不得声称兼容。
- 跨进程网络压力测试：H2 为进程内 MockMvc，不是外部端口压力测试；MySQL 补充为真实 JDBC/HTTP 子类，但非生产负载。
- #33 城市归属规则及任何其他审计项：未触碰。

### 10.4 数据/部署注意

- `V4_019_migration_batch_reuse.sql` 使用 `USE population_miniterm;`；隔离 gate 脚本会替换 schema 名，生产执行前需确认目标数据库名与权限。
- 新装 `population_miniterm.sql` 已与 V4_019 后结构一致；但 fresh install 不会自动执行 V4_019 的存量回填（新装无存量数据，无需回填）；已有库必须执行 V4_019。
- 备份表 `migration_in_issue19_backup` 和 `mysqldump` 备份应保留到独立复核/数据保留制度要求的期限，不得提前删除。

---

## 11. 最终结论

- **真实根因已确认并修复**：`cancelDraft` 只改 `business_application` 且不通知迁移明细；`MigrationBusinessStatus` 无 `CANCELLED`；`uk_migration_in_batch` 对 `transfer_batch_no` 全局唯一。三者叠加造成永久占号。
- **最终设计**：`active_transfer_batch_no`（`CANCELLED→NULL`，其余保留原值）`STORED` 生成列 + 条件唯一索引 `uk_migration_in_active_batch`，替换 `uk_migration_in_batch`；原 `transfer_batch_no` 原行原列保留；取消由 opt-in `ApplicationDraftCancellationListener` 同事务同步 `migration_in`/`migration_out`；`AiResolutionService` 的 `countAnyInBatch`/确认查询排除 `CANCELLED`，AI 辅助入口已可复用取消批次（旧限制已解除）。
- **验证完成度**：H2 专项 13/13、Phase03 36/36、Issue33 6/6、AI 51/51；H2 G 10 轮/H 20 轮；全量 Maven 913/0/0/3、BUILD SUCCESS 03:12；真实 MySQL 8.4.8 临时隔离实例 V4_019 两次应用、历史回填、并发 10+10、补充 HTTP 14/14、gate Maven 100/0/0；临时 mysqld 已关闭。
- **状态**：`CODE_CANDIDATE`，等待独立复核；不是 `FIXED_VERIFIED`，未合并/未部署。独立复核应重点看 §10.1 的 t11→t13 测试入口记录（t13 exact MySQL 8.4 已 13/13）、§10.2 的 `operation_log` 观察、以及 V4_019 历史升级脚本的实际执行前提。

---

## 12. 证据索引

| 证据 | 内容 | 关键数字/说明 |
|---|---|---|
| `doc/development/evidence/issue19-red-h2.txt` | 旧实现 RED（H2 Spring MVC/JWT/Service/Mapper） | 4 tests / 2 failures / 0 errors；HTTP 409；旧唯一索引 `CONSTRAINT_INDEX_B6` |
| `doc/development/evidence/issue19-h2-green.txt` | H2 GREEN、全量 Maven、G/H 并发、H2 schema | 13/13；Phase03 36 + Issue33 6；AI 15+36；913/0/0/3；H2 10+20 轮 |
| `doc/development/evidence/issue19-mysql84.txt` | 真实 MySQL 8.4 临时实例执行日志 | V4_019 两次；10+10 轮；100 tests（H2 gate regression）；补充 14/14 |
| `doc/development/evidence/issue19-mysql84-schema.txt` | MySQL 实例身份、SHOW INDEX/SHOW CREATE TABLE、备份表结构 | 8.4.8 / 13370 / REPEATABLE-READ / 非 root；旧索引消失、新索引/STORED 列 |
| `doc/development/evidence/issue19-mysql84-gate-rerun.txt` | t11 失败 + t13 ROUND 2 通过的 exact `Issue19MySql84IT` 真实 MySQL 门禁复跑 | t11：13 tests / 0 failures / 1 error（H2-only 诊断，补充 12/12）；t13：8.4.8 / 13373 / REPEATABLE-READ，13 / 0 / 0 / 0，exit 0，无 `No match found`/`H2VERSION` |
| `doc/database/migrations/V4_019_migration_batch_reuse.sql` | 生产升级脚本（305 行） | Q1-Q4、preflight、备份、生成列、条件索引、回填、验证 |
| `doc/database/V4_019-migration-batch-reuse-upgrade.md` | 人类可读升级说明 | 前置、统计/备份、DDL、失败/回滚、新装基线一致性 |
| `doc/api/household-migration-api.md` | 迁移 API 契约（含取消/批次复用/409） | DELETE 取消语义、CANCELLED 释放、COMPLETED 永久消费 |
| `doc/api/backend-api-index.md` | API 总索引 | 迁移模块入口说明 |

> 本报告为 t7 最终文档对账产物；不改变 t2 契约的 A-L 矩阵与状态边界。测试数字全部来自本轮证据文件与 target 日志/Surefire reports，未引用历史轮次数字。
