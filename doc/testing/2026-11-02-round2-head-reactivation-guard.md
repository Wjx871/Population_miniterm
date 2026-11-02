# 第二批户主关系：历史户主迁回保护

## 基线与旧实现复现

- 基线：`0750ce95aba8bbc8ba10e53d492da82d0f0bcdfa`
- 分支：`fix/20261102-round-head-reactivation-guard`
- `Phase03MigrationIntegrationTest.formerHeadMovingBackDoesNotCreateSecondActiveHead` 通过 API 完成原户主 A 迁出、继任者 B 接任，再完成 A 迁回原家庭户。
- 旧实现迁回后查询到 ACTIVE HEAD 人员为 `[101, 103]`，确认 A、B 同时成为有效户主。

## 根因与修复

迁出交接保留 A 的历史 `relationship='HEAD'`，并将其状态设为 `LEFT`；B 被提升为 `HEAD`。原 `reactivateMember` 只恢复状态，未调整关系，因此 A 再迁入时重新成为 ACTIVE HEAD。

重激活 SQL 现在仅在历史关系为 `HEAD` 时将其改为项目已有的普通成员关系 `MEMBER`；`CHILD`、`OTHER` 等其他关系保持不变。迁入执行检查重激活影响行数：无历史行则按既有流程新增普通成员，恰好一行则正常恢复，多行则拒绝并由事务回滚，避免产生重复有效成员。没有清理历史 LEFT 记录，也未修改迁出申请记录或 `household.head_person_id`。

无需修改 Mapper 接口：`reactivateMember` 已返回受影响行数，足以执行上述保护。

## 验证

- 原户主迁回：ACTIVE HEAD 只有 B；A 恢复为 ACTIVE `MEMBER`；A 只有一条 ACTIVE 成员关系；原迁出申请仍为 COMPLETED。
- 非户主迁出再迁入：保留 `CHILD` 关系，户主指针仍指向 A。
- 多条重复历史关系：迁入返回冲突，户籍、成员和申请状态回滚。
- 普通历史 `OTHER` 成员重激活、无户主家庭户最后成员销户、同市跨区迁移和迁入故障回滚均通过。
- Phase03 定向测试：33 通过，0 失败，0 错误，0 跳过。
- `mvnw.cmd clean test`：442 通过，0 失败，0 错误，0 跳过；BUILD SUCCESS。
- 使用隔离 H2 和合成数据，未连接真实人口数据库。`git diff --check` 通过。

## 范围与限制

- 未更改数据库 Schema、API DTO 或历史迁出记录。
- 实际 MySQL 未验证；需在目标 MySQL 版本上确认 `CASE` 更新及事务行为。

## 修改文件

- `src/main/java/com/wjx871/population/migration/MigrationService.java`
- `src/main/resources/mapper/MigrationMapper.xml`
- `src/test/java/com/wjx871/population/migration/Phase03MigrationIntegrationTest.java`
- `doc/testing/2026-11-02-round2-head-reactivation-guard.md`
