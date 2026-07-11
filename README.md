# Population Miniterm

## 第三阶段：户籍迁移闭环

系统现支持申请制迁入/迁出、单级审批后的显式业务执行、当前户籍唯一登记、家庭成员同步、迁出历史快照、户主变更及同市跨区批次关联。操作顺序为：创建迁移草稿 → 上传必需材料 → 提交/审批 → 授权经办人确认执行。审批通过不会自动改变户籍。

增量升级依次执行 `V4_001_system_auth_rbac.sql`、`V4_002_business_application_approval.sql`、`V4_003_household_migration_archive.sql`。数据库通过 `DB_USERNAME/DB_PASSWORD`，JWT 通过 `JWT_SECRET` 配置，上传目录通过 `APP_UPLOAD_DIR` 配置。演示账号仍为 `viewer/population/household/approver/admin`，课程环境初始密码 `123456`。

## 第四阶段：注销管理

系统支持人员死亡/普通注销申请、家庭户销户申请、材料与单级审批、审批后的显式执行、个人户籍归档和家庭户快照。人员与家庭户主档始终保留，注销不可在本阶段直接撤销。增量升级新增 `V4_004_cancellation_and_household_archive.sql`。

第五阶段已实现流动人口居住登记、当前与历史记录、居住证首次申领/签发/签注/注销、生命周期日志、关闭联动、自动到期和到期提醒。流动登记不创建户籍，居住证不再写入通用 `certificate`。

数据库增量脚本为 `doc/database/migrations/V4_005_floating_population_residence_permit.sql`。环境变量：`RESIDENCE_PERMIT_MIN_RESIDENCE_DAYS`（180）、`RESIDENCE_PERMIT_VALIDITY_DAYS`（365）、`RESIDENCE_PERMIT_ENDORSEMENT_EARLY_DAYS`（30）、`RESIDENCE_PERMIT_EXPIRY_WARNING_DAYS`（30）、`RESIDENCE_PERMIT_EXPIRY_CRON`（每天 02:00）。

演示流程：使用 `population` 创建申请和上传材料，使用 `approver` 审批，使用 `household` 或 `admin` 显式执行。演示账号密码沿用课程环境 `123456`，仅供本地课程数据。

重点人口、刑满释放恢复登记、多级审批、补换领、真实制卡和政务联网仍未实现。当前没有可用真实 MySQL 8 实例；迁移脚本已静态检查并由 H2 等价结构测试覆盖，但不得视为真实 MySQL 实机迁移验证。

第六阶段已完成接口权限收口、统一敏感字段脱敏、普通脱敏 XLSX 导出、敏感导出审批与显式执行、安全下载、SHA-256、下载审计和过期清理。数据库脚本为 `V4_006_export_audit_permission.sql`。

导出环境变量：`EXPORT_DIR`（默认 `./data/exports`）、`EXPORT_NORMAL_MAX_ROWS`（5000）、`EXPORT_SENSITIVE_MAX_ROWS`（20000）、`EXPORT_FILE_RETENTION_DAYS`（7）、`EXPORT_CLEANUP_CRON`（每天 02:30）。导出目录不会提交 Git。MySQL 8 实机验证仍留待第七阶段，后续工作以项目集成验收和最终答辩收口为主。

人口数据库管理系统课程项目。后端使用 Java 17、Spring Boot 3.5.3、Spring Web、Spring Security、普通 MyBatis 和 MySQL；前端使用 Vue 3、Vite、Element Plus、Pinia、Vue Router 和 Axios。项目没有使用 Spring Data JPA。

## 数据库

新环境在 MySQL 8 中执行：

```powershell
mysql -u root -p < doc/database/population_miniterm.sql
```

已有数据库执行增量升级：

```powershell
mysql -u root -p population_miniterm < doc/database/migrations/V4_001_system_auth_rbac.sql
```

迁移保留已有业务表和数据，不使用 `DROP TABLE` 重建业务表。

## 启动后端

JWT 使用 HMAC 密钥，生产或共享环境必须通过环境变量提供至少 32 个 UTF-8 字节的随机密钥：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的本地 MySQL 密码"
$env:JWT_SECRET="请替换为至少32字节的随机密钥"
$env:JWT_EXPIRE_MINUTES="120"
.\mvnw.cmd spring-boot:run
```

`application.properties` 中仅有本地开发占位密钥，不能用于生产。默认后端地址为 `http://localhost:8080`。

## 启动前端

```powershell
cd frontend
npm install
npm run dev
```

Vite 默认将 `/api` 代理到 `http://127.0.0.1:8080`。

## 登录与测试账号

登录接口为 `POST /api/auth/login`，当前用户为 `GET /api/auth/me`，退出为 `POST /api/auth/logout`。受保护接口使用 `Authorization: Bearer <token>`。

| 账号 | 角色 | 等级 | 数据范围 |
| --- | --- | --- | --- |
| viewer | QUERY_VIEWER | L1 | DEPARTMENT |
| population | POPULATION_MANAGER | L2 | REGION |
| household | HOUSEHOLD_MANAGER | L2 | REGION |
| approver | APPROVER | L3 | REGION |
| admin | SYSTEM_ADMIN | L3 | ALL |

以上账号的本地课程演示初始密码均为 `123456`，数据库中仅保存 BCrypt 哈希。它们仅用于本地课程演示，首次部署后应立即修改密码。

## 权限模型

角色表示用户身份；`role_level` 表示最高操作等级；`data_scope` 表示数据可见范围。当前示范性保护 `GET /api/persons`、`POST /api/persons` 和 `GET /api/statistics/logs`，其他业务接口当前至少要求登录，后续再逐模块补齐细粒度权限。

详细接口见 `doc/api/auth-rbac-api.md`，审计与范围见 `doc/development/phase-01-auth-rbac-audit.md`。

## 测试

```powershell
.\mvnw.cmd test
cd frontend
npm install
npm run build
```

## 当前阶段边界

当前已提供登录、JWT、三级权限、数据范围基础，以及可复用的业务申请、材料上传和单级审批状态流转。上传目录通过 `APP_UPLOAD_DIR` 配置，单文件上限通过 `APP_UPLOAD_MAX_SIZE_MB` 配置。

审批通过仅形成 `APPROVED` 申请，尚未执行实际销户、户籍归档、迁入迁出落表、重点人口变更或敏感导出生成。
