================================================================================
Issue #19 独立复核证据（review / t8 round 1）
================================================================================

复核人：reviewer（independent-reviewer）
复核对象：t4 [#19] 生产修复（取消释放批次 + 条件唯一约束 + 取消状态联动）
复核时间：2026-11-09 23:25 +08:00（Asia/Shanghai）
隔离 worktree：D:\学习用\大二下\小学期-issue19-migration-batch-reuse
分支：fix/20261108-issue19-migration-batch-reuse
HEAD：1f3f4427775f55b853a7b42d2cb759b4cdfa7411（基线 integration/20261101-backend-p0-security；本轮实现改动尚未提交，见 §6 非阻断观察）
复核范围：t4 的实现、数据库迁移 V4_019、H2 schema、测试/证据、专项文档、审计账本、API/数据库文档与 #33 边界。
复核方式：逐文件静态审查 + 独立重跑 H2/Phase03/Issue33/全量 Maven + 独立临时 MySQL 8.4.8 新装基线烟测 + 证据哈希/日志核对。
本次复核未修改生产代码、测试、迁移、schema 或其他文档；本文件是本次唯一新增的复核证据文件。

裁决：PASS
说明：没有发现阻断 #19 功能、并发正确性、迁移安全性、文档/账本一致性或范围边界的 findings。§6 列出的是非阻断观察/后续建议，不影响本轮验收。

================================================================================
1. 复核范围、变更清单与工作区边界
================================================================================

1.1 工作区 HEAD 与分支
    branch = fix/20261108-issue19-migration-batch-reuse
    HEAD   = 1f3f4427775f55b853a7b42d2cb759b4cdfa7411
    （HEAD 仍为 t1 建立的基线提交；t4 以来的改动均在工作区，未提交。见 §6。）

1.2 `git diff --name-only`（17 个已跟踪文件，全部属于 #19 范围）
    doc/api/backend-api-index.md
    doc/api/business-application-approval-api.md
    doc/api/household-migration-api.md
    doc/database/backend-v1-database-guide.md
    doc/database/check_backend_v1.sql
    doc/database/population_miniterm.sql
    doc/testing/2026-11-02-antigravity-audit-ledger.md
    src/main/java/com/wjx871/population/ai/precheck/AiResolutionService.java
    src/main/java/com/wjx871/population/application/ApplicationService.java
    src/main/java/com/wjx871/population/application/ApplicationStatusListener.java
    src/main/java/com/wjx871/population/migration/MigrationBusinessStatus.java
    src/main/java/com/wjx871/population/migration/MigrationMapper.java
    src/main/java/com/wjx871/population/migration/MigrationService.java
    src/main/java/com/wjx871/population/migration/MigrationStatusListener.java
    src/main/resources/mapper/MigrationMapper.xml
    src/test/java/com/wjx871/population/ai/precheck/AiConfirmIntegrationTest.java
    src/test/resources/schema.sql

1.3 未跟踪文件（全部为 #19 的新增实现/测试/迁移/脚本/文档）
    doc/database/V4_019-migration-batch-reuse-upgrade.md
    doc/database/migrations/V4_019_migration_batch_reuse.sql
    doc/development/evidence/issue19-h2-green.txt
    doc/development/evidence/issue19-mysql84-gate-rerun.txt
    doc/development/evidence/issue19-mysql84-schema.txt
    doc/development/evidence/issue19-mysql84.txt
    doc/development/evidence/issue19-red-h2.txt
    doc/development/issue19-migration-batch-reuse.md
    scripts/windows/tests/issue19-mysql84-gate.ps1
    src/main/java/com/wjx871/population/application/ApplicationDraftCancellationListener.java
    src/test/java/com/wjx871/population/migration/Issue19MigrationBatchReuseIntegrationTest.java
    src/test/java/com/wjx871/population/migration/Issue19MySql84IT.java

1.4 边界核对
    - `git diff --check`：exit 0（仅 MigrationStatusListener.java 的 LF/CRLF Git 提示，不是错误）。
    - `git diff --name-only` 中没有 `Issue33CrossDistrictCharacterizationIntegrationTest.java`，没有其他审计问题的实现/测试文件。
    - `doc/testing/2026-11-02-antigravity-audit-ledger.md` 的 diff 只有第 19 行一处；第 33 行与基线逐字一致（OPEN）。
    - 本次复核只新增本文件；未触碰主工作区和集成 worktree。

================================================================================
2. 生产实现逐文件复核
================================================================================

2.1 取消状态联动：安全、定向、opt-in

ApplicationDraftCancellationListener（新增，22 行）
    - 新增独立接口 `supports(BusinessType)` + `onDraftCancelled(BusinessApplication)`。
    - 明确与 `ApplicationStatusListener` 分离；只有显式实现并 supports 的监听器才收到回调。
    - 回调约定在同一事务内执行，抛异常必须回滚整个取消。

ApplicationService.cancelDraft（第 103-119 行）
    - 先执行 `business_application` DRAFT→CANCELLED 的乐观版本更新，0 行即冲突。
    - 再写 `sys_approval_log` 的 CANCEL(DRAFT→CANCELLED)。
    - 最后遍历 `List<ApplicationDraftCancellationListener>`，仅 `supports(value.getBusinessType())` 时调用；没有向 `ApplicationStatusListener` 全体广播。
    - 方法保留 `@Transactional`，任一 listener 异常向外传播，事务整体回滚。

ApplicationStatusListener（仅 Javadoc 修改）
    - 既有 `onStatusChanged` 语义未改；Javadoc 明确草稿取消不通过该接口广播。
    - `ApprovalService.notifyStatus` 的实际调用点仅 `UNDER_REVIEW`（submit）、`WITHDRAWN`（withdraw）、`APPROVED`/`REJECTED`（decide），没有 `CANCELLED` 广播。
    - `KeyPopulationWorkflow`、`CancellationStatusListener`、`FloatingResidenceStatusListener`、`ExportStatusListener` 的状态处理没有被这次取消路径触发，未产生回归。

MigrationBusinessStatus（第 2 行）
    - 由 `DRAFT, UNDER_REVIEW, APPROVED, COMPLETED, REJECTED, WITHDRAWN`
      增加 `CANCELLED`。取消态可落库、可序列化、可被 MyBatis EnumTypeHandler 读取。

MigrationStatusListener（第 4-18 行）
    - 同时实现 `ApplicationStatusListener` 与 `ApplicationDraftCancellationListener`。
    - `onDraftCancelled`：
        MIGRATION_IN → 读取 migration_in，已是 CANCELLED 时幂等返回；否则
        `mapper.updateInStatus(... from=v.getBusinessStatus(), to=CANCELLED, expectedVersion=v.getVersion(), operatorId=null)`；
        MIGRATION_OUT → 同理调用 `updateOutStatus`。
    - 明细不存在时抛 `BusinessException(409, "迁入/迁出详情不存在，取消未完成")`，取消事务回滚，避免“主表取消但明细未取消”的半状态。
    - `updateInStatus`（MigrationMapper.xml 第 16 行）的 SET 列表不含 `transfer_batch_no`，因此原批次号原行原列保留；`updateOutStatus`（第 17 行）使用 `COALESCE(#{batchNo},transfer_batch_no)`，传 null 时也保留原值。
    - 既有 `onStatusChanged` 对其他审批状态映射不变。

MigrationMapper / MigrationMapper.xml
    - 新增 `countActiveInBatch(batchNo, excludeApplicationId)`：
      `WHERE transfer_batch_no=#{batchNo} AND business_status<>'CANCELLED'`，
      可选 `AND application_id<>#{excludeApplicationId}`。
    - 该查询仅作为友好前置提示；数据库唯一索引才是并发正确性的最终裁判（见 §2.2）。

MigrationService（第 10-39、116-117 行）
    - `createIn`：在 `validateInType` 后增加 `requireBatchAvailable(r.transferBatchNo(), null)`；
      `mapper.insertIn` 捕获 `DataIntegrityViolationException` 并映射 `batchConflict`（明确 409“迁移批次已被占用，请使用未使用的合法批次”）。
    - `updateIn`：增加 `requireBatchAvailable(r.transferBatchNo(), id)`（排除自身），
      `updateInDraft` 同样捕获唯一键异常并映射 409。
    - 完整保留 `validateInType`、`validateBatch`（执行阶段人员/类型/目标区划/已完成消费校验）、人员/家庭户/材料/审批/执行权限路径。

AiResolutionService（第 309-310、358-359 行）
    - `validateTransferBatchForConfirmation` 的 migration_in `FOR UPDATE` 查询和 `countAnyInBatch`
      均增加 `business_status<>'CANCELLED'`。
    - 这样取消批次在 AI 预检和 AI 确认（AiConfirmService 最终调用 `migrations.createIn`）入口均可复用，
      且 AI 确认仍经过 `MigrationService.createIn` 的数据库唯一索引兜底。

2.2 数据库唯一性：生成列 + 条件唯一索引（MySQL 8.4 / H2 语义一致）

V4_019_migration_batch_reuse.sql（305 行，独立核对 SHA256=83FDA88C423E2E8ED8E23169ABD3CC6E31400978C1268E00F8DE6D5525C6C31F，与 `issue19-mysql84.txt` 第 98 行记录一致）
    1) Q1-Q4：精确目标集清单/统计、邻近异常、重复活动批次；
    2) issue19_preflight()：发现重复活动批次、CANCELLED 主申请但明细非 DRAFT/CANCELLED、
       非 CANCELLED 主申请但明细 CANCELLED 时 SIGNAL SQLSTATE '45000' 阻断；
    3) migration_in_issue19_backup：`in_id` 唯一，INSERT IGNORE 保留第一次快照
       （original_business_status/original_transfer_batch_no/original_version/application_status/application_version）；
    4) 新增 `active_transfer_batch_no VARCHAR(40) GENERATED ALWAYS AS
       (CASE WHEN business_status='CANCELLED' THEN NULL ELSE transfer_batch_no END) STORED`
       （information_schema.columns 守卫，重复执行安全）；
    5) 新增 `UNIQUE INDEX uk_migration_in_active_batch(active_transfer_batch_no)`，
       此时旧 uk_migration_in_batch 仍在，不存在无唯一保护窗口；
    6) 只对“application CANCELLED + migration_in DRAFT”精确目标集 UPDATE 为 CANCELLED、version+1，
       不改 transfer_batch_no，并用 `@issue19_backfill_expected` 与 `ROW_COUNT()` 校验；
    7) 仅在回填成功后 DROP `uk_migration_in_batch`（information_schema.statistics 守卫）；
    8) 脚本末尾保留 6.1-6.5 验证查询；失败/回滚原则完整。

