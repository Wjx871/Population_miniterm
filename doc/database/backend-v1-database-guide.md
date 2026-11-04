# Backend V1 数据库、备份与恢复

## 全新安装

在 MySQL 8 上用 utf8mb4 执行 `population_miniterm.sql`。演示环境可继续执行两个 demo 脚本；生产环境不得导入演示账号。完成后运行 `check_backend_v1.sql`，所有 abnormal_count 必须为 0。

## 增量升级

先做一致性备份，再从当前版本之后按编号顺序执行尚未应用的迁移，当前最新脚本为 `V4_015_ai_precheck_async.sql`。不得跳号、修改已发布脚本或在失败后继续。脚本设计为可重复执行，但仍应先在隔离副本演练。S3 迁移仅新增 AI 暂存、预审任务、关联、容量、Outbox、Inbox、审计表及两个最小权限，不修改既有人口、迁入或审批表。不要对共享课程数据库执行迁移。旧 residents 仅用于历史数据迁移，最终正式人口模型是 person。

## 备份与恢复

```powershell
mysqldump --single-transaction --routines --triggers -u backup_user -p population_miniterm > backend-v1.sql
mysql -u restore_user -p population_miniterm < backend-v1.sql
```

备份文件应加密、限制访问并校验哈希。恢复到隔离库后运行 `check_backend_v1.sql`、核对表/索引/权限/字典数量并执行核心 API 烟测，确认无异常后再切换。Redis 无需备份，因为它不保存业务事实。
