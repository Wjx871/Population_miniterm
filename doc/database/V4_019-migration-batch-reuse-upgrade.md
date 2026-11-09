# V4_019 迁移批次复用数据库升级说明（Issue #19）

状态：`CODE_CANDIDATE`（已在隔离 MySQL 8.4.8 实例应用两次并通过验证；尚未用于生产，等待独立复核）。

- 迁移脚本：`doc/database/migrations/V4_019_migration_batch_reuse.sql`（305 行，MySQL 8.4）
- 前置版本：`V4_018_s4b_confirm_draft_binding.sql`
- 影响表：`migration_in`（新增生成列、条件唯一索引、删除旧唯一索引）；新增备份表 `migration_in_issue19_backup`
- 专项报告：`doc/development/issue19-migration-batch-reuse.md`
- 验证证据：`doc/development/evidence/issue19-mysql84.txt`、`issue19-mysql84-schema.txt`

## 1. 变更内容

1. 新增 `migration_in.active_transfer_batch_no VARCHAR(40) GENERATED ALWAYS AS (CASE WHEN business_status='CANCELLED' THEN NULL ELSE transfer_batch_no END) STORED`。
   - `business_status='CANCELLED'` 的行生成列为 `NULL`，不占用唯一键；
   - 其他状态（`DRAFT`/`UNDER_REVIEW`/`APPROVED`/`COMPLETED`/`REJECTED`/`WITHDRAWN`）保留原 `transfer_batch_no`，继续受唯一索引约束。
2. 新增 `UNIQUE INDEX uk_migration_in_active_batch(active_transfer_batch_no)`。
3. 对存量 `business_application.status='CANCELLED'` 且 `migration_in.business_status='DRAFT'` 的精确目标集，将明细状态受控回填为 `CANCELLED`（`version+1`；**不修改 `transfer_batch_no`**）。
4. 在回填完成且新索引存在后，删除旧 `uk_migration_in_batch(transfer_batch_no)`。
5. 新增 `migration_in_issue19_backup`，保留目标行原状态、原批次号、原版本，支持审计和人工回滚。
6. 迁移不删除 `migration_in`/`business_application` 行，不修改已发布 V4_003/V4_015-V4_018 脚本。

## 2. 升级前置检查（必须全部通过）

### 2.1 环境与权限

- 目标必须是专用数据库/隔离副本，已完成可恢复备份；不得对共享课程数据库执行迁移。
- 使用具备 `ALTER`、`CREATE`、`DROP`、`UPDATE` 以及 `CREATE ROUTINE`（脚本使用存储过程，执行 `CALL` 还需 `EXECUTE`）权限的维护账户；业务应用账户不应拥有 DDL 权限。
- 确认 MySQL 为 8.4（生成列 `STORED` 与 `information_schema` 语法依赖；MySQL 8.0.13+ 也支持生成列，但本脚本只在本任务 8.4.8 验证）。
- 停止应用写入窗口；记录当前应用版本、数据库版本、schema、`@@transaction_isolation`（应为 `REPEATABLE-READ`）与执行人。
- 核对 `V4_019` 的 `USE population_miniterm;`：生产 schema 名为 `population_miniterm` 时直接执行；隔离副本若改了 schema 名，必须用受控替换（t6 gate 脚本的替换逻辑见 `scripts/windows/tests/issue19-mysql84-gate.ps1`），不得在生产直接改脚本后执行。

### 2.2 结构与旧索引检查

```sql
SELECT table_schema, table_name, index_name, non_unique, column_name
FROM information_schema.statistics
WHERE table_schema = DATABASE() AND table_name = 'migration_in'
  AND index_name IN ('uk_migration_in_batch','uk_migration_in_active_batch')
ORDER BY index_name, seq_in_index;

SELECT column_name, is_generated, generation_expression
FROM information_schema.columns
WHERE table_schema = DATABASE() AND table_name = 'migration_in'
  AND column_name IN ('transfer_batch_no','active_transfer_batch_no','business_status');
```