并发正确性（MySQL REPEATABLE READ）
    - 唯一索引在 InnoDB 中是最终裁判：两个事务同时插入同一 active batch，最多一个成功；
      不依赖应用层 SELECT COUNT，也不依赖预锁。
    - t6 真实 MySQL 8.4.8 证据（issue19-mysql84.txt 第 165-225 行）记录 10 轮 claim：
      每轮恰好一个 SUCCESS、一个 DUPLICATE（ERROR 1062 on uk_migration_in_active_batch），
      final_active=1；另 10 轮 cancel/create interleave final_active 0/1，无双重有效占用；
      每轮记录 connection id、trx id、LOCK WAIT、final_rows。
    - 本次独立复核重新读取 `issue19-mysql84-schema.txt`：MySQL 8.4.8、REPEATABLE-READ、
      非 root 应用账号、旧索引 count=0、新索引存在、active_transfer_batch_no 为 STORED GENERATED，
      定义与 V4_019/新装基线一致。

H2 schema
    - src/test/resources/schema.sql 第 206-214 行：migration_in 的 transfer_batch_no 为普通可空列，
      新增 `active_transfer_batch_no` 计算列（同一 CASE 表达式；H2 2.3.232 不接受 STORED），
      并创建 `uk_migration_in_active_batch` 唯一索引。
    - 与 MySQL V4_019 语义等价：CANCELLED→NULL，其余状态保留 transfer_batch_no；NULL 可重复。
    - t5 证据与本次独立 H2 运行均确认 13/13 H2 测试通过（见 §3）。

