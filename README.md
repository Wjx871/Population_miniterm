# Population Miniterm

后端已进入 Phase 11：提供综合人口/家庭户/迁移历史查询、统一统计口径、只读日志查询，以及默认关闭、故障可降级的 Redis 缓存与 JWT 注销撤销。MySQL 始终是唯一事实来源；Redis 不保存人口敏感业务数据。详见 `doc/development/phase-11-query-redis-summary.md`。

人口数据库管理系统课程项目。后端使用 Java 17、Spring Boot 3.5.3、Spring Web、Spring Security、普通 MyBatis 和 MySQL；前端使用 Vue 3、Vite、Element Plus、Pinia、Vue Router 和 Axios。

## 第三阶段：户籍迁移闭环

系统现支持申请制迁入/迁出、单级审批后的显式业务执行、当前户籍唯一登记、家庭成员同步、迁出历史快照、户主变更及同市跨区批次关联。操作顺序为：创建迁移草稿 → 上传必需材料 → 提交/审批 → 授权经办人确认执行。审批通过不会自动改变户籍。

增量升级依次执行 `doc/database/migrations` 下的 `V4_001` 至 `V4_007`。数据库通过 `DB_URL/DB_USERNAME/DB_PASSWORD`，JWT 通过 `JWT_SECRET` 配置，上传目录通过 `APP_UPLOAD_DIR` 配置。演示账号仍为 `viewer/population/household/approver/admin`，课程环境初始密码 `123456`。

## 第四阶段：注销管理

系统支持人员死亡/普通注销申请、家庭户销户申请、材料与单级审批、审批后的显式执行、个人户籍归档和家庭户快照。人员与家庭户主档始终保留，注销不可在本阶段直接撤销。增量升级新增 `V4_004_cancellation_and_household_archive.sql`。

第五阶段已实现流动人口居住登记、当前与历史记录、居住证首次申领/签发/签注/注销、生命周期日志、关闭联动、自动到期和到期提醒。流动登记不创建户籍，居住证不再写入通用 `certificate`。

数据库增量脚本为 `doc/database/migrations/V4_005_floating_population_residence_permit.sql`。环境变量：`RESIDENCE_PERMIT_MIN_RESIDENCE_DAYS`（180）、`RESIDENCE_PERMIT_VALIDITY_DAYS`（365）、`RESIDENCE_PERMIT_ENDORSEMENT_EARLY_DAYS`（30）、`RESIDENCE_PERMIT_EXPIRY_WARNING_DAYS`（30）、`RESIDENCE_PERMIT_EXPIRY_CRON`（每天 02:00）。

演示流程：使用 `population` 创建申请和上传材料，使用 `approver` 审批，使用 `household` 或 `admin` 显式执行。演示账号密码沿用课程环境 `123456`，仅供本地课程数据。

重点人口、刑满释放恢复登记、多级审批、补换领、真实制卡和政务联网仍未实现。

第六阶段已完成接口权限收口、统一敏感字段脱敏、普通脱敏 XLSX 导出、敏感导出审批与显式执行、安全下载、SHA-256、下载审计和过期清理。数据库脚本为 `V4_006_export_audit_permission.sql`。

导出环境变量：`EXPORT_DIR`（默认 `./data/exports`）、`EXPORT_NORMAL_MAX_ROWS`（5000）、`EXPORT_SENSITIVE_MAX_ROWS`（20000）、`EXPORT_FILE_RETENTION_DAYS`（7）、`EXPORT_CLEANUP_CRON`（每天 02:30）。导出目录不会提交 Git。

第七阶段已在隔离的 MySQL Community Server 8.4.10 LTS 上完成完整初始化、历史结构升级、V4_001-V4_006 重复执行、应用启动和五角色 API 烟测。验证脚本见 `scripts/verify-mysql.ps1`，结果见 `doc/testing/end-to-end-regression-report.md`。

第八阶段以 `person` 为唯一正式人口主模型，停用旧 `/api/residents` 和对应生产 Mapper，并禁止普通人口删除；人员注销继续走既有注销申请、审批和显式执行流程。新增 GB 11643 身份证校验，以及 `/api/households` 家庭户分页、详情、维护、成员离户和事务性户主变更接口。升级脚本为 `V4_007_household_master_data.sql`。

第八阶段已在隔离的 MySQL Community Server 8.4.10 上完成全新初始化、从 V4_006 历史状态连续三次执行 V4_007、应用启动、API/权限/数据范围、一致性和原业务回归验收。legacy `residents` 表保留但生产代码零依赖；完整证据见 `doc/testing/phase-08-household-master-data-test-report.md`。

第九阶段完成行政区划树和受控维护、数据字典查询与维护、通用证件查询/创建/修改/软注销，并统一权限、人员数据范围、敏感字段脱敏和写审计。居住证继续由专业 `residence_permit` 模块管理。升级脚本为 `V4_008_reference_data_certificate.sql`，真实 MySQL 8.4.10 验收见 `doc/testing/phase-09-reference-certificate-test-report.md`。