预期：升级前旧 `uk_migration_in_batch` 存在且 `active_transfer_batch_no` 不存在；若脚本已部分执行过，按实际结构记录，脚本中的 `information_schema` 守卫会跳过已存在对象并重试。

### 2.3 存量数据统计（冻结基线）

V4_019 会自动执行 Q1-Q4（见脚本 51-92 行），执行前必须记录输出并核对：

```sql
-- Q1 精确目标明细（application CANCELLED + detail DRAFT）
SELECT a.application_id, a.application_no, a.status AS application_status,
       m.in_id, m.business_status, m.transfer_batch_no, m.person_id,
       m.to_region_code, m.to_household_id, m.version, m.created_at, m.updated_at
FROM business_application a
JOIN migration_in m ON m.application_id = a.application_id
WHERE a.business_type='MIGRATION_IN' AND a.status='CANCELLED' AND m.business_status='DRAFT'
ORDER BY a.application_id;

-- Q2 统计
SELECT COUNT(*) AS affected_rows,
       SUM(CASE WHEN m.transfer_batch_no IS NOT NULL THEN 1 ELSE 0 END) AS with_batch,
       SUM(CASE WHEN m.transfer_batch_no IS NULL THEN 1 ELSE 0 END) AS without_batch,
       COUNT(DISTINCT m.transfer_batch_no) AS distinct_batches,
       COALESCE(MAX(m.in_id),0) AS max_in_id, COALESCE(SUM(m.in_id),0) AS in_id_checksum
FROM business_application a
JOIN migration_in m ON m.application_id = a.application_id
WHERE a.business_type='MIGRATION_IN' AND a.status='CANCELLED' AND m.business_status='DRAFT';
```

同时运行脚本 Q3（邻近异常）和 Q4（重复活动批次），两者必须为空。`issue19_preflight()` 会对以下异常 `SIGNAL SQLSTATE '45000'` 阻断：

- `ISSUE19_PRECHECK_DUPLICATE_ACTIVE_BATCH`：非 CANCELLED 同批次活动行 >1；
- `ISSUE19_PRECHECK_CANCELLED_DETAIL_STATE_ANOMALY`：主申请已 CANCELLED 但明细不是 DRAFT/CANCELLED；
- `ISSUE19_PRECHECK_UNEXPECTED_CANCELLED_DETAIL`：主申请非 CANCELLED 但明细已 CANCELLED。

若出现上述任一错误或 Q3/Q4 非空：**停止升级并人工核对**；不得修改脚本绕过检查，不得自动修复非目标行。

## 3. 备份

### 3.1 数据库全量/逻辑备份

```powershell
mysqldump --single-transaction --routines --triggers --default-character-set=utf8mb4 `
  -u <backup_user> -p population_miniterm > issue19-pre-upgrade-<timestamp>.sql