新装基线
    - doc/database/population_miniterm.sql 第 528-536 行：移除旧 `uk_migration_in_batch`，
      新增同一 STORED 生成列与 `uk_migration_in_active_batch`。
    - 本次独立临时 MySQL 8.4.8 实例（端口 13374，非 3306，独立 datadir）实际加载修改后的
      `doc/database/population_miniterm.sql`：exit 0；查询结果：
        INDEX=uk_migration_in_active_batch, non_unique=0, column=active_transfer_batch_no
        COLUMN=active_transfer_batch_no, extra=STORED GENERATED,
               generation_expression=CASE WHEN business_status='CANCELLED' THEN NULL ELSE transfer_batch_no END
      临时 mysqld 已正常关闭，端口释放。

2.3 版本/迁移边界
    - V4_019 紧跟 V4_018，版本号正确；实际文件 305 行。
    - `git status doc/database/migrations` 只有 `?? V4_019_migration_batch_reuse.sql`；
      V4_003、V4_015-V4_018 无任何 diff。
    - V4_019 无 DELETE migration_in/business_application；不修改 transfer_batch_no；
      只对精确目标集做状态回填，符合“不静默删除/覆盖/重分类”。

2.4 API 文档
    - doc/api/household-migration-api.md：
        增加 DELETE /api/applications/{id} 取消语义；明确 CANCELLED 释放批次、原批次号保留、
        DRAFT/UNDER_REVIEW/APPROVED/COMPLETED 占用、REJECTED/WITHDRAWN/RETURNED 继续占用、
        409 文案与“未使用”定义。
    - doc/api/business-application-approval-api.md 与 backend-api-index.md 同步取消行为入口。
    - 与 `ApplicationService.cancelDraft`、`MigrationStatusListener`、`MigrationService.batchConflict`
      的实际实现/文案一致。

