# Backend V1 数据库、备份与恢复

## 全新安装

在 MySQL 8 上用 utf8mb4 执行 `population_miniterm.sql`。演示环境可继续执行两个 demo 脚本；生产环境不得导入演示账号。完成后运行 `check_backend_v1.sql`，所有 abnormal_count 必须为 0。

## 增量升级

先做一致性备份，再从当前版本之后按编号顺序执行尚未应用的迁移，当前最新脚本为 `V4_019_migration_batch_reuse.sql`。不得跳号、修改已发布脚本或在失败后继续；版本化迁移按一次性升级脚本执行，先在隔离副本演练。`V4_017`/`V4_018` 分别为 S4-A 保留索引、S4-B 确认草稿绑定的迁移；`V4_019` 为 #19 取消草稿批次释放，详见 [V4_019-migration-batch-reuse-upgrade.md](V4_019-migration-batch-reuse-upgrade.md)。V4_016 为人员增加单调版本，并新增 AI 解析会话/候选令牌表；不修改 `business_application`、`migration_in` 或 `application_material`。不要对共享课程数据库执行迁移。旧 residents 仅用于历史数据迁移，最终正式人口模型是 person。

## 备份与恢复

```powershell
mysqldump --single-transaction --routines --triggers -u backup_user -p population_miniterm > backend-v1.sql
mysql -u restore_user -p population_miniterm < backend-v1.sql
```

备份文件应加密、限制访问并校验哈希。恢复到隔离库后运行 `check_backend_v1.sql`、核对表/索引/权限/字典数量并执行核心 API 烟测，确认无异常后再切换。Redis 无需备份，因为它不保存业务事实。
