# Population Miniterm

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