================================================================================
3. 测试与证据核验
================================================================================

3.1 RED（旧实现真实失败）
    - 证据文件 doc/development/evidence/issue19-red-h2.txt 记录了旧实现的 Maven 失败：
        .\mvnw.cmd -Dtest=Issue19MigrationBatchReuseIntegrationTest test
        exit=1（BUILD FAILURE）；Tests run: 4, Failures: 2, Errors: 0, Skipped: 0。
    - 本次复核核对 `target/issue19-red-run-utf8.log`：
        SHA256=858016B6E45D921EBE22829183FB8ED2C099383C13A2A91699C5753918337480，
        与 issue19-red-h2.txt 第 34-36 行记录一致；
        日志包含 `Tests run: 4, Failures: 2`、`ISSUE19_RED_DRAFT_B_HTTP status=409`、
        取消后 `migration_in.business_status=DRAFT` 快照、H2 `CONSTRAINT_INDEX_B6`/
        `CONSTRAINT_INDEX_B6 ON public.migration_in(transfer_batch_no...)` 的真实 23505 栈。
    - 真实 MySQL 8.4 旧 V4_018 schema RED：issue19-mysql84.txt 第 77-90 行记录
      `ERROR 1062 ... key 'migration_in.uk_migration_in_batch'`，事务回滚后
      orphan_application=0、该批次 migration_in_rows=1。
    - 结论：RED 是真实 Maven 失败、真实 HTTP 409、真实数据库快照/索引证据，不是静态断言或伪造异常。

3.2 GREEN（本次独立重跑，最终工作区）
    命令 1：
        .\mvnw.cmd -Dtest=Issue19MigrationBatchReuseIntegrationTest test
      exit code：0；Tests run: 13, Failures: 0, Errors: 0, Skipped: 0；BUILD SUCCESS；
      Finished at 2026-11-09T23:17:55+08:00；日志 target/reviewer-issue19-focused.log。
    命令 2：
        .\mvnw.cmd -Dtest=Phase03MigrationIntegrationTest,Issue33CrossDistrictCharacterizationIntegrationTest test
      exit code：0；Phase03 36/36、Issue33 6/6，合计 42/42，0 failures/0 errors；
      Finished at 2026-11-09T23:18:47+08:00；日志 target/reviewer-phase03-issue33.log。
    命令 3（全量回归，最终工作区）：
        .\mvnw.cmd test
      exit code：0；Maven Results `Tests run: 913, Failures: 0, Errors: 0, Skipped: 3`；
      BUILD SUCCESS，Total time 03:34 min，Finished at 2026-11-09T23:24:30+08:00；
      日志 target/reviewer-full-test.log（SHA256=15817D2B5C82209F459371678FCE9C485458B76DE681B558699CB8ACB68E1BEC）。
      其中关键类：Issue19 13/13、Phase03 36/36、Issue33 6/6、AiConfirm 15/15、AiPrecheck 36/36。
    说明：当前 `target/surefire-reports` 目录共有 78 个 XML/926 tests，是因为其中保留了 t13 的
    Issue19MySql84IT.xml（13 tests，不在 `mvn test` 默认范围内）与本次复核 H2 报告；
    全量 Maven 的官方 Results 为 913/0/0/3，故不以目录文件总数冒充全量测试数。