第十阶段完成重点人口建档申请、通用材料与审批、审批后显式建档、解除申请与显式解除、当前记录和追加式历史。数据范围、脱敏、审计和幂等约束完整，审批不会自动修改重点人口业务表。升级脚本为 `V4_009_key_population.sql`。

第十阶段最终门禁已在 H2 和 MySQL Community Server 8.4.10 上验证 viewer 只读权限、建档/解除双线程并发 execute、历史唯一性和中途异常完整回滚；真实 MySQL 并发结果为一个成功、一个 409。详细证据见 `doc/testing/phase-10-key-population-test-report.md`。

## 数据库

新环境在 MySQL 8 中执行：

```powershell
mysql -u root -p < doc/database/population_miniterm.sql
```

已有数据库在备份后按文件名顺序执行 V4_001 至 V4_009。也可以先在明确命名的测试库中运行：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/population_miniterm_upgrade'
$env:DB_USERNAME='root'
$env:DB_PASSWORD='<通过安全方式设置>'
.\scripts\verify-mysql.ps1 -Mode Repeat -MySqlClient 'C:\path\to\mysql.exe'
```

迁移保留已有业务表和数据，不使用 `DROP TABLE` 重建业务表。

`doc/database/demo_data.sql` 只用于可丢弃的课程演示环境，包含可重复执行的虚构人员、户籍、流动人口和即将到期居住证数据，不得用于生产。

## Windows 一键启动（推荐）

课程本地开发环境推荐使用一键启动脚本，无需手动设置环境变量。

### 首次配置

```cmd
copy config\start.local.env.example start.local.env
```

用记事本编辑项目根目录的 `start.local.env`，至少填写 `DB_PASSWORD`。该文件已被 `.gitignore` 忽略，不会提交。

### 启动

双击项目根目录的 `start.bat` 即可一键启动前后端。根目录只保留这一处入口，具体实现位于 `scripts/windows/`：

| 脚本 | 作用 |
|------|------|
| `start.bat` | 根目录统一入口，调用一键启动流程（推荐） |
| `scripts/windows/start_all.bat` | 独立窗口启动后端，等待就绪后启动前端 |
| `scripts/windows/start_backend.bat` | 仅启动后端 |
| `scripts/windows/start_frontend.bat` | 仅启动前端（会探测后端是否在线，未就绪时给出警告） |

### 端口配置

默认端口：后端 `http://127.0.0.1:8080`，前端 `http://localhost:5180`。

如需自定义端口，修改项目根目录的 `start.local.env`：

```
SERVER_PORT=18080
FRONTEND_PORT=15180
```

修改 `SERVER_PORT` 后，一键启动脚本的后端探测和 Vite 代理目标都会自动同步，无需手动修改 `vite.config.js`。

### 注意事项

- `start.local.env`、`.env.backend`、`frontend/.env` 均为本地文件，已被 `.gitignore` 忽略，不得提交。
- 关闭前端窗口只停止前端；后端在独立窗口运行，需单独关闭。
- Vite 使用 `strictPort: true`，端口被占用时会直接报错而非自动切换端口。

## 启动后端（手动）

JWT 使用 HMAC 密钥，生产或共享环境必须通过环境变量提供至少 32 个 UTF-8 字节的随机密钥：

```powershell
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的本地 MySQL 密码"
$env:JWT_SECRET="请替换为至少32字节的随机密钥"
$env:JWT_EXPIRE_MINUTES="120"
.\mvnw.cmd spring-boot:run
```

`application.properties` 中仅有本地开发占位密钥，不能用于生产。默认后端地址为 `http://localhost:8080`。

## 启动前端（手动）

```powershell
cd frontend
npm ci
npm run dev
```

Vite 将 `/api` 代理到后端地址（默认 `http://127.0.0.1:8080`），代理目标由 `scripts/windows/start_frontend.bat` 通过 `VITE_BACKEND_TARGET` 传入，也可在 `start.local.env` 中通过 `SERVER_PORT` 控制。

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

角色表示用户身份；`role_level` 表示最高操作等级；`data_scope` 表示数据可见范围。所有 Controller 方法均已纳入权限审计；登录是唯一匿名业务入口。查询、写入、审批、执行、下载、日志和系统管理使用独立权限。

详细接口见 `doc/api/auth-rbac-api.md`，审计与范围见 `doc/development/phase-01-auth-rbac-audit.md`。

## 测试

```powershell
.\mvnw.cmd test
cd frontend
npm ci
npm run build
```

## 当前阶段边界

当前已提供登录、JWT、三级权限、数据范围、统一 person 人口主模型、严格身份证校验、家庭户与成员管理、安全户主变更、行政区划、数据字典、通用证件、业务申请、材料上传、单级审批、迁入迁出、注销、流动人口、居住证、普通/敏感导出和审计闭环。专业业务审批通过后仍需授权人员显式执行。

尚未实现重点人口完整业务、刑满释放恢复登记、多级审批、消息队列、微服务、云存储、实体制卡、政务平台对接、短信和邮件。这些内容不属于当前验收范围。
