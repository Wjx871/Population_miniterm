# Backend V1 原生部署指南

## 前置条件

安装 Java 17 和 MySQL 8。Redis 7 为可选增强，不安装也能运行；Docker 不是必需条件。复制 `.env.example` 的变量到操作系统或本地忽略文件，生成至少 32 UTF-8 字节的随机 `JWT_SECRET`。

## 数据库与构建

全新安装按顺序执行 `doc/database/population_miniterm.sql`、`demo_data.sql`、`demo_data_household_migration.sql`。历史环境按 `backend-v1-database-guide.md` 依次执行 V4_001—V4_010。构建：

```powershell
.\mvnw.cmd clean package
java -jar target\population-miniterm-0.0.1-SNAPSHOT.jar
```

PowerShell 示例：

```powershell
$env:DB_URL='jdbc:mysql://127.0.0.1:3306/population_miniterm?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai'
$env:DB_USERNAME='population_app'
$env:DB_PASSWORD='<secret>'
$env:JWT_SECRET='<32-byte-or-longer-random-secret>'
$env:REDIS_ENABLED='false'
$env:APP_UPLOAD_DIR='.\data\uploads'
$env:EXPORT_DIR='.\data\exports'
$env:LOG_DIR='.\logs'
java -jar target\population-miniterm-0.0.1-SNAPSHOT.jar
```

访问 `GET /api/health`，确认 database=UP；启用 Redis 时还应为 redisStatus=UP。应用会在首次文件操作时使用配置的上传/导出目录；运行账户必须拥有创建和读写权限。生产环境不得使用演示账号密码。