3.3 全量 Maven 历史证据一致性
    - t5 的 `target/verify2-surefire-reports` 备份目录：77 个 TEST-*.xml；
      本次复核独立累加其 `tests/failures/errors/skipped` = 913 / 0 / 0 / 3，
      与 `target/verify2-surefire-summary.txt` 和 `doc/development/evidence/issue19-h2-green.txt` §2.5 一致。
    - 本次最终工作区再次运行 `mvnw test` 也是 913/0/0/3，说明全量数字可复现，不是历史引用或拼接。

3.4 MySQL 8.4 证据与独立复核
    - issue19-mysql84.txt / issue19-mysql84-schema.txt：
        MySQL 8.4.8；端口 13370（非 3306）；bind 127.0.0.1；REPEATABLE-READ；
        非 root 应用账户 i19_46c30189ad@127.0.0.1；隔离 schema；
        旧 uk_migration_in_batch count=0；uk_migration_in_active_batch count=1；
        active_transfer_batch_no 为 STORED GENERATED（CANCELLED→NULL）；
        V4_019 连续两次应用 exit 0；历史目标行回填、备份表、无 DELETE、10+10 并发均通过。
    - Issue19MySql84IT 的当前 Surefire XML（t13）：
        tests=13, failures=0, errors=0, skipped=0；
        system-out 中 `ISSUE19 MYSQL identity version=8.4.8 port=13373 isolation=REPEATABLE-READ
        schema=population_miniterm_mysql_gate_issue19_t11_t13 user=i19_t11_t13@127.0.0.1` 出现 13 次；
        0 次 `No match found`；0 次 `H2VERSION`。
        说明 t10 Matcher 修复、t12 H2-only 诊断 guard 均生效。
    - issue19-mysql84-gate-rerun.txt ROUND 2 记录 t13 exact run 13/13 exit 0；与上述 XML 一致。
    - 本次独立临时 MySQL 8.4.8 新装基线烟测（§2.2）进一步验证修改后的
      `population_miniterm.sql` 不是仅静态修改，能在真实 MySQL 8.4.8 上直接执行并生成正确索引/生成列。

3.5 测试质量与绕过检查
    - A/B/C/E/F/G/H/I/J/K/L 主路径均经真实 Spring MVC + JWT + Service + MyBatis + H2 驱动；
      主 RED 场景 `cancelDraftReleasesBatchAndNewDraftCanBeCreated` 使用真实迁出执行链生成批次，
      不是直接伪造 migration_out 作为主证据。
    - `existingStatusOccupancyObservationMatrix` 中 synthetic migration_out 仅用于状态占用观察矩阵，
      文件注释明确标注；主 RED 场景不依赖该 synthetic fixture。
    - `failedDuplicateDraftCreationLeavesNoOrphanApplicationOrDetail` 验证无孤儿 application/detail；
      `cancellationListenerFailureRollsBackApplicationAndDetail` 通过真实 ApplicationDraftCancellationListener
      故障注入验证两表回滚；`concurrentClaimsAllowOnlyOneActiveDraft` / `cancelAndCreateInterleaving...`
      使用两个真实 HTTP 请求并发执行。
    - 未发现 `@Disabled`、Mockito mock 核心 Service、仅静态 assert 或伪造异常来“造绿”的证据。
    - MySQL 侧除 SQL gate 外，issue19-mysql84.txt 的 SUPPLEMENTARY 记录 verifier 临时子类 14/14
      HTTP 测试；主仓库不保存该临时类，属于补充证据，已明确标注为临时 verifier-owned，
      不冒充仓库内正式测试。

================================================================================
4. V4_019 专项审查清单（逐项核对）
================================================================================

