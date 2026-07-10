# MySQL 迁移验证记录

验证日期：2026-07-10。

当前开发环境未安装 `mysql`、`mysqld` 或 Docker，也没有提供可恢复的测试 MySQL 实例及凭据。因此本轮无法执行真实 MySQL 初始化和 V4_001 两次迁移验证，结果明确记为：**未执行，不得视为已通过实库验证**。

已完成的替代检查：第一阶段 H2 MySQL 模式集成测试 27 项全部通过；初始化脚本和迁移脚本未使用 `DROP TABLE`；V4_001 使用 `information_schema` 检查列、索引和约束，种子数据使用唯一键及幂等写入。

获得隔离 MySQL 8 环境后应执行：

```powershell
mysql -u root -p -e "CREATE DATABASE phase01_empty CHARACTER SET utf8mb4"
mysql -u root -p phase01_empty < doc/database/population_miniterm.sql
mysql -u root -p phase01_legacy < doc/database/migrations/V4_001_system_auth_rbac.sql
mysql -u root -p phase01_legacy < doc/database/migrations/V4_001_system_auth_rbac.sql
```

随后启动后端验证 `viewer`、`population`、`household`、`approver`、`admin` 的 BCrypt 登录，并在迁移前后核对 `person`、`household`、`migration_in`、`migration_out`、`certificate` 的行数和抽样主键。必须只使用可删除的空库或备份副本，禁止连接不可恢复的正式数据。
