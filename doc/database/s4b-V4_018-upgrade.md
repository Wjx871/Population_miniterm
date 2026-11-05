# V4_018 S4-B 数据库升级说明

## 内容

`migrations/V4_018_s4b_confirm_draft_binding.sql` 必须在 V4_015、V4_016、V4_017 后执行。迁移：

- 新增最小授权权限 `ai:precheck:confirm`，只授予 `HOUSEHOLD_MANAGER` 与 `SYSTEM_ADMIN`。
- 为 `application_material` 增加 `storage_kind`（既有记录缺省 `LOCAL`）与不透明 `storage_reference`，并建立来源引用唯一索引。
- 新增 `ai_confirmation_receipt`，以 task 唯一约束和 user/task/idempotencyKey 唯一约束保证幂等。
- 新增 `ai_stage_material_binding`，对 stageMaterialId 和 materialId 分别唯一，并保存 ACTIVE/RELEASE_PENDING/RELEASED 清理状态。

迁移不修改 V4_015～V4_017，不修改 person、household、migration_in 或既有 S2/S3 状态机。

## 升级前检查

1. 先确认目标数据库是专用隔离库并已有可恢复备份；不得把团队共享 MySQL 用作验证库。
2. 核对已应用迁移顺序及 `application_material` 当前结构；执行前确认未存在同名字段、索引或新表。
3. 停止旧版本写入窗口，记录当前应用版本和迁移记录；迁移过程中不得运行 S4-B 确认流量。

## 执行后核对

- 确认新 permission 和两角色映射存在，其他角色未获授权。
- 确认旧 `application_material` 行的 `storage_kind='LOCAL'` 且 `storage_reference IS NULL`。
- 检查 receipt 的 task 唯一约束、binding 的 stage/material 唯一约束和外键。
- 检查新建 receipt/binding 表为空；S4-B 服务启用后，以 H2 集成测试验证事务和绑定行为。

## 回滚原则

若尚未有 S4-B 生产写入，先关闭新版 S4-B 流量并备份 V4_018 新表；可按维护流程撤销确认权限、新表、索引与字段。若已有 AI_STAGE 正式材料、确认回执或 RELEASE_PENDING 文件清理记录，不得直接删除新表/字段；先恢复/导出绑定与回执事实，再制定数据迁移。回滚流程不在本轮真实 MySQL 上执行或验证。

## 本轮验证状态

本次只运行隔离 H2 集成测试并静态核对 MySQL 8 DDL；团队共享 MySQL 未访问。真实 MySQL V4_018 执行状态为 `NOT_VERIFIED`。