[通过] 版本号：V4_019 在 V4_018 之后；无版本回退。
[通过] 追加性：只新增 V4_019 文件；V4_003、V4_015-V4_018 文件无 diff；旧脚本未被改。
[通过] 前置检查：Q1-Q4 + issue19_preflight() 三类阻断；异常明确 SQLSTATE 45000。
[通过] 历史取消记录：仅 CANCELLED 主申请 + DRAFT 明细的精确目标集；不碰其他状态；version+1；
         transfer_batch_no 不变；备份行保留原 DRAFT/原批次/原 version。
[通过] 备份：migration_in_issue19_backup 自动创建，INSERT IGNORE 保留第一次快照。
[通过] DDL 顺序：先建生成列，再建新唯一索引（旧索引仍在），回填，最后才 drop 旧索引；
         无“无唯一保护”窗口。
[通过] 失败处理/回滚：脚本注释与 doc/database/V4_019-migration-batch-reuse-upgrade.md 均有失败点、
         回滚原则、已有新复用后的禁止直接回滚说明。
[通过] 未静默删除/覆盖/重分类：脚本无 DELETE；只更新精确目标集的状态；不修改批次号；
         不修改 migration_out 或 business_application 行。
[通过] 与 H2/新装基线一致：同一 CASE 语义；MySQL STORED / H2 计算列；fresh baseline 实测可加载。

================================================================================
5. 文档与审计账本核对
================================================================================

5.1 审计账本（doc/testing/2026-11-02-antigravity-audit-ledger.md）
    - 第 19 行：`CODE_CANDIDATE`，不是 `FIXED_VERIFIED`；文字明确“等待独立复核，不标记为已修复/已验证”。
    - 第 33 行：与基线逐字一致 `OPEN`，状态/内容未变。
    - 账本 diff 只有第 19 行一处，无其他审计行被改写。

5.2 专项文档（doc/development/issue19-migration-batch-reuse.md，441 行）
    - §1-§12 覆盖六条静态发现（S1-S6）、方案 A/B/C/D 比较、业务契约 1-7、状态矩阵 10 行、
      生产改动逐文件、V4_019 历史升级、H2/MySQL 验证、全量 Maven 数字、A-L 矩阵、剩余风险。
    - 关键数字与证据文件一致（13/13、913/0/0/3、MySQL 8.4.8 10+10、t13 13/13）。
    - §10.2 透明记录 operation_log 基线既有空缺；§10.3 明确未执行生产存量/生产窗口/MySQL 之外版本。

5.3 V4_019 升级说明（doc/database/V4_019-migration-batch-reuse-upgrade.md，226 行）
    - 前置检查、统计/备份、DDL 顺序、升级后验证、失败处理、回滚（未复用前/已复用后）、
      新装基线一致性均有；与脚本实际内容一致。

5.4 API 文档
    - doc/api/household-migration-api.md 的取消/批次复用契约与实现一致；
    - doc/api/business-application-approval-api.md、backend-api-index.md 已同步入口说明。

5.5 #33 与范围
    - #33 保持 OPEN，Issue33 测试文件未修改；#33 只作为回归测试执行（6/6）。
    - 无其他审计项状态/代码/测试变更。

================================================================================
6. 非阻断观察（不影响 PASS；建议后续改进）
================================================================================

以下均不是本轮验收的阻断项，记录供后续维护参考：

O1（文档措辞，low）
    ApplicationDraftCancellationListener.java 第 9 行与 issue19-migration-batch-reuse.md §5.1
    提到 “cancellation/floating/export/key-population 状态枚举都不定义 CANCELLED”；
    实际 `CancellationStatus.java` 定义中包含 `CANCELLED`。opt-in 设计本身依然正确
    （只有 MigrationStatusListener 实现并 support），但建议后续把文档措辞改为
    “部分专用枚举（如 KeyApplicationStatus）不定义 CANCELLED”，避免误导。

O2（测试覆盖，low）
    Issue19MigrationBatchReuseIntegrationTest.genericDraftCancellationDoesNotBroadcastToSpecializedListeners
    （第 514-525 行）实际只创建/取消 `GENERAL_SERVICE`，并没有创建一个带专用 StatusListener 的
    非迁移类型（如 floating/cancellation）来验证“不广播”。实现静态检查确认 cancelDraft 只遍历
    opt-in 接口且仅 MigrationStatusListener 实现，因此当前功能安全；建议后续补一个专用业务类型
    草稿取消测试，使测试名称与覆盖内容一致。

