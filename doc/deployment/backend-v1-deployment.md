# Backend V1 原生部署指南

## 前置条件

安装 Java 17 和 MySQL 8。Redis 7 为可选增强，不安装也能运行；Docker 不是必需条件。复制 `.env.example` 的变量到操作系统或本地忽略文件，生成至少 32 UTF-8 字节的随机 `JWT_SECRET`。

## 数据库与构建

全新安装使用显式工具 `scripts/windows/init_database.ps1 -Mode Fresh`；默认只允许新建隔离验证库，拒绝非空库和课程库。课程演示数据仅在 `-DemoData` 时导入。历史环境按 `backend-v1-database-guide.md` 依次执行 V4_001—V4_010，绝不使用 Fresh 进行升级。构建：

```powershell
.\mvnw.cmd clean package
java -jar target\population-miniterm-1.0.0.jar
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
java -jar target\population-miniterm-1.0.0.jar
```

访问 `GET /api/health`，确认 HTTP 200、响应 `code=200` 且 `data.database=UP`。`redisStatus=DISABLED` 是允许的降级状态，不阻断启动。应用会在首次文件操作时使用配置的上传/导出目录；运行账户必须拥有创建和读写权限。生产环境不得使用演示账号密码。