# 记录 SHA256 与行数；恢复到隔离库验证可读后再开始 DDL
```

### 3.2 目标行备份表（V4_019 自动创建）

```sql
CREATE TABLE IF NOT EXISTS migration_in_issue19_backup (
  backup_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  in_id BIGINT NOT NULL,
  application_id BIGINT NOT NULL,
  original_business_status VARCHAR(30) NOT NULL,
  original_transfer_batch_no VARCHAR(40) NULL,
  original_version INT NOT NULL,
  application_status VARCHAR(30) NOT NULL,
  application_version INT NOT NULL,
  backup_run VARCHAR(64) NOT NULL,
  backup_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_issue19_backup_in (in_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

脚本用 `INSERT IGNORE` 写入精确目标行，重跑保留第一次快照（原 `DRAFT`/原批次/原版本），不覆盖。执行前必须确认备份表行数 = Q2 `affected_rows`；抽样核对 `transfer_batch_no` 原值。

## 4. DDL 执行步骤

在维护窗口按顺序执行（脚本已包含 2.3 的 Q1-Q4 与 preflight）：

1. **前置检查**：执行 `V4_019` 开头的 Q1-Q4 与 `issue19_preflight()`；任一失败停止。
2. **备份**：完成 3.1 全量 dump 与 3.2 备份表写入；核对行数/校验和。
3. **新增生成列**：`ALTER TABLE migration_in ADD COLUMN active_transfer_batch_no ... STORED`；`information_schema.columns` 守卫，重复执行安全。
4. **新增条件唯一索引**：`ADD UNIQUE INDEX uk_migration_in_active_batch(active_transfer_batch_no)`；此时旧 `uk_migration_in_batch` 仍在，不存在“无唯一保护”窗口。
5. **受控回填**：对精确目标集执行 `UPDATE migration_in ... SET business_status='CANCELLED', version=version+1, updated_at=CURRENT_TIMESTAMP`；不改 `transfer_batch_no`。脚本比较 `@issue19_backfill_expected` 与 `ROW_COUNT()`，不一致抛 `ISSUE19_BACKFILL_ROW_COUNT_MISMATCH`。
6. **删除旧索引**：回填成功后 `ALTER TABLE migration_in DROP INDEX uk_migration_in_batch`；若旧索引不存在，`information_schema` 守卫跳过。
7. **升级后验证**：执行脚本 §6 的 6.1-6.5（见下）。

执行命令示例（生产 schema 名固定为 `population_miniterm`）：

```powershell
mysql --default-character-set=utf8mb4 -u <admin_user> -p population_miniterm `
  < doc/database/migrations/V4_019_migration_batch_reuse.sql
```

> 不要用业务应用账号执行 DDL；不要把 `USE population_miniterm` 改成其他生产 schema 名后直接执行；隔离验证使用受控副本/替换 schema 的 gate 脚本。

## 5. 升级后验证

脚本末尾已包含以下查询；全部必须符合期望：

```sql
-- 5.1 目标异常应为 0
SELECT COUNT(*) AS remaining_cancelled_draft
FROM business_application a JOIN migration_in m ON m.application_id=a.application_id
WHERE a.business_type='MIGRATION_IN' AND a.status='CANCELLED' AND m.business_status='DRAFT';

-- 5.2 每个批次活动占用 <= 1（应无行）
SELECT transfer_batch_no, COUNT(*) AS active_rows
FROM migration_in WHERE active_transfer_batch_no IS NOT NULL
GROUP BY transfer_batch_no HAVING COUNT(*) > 1;

-- 5.3 取消行原值保留、active 为 NULL
SELECT in_id, application_id, business_status, transfer_batch_no, active_transfer_batch_no
FROM migration_in WHERE business_status='CANCELLED' ORDER BY in_id;

-- 5.4 只保留条件唯一索引
SELECT index_name, non_unique, column_name
FROM information_schema.statistics
WHERE table_schema=DATABASE() AND table_name='migration_in'
  AND index_name IN ('uk_migration_in_batch','uk_migration_in_active_batch')
ORDER BY index_name, seq_in_index;

-- 5.5 行数不变（无 DELETE）
SELECT (SELECT COUNT(*) FROM migration_in) AS migration_in_rows,
       (SELECT COUNT(*) FROM business_application) AS business_application_rows,
       (SELECT COUNT(*) FROM migration_in_issue19_backup) AS backup_rows;
```

另运行 `doc/database/check_backend_v1.sql`：所有 `abnormal_count` 必须为 0；重点检查新增 `multiple_active_migration_batch`、`cancelled_application_detail_not_cancelled`、`unexpected_cancelled_migration_detail`、`legacy_migration_batch_unique_index`，以及 `active_transfer_batch_no` 列和 `uk_migration_in_active_batch` 索引存在性。

## 6. 失败处理

| 失败点 | 处理 |
|---|---|
| Q3/Q4 非空或 preflight 抛 45000 | 阻断；人工核对目标/邻近数据；不得绕过或自动修复。 |
| 全量备份/备份表失败或行数不一致 | 阻断，不执行 DDL。 |
| 生成列/新唯一索引创建失败 | 旧 `uk_migration_in_batch` 仍在，数据仍受旧唯一性保护；修复后重跑（脚本可重复）。 |
| 回填失败/行数不一致 | 单条 UPDATE 回滚，业务状态/批次不变；修复后重跑。 |
| 删除旧索引失败 | 旧索引仍在，功能未生效但数据安全；修复后重试。 |
| 升级后验证失败 | 停止写入；检查备份表；必要时按 7.2 回滚；不得删除/覆盖历史数据。 |

## 7. 回滚原则

### 7.1 未发生新复用前

1. 停止应用写入；
2. 从 `migration_in_issue19_backup` 将目标行 `business_status`/`version` 恢复为 `original_business_status`/`original_version`（原 `transfer_batch_no` 从未改动，无需恢复）；
3. 重建 `uk_migration_in_batch(transfer_batch_no)`，确认旧批次唯一性可恢复；
4. 删除 `uk_migration_in_active_batch` 与 `active_transfer_batch_no`（或保留列但回退到旧索引；建议按运维评审执行）；
5. 运行旧结构 `check_backend_v1.sql`（旧版本检查项）+ 核心 API 烟测。

### 7.2 已有新申请复用原批次后

**禁止直接回滚为旧 `uk_migration_in_batch`**：新申请复用原取消批次后，旧索引会与新活动行冲突，可能导致迁移无法重启。必须先对账：

- 找出 `migration_in_issue19_backup` 中已释放、且已被新申请引用的批次；
- 与业务确认撤销/保留新申请；
- 由 DBA 制定一次性数据迁移/索引重建方案并评审；不得静默删除/覆盖历史数据。

### 7.3 回滚验证

- `SELECT index_name...` 确认恢复后的索引与升级前一致；
- `check_backend_v1.sql` 与核心迁移 API 烟测通过；
- 保留 `issue19-pre-upgrade-<timestamp>.sql` 与备份表至少至独立复核结束后按数据保留制度归档。

## 8. 新装基线一致性

`doc/database/population_miniterm.sql` 已同步：

- `migration_in` 不再定义 `uk_migration_in_batch`；
- 第 535-536 行定义 `active_transfer_batch_no`（`STORED` 生成列）与 `uk_migration_in_active_batch`；
- `doc/database/check_backend_v1.sql` 包含新结构/状态一致性检查。

因此：

- **全新安装**：直接执行 `population_miniterm.sql`，得到与 V4_019 升级后一致的 `migration_in` 结构（新装无存量数据，无需回填；`migration_in_issue19_backup` 仅升级路径创建，fresh install 不创建）；
- **已有库**：不重跑 fresh 脚本；按本说明执行 `V4_019`；
- 两条路径后续执行 `check_backend_v1.sql` 均要求 abnormal_count=0。

## 9. 本轮实际验证状态

- 已在临时隔离 **MySQL 8.4.8**（端口 13370、`REPEATABLE-READ`、非 root 应用账号、独立 schema）执行：
  - V4_011～V4_013、V4_015～V4_018 历史链 exit 0（V4_014 基线已含 initial_scope 列，未重复应用）；
  - 旧 V4_018 schema 真实 RED：重复批次 `ERROR 1062 ... uk_migration_in_batch`；
  - V4_019 **连续应用两次均成功**；
  - 旧索引消失、`uk_migration_in_active_batch` 存在、`active_transfer_batch_no` 为 `STORED` 生成列；
  - 历史目标行回填：首跑 `expected=2/updated=2/backup=2`，第二跑 `0/0/2`；升级前后行数 `3|4 → 3|4`；
  - 10 轮抢占 + 10 轮取消/新建交错通过；临时 mysqld 已关闭（13370 不再监听）。
- 完整原始输出：`doc/development/evidence/issue19-mysql84.txt`、`issue19-mysql84-schema.txt`。
- **未验证**：生产库实际存量规模、生产维护窗口执行、MySQL 8.4 以外版本、生产回滚演练；不得把上述隔离实例结果写成生产已执行。