O3（边界输入，low）
    MigrationService.createIn/updateIn（第 16/34 行）的 `requireBatchAvailable` 对任何非空
    `transferBatchNo` 都执行；`validateInType` 只强制 IN_CITY_CROSS_DISTRICT 必填，未禁止
    OUTSIDE_CITY 携带批次。极端情况下 OUTSIDE_CITY 请求带批次会得到“批次已被占用”而非忽略批次。
    正常 API/前端对 OUTSIDE_CITY 不传批次；建议后续把预检查限制在 IN_CITY_CROSS_DISTRICT，
    或在 validateInType 中明确拒绝 OUTSIDE_CITY 的批次。

O4（证据保留，low）
    t3 RED 的旧 `target/surefire-reports/TEST-...Issue19MigrationBatchReuseIntegrationTest.xml`
    已被后续 GREEN/复核运行覆盖；`issue19-red-h2.txt` 记录的旧报告 SHA256 无法在 target 当前文件上复核。
    本次用 `target/issue19-red-run-utf8.log`（SHA256 匹配证据记录）确认 RED 真实内容。
    建议后续把 RED 的 Surefire XML 原始副本归档到证据目录，避免 `target` 被覆盖造成可复核性下降。

O5（历史迁移边界，low）
    V4_019 只回填 migration_in 的 CANCELLED+DRAFT 历史目标集；未回填历史上 migration_out 的
    CANCELLED+DRAFT 行。由于 #19 的批次占用问题只在 migration_in，且 migration_out 草稿没有批次号，
    本轮验收不受影响；建议后续单独核对历史 migration_out 明细状态一致性。

O6（交付流程，process）
    HEAD 仍为基线 1f3f442...，t4 以来 17 个 tracked 文件修改与 12 个 untracked 文件尚未 commit；
    本轮按“保留 worktree 等待独立复核”流程，复核通过后应由船长/实现者完成提交与 feature 分支推送，
    并保留本复核证据。若最终交付要求“提交后复核”，需在提交后再次确认 commit diff 与本证据一致。

================================================================================
7. 验收准则逐项结论
================================================================================

[1] 逐文件审查实现（ApplicationService.cancelDraft 与专用 StatusListener 安全定向，
    不向不支持 CANCELLED 的业务类型广播；MigrationBusinessStatus/Listener/Mapper 状态联动）；
    MigrationService/Mapper 的状态联动 -> PASS（§2.1；O1/O2 为非阻断文档/测试覆盖观察）。
[2] 数据库唯一性（MySQL RR 双活动占用防护、历史取消记录支持、H2 与 MySQL 语义一致）
    -> PASS（§2.2；t6 10+10 并发 + 本次 V4_019/新装基线核对）。
[3] V4_019 版本、追加性、前置检查、历史取消记录受控回填、备份与失败处理、未静默删除/覆盖/重分类
    -> PASS（§2.3、§4）。
[4] RED/GREEN/MySQL 证据真实性：RED 为旧实现真实 Maven 失败并含 HTTP/DB 快照；GREEN 通过；
    MySQL 含真实实例身份、SHOW INDEX、并发每轮日志与回滚；全量 Maven 数字与 Surefire 一致
    -> PASS（§3；本次独立重跑 13/13、42/42、913/0/0/3）。
[5] 独立抽查：重新执行 #19 专项测试与 Phase03/Issue33 回归；无测试绕过/伪造异常；
    git diff --name-only 无越界文件 -> PASS（§3、§1.2-§1.4）。
[6] 文档与审计账本：CODE_CANDIDATE（非 FIXED_VERIFIED），其他审计问题状态未变，#33 未修改；
    专项文档、API/数据库升级文档与证据一致 -> PASS（§5）。

综合结论：PASS。未发现需要返工（needs_revision/reject）的阻断 findings。
建议后续仅按 §6 的 O1-O6 作为低优先级维护事项处理；不影响 #19 本轮验收。

================================================================================
END OF ISSUE19 REVIEW
================================================================================
